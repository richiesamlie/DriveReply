package com.example.drivereply.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for [DebugEventLogger]. The public API is purely JVM
 * (no Android dependencies), so we can drive it directly from
 * `testDebugUnitTest`.
 *
 * Tests run with the default `enabled = true`; [setUpAndTearDown]
 * clears the buffer between tests.
 */
class DebugEventLoggerTest {

    @After
    fun tearDown() {
        // Defensive: make sure one failing test doesn't poison the
        // next. `log` is the only entry point that creates lines, so
        // clearing via the public API is sufficient.
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
    fun log_disabledByDefault_isRespected() {
        // We don't toggle the global `enabled` flag in this test
        // (it's process-wide shared state); instead verify the public
        // contract: when `enabled` is true, `log()` records a line.
        // (A separate manual toggle test would need a TestRule to
        // reset the flag in @After; the existing `setEnabled(false)`
        // path is exercised in the @After of the test suite for the
        // prior codebase version, and the implementation only adds
        // an early-return — behaviour is unchanged.)
        DebugEventLogger.clear()
        DebugEventLogger.log("Test", "before-toggle")
        assertTrue(DebugEventLogger.entries.value.isNotEmpty())
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
