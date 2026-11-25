package com.drivereply.app.util

import android.content.ContextWrapper
import com.drivereply.app.util.ApkUpdateInstaller.ArchiveInspection
import com.drivereply.app.util.ApkUpdateInstaller.ArchiveInspector
import com.drivereply.app.util.ApkUpdateInstaller.SignatureMatchResult
import com.drivereply.app.util.ApkUpdateInstaller.UpdateError
import com.drivereply.app.util.ApkUpdateInstaller.UpdateEvent
import com.drivereply.app.util.ApkUpdateInstaller.UpdateNotifier
import com.drivereply.app.util.ApkUpdateInstaller.VersionCheckResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Pure-JVM unit tests for [ApkUpdateInstaller] covering signature helpers,
 * version comparison, backoff math, and end-to-end [ApkUpdateInstaller.downloadAndVerify]
 * pipeline behavior using fake transport, archive inspector, signature store,
 * and notifier implementations.
 */
class ApkUpdateInstallerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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
    //  fingerprintBytes(): exercises the production SHA-256 -> Base64
    //  helper directly on the JVM.
    // ------------------------------------------------------------------

    @Test
    fun sha256OfBytes_isStable() {
        val a = ApkUpdateInstaller.fingerprintBytes("hello".toByteArray())
        val b = ApkUpdateInstaller.fingerprintBytes("hello".toByteArray())
        assertEquals(a, b)
    }

    @Test
    fun sha256OfBytes_differsForDifferentInput() {
        val a = ApkUpdateInstaller.fingerprintBytes("hello".toByteArray())
        val b = ApkUpdateInstaller.fingerprintBytes("world".toByteArray())
        assertNotEquals(a, b)
    }

    @Test
    fun sha256OfBytes_matchesJavaSecurityMessageDigest() {
        val bytes = "the quick brown fox jumps over the lazy dog".toByteArray()
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
        val expectedB64 = Base64.getEncoder().withoutPadding().encodeToString(expected)
        assertEquals(expectedB64, ApkUpdateInstaller.fingerprintBytes(bytes))
    }

    @Test
    fun sha256OfBytes_producesAscii() {
        val out = ApkUpdateInstaller.fingerprintBytes("anything".toByteArray())
        assertTrue("output should be base64: $out", out.all { it.isLetterOrDigit() || it == '+' || it == '/' })
    }

    // ------------------------------------------------------------------
    //  sanitizeTag & isTrustedUpdateUrl
    // ------------------------------------------------------------------

    @Test
    fun sanitizeTag_stripsPathTraversalAndUnsafeChars() {
        assertEquals("v2.0.5", ApkUpdateInstaller.sanitizeTag("v2.0.5"))
        assertEquals("evil", ApkUpdateInstaller.sanitizeTag("../../evil"))
        assertEquals("latest", ApkUpdateInstaller.sanitizeTag("   "))
        assertEquals("latest", ApkUpdateInstaller.sanitizeTag("..."))
    }

    @Test
    fun isTrustedUpdateUrl_allowsOnlyHttpsGitHubHosts() {
        assertTrue(
            ApkUpdateInstaller.isTrustedUpdateUrl(
                "https://github.com/richiesamlie/DriveReply/releases/download/v2.0.5/DriveReply-v2.0.5-release.apk"
            )
        )
        assertTrue(
            ApkUpdateInstaller.isTrustedUpdateUrl(
                "https://objects.githubusercontent.com/github-production-release-asset-2e65be/123/apk"
            )
        )
        assertTrue(
            !ApkUpdateInstaller.isTrustedUpdateUrl(
                "http://github.com/richiesamlie/DriveReply/releases/download/v2.0.5/DriveReply-v2.0.5-release.apk"
            )
        )
        assertTrue(
            !ApkUpdateInstaller.isTrustedUpdateUrl(
                "https://evil.example.com/DriveReply-v2.0.5-release.apk"
            )
        )
        assertTrue(!ApkUpdateInstaller.isTrustedUpdateUrl(""))
    }

    // ------------------------------------------------------------------
    //  verifySignerBytesMatch (single-signer, multi-signer, key rotation)
    // ------------------------------------------------------------------

    @Test
    fun verifySignerBytesMatch_matchingSingleSigner_returnsMatches() {
        val certA = "cert-alpha".toByteArray()
        val result = ApkUpdateInstaller.verifySignerBytesMatch(
            apkContentsSigners = listOf(certA),
            installedContentsSigners = listOf(certA.copyOf())
        )
        assertEquals(
            SignatureMatchResult.Matches(ApkUpdateInstaller.fingerprintBytes(certA)),
            result
        )
    }

    @Test
    fun verifySignerBytesMatch_differentSingleSignerWithoutLineage_returnsMismatch() {
        val certA = "cert-alpha".toByteArray()
        val certB = "cert-beta".toByteArray()
        val result = ApkUpdateInstaller.verifySignerBytesMatch(
            apkContentsSigners = listOf(certB),
            installedContentsSigners = listOf(certA)
        )
        assertEquals(SignatureMatchResult.Mismatch, result)
    }

    @Test
    fun verifySignerBytesMatch_keyRotationLineageContainsInstalledAndEndsAtApkActive_returnsMatches() {
        val oldCert = "cert-old".toByteArray()
        val newCert = "cert-new".toByteArray()
        val result = ApkUpdateInstaller.verifySignerBytesMatch(
            apkContentsSigners = listOf(newCert),
            apkHistorySigners = listOf(oldCert, newCert),
            installedContentsSigners = listOf(oldCert)
        )
        assertEquals(
            SignatureMatchResult.Matches(ApkUpdateInstaller.fingerprintBytes(newCert)),
            result
        )
    }

    @Test
    fun verifySignerBytesMatch_forgedHistoryNotEndingAtActive_returnsMismatch() {
        val oldCert = "cert-old".toByteArray()
        val newCert = "cert-new".toByteArray()
        val unrelatedCert = "cert-unrelated".toByteArray()
        val result = ApkUpdateInstaller.verifySignerBytesMatch(
            apkContentsSigners = listOf(newCert),
            apkHistorySigners = listOf(oldCert, unrelatedCert),
            installedContentsSigners = listOf(oldCert)
        )
        assertEquals(SignatureMatchResult.Mismatch, result)
    }

    @Test
    fun verifySignerBytesMatch_multiSignerSetOrderIndependent_returnsMatches() {
        val certA = "cert-alpha".toByteArray()
        val certB = "cert-beta".toByteArray()
        val result = ApkUpdateInstaller.verifySignerBytesMatch(
            apkContentsSigners = listOf(certB, certA),
            apkHasMultipleSigners = true,
            installedContentsSigners = listOf(certA, certB),
            installedHasMultipleSigners = true
        )
        assertTrue(result is SignatureMatchResult.Matches)
    }

    @Test
    fun verifySignerBytesMatch_emptySigners_returnsExpectedSentinel() {
        val certA = "cert-alpha".toByteArray()
        assertEquals(
            SignatureMatchResult.NoSigners,
            ApkUpdateInstaller.verifySignerBytesMatch(
                apkContentsSigners = emptyList(),
                installedContentsSigners = listOf(certA)
            )
        )
        assertEquals(
            SignatureMatchResult.NoInstalledSigners,
            ApkUpdateInstaller.verifySignerBytesMatch(
                apkContentsSigners = listOf(certA),
                installedContentsSigners = emptyList()
            )
        )
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
    //  End-to-end downloadAndVerify pipeline (fake transport & verifier)
    // ------------------------------------------------------------------

    @Test
    fun downloadAndVerify_happyPath_promotesPartFileAndCleansStaleFiles() = runTest {
        val updatesDir = tempFolder.newFolder("updates")
        val stale = File(updatesDir, "DriveReply-v2.0.46-old.apk").apply { writeText("stale") }
        val payload = ByteArray(1024) { 7 }
        val notifier = RecordingNotifier()
        val installer = buildInstaller(
            updatesDir = updatesDir,
            responses = ArrayDeque(listOf(FakeResponse.Success(payload))),
            inspector = FakeArchiveInspector("com.drivereply.app", 48L, 47L),
            signatureResult = SignatureMatchResult.Matches("cert-sha"),
            notifier = notifier
        )

        val events = installer.downloadAndVerify("https://example.invalid/app.apk", "v2.0.48").toList()

        assertFalse("stale file should be deleted", stale.exists())
        assertFalse("temp .part file should be promoted", File(updatesDir, "DriveReply-v2.0.48.apk.part").exists())
        assertTrue(events.first() is UpdateEvent.Preparing)
        assertTrue(events.any { it is UpdateEvent.Progress && it.percent == 100 })
        assertTrue(events.any { it is UpdateEvent.Verifying })
        val ready = events.last() as UpdateEvent.Ready
        assertEquals("cert-sha", ready.certificateSha256)
        assertEquals(payload.size.toLong(), ready.target.length())
        assertEquals("cert-sha", notifier.readySha)
    }

    @Test
    fun downloadAndVerify_retriesTransient503ThenSucceeds() = runTest {
        val updatesDir = tempFolder.newFolder("updates")
        val payload = "apk-bytes".toByteArray()
        var attempts = 0
        val queue = ArrayDeque<FakeResponse>(
            listOf(FakeResponse.Status(503), FakeResponse.Success(payload))
        )
        val installer = ApkUpdateInstaller(
            context = ContextWrapper(null),
            transport = {
                attempts++
                queue.removeFirst().toConnection()
            },
            archiveInspector = FakeArchiveInspector("com.drivereply.app", 48L, 47L),
            signatureStore = { _, _ -> SignatureMatchResult.Matches("ok") },
            notifier = RecordingNotifier(),
            updatesDirOverride = updatesDir
        )

        val events = installer.downloadAndVerify("https://example.invalid/app.apk", "v2.0.48").toList()

        assertEquals(2, attempts)
        assertTrue(events.last() is UpdateEvent.Ready)
    }

    @Test
    fun downloadAndVerify_failsImmediatelyOnPermanent404() = runTest {
        val updatesDir = tempFolder.newFolder("updates")
        var attempts = 0
        val installer = ApkUpdateInstaller(
            context = ContextWrapper(null),
            transport = {
                attempts++
                FakeResponse.Status(404).toConnection()
            },
            archiveInspector = FakeArchiveInspector("com.drivereply.app", 48L, 47L),
            signatureStore = { _, _ -> SignatureMatchResult.Matches("ok") },
            notifier = RecordingNotifier(),
            updatesDirOverride = updatesDir
        )

        val events = installer.downloadAndVerify("https://example.invalid/app.apk", "v2.0.48").toList()

        assertEquals(1, attempts)
        val failed = events.last() as UpdateEvent.Failed
        assertEquals(UpdateError.HttpStatus(404), failed.error)
    }

    @Test
    fun downloadAndVerify_rejectsWrongPackageAndDeletesPartFile() = runTest {
        val updatesDir = tempFolder.newFolder("updates")
        val installer = buildInstaller(
            updatesDir = updatesDir,
            responses = ArrayDeque(listOf(FakeResponse.Success("bad".toByteArray()))),
            inspector = FakeArchiveInspector("com.evil.clone", 99L, 47L),
            signatureResult = SignatureMatchResult.Matches("ok"),
            notifier = RecordingNotifier()
        )

        val events = installer.downloadAndVerify("https://example.invalid/app.apk", "v2.0.48").toList()

        val failed = events.last() as UpdateEvent.Failed
        assertEquals(UpdateError.WrongPackage("com.drivereply.app", "com.evil.clone"), failed.error)
        assertFalse(File(updatesDir, "DriveReply-v2.0.48.apk").exists())
        assertFalse(File(updatesDir, "DriveReply-v2.0.48.apk.part").exists())
    }

    @Test
    fun downloadAndVerify_rejectsDowngradeAndSignatureMismatch() = runTest {
        val updatesDir = tempFolder.newFolder("updates")
        val downgradeInstaller = buildInstaller(
            updatesDir = updatesDir,
            responses = ArrayDeque(listOf(FakeResponse.Success("old".toByteArray()))),
            inspector = FakeArchiveInspector("com.drivereply.app", 40L, 47L),
            signatureResult = SignatureMatchResult.Matches("ok"),
            notifier = RecordingNotifier()
        )
        val downgradeEvents = downgradeInstaller.downloadAndVerify("https://example.invalid/old.apk", "v2.0.40").toList()
        assertEquals(UpdateError.Downgrade(40L, 47L), (downgradeEvents.last() as UpdateEvent.Failed).error)

        val mismatchInstaller = buildInstaller(
            updatesDir = updatesDir,
            responses = ArrayDeque(listOf(FakeResponse.Success("bad-sig".toByteArray()))),
            inspector = FakeArchiveInspector("com.drivereply.app", 48L, 47L),
            signatureResult = SignatureMatchResult.Mismatch,
            notifier = RecordingNotifier()
        )
        val mismatchEvents = mismatchInstaller.downloadAndVerify("https://example.invalid/bad.apk", "v2.0.48").toList()
        assertTrue((mismatchEvents.last() as UpdateEvent.Failed).error is UpdateError.SignatureMismatch)
        assertFalse(File(updatesDir, "DriveReply-v2.0.48.apk").exists())
        assertFalse(File(updatesDir, "DriveReply-v2.0.48.apk.part").exists())
    }

    private fun buildInstaller(
        updatesDir: File,
        responses: ArrayDeque<FakeResponse>,
        inspector: ArchiveInspector,
        signatureResult: SignatureMatchResult,
        notifier: UpdateNotifier
    ): ApkUpdateInstaller {
        return ApkUpdateInstaller(
            context = ContextWrapper(null),
            transport = { responses.removeFirst().toConnection() },
            archiveInspector = inspector,
            signatureStore = { _, _ -> signatureResult },
            notifier = notifier,
            updatesDirOverride = updatesDir
        )
    }

    private sealed interface FakeResponse {
        data class Success(val bytes: ByteArray) : FakeResponse
        data class Status(val code: Int) : FakeResponse

        fun toConnection(): HttpURLConnection = when (this) {
            is Success -> FakeHttpURLConnection(200, bytes)
            is Status -> FakeHttpURLConnection(code, ByteArray(0))
        }
    }

    private class FakeHttpURLConnection(
        private val status: Int,
        private val body: ByteArray
    ) : HttpURLConnection(URL("https://example.invalid/app.apk")) {
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun connect() {}
        override fun getResponseCode(): Int = status
        override fun getContentLengthLong(): Long = body.size.toLong()
        override fun getInputStream(): InputStream {
            if (status !in 200..299) throw IOException("HTTP $status")
            return ByteArrayInputStream(body)
        }
    }

    private class FakeArchiveInspector(
        private val packageName: String?,
        private val versionCode: Long,
        private val installedCode: Long,
        override val expectedPackageName: String = "com.drivereply.app"
    ) : ArchiveInspector {
        override fun inspectDownloadedApk(apk: File): Result<ArchiveInspection> =
            Result.success(ArchiveInspection(packageName, versionCode, null))

        override fun installedVersionCode(): Result<Long> =
            Result.success(installedCode)
    }

    private class RecordingNotifier : UpdateNotifier {
        var readySha: String? = null
        override fun onPreparing(channelId: String, targetTag: String) {}
        override fun onProgress(channelId: String, targetTag: String, percent: Int) {}
        override fun onVerifying(channelId: String, targetTag: String) {}
        override fun onReady(channelId: String, targetTag: String, certificateSha256: String) {
            readySha = certificateSha256
        }
        override fun onFailed(channelId: String, targetTag: String, reason: String) {}
    }
}
