package com.v2ray.ang.handler.vpn5g

/**
 * Startup sync progress (v1.1.14).
 *
 * The startup screen's 0→100% bar is driven by the REAL server-list
 * sync stages, not a timer: each stage owns a slice of the bar and the
 * fraction inside a stage comes from real work (sources fetched so
 * far, servers pinged so far). Pure, JVM-tested.
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
}
