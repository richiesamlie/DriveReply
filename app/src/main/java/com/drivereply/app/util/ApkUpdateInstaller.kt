package com.drivereply.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.drivereply.app.MainActivity
import com.drivereply.app.R
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
 * Streams an APK from a remote URL into the app cache, verifies it is a
 * newer signed-by-us build, optionally posts a system progress
 * notification, and launches the system package installer via a
 * FileProvider URI.
 *
 * Public surface (intentionally small):
 *   - [downloadAndVerify]  : Flow<UpdateEvent> — emit progress, then
 *                            complete with the local APK file on success
 *                            or a typed [UpdateError] on failure.
 *   - [launchInstaller]    : Result<Unit> — fires the system install
 *                            intent for a previously-downloaded APK.
 *   - [cancel]             : void — aborts the in-flight download.
 *
 * Everything else is `private` or `internal` so tests can swap out the
 * transport, signature store, and notifier.
 */
class ApkUpdateInstaller(
    private val context: Context,
    private val transport: UpdateTransport = HttpUrlConnectionTransport,
    private val signatureStore: SignatureStore = PackageManagerSignatureStore(context),
    private val notifier: UpdateNotifier = SystemUpdateNotifier(context),
) {

    /** Where downloaded APKs live. Wiped on cache clear or app uninstall. */
    private val updatesDir: File
        get() = File(context.cacheDir, "updates").apply { mkdirs() }

    @Volatile
    private var currentConnection: HttpURLConnection? = null

    /**
     * Download [url] into the cache and verify it is a newer build signed
     * by the same certificate as the installed app. Emits progress
     * events for the UI to render.
     */
    fun downloadAndVerify(
        url: String,
        targetTag: String,
        channelId: String = DEFAULT_CHANNEL_ID,
    ): Flow<UpdateEvent> = flow<UpdateEvent> {
        val targetFile = File(updatesDir, "DriveReply-$targetTag.apk")
        // Best-effort cleanup of any previous in-progress attempt:
        //   - zero-byte placeholder from a canceled download
        //   - any older file in updates/ that we did not produce
        //     ourselves (an interrupted download that crashed the
        //     process before completion)
        if (targetFile.exists() && targetFile.length() == 0L) targetFile.delete()
        cleanupStaleDownloads(keepCanonical = targetFile.name)
        notifier.onPreparing(channelId, targetTag)
        emit(UpdateEvent.Preparing(targetFile))

        // Download + verify with bounded exponential backoff. Only
        // transient errors (IOException, 5xx, 408/429) are retried;
        // permanent failures (4xx, signature mismatch, wrong
        // package, version gate) surface immediately.
        val maxAttempts = MAX_DOWNLOAD_ATTEMPTS
        var attempt = 0
        while (true) {
            attempt++
            var transient: IOException? = null
            try {
                val outcome = downloadAndVerifyOnce(
                    url = url,
                    targetFile = targetFile,
                    channelId = channelId,
                    targetTag = targetTag,
                    publish = { event -> emit(event) },
                )
                when (outcome) {
                    is DownloadOnceOutcome.Done -> {
                        notifier.onReady(channelId, targetTag, outcome.certificateSha256)
                        emit(UpdateEvent.Ready(targetFile, outcome.certificateSha256))
                        return@flow
                    }
                    is DownloadOnceOutcome.PermanentFailure -> {
                        notifier.onFailed(channelId, targetTag, outcome.error.message)
                        emit(UpdateEvent.Failed(outcome.error))
                        return@flow
                    }
                    is DownloadOnceOutcome.TransientFailure -> transient = outcome.cause
                }
            } catch (e: IOException) {
                transient = e
            } catch (e: SecurityException) {
                notifier.onFailed(channelId, targetTag, e.message ?: "Permission denied")
                emit(UpdateEvent.Failed(UpdateError.PermissionDenied(e.message ?: "Permission denied")))
                return@flow
            } catch (e: Exception) {
                notifier.onFailed(channelId, targetTag, e.message ?: "Unknown error")
                emit(UpdateEvent.Failed(UpdateError.Unknown(e.message ?: "Unknown error")))
                return@flow
            }
            if (attempt >= maxAttempts) {
                val transientMsg = transient?.message ?: "I/O error"
                notifier.onFailed(
                    channelId, targetTag,
                    "Network failed after $maxAttempts attempts: $transientMsg"
                )
                emit(UpdateEvent.Failed(UpdateError.Io(
                    "Network failed after $maxAttempts attempts: $transientMsg"
                )))
                return@flow
            }
            val backoffMs = computeBackoffMs(attempt)
            notifier.onFailed(
                channelId, targetTag,
                "Network error, retrying in ${backoffMs}ms (attempt ${attempt + 1}/$maxAttempts)"
            )
            // Cancellable: if the user hits Cancel during the backoff
            // window, kotlinx.coroutines.delay throws
            // CancellationException and the flow unwinds cleanly.
            kotlinx.coroutines.delay(backoffMs)
            // Drop any partial bytes so the next attempt starts clean.
            if (targetFile.exists()) targetFile.delete()
        }
    }.flowOn(Dispatchers.IO)

    /** One pass of the download + verify pipeline. */
    internal sealed interface DownloadOnceOutcome {
        data class Done(val certificateSha256: String) : DownloadOnceOutcome
        data class PermanentFailure(val error: UpdateError) : DownloadOnceOutcome
        data class TransientFailure(val cause: IOException) : DownloadOnceOutcome
    }

    internal suspend fun downloadAndVerifyOnce(
        url: String,
        targetFile: File,
        channelId: String,
        targetTag: String,
        publish: suspend (UpdateEvent) -> Unit,
    ): DownloadOnceOutcome {
        val conn = transport.open(url)
        try {
            currentConnection = conn
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                // 4xx (except 408/429) are permanent. 5xx and 408/429 are
                // transient: server issues or "back off and retry".
                if (responseCode in 400..499 &&
                    responseCode != 408 && responseCode != 429
                ) {
                    targetFile.delete()
                    return DownloadOnceOutcome.PermanentFailure(
                        UpdateError.HttpStatus(responseCode)
                    )
                }
                return DownloadOnceOutcome.TransientFailure(
                    IOException("HTTP $responseCode")
                )
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
                                notifier.onProgress(channelId, targetTag, percent)
                                publish(UpdateEvent.Progress(percent, written, totalBytes))
                            }
                        }
                    }
                    output.flush()
                }
            }
            publish(UpdateEvent.Verifying(targetFile))
            notifier.onVerifying(channelId, targetTag)

            // Gate #1: parse the downloaded APK manifest.
            val pm = context.packageManager
            val apkInfo: PackageInfo = try {
                pm.getPackageArchiveInfo(
                    targetFile.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                ) ?: throw IllegalStateException("PackageManager returned null for APK")
            } catch (e: Exception) {
                targetFile.delete()
                return DownloadOnceOutcome.PermanentFailure(
                    UpdateError.Unknown("Could not parse APK manifest: ${e.message}")
                )
            }

            // Gate #1b: package name must match the installed app.
            if (apkInfo.packageName != context.packageName) {
                targetFile.delete()
                return DownloadOnceOutcome.PermanentFailure(
                    UpdateError.WrongPackage(
                        expected = context.packageName,
                        actual = apkInfo.packageName,
                    )
                )
            }

            // Gate #2: versionCode direction.
            val installedInfo = try {
                pm.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                targetFile.delete()
                return DownloadOnceOutcome.PermanentFailure(
                    UpdateError.Unknown("Could not read installed info: ${e.message}")
                )
            }
            when (val versionCheck = compareVersionCodes(
                // longVersionCode was added in API 28 (P); minSdk = 29 so
                // it is always available.
                downloaded = apkInfo.longVersionCode.toInt(),
                installed = installedInfo.longVersionCode.toInt(),
            )) {
                VersionCheckResult.Newer -> { /* proceed to signature check */ }
                VersionCheckResult.AlreadyCurrent -> {
                    targetFile.delete()
                    return DownloadOnceOutcome.PermanentFailure(
                        UpdateError.AlreadyCurrent
                    )
                }
                is VersionCheckResult.Downgrade -> {
                    targetFile.delete()
                    return DownloadOnceOutcome.PermanentFailure(
                        UpdateError.Downgrade(versionCheck.downloaded, versionCheck.installed)
                    )
                }
            }

            // Gate #3: signing certificate must match the installed app.
            val verification = signatureStore.verifyMatchesInstalledApp(targetFile, apkInfo)
            if (verification !is SignatureMatchResult.Matches) {
                targetFile.delete()
                return DownloadOnceOutcome.PermanentFailure(
                    UpdateError.SignatureMismatch(verification.javaClass.simpleName)
                )
            }
            return DownloadOnceOutcome.Done(verification.certificateSha256)
        } finally {
            currentConnection = null
            conn.disconnect()
        }
    }

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
        // ACTION_INSTALL_PACKAGE is marked deprecated since API 28 but
        // remains the only public contract for sideloading an APK
        // (PackageInstaller requires the system INSTALL_PACKAGES
        // permission, which third-party apps cannot hold).
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    /**
     * Delete any non-canonical files left in [updatesDir]. Called at
     * the start of a new download to make sure an interrupted previous
     * attempt (process killed mid-download, app uninstalled mid-update)
     * does not accumulate as cache garbage.
     */
    fun cleanupStaleDownloads(keepCanonical: String? = null) {
        if (!updatesDir.isDirectory) return
        val files = updatesDir.listFiles() ?: return
        for (f in files) {
            if (keepCanonical != null && f.name == keepCanonical) continue
            try {
                f.delete() // best effort
            } catch (_: SecurityException) {
                // ignore
            }
        }
    }

    // ---------------------------------------------------------------------
    //  Version check (pure, testable)
    // ---------------------------------------------------------------------

    /**
     * Compare the versionCode of a downloaded APK against the installed
     * one. Used internally by [downloadAndVerify] before the signature
     * check runs, so a downgrade or "already current" build is rejected
     * without surfacing an install dialog the OS would also block.
     *
     * Exposed as a static so the pure logic is unit-testable without
     * an Android Context or any platform dependency.
     */
    sealed interface VersionCheckResult {
        data object Newer : VersionCheckResult
        data object AlreadyCurrent : VersionCheckResult
        data class Downgrade(val downloaded: Int, val installed: Int) : VersionCheckResult
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
        data class Downgrade(val downloaded: Int, val installed: Int) :
            UpdateError("Refusing to install older build: downloaded=$downloaded, installed=$installed")
        data object AlreadyCurrent :
            UpdateError("Downloaded build has the same versionCode as the installed app")
        data class WrongPackage(val expected: String, val actual: String?) :
            UpdateError("Downloaded APK is for package '$actual' but this installation is '$expected'")
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

    /**
     * Compares a downloaded APK to the installed app. The [apkInfo] is
     * pre-parsed by the caller (avoids re-reading the same archive).
     */
    fun interface SignatureStore {
        fun verifyMatchesInstalledApp(
            apk: File,
            apkInfo: PackageInfo,
        ): SignatureMatchResult
    }

    /**
     * Default [SignatureStore] backed by the platform PackageManager.
     * Uses `GET_SIGNING_CERTIFICATES` (API 28+) — minSdk is 29, so this is
     * always available.
     */
    class PackageManagerSignatureStore(private val context: Context) : SignatureStore {
        override fun verifyMatchesInstalledApp(
            apk: File,
            apkInfo: PackageInfo,
        ): SignatureMatchResult {
            val pm = context.packageManager
            return try {
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

    /**
     * Pluggable notifier. The default [SystemUpdateNotifier] posts a real
     * Notification; tests can pass a no-op or a recording implementation.
     */
    interface UpdateNotifier {
        fun onPreparing(channelId: String, targetTag: String)
        fun onProgress(channelId: String, targetTag: String, percent: Int)
        fun onVerifying(channelId: String, targetTag: String)
        fun onReady(channelId: String, targetTag: String, certificateSha256: String)
        fun onFailed(channelId: String, targetTag: String, reason: String)
    }

    /**
     * Real notifier. Posts a single notification under
     * [DEFAULT_NOTIFICATION_ID], updates it on each progress event, and
     * dismisses it on Ready or Failed. The notification tap deep-links to
     * MainActivity, which is responsible for navigating the user into
     * Settings → Updates if appropriate.
     *
     * If the app has not been granted `POST_NOTIFICATIONS` (API 33+),
     * `NotificationManagerCompat.notify` is a silent no-op — the in-app
     * progress UI remains the source of truth.
     */
    class SystemUpdateNotifier(private val context: Context) : UpdateNotifier {
        private val manager: NotificationManagerCompat =
            NotificationManagerCompat.from(context)

        @SuppressLint("MissingPermission")
        private fun post(n: Notification) {
            manager.notify(DEFAULT_NOTIFICATION_ID, n)
        }

        @SuppressLint("MissingPermission")
        private fun canPost(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }

        override fun onPreparing(channelId: String, targetTag: String) {
            ensureChannel(channelId)
            if (!canPost()) return
            val n = baseBuilder(channelId, targetTag)
                .setContentText(context.getString(R.string.updates_install_in_progress))
                .setProgress(100, 0, true)
                .build()
            post(n)
        }

        override fun onProgress(channelId: String, targetTag: String, percent: Int) {
            if (!canPost()) return
            val n = baseBuilder(channelId, targetTag)
                .setContentText(context.getString(R.string.updates_install_in_progress))
                .setProgress(100, percent.coerceIn(0, 100), false)
                .build()
            post(n)
        }

        override fun onVerifying(channelId: String, targetTag: String) {
            if (!canPost()) return
            val n = baseBuilder(channelId, targetTag)
                .setContentText(context.getString(R.string.updates_install_pending_signature))
                .setProgress(100, 100, true)
                .build()
            post(n)
        }

        override fun onReady(channelId: String, targetTag: String, certificateSha256: String) {
            // The system install dialog takes over from here; dismiss ours
            // so we don't leave an ongoing notification pinned.
            manager.cancel(DEFAULT_NOTIFICATION_ID)
        }

        override fun onFailed(channelId: String, targetTag: String, reason: String) {
            // Errors are surfaced in-app; dismiss the progress notification
            // so the user is not left looking at a 0% bar.
            manager.cancel(DEFAULT_NOTIFICATION_ID)
        }

        private fun baseBuilder(channelId: String, targetTag: String): NotificationCompat.Builder {
            val title = context.getString(R.string.updates_notification_title, targetTag)
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_OPEN_UPDATES, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        }

        private fun ensureChannel(channelId: String) {
            // NotificationChannel is API 26+; minSdk is 29 so always available.
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.updates_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.updates_channel_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val DEFAULT_CHANNEL_ID = "drive_reply_updates"
        const val DEFAULT_NOTIFICATION_ID = 4701
        const val EXTRA_OPEN_UPDATES = "com.drivereply.app.extra.OPEN_UPDATES"

        /**
         * Maximum number of download attempts before we surface a
         * permanent `UpdateError.Io`. Three gives a flaky connection
         * ~1+2+4 = 7 seconds of total backoff before giving up,
         * which is enough to ride out a brief WiFi handoff without
         * making the user wait too long.
         */
        const val MAX_DOWNLOAD_ATTEMPTS = 3

        /**
         * Exponential backoff, in milliseconds.
         *   attempt 1 (just failed) -> 1_000 ms before retry
         *   attempt 2 (just failed) -> 2_000 ms before retry
         *   attempt 3 (just failed) -> give up (caller checks the
         *   attempt counter before calling this)
         */
        internal fun computeBackoffMs(attempt: Int): Long =
            (1L shl (attempt - 1).coerceAtLeast(0)) * 1_000L

        /**
         * Compare the versionCode of a downloaded APK against the
         * installed one. Used internally by [downloadAndVerify] before
         * the signature check runs, so a downgrade or "already current"
         * build is rejected without surfacing an install dialog the OS
         * would also block.
         *
         * Exposed as a static so the pure logic is unit-testable without
         * an Android Context or any platform dependency.
         */
        internal fun compareVersionCodes(downloaded: Int, installed: Int): VersionCheckResult =
            when {
                downloaded < installed ->
                    VersionCheckResult.Downgrade(downloaded = downloaded, installed = installed)
                downloaded == installed -> VersionCheckResult.AlreadyCurrent
                else -> VersionCheckResult.Newer
            }

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
