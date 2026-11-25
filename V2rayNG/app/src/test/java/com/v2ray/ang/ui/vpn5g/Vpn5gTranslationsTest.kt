package com.v2ray.ang.ui.vpn5g

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the vpn5g_ string coverage (v1.1.22, fix #5):
 * switching language must REALLY translate every screen, including
 * the Guide — so every vpn5g_ key in the default (Persian) file must
 * exist, non-empty, in values-fa, values-en and values-az, with the
 * same format placeholders. Reads the committed resource files from
 * the module directory (the Gradle unit-test working directory).
 */
class Vpn5gTranslationsTest {

    private fun loadStrings(locale: String): Map<String, String> {
        val file = File("src/main/res/$locale/strings.xml")
        assertTrue(
            file.exists(),
            "missing $locale strings.xml at ${file.absolutePath}",
        )
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(file)
        val out = mutableMapOf<String, String>()
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val element = nodes.item(i)
            val name = element.attributes.getNamedItem("name").nodeValue
            if (name.startsWith("vpn5g_")) {
                out[name] = element.textContent
            }
        }
        return out
    }

    @Test
    fun test_all_vpn5g_keys_translated_in_fa_en_az() {
        val base = loadStrings("values")
        val fa = loadStrings("values-fa")
        val en = loadStrings("values-en")
        val az = loadStrings("values-az")
        assertTrue(base.isNotEmpty(), "no vpn5g_ keys in the default file")
        for (key in base.keys) {
            for ((locale, map) in listOf("fa" to fa, "en" to en, "az" to az)) {
                val translated = map[key]
                assertTrue(
                    !translated.isNullOrBlank(),
                    "$key is missing or empty in $locale",
                )
            }
        }
    }

    @Test
    fun test_placeholders_match_across_locales() {
        val placeholder = Regex("%\\d+\\$[sd]")
        val base = loadStrings("values")
        val en = loadStrings("values-en")
        val az = loadStrings("values-az")
        for ((key, value) in base) {
            val expected = placeholder.findAll(value)
                .map { it.value }.toList().sorted()
            for ((locale, map) in listOf("en" to en, "az" to az)) {
                val actual = placeholder.findAll(map[key] ?: "")
                    .map { it.value }.toList().sorted()
                assertEquals(
                    expected, actual,
                    "$key placeholders differ in $locale",
                )
            }
        }
    }

    @Test
    fun test_guide_keys_translated() {
        // The Guide is the screen Aref called out by name: all 20
        // sections + the 10 FAQ pairs must exist in en and az.
        val en = loadStrings("values-en")
        val az = loadStrings("values-az")
        for (i in 1..20) {
            val section = "vpn5g_guide_s%02d".format(i)
            assertTrue(!en["${section}_title"].isNullOrBlank(), "$section title in en")
            assertTrue(!az["${section}_title"].isNullOrBlank(), "$section title in az")
        }
        for (i in 1..10) {
            val faq = "vpn5g_faq_a%02d".format(i)
            assertTrue(!en[faq].isNullOrBlank(), "$faq in en")
            assertTrue(!az[faq].isNullOrBlank(), "$faq in az")
        }
    }

    @Test
    fun test_default_language_is_persian() {
        // v1.1.22: the default locale is fa, not "auto".
        assertEquals("fa", Vpn5gHomeUiState().languageCode)
    }
}
