package com.example.drivereply.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Streams an APK from a remote URL into the app cache, verifies its signing
 * certificate matches the currently installed app, and launches the system
 * package installer via a FileProvider URI.
 *
 * Public surface (intentionally small):
 *   - [downloadAndVerify]  : Flow<UpdateEvent> — emit progress, then
 *                            complete with the local APK file on success or
 *                            a typed error on failure.
 *   - [launchInstaller]    : Result<Unit> — fires the system install intent
 *                            for a previously-downloaded APK.
 *   - [cancel]             : void — aborts the in-flight download.
 *
 * Everything else is private or `internal` so tests can swap out the
 * transport and signature store.
 */
class ApkUpdateInstaller(
    private val context: Context,
    private val transport: UpdateTransport = HttpUrlConnectionTransport,
    private val signatureStore: SignatureStore = PackageManagerSignatureStore(context),
) {

    /** Where downloaded APKs live. Wiped on cache clear or app uninstall. */
    private val updatesDir: File
        get() = File(context.cacheDir, "updates").apply { mkdirs() }

    @Volatile
    private var currentConnection: HttpURLConnection? = null

    /**
     * Download [url] into the cache and verify it is signed by the same
     * certificate that signed the currently installed app. Emits progress
     * events for the UI to render.
     */
    fun downloadAndVerify(
        url: String,
        targetTag: String,
        channelId: String = DEFAULT_CHANNEL_ID,
    ): Flow<UpdateEvent> = flow<UpdateEvent> {
        val targetFile = File(updatesDir, "DriveReply-$targetTag.apk")
        // Best-effort cleanup of previous attempts (only the same tag).
        if (targetFile.exists() && targetFile.length() == 0L) targetFile.delete()
        emit(UpdateEvent.Preparing(targetFile))
        val conn = transport.open(url)
        try {
            currentConnection = conn
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                emit(UpdateEvent.Failed(UpdateError.HttpStatus(responseCode)))
                return@flow
            }
            val totalBytes = conn.contentLengthLong.takeIf { it > 0 }
            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    var lastEmittedPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (totalBytes != null) {
                            val percent = ((written * 100L) / totalBytes).toInt()
                            if (percent != lastEmittedPercent) {
                                lastEmittedPercent = percent
                                emit(UpdateEvent.Progress(percent, written, totalBytes))
                            }
                        }
                    }
                    output.flush()
                }
            }
            emit(UpdateEvent.Verifying(targetFile))
            val verification = signatureStore.verifyMatchesInstalledApp(targetFile)
            if (verification !is SignatureMatchResult.Matches) {
                if (!targetFile.delete()) {
                    // leave it; cache clear will eventually reap it
                }
                emit(UpdateEvent.Failed(UpdateError.SignatureMismatch(verification.javaClass.simpleName)))
                return@flow
            }
            emit(UpdateEvent.Ready(targetFile, verification.certificateSha256))
        } catch (e: IOException) {
            emit(UpdateEvent.Failed(UpdateError.Io(e.message ?: "I/O error")))
        } catch (e: SecurityException) {
            emit(UpdateEvent.Failed(UpdateError.PermissionDenied(e.message ?: "Permission denied")))
        } catch (e: Exception) {
            emit(UpdateEvent.Failed(UpdateError.Unknown(e.message ?: "Unknown error")))
        } finally {
            currentConnection = null
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fire the system install intent for [apk]. The user sees the standard
     * Android install confirmation dialog; the update is *not* silent.
     */
    fun launchInstaller(apk: File): Result<Unit> {
        if (!apk.exists() || apk.length() == 0L) {
            return Result.failure(IllegalStateException("APK file missing or empty"))
        }
        val authority = "${context.packageName}.updater.fileprovider"
        val uri: Uri = try {
            FileProvider.getUriForFile(context, authority, apk)
        } catch (e: IllegalArgumentException) {
            return Result.failure(IllegalStateException(
                "FileProvider misconfigured: ${e.message}", e
            ))
        }
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        return try {
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Result.failure(e)
        }
    }

    fun cancel() {
        currentConnection?.disconnect()
        currentConnection = null
    }

    // ---------------------------------------------------------------------
    //  Events
    // ---------------------------------------------------------------------

    sealed interface UpdateEvent {
        data class Preparing(val target: File) : UpdateEvent
        data class Progress(val percent: Int, val bytesRead: Long, val totalBytes: Long) : UpdateEvent
        data class Verifying(val target: File) : UpdateEvent
        data class Ready(val target: File, val certificateSha256: String) : UpdateEvent
        data class Failed(val error: UpdateError) : UpdateEvent
    }

    sealed class UpdateError(open val message: String) {
        data class HttpStatus(val code: Int) : UpdateError("HTTP $code")
        data class Io(override val message: String) : UpdateError(message)
        data class PermissionDenied(override val message: String) : UpdateError(message)
        data class SignatureMismatch(val verifier: String) :
            UpdateError("Signing certificate mismatch (verifier=$verifier)")
        data class Unknown(override val message: String) : UpdateError(message)
    }

    // ---------------------------------------------------------------------
    //  Pluggable surface for tests
    // ---------------------------------------------------------------------

    /** Anything that can produce a single HTTP-GET connection. */
    fun interface UpdateTransport {
        fun open(url: String): HttpURLConnection
    }

    object HttpUrlConnectionTransport : UpdateTransport {
        override fun open(url: String): HttpURLConnection {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.android.package-archive")
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            return conn
        }
    }

    /** Outcome of comparing the downloaded APK's signer to the installed app. */
    sealed interface SignatureMatchResult {
        /**
         * Downloaded APK is signed by the same certificate as the installed app.
         * [certificateSha256] is the base64-NOPAD SHA-256 of the raw signer bytes,
         * suitable for logging / diagnostics only.
         */
        data class Matches(val certificateSha256: String) : SignatureMatchResult
        data object NoSigners : SignatureMatchResult
        data object NoInstalledSigners : SignatureMatchResult
        data object Mismatch : SignatureMatchResult
        data class Error(val message: String) : SignatureMatchResult
    }

    fun interface SignatureStore {
        fun verifyMatchesInstalledApp(apk: File): SignatureMatchResult
    }

    /**
     * Default [SignatureStore] backed by the platform PackageManager.
     * Uses `GET_SIGNING_CERTIFICATES` (API 28+) — minSdk is 29, so this is
     * always available.
     */
    class PackageManagerSignatureStore(private val context: Context) : SignatureStore {
        override fun verifyMatchesInstalledApp(apk: File): SignatureMatchResult {
            val pm = context.packageManager
            return try {
                val apkInfo: PackageInfo = pm.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                ) ?: return SignatureMatchResult.Error("PackageManager returned null for APK")
                val apkSigners = apkInfo.signingInfo
                    ?.takeIf { it.hasMultipleSigners() || it.hasPastSigningCertificates() }
                    ?.signingCertificateHistory
                    ?: apkInfo.signingInfo?.apkContentsSigners
                    ?: return SignatureMatchResult.NoSigners
                val apkSig = apkSigners.firstOrNull() ?: return SignatureMatchResult.NoSigners

                val installed: PackageInfo = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                val installedSigners = installed.signingInfo
                    ?.takeIf { it.hasMultipleSigners() || it.hasPastSigningCertificates() }
                    ?.signingCertificateHistory
                    ?: installed.signingInfo?.apkContentsSigners
                    ?: return SignatureMatchResult.NoInstalledSigners
                val installedSig = installedSigners.firstOrNull()
                    ?: return SignatureMatchResult.NoInstalledSigners

                if (signaturesEqual(apkSig, installedSig)) {
                    SignatureMatchResult.Matches(fingerprint(installedSig))
                } else {
                    SignatureMatchResult.Mismatch
                }
            } catch (e: Exception) {
                SignatureMatchResult.Error(e.message ?: e.javaClass.simpleName)
            }
        }

        private fun signaturesEqual(a: Signature, b: Signature): Boolean {
            // Bytewise equality is correct for an APK's `apkContentsSigners`
            // because the platform returns the raw certificate bytes of the
            // exact signer that v2/v3 verified against. No DER parsing needed
            // unless the platform starts rotating the same key, in which case
            // the byte sequences differ and we correctly reject.
            return MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
        }
    }

    companion object {
        const val DEFAULT_CHANNEL_ID = "drive_reply_updates"

        /**
         * Base64-NOPAD SHA-256 of a Signature's bytes. Exposed for the
         * notification/debug-log path; not used in the equality check.
         */
        fun fingerprint(signature: Signature): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(signature.toByteArray())
            return Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }
}

/**
 * Convenience: build the persistent notification shown while an update
 * downloads. Returns null on API < 26 (we don't pre-Oreo users; minSdk 29).
 */
fun NotificationCompat.Builder.updateProgress(
    percent: Int,
    contentTitle: String,
    contentText: String,
    smallIcon: Int = android.R.drawable.stat_sys_download,
): NotificationCompat.Builder = this
    .setSmallIcon(smallIcon)
    .setContentTitle(contentTitle)
    .setContentText(contentText)
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setProgress(100, percent.coerceIn(0, 100), percent == 0)
    .setPriority(NotificationCompat.PRIORITY_LOW)
    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
