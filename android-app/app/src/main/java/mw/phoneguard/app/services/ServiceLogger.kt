package mw.phoneguard.app.services

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object ServiceLogger {
    private const val MAX_LOGS = 20
    private val logs = ConcurrentLinkedQueue<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        log("ServiceLogger", "Initialized logger buffer")
    }

    fun log(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] $tag: $message"
        logs.add(entry)
        while (logs.size > MAX_LOGS) {
            logs.poll()
        }
        Log.d(tag, message)
    }

    fun getLogs(): List<String> {
        return logs.toList()
    }
}
