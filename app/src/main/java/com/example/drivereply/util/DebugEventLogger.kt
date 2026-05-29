package com.example.drivereply.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugEventLogger {

    private const val MAX_ENTRIES = 500
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()
    @Volatile
    private var enabled = true

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

        _entries.value = (_entries.value + line).takeLast(MAX_ENTRIES)
        if (throwable == null) {
            Log.d(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        enabled = value
        if (value) {
            log("DebugEventLogger", "Debug logging enabled")
        } else {
            Log.d("DebugEventLogger", "Debug logging disabled")
        }
    }
}
