package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the app_config parser ([Vpn5gRemoteConfig.parseForTest],
 * v1.1.30): the bot publishes `ads` in two shapes — the original
 * object and a flat ARRAY of ad entries. Plain Gson throws on the
 * array shape and the whole config would fall back to defaults, so
 * the tolerant deserializer must map the array shape without losing
 * the rest of the config (plans, codes, announcement).
 */
class Vpn5gRemoteConfigTest {

    @Test
    fun test_ads_array_shape_maps_to_ads_remote() {
        // The live config shape (2026-11-26): ads as a flat array.
        val json = """
            {
              "plans": [
                {"id": "sub_1", "price_toman": 50000, "days": 30},
                {"id": "gb_20", "price_toman": 50000, "days": 30, "gb": 20}
              ],
              "ads": [
                {"enabled": true, "id": "ad-1", "text": "تبلیغ یک",
                 "image_url": "https://img.example/1.png",
                 "link_url": "https://link.example/1",
                 "button_title": "باز کن", "duration_seconds": 7,
                 "type": "interstitial"},
                {"enabled": false, "id": "ad-2", "text": "تبلیغ دو",
                 "link_url": "https://link.example/2",
                 "duration_seconds": 5, "type": "banner"}
              ]
            }
        """.trimIndent()
        val config = Vpn5gRemoteConfig.parseForTest(json)
        assertNotNull(config)
        // The rest of the config survives the array shape.
        assertEquals(2, config?.plans?.size)
        val ads = config?.ads
        assertNotNull(ads)
        // First ENABLED entry becomes the interstitial.
        assertEquals("ad-1", ads?.interstitial?.id)
        assertEquals(7, ads?.interstitial?.duration_seconds)
        assertEquals("https://link.example/1", ads?.interstitial?.link_url)
        // Every entry becomes a user ad, approved as published.
        assertEquals(2, ads?.user_ads?.size)
        assertEquals("ad-2", ads?.user_ads?.get(1)?.id)
        assertEquals(true, ads?.user_ads?.get(1)?.approved)
    }

    @Test
    fun test_ads_object_shape_still_parses() {
        val json = """
            {
              "ads": {
                "interstitial": {"enabled": true, "id": "main-ad",
                                 "duration_seconds": 10,
                                 "show_from_connect_number": 3,
                                 "applies_to_free_only": true},
                "user_ads": [
                  {"id": "u1", "text": "آگهی", "enabled": true,
                   "approved": true, "max_impressions": 10}
                ]
              }
            }
        """.trimIndent()
        val config = Vpn5gRemoteConfig.parseForTest(json)
        assertNotNull(config)
        assertEquals("main-ad", config?.ads?.interstitial?.id)
        assertEquals(3, config?.ads?.interstitial?.show_from_connect_number)
        assertEquals(1, config?.ads?.user_ads?.size)
    }

    @Test
    fun test_ads_absent_or_unknown_shape_is_not_a_failed_config() {
        val noAds = Vpn5gRemoteConfig.parseForTest("""{"plans": []}""")
        assertNotNull(noAds)
        assertNull(noAds?.ads)
        // An unknown ads shape reads as no ads, never a failed config.
        val weird = Vpn5gRemoteConfig.parseForTest("""{"ads": "oops", "plans": []}""")
        assertNotNull(weird)
        assertNull(weird?.ads)
    }

    @Test
    fun test_payment_gateway_new_fields_parse() {
        // v1.1.30: payment link + USDT fields ride the payment_gateway
        // block (bot-published, config-driven).
        val json = """
            {
              "payment_gateway": {
                "enabled": true,
                "url": "https://gateway.example",
                "payment_link_url": "https://link.example/pay",
                "payment_link_name_fa": "پرداخت با لینک",
                "payment_link_guide_fa": "راهنما",
                "usdt_address": "TXyzWalletAddress",
                "usdt_network": "TRC20",
                "usdt_name_fa": "پرداخت با تتر",
                "usdt_guide_fa": "راهنمای تتر"
              }
            }
        """.trimIndent()
        val config = Vpn5gRemoteConfig.parseForTest(json)
        assertNotNull(config)
        val gateway = config?.payment_gateway
        assertEquals("https://link.example/pay", gateway?.payment_link_url)
        assertEquals("TXyzWalletAddress", gateway?.usdt_address)
        assertEquals("TRC20", gateway?.usdt_network)
        // And the resolved methods come out in display order.
        val methods = Vpn5gPlans.paymentMethods(gateway)
        assertEquals(
            listOf(
                Vpn5gPlans.PaymentMethodUi.Kind.GATEWAY,
                Vpn5gPlans.PaymentMethodUi.Kind.PAYMENT_LINK,
                Vpn5gPlans.PaymentMethodUi.Kind.USDT,
            ),
            methods.map { it.kind },
        )
        assertTrue(methods.all { it.url != null || it.walletAddress != null })
    }
}
