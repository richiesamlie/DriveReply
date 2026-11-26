package com.drivereply.app.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale

/**
 * In-memory ring buffer of recent debug log lines, exposed as a snapshot
 * `List<String>` via [entries] for the Settings → Debugging viewer.
 */
object DebugEventLogger {

    private const val MAX_ENTRIES = 500
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        .withZone(ZoneId.systemDefault())
    private val processSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private val buffer = ArrayDeque<String>(MAX_ENTRIES)
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    @Volatile
    private var enabled = false

    fun isEnabled(): Boolean = enabled

    fun log(tag: String, message: String, throwable: Throwable? = null) {
        if (!enabled) return

        val timestamp = timeFormatter.format(Instant.now())
        val line = buildString {
            append("[")
            append(timestamp)
            append("] [")
            append(tag)
            append("] ")
            append(message)
            if (throwable != null) {
                append(" | ")
                append(throwable::class.java.simpleName)
                append(": ")
                append(throwable.message ?: "no-message")
            }
        }

        appendLine(line)

        if (throwable == null) {
            Log.d(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
    }

    @Synchronized
    private fun appendLine(line: String) {
        if (!enabled) return
        if (buffer.size == MAX_ENTRIES) {
            buffer.pollFirst()
        }
        buffer.addLast(line)
        _entries.value = buffer.toList()
    }

    fun d(tag: String, message: String) {
        log(tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(tag, message, throwable)
    }

    /**
     * Redacts sensitive PII identifiers (such as contact names, notification keys,
     * or Bluetooth device names/MACs) into a short process-salted token suitable
     * for diagnostic correlation without exposing personal data.
     */
    fun redact(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return "<redacted:empty>"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(processSalt)
            val digest = md.digest(trimmed.toByteArray(Charsets.UTF_8))
            val shortHex = digest.take(3).joinToString("") { "%02x".format(it) }
            "<redacted:#$shortHex>"
        } catch (_: Exception) {
            "<redacted>"
        }
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        val wasEnabled = enabled
        enabled = value
        if (value) {
            if (!wasEnabled) {
                log("DebugEventLogger", "Debug logging enabled")
            }
        } else {
            buffer.clear()
            _entries.value = emptyList()
        }
    }
}
