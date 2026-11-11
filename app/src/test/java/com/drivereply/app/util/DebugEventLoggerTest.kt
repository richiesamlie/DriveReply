package com.drivereply.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DebugEventLogger]. The public API is purely JVM
 * (no Android dependencies), so we can drive it directly from
 * `testDebugUnitTest`.
 *
 * Production `enabled` defaults to `false` (opt-in diagnostic logging).
 * [setUp] enables logging and clears the activation line so individual
 * tests start with an empty buffer; [tearDown] resets `enabled = false`.
 */
class DebugEventLoggerTest {

    @Before
    fun setUp() {
        DebugEventLogger.setEnabled(true)
        DebugEventLogger.clear()
    }

    @After
    fun tearDown() {
        DebugEventLogger.setEnabled(false)
        DebugEventLogger.clear()
    }

    @Test
    fun log_addsLine_toEntries() {
        DebugEventLogger.log("Test", "hello world")
        val lines = DebugEventLogger.entries.value
        assertEquals(1, lines.size)
        assertTrue("line should contain the message: ${lines[0]}",
            lines[0].contains("hello world"))
        assertTrue("line should contain the tag: ${lines[0]}",
            lines[0].contains("[Test]"))
        assertTrue("line should contain a timestamp: ${lines[0]}",
            lines[0].contains("[20")) // yyyy-MM-dd starts with "20"
    }

    @Test
    fun log_appendsInOrder() {
        DebugEventLogger.log("Test", "first")
        DebugEventLogger.log("Test", "second")
        DebugEventLogger.log("Test", "third")
        val lines = DebugEventLogger.entries.value
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("first"))
        assertTrue(lines[1].contains("second"))
        assertTrue(lines[2].contains("third"))
    }

    @Test
    fun log_respectsCap_oldestEvicted() {
        // MAX_ENTRIES is 500. Push 510, verify the first 10 are gone
        // and the last 500 remain.
        repeat(510) { i -> DebugEventLogger.log("Test", "line-$i") }
        val lines = DebugEventLogger.entries.value
        assertEquals(500, lines.size)
        // The oldest surviving line should be "line-10" (we evicted 0..9).
        assertTrue("oldest surviving should be line-10: ${lines.first()}",
            lines.first().contains("line-10"))
        // The newest should be "line-509".
        assertTrue("newest should be line-509: ${lines.last()}",
            lines.last().contains("line-509"))
    }

    @Test
    fun clear_emptiesTheBuffer() {
        DebugEventLogger.log("Test", "alpha")
        DebugEventLogger.log("Test", "beta")
        assertEquals(2, DebugEventLogger.entries.value.size)
        DebugEventLogger.clear()
        assertEquals(0, DebugEventLogger.entries.value.size)
    }

    @Test
    fun log_withThrowable_includesExceptionTypeAndMessage() {
        val ex = IllegalStateException("kaboom")
        DebugEventLogger.log("Test", "operation failed", ex)
        val line = DebugEventLogger.entries.value.single()
        assertTrue("expected exception type in line: $line",
            line.contains("IllegalStateException"))
        assertTrue("expected exception message in line: $line",
            line.contains("kaboom"))
    }

    @Test
    fun setEnabled_false_clearsBufferAndSuppressesSubsequentLogs() {
        DebugEventLogger.log("Test", "sensitive-event")
        assertEquals(1, DebugEventLogger.entries.value.size)

        DebugEventLogger.setEnabled(false)
        assertFalse(DebugEventLogger.isEnabled())
        assertTrue(DebugEventLogger.entries.value.isEmpty())

        DebugEventLogger.log("Test", "should-be-dropped")
        DebugEventLogger.d("Test", "debug-dropped")
        DebugEventLogger.w("Test", "warn-dropped")
        assertTrue(DebugEventLogger.entries.value.isEmpty())

        DebugEventLogger.setEnabled(true)
        assertTrue(DebugEventLogger.isEnabled())
        assertEquals(1, DebugEventLogger.entries.value.size)
        assertTrue(DebugEventLogger.entries.value.single().contains("Debug logging enabled"))
    }

    @Test
    fun redact_masksSenderIdentityDeterministicallyWithoutLeakingRawValue() {
        assertEquals("<redacted:empty>", DebugEventLogger.redact(null))
        assertEquals("<redacted:empty>", DebugEventLogger.redact("   "))

        val alice1 = DebugEventLogger.redact("Alice Smith")
        val alice2 = DebugEventLogger.redact("  Alice Smith ")
        val bob = DebugEventLogger.redact("Bob Jones")

        assertEquals(alice1, alice2)
        assertNotEquals(alice1, bob)
        assertFalse(alice1.contains("Alice", ignoreCase = true))
        assertTrue(alice1.startsWith("<redacted:#") && alice1.endsWith(">"))
    }

    @Test
    fun log_isThreadSafe_underBurst() {
        // Spawn N threads each logging M lines; the total should be
        // exactly N*M, bounded by MAX_ENTRIES. This exercises the
        // @Synchronized guards.
        val threads = 8
        val perThread = 50
        val runners = (0 until threads).map { tid ->
            Thread {
                repeat(perThread) { i ->
                    DebugEventLogger.log("T$tid", "msg-$i")
                }
            }
        }
        runners.forEach { it.start() }
        runners.forEach { it.join() }

        val total = threads * perThread
        val expected = total.coerceAtMost(500) // MAX_ENTRIES
        assertEquals(expected, DebugEventLogger.entries.value.size)
    }
}
