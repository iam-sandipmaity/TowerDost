package app.telecom.sitetracker.util

import android.content.Context
import android.os.Build
import app.telecom.sitetracker.ui.crash.CrashActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions to a persistent local crash log file in app-private storage
 * and automatically displays CrashActivity to prevent silent closing.
 */
object CrashLog {
    private const val LOG_FILE_NAME = "telecom_crash_log.txt"

    fun init(context: Context) {
        val appContext = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashToFile(appContext, thread, throwable)

                // Launch crash diagnostic activity
                val title = "${throwable::class.java.simpleName}: ${throwable.message ?: "Unexpected failure"}"
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val details = sw.toString()

                CrashActivity.launch(appContext, title, details)
            } catch (_: Throwable) {
                // Defensive against secondary failure
            }

            // Terminate the crashing process cleanly
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }
    }

    private fun writeCrashToFile(context: Context, thread: Thread, throwable: Throwable) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val timestamp = sdf.format(Date())

        val report = buildString {
            appendLine("=== TELECOM TRACKER CRASH REPORT ===")
            appendLine("Timestamp: $timestamp")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name} (id: ${thread.id})")
            appendLine("Exception: ${throwable::class.java.name}: ${throwable.message}")
            appendLine("--- Stack Trace ---")
            appendLine(stackTrace)
            appendLine("=====================================")
            appendLine()
        }

        if (file.exists() && file.length() > 100_000) {
            file.delete()
        }
        file.appendText(report)
    }

    fun getLatestCrashLog(context: Context): String? {
        val file = File(context.filesDir, LOG_FILE_NAME)
        return if (file.exists() && file.length() > 0) {
            file.readText()
        } else null
    }

    fun clearCrashLog(context: Context) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (file.exists()) file.delete()
    }
}
