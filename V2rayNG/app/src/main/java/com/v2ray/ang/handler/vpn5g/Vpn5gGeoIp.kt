package com.v2ray.ang.handler.vpn5g

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Bundled IP → country lookup (no runtime geo API).
 *
 * The table lives in assets/vpn5g/ip_country_ranges.txt (see
 * docs/generate-vpn5g-assets.py): merged IPv4 ranges, one line per range
 * "<startInt> <endInt> <CC>", sorted by startInt. Lookup is a binary search
 * over the starts array; the table is loaded once, on first use, on a
 * background thread. A missing or corrupt asset degrades to "unknown"
 * (globe) — never a crash, never a blocked list.
 *
 * Country resolution order for a server:
 *  1. The config remark (e.g. "US | @Raydikalx | 853160", "🇫🇷 فرانسه",
 *     or a Persian country name) — free, no I/O.
 *  2. The server host resolved to an IP, looked up in the bundled table.
 * DNS results are cached per host for the process lifetime.
 */
object Vpn5gGeoIp {

    private const val ASSET_PATH = "vpn5g/ip_country_ranges.txt"
    private const val DNS_TIMEOUT_MS = 800L

    data class Country(
        /** ISO 3166-1 alpha-2 code, upper-case ("US", "DE", "IR"). */
        val code: String,
        /** Flag emoji for the code, or "🌐" when unknown. */
        val flag: String,
        /** Persian display name ("آمریکا"), falling back to the code. */
        val nameFa: String,
    )

    val UNKNOWN = Country(code = "", flag = "🌐", nameFa = "")

    private val hostCache = ConcurrentHashMap<String, Country>()

    @Volatile
    private var table: Table? = null

    private class Table(
        val starts: IntArray,
        val ends: IntArray,
        val codes: Array<String>,
    )

    /** Flag emoji from an ISO country code, or "🌐" when it is not a code. */
    fun flagFor(code: String): String {
        val upper = code.trim().uppercase(Locale.US)
        if (upper.length != 2 || !upper.all { it in 'A'..'Z' }) return "🌐"
        val points = upper.map { 0x1F1E6 + (it - 'A') }
        return buildString {
            for (cp in points) appendCodePoint(cp)
        }
    }

    /** Persian country name for an ISO code, falling back to the code itself. */
    fun nameFaFor(code: String): String {
        val upper = code.trim().uppercase(Locale.US)
        if (upper.length != 2) return upper
        return try {
            val name = Locale("", upper).getDisplayCountry(Locale("fa"))
            if (name.isBlank() || name.equals(upper, ignoreCase = true)) upper else name
        } catch (e: Exception) {
            upper
        }
    }

    fun countryFor(code: String): Country {
        val upper = code.trim().uppercase(Locale.US)
        if (upper.length != 2 || !upper.all { it in 'A'..'Z' }) return UNKNOWN
        return Country(code = upper, flag = flagFor(upper), nameFa = nameFaFor(upper))
    }

    /**
     * Parses a country out of a config remark. Understands:
     * - a leading flag emoji (regional-indicator pair),
     * - a bare ISO code token ("US | ...", "DE | ..."),
     * - a Persian country name ("آمریکا", "آلمان", "فرانسه", ...).
     * Returns null when the remark carries no country.
     */
    fun countryFromRemark(remark: String?): Country? {
        if (remark.isNullOrBlank()) return null
        val text = remark.trim()

        // Leading flag emoji: two regional-indicator code points.
        val codePoints = text.codePoints().toArray()
        if (codePoints.size >= 2 &&
            codePoints[0] in 0x1F1E6..0x1F1FF &&
            codePoints[1] in 0x1F1E6..0x1F1FF
        ) {
            val code = String(
                charArrayOf(
                    ('A'.code + (codePoints[0] - 0x1F1E6)).toChar(),
                    ('A'.code + (codePoints[1] - 0x1F1E6)).toChar(),
                )
            )
            return countryFor(code)
        }

        // Bare ISO code token, e.g. "US | @Raydikalx | 853160" or "DE-32AE1C".
        val token = Regex("[A-Za-z]{2}").findAll(text).firstOrNull { m ->
            val value = m.value.uppercase(Locale.US)
            // Must look like a token, not two letters inside a word.
            val before = if (m.range.first > 0) text[m.range.first - 1] else ' '
            val after = if (m.range.last + 1 < text.length) text[m.range.last + 1] else ' '
            !before.isLetter() && !after.isLetter() && ISO_CODES.contains(value)
        }?.value?.uppercase(Locale.US)
        if (token != null) return countryFor(token)

        // Persian country name inside the remark.
        for ((name, code) in FA_NAME_TO_CODE) {
            if (text.contains(name)) return countryFor(code)
        }
        return null
    }

    /** Best-effort country for a profile: remark first, then host GeoIP. */
    suspend fun countryForProfile(
        context: Context,
        profile: ProfileItem,
    ): Country {
        countryFromRemark(profile.remarks)?.let { return it }
        val host = profile.server?.trim().orEmpty()
        if (host.isBlank()) return UNKNOWN
        return countryForHost(context, host)
    }

    /** Resolves a host to an IP and looks it up in the bundled table. */
    suspend fun countryForHost(context: Context, host: String): Country {
        val key = host.trim().lowercase(Locale.US)
        if (key.isBlank()) return UNKNOWN
        hostCache[key]?.let { return it }
        val ip = resolveHost(key) ?: return UNKNOWN
        val country = countryForIp(context, ip)
        hostCache[key] = country
        return country
    }

    /** Looks up a dotted-quad IP in the bundled table. */
    fun countryForIp(context: Context, ip: String): Country {
        val table = loadTable(context) ?: return UNKNOWN
        val address = ipToInt(ip) ?: return UNKNOWN
        // Binary search: greatest start <= address.
        var low = 0
        var high = table.starts.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (table.starts[mid] <= address) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (found < 0 || address > table.ends[found]) return UNKNOWN
        return countryFor(table.codes[found])
    }

    private suspend fun resolveHost(host: String): String? {
        // Skip resolution for literal IPs and for .onion-style hosts.
        if (ipToInt(host) != null) return host
        return try {
            withTimeoutOrNull(DNS_TIMEOUT_MS) {
                InetAddress.getByName(host).hostAddress
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gGeoIp: DNS failed for host", e)
            null
        }
    }

    private fun ipToInt(ip: String): Int? {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return null
        var value = 0
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            value = (value shl 8) or octet
        }
        return value
    }

    @Synchronized
    private fun loadTable(context: Context): Table? {
        table?.let { return it }
        return try {
            val starts = ArrayList<Int>()
            val ends = ArrayList<Int>()
            val codes = ArrayList<String>()
            context.applicationContext.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.trim().split(" ")
                    if (parts.size != 3) return@forEach
                    val start = parts[0].toIntOrNull() ?: return@forEach
                    val end = parts[1].toIntOrNull() ?: return@forEach
                    val code = parts[2].trim()
                    if (code.length != 2) return@forEach
                    starts.add(start)
                    ends.add(end)
                    codes.add(code)
                }
            }
            if (starts.isEmpty()) {
                LogUtil.w(AppConfig.TAG, "Vpn5gGeoIp: empty range table")
                null
            } else {
                Table(starts.toIntArray(), ends.toIntArray(), codes.toTypedArray()).also {
                    table = it
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gGeoIp: failed to load range table", e)
            null
        }
    }

    /** ISO 3166-1 alpha-2 codes, used to validate bare-code tokens in remarks. */
    private val ISO_CODES = setOf(
        "AD", "AE", "AF", "AG", "AI", "AL", "AM", "AO", "AQ", "AR", "AS", "AT", "AU", "AW",
        "AX", "AZ", "BA", "BB", "BD", "BE", "BF", "BG", "BH", "BI", "BJ", "BL", "BM", "BN",
        "BO", "BQ", "BR", "BS", "BT", "BV", "BW", "BY", "BZ", "CA", "CC", "CD", "CF", "CG",
        "CH", "CI", "CK", "CL", "CM", "CN", "CO", "CR", "CU", "CV", "CW", "CX", "CY", "CZ",
        "DE", "DJ", "DK", "DM", "DO", "DZ", "EC", "EE", "EG", "EH", "ER", "ES", "ET", "FI",
        "FJ", "FK", "FM", "FO", "FR", "GA", "GB", "GD", "GE", "GF", "GG", "GH", "GI", "GL",
        "GM", "GN", "GP", "GQ", "GR", "GS", "GT", "GU", "GW", "GY", "HK", "HM", "HN", "HR",
        "HT", "HU", "ID", "IE", "IL", "IM", "IN", "IO", "IQ", "IR", "IS", "IT", "JE", "JM",
        "JO", "JP", "KE", "KG", "KH", "KI", "KM", "KN", "KP", "KR", "KW", "KY", "KZ", "LA",
        "LB", "LC", "LI", "LK", "LR", "LS", "LT", "LU", "LV", "LY", "MA", "MC", "MD", "ME",
        "MF", "MG", "MH", "MK", "ML", "MM", "MN", "MO", "MP", "MQ", "MR", "MS", "MT", "MU",
        "MV", "MW", "MX", "MY", "MZ", "NA", "NC", "NE", "NF", "NG", "NI", "NL", "NO", "NP",
        "NR", "NU", "NZ", "OM", "PA", "PE", "PF", "PG", "PH", "PK", "PL", "PM", "PN", "PR",
        "PS", "PT", "PW", "PY", "QA", "RE", "RO", "RS", "RU", "RW", "SA", "SB", "SC", "SD",
        "SE", "SG", "SH", "SI", "SJ", "SK", "SL", "SM", "SN", "SO", "SR", "SS", "ST", "SV",
        "SX", "SY", "SZ", "TC", "TD", "TF", "TG", "TH", "TJ", "TK", "TL", "TM", "TN", "TO",
        "TR", "TT", "TV", "TW", "TZ", "UA", "UG", "UM", "US", "UY", "UZ", "VA", "VC", "VE",
        "VG", "VI", "VN", "VU", "WF", "WS", "YE", "YT", "ZA", "ZM", "ZW",
    )

    /** Common Persian country names → ISO code, for remark parsing. */
    private val FA_NAME_TO_CODE = mapOf(
        "آمریکا" to "US", "ایالات متحده" to "US", "آلمان" to "DE", "فرانسه" to "FR",
        "انگلیس" to "GB", "انگلستان" to "GB", "بریتانیا" to "GB", "هلند" to "NL",
        "کانادا" to "CA", "ترکیه" to "TR", "امارات" to "AE", "روسیه" to "RU",
        "چین" to "CN", "ژاپن" to "JP", "کره" to "KR", "هند" to "IN",
        "برزیل" to "BR", "سوئد" to "SE", "نروژ" to "NO", "فنلاند" to "FI",
        "دانمارک" to "DK", "سوئیس" to "CH", "اتریش" to "AT", "اسپانیا" to "ES",
        "ایتالیا" to "IT", "لهستان" to "PL", "اوکراین" to "UA", "ایران" to "IR",
        "عراق" to "IQ", "عربستان" to "SA", "قطر" to "QA", "سنگاپور" to "SG",
        "استرالیا" to "AU", "مکزیک" to "MX", "آرژانتین" to "AR", "شیلی" to "CL",
        "کلمبیا" to "CO", "پرو" to "PE", "مصر" to "EG", "اندونزی" to "ID",
        "مالزی" to "MY", "تایلند" to "TH", "ویتنام" to "VN", "فیلیپین" to "PH",
        "تایوان" to "TW", "هنگ‌کنگ" to "HK", "هنگ کنگ" to "HK", "یونان" to "GR",
        "پرتغال" to "PT", "بلژیک" to "BE", "ایرلند" to "IE", "ایسلند" to "IS",
        "مجارستان" to "HU", "رومانی" to "RO", "بلغارستان" to "BG", "کرواسی" to "HR",
        "صربستان" to "RS", "لوکزامبورگ" to "LU", "مالت" to "MT", "قبرس" to "CY",
        "گرجستان" to "GE", "ارمنستان" to "AM", "آذربایجان" to "AZ", "قزاقستان" to "KZ",
        "ازبکستان" to "UZ", "پاکستان" to "PK", "بنگلادش" to "BD", "نپال" to "NP",
        "نیوزیلند" to "NZ", "چک" to "CZ", "لیتوانی" to "LT", "لتونی" to "LV",
        "استونی" to "EE", "آفریقای جنوبی" to "ZA", "نیجریه" to "NG", "کنیا" to "KE",
    )
}
