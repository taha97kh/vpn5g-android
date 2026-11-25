package com.v2ray.ang.handler.vpn5g

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** JVM tests for the redeem messages (v1.1.20): every server reason
 * maps to its exact Persian wording — never a generic "invalid".
 * Reads the real strings.xml so a wording regression fails here,
 * not on Aref's phone. */
class Vpn5gRedeemMessagesTest {

    private fun stringsXml(): String {
        // Gradle runs unit tests with the module dir (V2rayNG/) as the
        // working directory; tolerate the repo root too.
        val candidates = listOf(
            "app/src/main/res/values/strings.xml",
            "V2rayNG/app/src/main/res/values/strings.xml",
            "src/main/res/values/strings.xml",
        )
        val found = candidates.map { File(it) }.firstOrNull { it.isFile }
        requireNotNull(found) {
            "strings.xml not found (tried ${candidates.joinToString()})"
        }
        return found.readText()
    }

    private fun stringValue(xml: String, name: String): String {
        val match = Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(xml) ?: error("string $name missing from strings.xml")
        return match.groupValues[1]
    }

    @Test
    fun test_redeem_reason_messages_are_exact() {
        val xml = stringsXml()
        val expected = mapOf(
            "vpn5g_promo_unknown" to "کد پیدا نشد؛ کد را دوباره بررسی کن.",
            "vpn5g_promo_already" to "این کد قبلاً با همین اکانت ثبت شده است.",
            "vpn5g_promo_exhausted" to "مصرف این کد تمام شده است.",
            "vpn5g_promo_expired" to "این کد منقضی شده است.",
            "vpn5g_promo_invalid_email" to "ایمیل نامعتبر است.",
            "vpn5g_promo_no_registration" to "این ایمیل ثبت‌نام نکرده است؛ اول ثبت‌نام کن.",
            "vpn5g_promo_server_unavailable" to "ارتباط با سرور برقرار نشد؛ دوباره تلاش کن.",
            "vpn5g_promo_not_applied" to "کد ثبت شد اما اشتراک اعمال نشد؛ دوباره تلاش کن.",
        )
        for ((name, wording) in expected) {
            assertEquals(wording, stringValue(xml, name), name)
        }
        // The discount-only message still exists (the bot step).
        assertEquals(
            "کد تخفیف ثبت شد؛ برای اعمال تخفیف در ربات ادامه بده.",
            stringValue(xml, "vpn5g_promo_valid"),
        )
    }
}
