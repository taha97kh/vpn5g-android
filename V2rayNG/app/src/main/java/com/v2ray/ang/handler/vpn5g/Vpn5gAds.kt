package com.v2ray.ang.handler.vpn5g

/**
 * Interstitial + user-ad decisions (v1.1.15).
 *
 * Pure Kotlin — no Android APIs (callers pass the monotonic-clock
 * values and the local hour in) — so every rule is covered by JVM
 * unit tests in Vpn5gAdsTest.
 *
 * Forced-ad rule (Aref's master spec): the FIRST connection in an app
 * session has NO forced ad. After the user disconnects and connects
 * for the SECOND time, the full-screen interstitial shows BEFORE the
 * VPN starts, and the VPN starts ONLY after the ad is fully viewed
 * (countdown completes). Closing the ad early means the view is
 * INCOMPLETE and the connection must NOT start. Subscribers skip the
 * forced ad unless the remote config says otherwise. The countdown
 * runs on the MONOTONIC clock (elapsedRealtime), never the wall
 * clock, so changing the phone's time cannot bypass it.
 *
 * Everything commercial comes from the remote app_config.json
 * ([AdsRemote]): ad content, link, duration, schedule and prices.
 * No ad content or price is ever hardcoded here. When the config
 * publishes no ad, no ad is shown.
 */
object Vpn5gAds {

    /** Admin-set interstitial duration bounds (seconds). */
    const val MIN_DURATION_SECONDS = 1
    const val MAX_DURATION_SECONDS = 20

    /** Duration when the config enables the interstitial but sets none. */
    const val DEFAULT_DURATION_SECONDS = 10

    /** First connect number that may show a forced ad (connect #1 never does). */
    const val DEFAULT_SHOW_FROM_CONNECT_NUMBER = 2

    /** One resolved interstitial, ready for the UI. */
    data class InterstitialAdUi(
        val id: String,
        val text: String?,
        val imageUrl: String?,
        val linkUrl: String?,
        val durationSeconds: Int,
        /** True when this is a user-submitted ad (scheduled impressions). */
        val isUserAd: Boolean,
    )

    /** Impression counters the app persists per ad id. */
    data class AdImpressions(
        val total: Int = 0,
        /** Local date ("yyyy-MM-dd") the [today] count belongs to. */
        val day: String = "",
        val today: Int = 0,
    )

    /** The persisted impression store: ad id -> counters. */
    data class AdImpressionStore(
        val entries: Map<String, AdImpressions> = emptyMap(),
    )

    /** Clamps an admin-set duration to the allowed 1..20 seconds. */
    fun clampDuration(seconds: Int?): Int {
        val s = seconds ?: DEFAULT_DURATION_SECONDS
        return s.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)
    }

    /**
     * Whether a forced interstitial is due on the NEXT connect.
     * [completedSessions] counts sessions STARTED so far in this app
     * session, so the upcoming connect is number completedSessions + 1.
     * The admin tunes the frequency with
     * [InterstitialAdRemote.show_from_connect_number] and may include
     * subscribers with [InterstitialAdRemote.applies_to_free_only]
     * false (default true = free tier only).
     */
    fun shouldShowInterstitial(
        ads: AdsRemote?,
        completedSessions: Int,
        isSubscriber: Boolean,
        isGift: Boolean,
    ): Boolean {
        val interstitial = ads?.interstitial ?: return false
        if (interstitial.enabled != true) return false
        val from = (interstitial.show_from_connect_number
            ?: DEFAULT_SHOW_FROM_CONNECT_NUMBER).coerceAtLeast(1)
        if (completedSessions + 1 < from) return false
        if (interstitial.applies_to_free_only != false && (isSubscriber || isGift)) return false
        return true
    }

    /**
     * Seconds left on an interstitial countdown, from the MONOTONIC
     * clock: [deadlineRealtimeMs] was fixed at show time as
     * elapsedRealtime() + duration*1000. The wall clock is never
     * consulted, so changing the phone's time cannot shorten the ad.
     * Ceiling so the countdown reads the full duration at the start
     * and reaches 0 exactly when the view completes.
     */
    fun remainingSeconds(
        deadlineRealtimeMs: Long,
        nowRealtimeMs: Long,
        durationSeconds: Int,
    ): Long {
        val remainingMs = deadlineRealtimeMs - nowRealtimeMs
        if (remainingMs <= 0L) return 0L
        return (remainingMs + 999L) / 1000L
    }

    /**
     * Whether a user-submitted ad is due right now: approved + enabled,
     * under its total cap, under its per-day cap, and inside one of its
     * hour windows (when windows are published). [hour] is the device
     * local hour 0..23. No windows published = any hour. Hour windows
     * are [start, end) pairs — start inclusive, end exclusive — so
     * [9, 12] means 09:00–11:59. Never shows more impressions than
     * purchased: the caps are the purchased amounts. The per-day cap
     * counts only impressions from [today] ("yyyy-MM-dd"): a stored
     * count from an earlier day rolls over to 0, so a new day never
     * stays blocked by yesterday's impressions.
     */
    fun isUserAdDue(
        ad: UserAdRemote,
        impressions: AdImpressions,
        hour: Int,
        today: String = java.time.LocalDate.now().toString(),
    ): Boolean {
        if (ad.enabled != true || ad.approved != true) return false
        val max = ad.max_impressions
        if (max != null && impressions.total >= max) return false
        val perDay = ad.impressions_per_day
        val todayCount = if (impressions.day == today) impressions.today else 0
        if (perDay != null && todayCount >= perDay) return false
        val windows = ad.hour_windows
        if (windows.isNullOrEmpty()) return true
        return windows.any { window ->
            val start = window.getOrNull(0) ?: return@any false
            val end = window.getOrNull(1) ?: return@any false
            hour in start until end
        }
    }

    /**
     * Which interstitial to show on a reconnect: a due user-submitted
     * ad first (the bot orders them by schedule), else the admin
     * interstitial. Null when nothing is due or enabled — then the
     * connect starts at once, with no ad. User ads use the same
     * admin-set interstitial duration (1..20 s).
     */
    fun pickInterstitial(
        ads: AdsRemote?,
        impressionsById: Map<String, AdImpressions>,
        hour: Int,
        today: String = java.time.LocalDate.now().toString(),
    ): InterstitialAdUi? {
        val duration = clampDuration(ads?.interstitial?.duration_seconds)
        val userAd = ads?.user_ads
            ?.filter {
                isUserAdDue(
                    it,
                    impressionsById[it.id.orEmpty()] ?: AdImpressions(),
                    hour,
                    today,
                )
            }
            ?.firstOrNull()
        if (userAd != null) {
            return InterstitialAdUi(
                id = userAd.id?.takeIf { it.isNotBlank() } ?: "user-ad",
                text = userAd.text,
                imageUrl = userAd.image_url,
                linkUrl = userAd.link_url,
                durationSeconds = duration,
                isUserAd = true,
            )
        }
        val interstitial = ads?.interstitial
        if (interstitial?.enabled == true) {
            val hasContent = !interstitial.text.isNullOrBlank() ||
                !interstitial.image_url.isNullOrBlank()
            if (hasContent) {
                return InterstitialAdUi(
                    id = interstitial.id?.takeIf { it.isNotBlank() }
                        ?: "admin-interstitial",
                    text = interstitial.text,
                    imageUrl = interstitial.image_url,
                    linkUrl = interstitial.link_url,
                    durationSeconds = duration,
                    isUserAd = false,
                )
            }
        }
        return null
    }

    /**
     * Records one completed (fully viewed) impression for [adId],
     * rolling the per-day count over on a new [today] ("yyyy-MM-dd").
     * Pure, JVM-tested.
     */
    fun recordImpression(
        store: AdImpressionStore,
        adId: String,
        today: String,
    ): AdImpressionStore {
        if (adId.isBlank()) return store
        val current = store.entries[adId] ?: AdImpressions()
        val todayCount = if (current.day == today) current.today else 0
        val updated = current.copy(
            total = current.total + 1,
            day = today,
            today = todayCount + 1,
        )
        return store.copy(entries = store.entries + (adId to updated))
    }
}
