package app.telecom.sitetracker.ui.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.telecom.sitetracker.MainActivity
import app.telecom.sitetracker.data.db.AppDatabase
import java.io.File

/**
 * Isolated crash diagnostic activity that runs in a separate process (:crash).
 * Renders without Compose, Room, or external fonts to guarantee 100% reliability.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val errorTitle = intent.getStringExtra(EXTRA_ERROR_TITLE) ?: "Application Error"
        val errorDetails = intent.getStringExtra(EXTRA_ERROR_DETAILS) ?: "No crash details available."

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(32, 48, 32, 32)
        }

        val headerText = TextView(this).apply {
            text = "TowerDost Diagnostic Mode"
            textSize = 20f
            setTextColor(Color.parseColor("#F87171"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }
        rootLayout.addView(headerText)

        val subheaderText = TextView(this).apply {
            text = "The application encountered an issue and was prevented from silently closing:"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(subheaderText)

        // Error message box
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(20, 20, 20, 20)
        }

        val logText = TextView(this).apply {
            text = "$errorTitle\n\n$errorDetails"
            textSize = 11f
            setTextColor(Color.parseColor("#F1F5F9"))
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(logText)
        rootLayout.addView(scrollView)

        // Action Buttons Row
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 24, 0, 0)
            }
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val copyButton = Button(this).apply {
            text = "Copy Error"
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", "$errorTitle\n\n$errorDetails"))
                Toast.makeText(this@CrashActivity, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        buttonRow.addView(copyButton)

        val spacer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 1)
        }
        buttonRow.addView(spacer)

        val clearButton = Button(this).apply {
            text = "Reset DB & Restart"
            setBackgroundColor(Color.parseColor("#DC2626"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                try {
                    deleteDatabase(AppDatabase.DATABASE_NAME)
                    val files = File(filesDir, "telecom_crash_log.txt")
                    if (files.exists()) files.delete()
                } catch (_: Exception) { }

                restartMainActivity()
            }
        }
        buttonRow.addView(clearButton)

        val spacer2 = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 1)
        }
        buttonRow.addView(spacer2)

        val restartButton = Button(this).apply {
            text = "Restart App"
            setBackgroundColor(Color.parseColor("#0F766E"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                restartMainActivity()
            }
        }
        buttonRow.addView(restartButton)

        rootLayout.addView(buttonRow)
        setContentView(rootLayout)
    }

    private fun restartMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finishAffinity()
    }

    companion object {
        const val EXTRA_ERROR_TITLE = "extra_error_title"
        const val EXTRA_ERROR_DETAILS = "extra_error_details"

        fun launch(context: Context, title: String, details: String) {
            val intent = Intent(context, CrashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_ERROR_TITLE, title)
                putExtra(EXTRA_ERROR_DETAILS, details)
            }
            context.startActivity(intent)
        }
    }
}
