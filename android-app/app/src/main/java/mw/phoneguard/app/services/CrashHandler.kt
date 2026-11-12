package mw.phoneguard.app.services

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import mw.phoneguard.app.data.storage.TokenStore
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val crashDir = File(context.filesDir, "crashes")
            if (!crashDir.exists()) crashDir.mkdirs()

            val crashFile = File(crashDir, "crash_$timestamp.txt")
            val reportContent = """
                PhoneGuard Crash Report
                Timestamp: $timestamp
                Device: ${Build.MANUFACTURER} ${Build.MODEL}
                Android SDK: ${Build.VERSION.SDK_INT}
                
                Stacktrace:
                $stackTrace
            """.trimIndent()

            crashFile.writeText(reportContent)
            Timber.e("Crash saved to ${crashFile.absolutePath}")

            val store = TokenStore.get(context)
            if (store.getCrashReportConsent()) {
                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("developer@phoneguard.mw"))
                    putExtra(Intent.EXTRA_SUBJECT, "PhoneGuard Crash Report ($timestamp)")
                    putExtra(Intent.EXTRA_TEXT, "A crash occurred on PhoneGuard:\n\n$stackTrace")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", crashFile)
                    emailIntent.putExtra(Intent.EXTRA_STREAM, uri)
                    emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    Timber.w("Could not attach crash report file: ${e.message}")
                }

                try {
                    context.startActivity(emailIntent)
                } catch (e: Exception) {
                    Timber.e("Failed to launch crash email intent: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling crash")
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun init(context: Context) {
            val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (oldHandler !is CrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context, oldHandler))
            }
        }
    }
}
