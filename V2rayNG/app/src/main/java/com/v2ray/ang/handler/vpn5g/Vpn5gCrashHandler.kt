package com.v2ray.ang.handler.vpn5g

import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import java.io.File
import java.time.Instant

/**
 * Global uncaught-exception handler (v1.1.16).
 *
 * The 1.1.15 connect crash reproduced deterministically (2/2 taps)
 * with no stack trace available — the app died with Android's "this
 * app has a bug" dialog and nothing was recorded anywhere. From now
 * on, ANY uncaught exception writes a SHORT crash report to a file
 * the user can share with support, then the previous default handler
 * still runs, so Android shows the same crash dialog and the process
 * dies exactly as before. Nothing is uploaded anywhere, ever.
 *
 * The report contains ONLY: app version, time, thread name, the
 * exception class + message, and the first 30 stack frames. It never
 * contains personal data — no email, no device id, no proxy URI, no
 * token, no config content. Stack frames name classes and methods
 * only.
 *
 * Install once, in [AngApplication.onCreate], before anything else
 * runs. The handler itself never throws and never blocks: a failed
 * write is swallowed and the previous handler still runs.
 */
object Vpn5gCrashHandler {

    /** File name inside the app's external files dir (user-shareable). */
    private const val REPORT_FILE_NAME = "vpn5g-crash-report.txt"

    /** How many stack frames the report keeps (short by design). */
    const val MAX_STACK_FRAMES = 30

    /** The report file, or the internal files dir when external storage is unavailable. */
    fun crashReportFile(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, REPORT_FILE_NAME)
    }

    /** True when a crash report from an earlier run is waiting. */
    fun hasCrashReport(context: Context): Boolean {
        return runCatching { crashReportFile(context).exists() }.getOrDefault(false)
    }

    /** The stored report text, or null when there is none / it cannot be read. */
    fun readCrashReport(context: Context): String? {
        return runCatching {
            val file = crashReportFile(context)
            if (!file.exists()) null else file.readText()
        }.getOrNull()
    }

    /** Deletes the stored report (user dismissed it / shared it already). */
    fun deleteCrashReport(context: Context) {
        runCatching { crashReportFile(context).delete() }
    }

    /**
     * Share intent for the stored report (v1.1.16): the report is
     * shared as TEXT (no FileProvider needed), so the user can paste
     * it into Telegram / WhatsApp for support. Null when no report
     * is stored.
     */
    fun shareIntent(context: Context): Intent? {
        val text = readCrashReport(context) ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "VPN 5G — crash report")
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    /**
     * Formats one crash report. Pure (no Android APIs) so it is
     * covered by JVM unit tests. [versionName]/[versionCode] are
     * passed in so tests do not need BuildConfig.
     */
    fun formatReport(
        versionName: String,
        versionCode: Int,
        timestampMs: Long,
        threadName: String,
        throwable: Throwable,
    ): String {
        val time = runCatching { Instant.ofEpochMilli(timestampMs).toString() }
            .getOrDefault(timestampMs.toString())
        val frames = throwable.stackTrace
            .take(MAX_STACK_FRAMES)
            .joinToString("\n") { "    at $it" }
        return buildString {
            appendLine("VPN 5G crash report")
            appendLine("version: $versionName ($versionCode)")
            appendLine("time: $time")
            appendLine("thread: $threadName")
            appendLine("exception: ${throwable.javaClass.name}: ${throwable.message}")
            if (frames.isNotEmpty()) {
                appendLine("stack:")
                appendLine(frames)
            }
            throwable.cause?.let { cause ->
                appendLine("caused by: ${cause.javaClass.name}: ${cause.message}")
            }
        }
    }

    /**
     * Installs the handler, chaining to whatever handler was default
     * before (usually Android's own, which shows the crash dialog).
     * Safe to call twice — the second call keeps the first handler as
     * the chained one, it never wraps itself.
     */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Vpn5gCrashHandlerWrapper) return
        Thread.setDefaultUncaughtExceptionHandler(
            Vpn5gCrashHandlerWrapper(context.applicationContext, previous)
        )
    }

    private class Vpn5gCrashHandlerWrapper(
        private val context: Context,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                val report = formatReport(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    timestampMs = System.currentTimeMillis(),
                    threadName = thread.name,
                    throwable = throwable,
                )
                crashReportFile(context).writeText(report)
            } catch (_: Exception) {
                // The report must never make a crash worse.
                runCatching {
                    LogUtil.e(AppConfig.TAG, "Vpn5gCrashHandler: report write failed")
                }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
