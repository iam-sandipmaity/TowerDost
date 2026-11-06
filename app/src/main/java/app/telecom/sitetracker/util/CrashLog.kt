package app.telecom.sitetracker.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions to a persistent local crash log file in app-private storage.
 * Designed for offline field operation where no crash-reporting SDK or Play Console is present.
 */
object CrashLog {
    private const val LOG_FILE_NAME = "telecom_crash_log.txt"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashToFile(context.applicationContext, thread, throwable)
            } catch (t: Throwable) {
                // Defensive against secondary failure
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
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

        // Keep last 100 KB max by truncating if needed
        if (file.exists() && file.length() > 100_000) {
            file.delete()
        }
        file.appendText(report)
    }

    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    fun hasLog(context: Context): Boolean {
        val file = getLogFile(context)
        return file.exists() && file.length() > 0
    }

    fun readLog(context: Context): String {
        val file = getLogFile(context)
        return if (file.exists()) file.readText() else "No crashes recorded."
    }

    fun clearLog(context: Context): Boolean {
        val file = getLogFile(context)
        return if (file.exists()) file.delete() else true
    }
}
