package com.drivereply.app.util

import com.drivereply.app.util.ApkUpdateInstaller.SignatureMatchResult
import com.drivereply.app.util.ApkUpdateInstaller.UpdateError
import com.drivereply.app.util.ApkUpdateInstaller.VersionCheckResult
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
    //  compareVersionCodes (pure, internal) — version direction gate.
    // ------------------------------------------------------------------

    @Test
    fun versionCheck_newerBuild_returnsNewer() {
        val r = ApkUpdateInstaller.compareVersionCodes(downloaded = 42, installed = 41)
        assertEquals(VersionCheckResult.Newer, r)
    }

    @Test
    fun versionCheck_equalBuilds_returnsAlreadyCurrent() {
        val r = ApkUpdateInstaller.compareVersionCodes(downloaded = 1, installed = 1)
        assertEquals(VersionCheckResult.AlreadyCurrent, r)
    }

    @Test
    fun versionCheck_olderBuild_returnsDowngradeWithPayload() {
        val r = ApkUpdateInstaller.compareVersionCodes(downloaded = 5, installed = 10)
        assertEquals(VersionCheckResult.Downgrade(downloaded = 5, installed = 10), r)
    }

    @Test
    fun versionCheck_firstInstall_returnsNewer() {
        val r = ApkUpdateInstaller.compareVersionCodes(downloaded = 1, installed = 0)
        assertEquals(VersionCheckResult.Newer, r)
    }

    // ------------------------------------------------------------------
    //  UpdateError variants (constructed only — no behavior to assert
    //  beyond type identity).
    // ------------------------------------------------------------------

    @Test
    fun updateError_signatureMismatch_carriesVerifierName() {
        val e = UpdateError.SignatureMismatch("PackageManagerSignatureStore")
        assertTrue(e.message.contains("PackageManagerSignatureStore"))
    }

    @Test
    fun updateError_downgrade_carriesBothCodes() {
        val e = UpdateError.Downgrade(downloaded = 5, installed = 10)
        assertTrue("expected both versionCodes in message: ${e.message}",
            e.message.contains("5") && e.message.contains("10"))
    }

    @Test
    fun updateError_alreadyCurrent_hasDescriptiveMessage() {
        val e: UpdateError = UpdateError.AlreadyCurrent
        assertTrue(e.message.contains("versionCode"))
    }

    @Test
    fun updateError_httpStatus_includesCodeInMessage() {
        val e = UpdateError.HttpStatus(404)
        assertTrue(e.message.contains("404"))
    }

    @Test
    fun updateError_wrongPackage_carriesBothNames() {
        val e = UpdateError.WrongPackage(
            expected = "com.drivereply.app",
            actual = "com.attacker.drivereply",
        )
        assertTrue("expected the actual package in the message: ${e.message}",
            e.message.contains("com.attacker.drivereply"))
        assertTrue("expected the expected package in the message: ${e.message}",
            e.message.contains("com.drivereply.app"))
    }

    @Test
    fun updateError_wrongPackage_handlesNullActual() {
        // Defensive: an APK with a missing packageName (rare but possible
        // for a corrupted manifest) must still surface a readable error.
        val e = UpdateError.WrongPackage(
            expected = "com.drivereply.app",
            actual = null,
        )
        assertTrue("expected the expected package in the message: ${e.message}",
            e.message.contains("com.drivereply.app"))
    }

    // ------------------------------------------------------------------
    //  Backoff scheduling
    // ------------------------------------------------------------------

    @Test
    fun backoff_firstAttempt_isOneSecond() {
        assertEquals(1_000L, ApkUpdateInstaller.computeBackoffMs(1))
    }

    @Test
    fun backoff_secondAttempt_isTwoSeconds() {
        assertEquals(2_000L, ApkUpdateInstaller.computeBackoffMs(2))
    }

    @Test
    fun backoff_thirdAttempt_isFourSeconds() {
        assertEquals(4_000L, ApkUpdateInstaller.computeBackoffMs(3))
    }

    @Test
    fun backoff_isCappedByMaxAttempts() {
        // In production the caller checks `attempt >= MAX_DOWNLOAD_ATTEMPTS`
        // and gives up before calling computeBackoffMs with a value past 3,
        // so the worst-case backoff we ever actually wait is 4 seconds.
        val lastAttempt = ApkUpdateInstaller.MAX_DOWNLOAD_ATTEMPTS
        val worstCaseMs = ApkUpdateInstaller.computeBackoffMs(lastAttempt)
        assertTrue("worst-case backoff should be reasonable: $worstCaseMs ms",
            worstCaseMs <= 10_000L)
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
