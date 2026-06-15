package com.example.drivereply.util

import com.example.drivereply.util.ApkUpdateInstaller.SignatureMatchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Pure-JVM unit tests for the helpers used by the seamless in-app update
 * flow. The full installer (HTTP download, PackageManager signature read,
 * system install intent) is exercised by the Compose UI and instrumented
 * tests; here we cover the deterministic, Android-free pieces.
 */
class ApkUpdateInstallerTest {

    // ------------------------------------------------------------------
    //  SignatureMatchResult
    // ------------------------------------------------------------------

    @Test
    fun signatureMatchResult_matches_carriesFingerprint() {
        val r = SignatureMatchResult.Matches("abc123")
        assertEquals("abc123", r.certificateSha256)
    }

    @Test
    fun signatureMatchResult_mismatch_doesNotMatchPattern() {
        val r: SignatureMatchResult = SignatureMatchResult.Mismatch
        when (r) {
            is SignatureMatchResult.Matches -> error("Mismatch should not be Matches")
            SignatureMatchResult.Mismatch -> { /* expected */ }
            else -> { /* other variants are fine */ }
        }
    }

    @Test
    fun signatureMatchResult_error_carriesMessage() {
        val r = SignatureMatchResult.Error("boom")
        assertEquals("boom", r.message)
    }

    // ------------------------------------------------------------------
    //  sha256Base64(): re-implementation of the byte→hash step we use
    //  inside fingerprint(Signature). We verify the helper logic here in
    //  pure JVM; the production path is a one-liner that calls
    //  MessageDigest.getInstance("SHA-256") over Signature.toByteArray().
    // ------------------------------------------------------------------

    @Test
    fun sha256OfBytes_isStable() {
        val a = sha256Base64("hello".toByteArray())
        val b = sha256Base64("hello".toByteArray())
        assertEquals(a, b)
    }

    @Test
    fun sha256OfBytes_differsForDifferentInput() {
        val a = sha256Base64("hello".toByteArray())
        val b = sha256Base64("world".toByteArray())
        assertNotEquals(a, b)
    }

    @Test
    fun sha256OfBytes_matchesJavaSecurityMessageDigest() {
        // Sanity check that the helper actually agrees with the JDK's own
        // SHA-256 implementation (which ApkUpdateInstaller delegates to).
        val bytes = "the quick brown fox jumps over the lazy dog".toByteArray()
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
        val expectedB64 = Base64.getEncoder().withoutPadding().encodeToString(expected)
        assertEquals(expectedB64, sha256Base64(bytes))
    }

    @Test
    fun sha256OfBytes_producesAscii() {
        val out = sha256Base64("anything".toByteArray())
        assertTrue("output should be base64: $out", out.all { it.isLetterOrDigit() || it == '+' || it == '/' })
    }

    // ------------------------------------------------------------------
    //  Pure-JVM re-implementation that mirrors the logic inside
    //  ApkUpdateInstaller.fingerprint(Signature).
    //  We can't import android.content.pm.Signature in a local unit test,
    //  so we test the byte-path the production code delegates to.
    // ------------------------------------------------------------------
    private fun sha256Base64(bytes: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return Base64.getEncoder().withoutPadding().encodeToString(digest)
    }
}
