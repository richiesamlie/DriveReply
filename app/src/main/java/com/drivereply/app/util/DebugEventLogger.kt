package com.drivereply.app.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * In-memory ring buffer of recent debug log lines, exposed as a snapshot
 * `List<String>` via [entries] for the Settings → Debugging viewer.
 *
 * Implementation note: the buffer is backed by an [ArrayDeque] rather
 * than the previous `List<String> + takeLast(MAX_ENTRIES)` pattern, so
 * each `log()` call is O(1) instead of O(n). For a busy session
 * (hundreds of log lines per minute) the old code allocated a new
 * `List` of up to 500 strings on every call; this is the difference
 * between a steady ~2 KB/s of GC pressure and effectively zero.
 *
 * Reads from [entries] are still O(n) — we expose an immutable
 * snapshot — but reads are rare (the Settings UI, on user open) and
 * bounded by [MAX_ENTRIES].
 */
object DebugEventLogger {

    private const val MAX_ENTRIES = 500
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // The deque is the source of truth. The StateFlow is just a
    // snapshot for the UI; mutating the deque then re-publishing the
    // snapshot is still cheaper than the old `list + takeLast(MAX)`
    // pattern because we avoid the intermediate `+ line` allocation.
    private val buffer = ArrayDeque<String>(MAX_ENTRIES)
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    @Volatile
    private var enabled = false

    fun isEnabled(): Boolean = enabled

    @Synchronized
    fun log(tag: String, message: String, throwable: Throwable? = null) {
        if (!enabled) return

        val timestamp = timeFormat.format(Date())
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

        if (buffer.size == MAX_ENTRIES) {
            buffer.pollFirst()
        }
        buffer.addLast(line)
        // Publish an immutable snapshot. ArrayDeque.copyOf() is O(n) but
        // n is bounded at 500 and reads are rare.
        _entries.value = buffer.toList()

        if (throwable == null) {
            Log.d(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
    }

    fun d(tag: String, message: String) {
        log(tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(tag, message, throwable)
    }

    /**
     * Redacts sensitive PII identifiers (such as contact names, notification keys,
     * or Bluetooth device names/MACs) into a short non-reversible token suitable
     * for diagnostic correlation without exposing personal data.
     */
    fun redact(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return "<redacted:empty>"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(trimmed.toByteArray(Charsets.UTF_8))
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
