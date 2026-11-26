package com.v2ray.ang.handler.vpn5g

/**
 * Startup sync progress (v1.1.14).
 *
 * The startup screen's 0→100% bar is driven by the REAL server-list
 * sync stages, not a timer: each stage owns a slice of the bar and the
 * fraction inside a stage comes from real work (sources fetched so
 * far, servers pinged so far). Pure, JVM-tested.
 *
 * v1.1.32 (Aref's order): the entry screen must stay visible at
 * least 8–10 s so his entrance video is actually seen («ورودی اول
 * که گفتم ویدیو داده بودم اصلا نبود حداقل ۸ الی ۱۰ ثانیه باشه و تو
 * این چند ثانیه از ۱ تا ۱۰۰ سریع پر بشه») — with a cached list the
 * real work finishes in well under a second and the screen vanished
 * before the video could play. [gatedFraction] merges the real
 * stage fraction with a minimum-display ramp: the bar fills 1→100
 * across the minimum window, reaches 100% only when the real work
 * is done AND the window has elapsed, and never runs backwards.
 * Real work keeps running in the background either way — the gate
 * only holds the screen, it never blocks the sync.
 */
object Vpn5gStartupProgress {

    enum class Stage {
        /** Reading the last stored list / bundled fallback. */
        LOADING_CACHE,
        /** Fetching app_config.json (plans, codes, announcement). */
        FETCH_CONFIG,
        /** Fetching the config sources, one by one. */
        FETCH_SOURCES,
        /** Parsing + validating the fetched configs. */
        PARSE_VALIDATE,
        /** TCP-pinging the candidates. */
        TCP_PING,
        /** Real through-the-proxy ping probe. */
        REAL_PROBE,
        /** Saving the live list. */
        SAVING,
        /** Done: 100%. */
        DONE,
    }

    /**
     * Bar fraction 0..1 for [stage] with [within] 0..1 of that stage
     * done. Stage boundaries: cache 0–0.10, config 0.10–0.15, sources
     * 0.15–0.45, parse 0.45–0.55, TCP ping 0.55–0.80, real probe
     * 0.80–0.95, saving 0.95–1.00.
     */
    fun fraction(stage: Stage, within: Float = 0f): Float {
        val w = within.coerceIn(0f, 1f)
        val (from, to) = when (stage) {
            Stage.LOADING_CACHE -> 0f to 0.10f
            Stage.FETCH_CONFIG -> 0.10f to 0.15f
            Stage.FETCH_SOURCES -> 0.15f to 0.45f
            Stage.PARSE_VALIDATE -> 0.45f to 0.55f
            Stage.TCP_PING -> 0.55f to 0.80f
            Stage.REAL_PROBE -> 0.80f to 0.95f
            Stage.SAVING -> 0.95f to 1f
            Stage.DONE -> 1f to 1f
        }
        return (from + (to - from) * w).coerceIn(0f, 1f)
    }

    /** Minimum time the entry (1→100) screen stays visible (v1.1.32). */
    const val MIN_DISPLAY_MS = 8_000L

    /** The bar never shows 100% before the gate opens. */
    const val MAX_BEFORE_DONE = 0.99f

    /**
     * Displayed bar fraction (v1.1.32): the further of the REAL
     * stage fraction and the minimum-display ramp
     * ([elapsedMs] / [MIN_DISPLAY_MS]), capped at [MAX_BEFORE_DONE]
     * until the real work is done AND the minimum window has
     * elapsed — only then is it exactly 1f. Pure.
     */
    fun gatedFraction(real: Float, elapsedMs: Long, workDone: Boolean): Float {
        val r = real.coerceIn(0f, 1f)
        if (workDone && elapsedMs >= MIN_DISPLAY_MS) return 1f
        val ramp = (elapsedMs.toFloat() / MIN_DISPLAY_MS).coerceIn(0f, 1f)
        return maxOf(r, ramp).coerceAtMost(MAX_BEFORE_DONE)
    }

    /** True when the entry screen may leave (v1.1.32). Pure. */
    fun gateOpen(elapsedMs: Long, workDone: Boolean): Boolean =
        workDone && elapsedMs >= MIN_DISPLAY_MS
}
