package com.drivereply.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class UpdateCheckResult(
    val latestTag: String,
    val releaseUrl: String,
    val apkDownloadUrl: String?,
    val hasUpdate: Boolean
)

object GitHubReleaseChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/richiesamlie/DriveReply/releases/latest"
    private const val LEGACY_STATIC_METADATA_LAST_TAG_PATCH = 19
    private const val MAX_API_RESPONSE_BYTES = 256 * 1024
    private const val EXPECTED_GITHUB_REPO_PREFIX = "/richiesamlie/DriveReply/"
    private val ALLOWED_DOWNLOAD_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "api.github.com"
    )

    suspend fun checkForUpdates(installedTag: String): Result<UpdateCheckResult> = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "DriveReply/$installedTag")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode == 403 || responseCode == 429) {
                    val remaining = connection.getHeaderField("X-RateLimit-Remaining")
                    if (responseCode == 429 || remaining == "0") {
                        throw IllegalStateException("GitHub API rate limit reached. Try again later.")
                    }
                    throw IllegalStateException("GitHub API returned HTTP $responseCode")
                }
                if (responseCode !in 200..299) {
                    throw IllegalStateException("GitHub API returned HTTP $responseCode")
                }

                val body = readBoundedText(connection)
                Result.success(parseReleaseJson(body, installedTag))
            } finally {
                connection.disconnect()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readBoundedText(connection: HttpURLConnection): String {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val out = java.io.ByteArrayOutputStream()
        connection.inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                if (out.size() + read > MAX_API_RESPONSE_BYTES) {
                    throw IllegalStateException("GitHub release metadata response too large")
                }
                out.write(buffer, 0, read)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    internal fun parseReleaseJson(body: String, installedTag: String): UpdateCheckResult {
        val json = JSONObject(body)
        val latestTag = json.optString("tag_name", "").ifBlank {
            throw IllegalStateException("Missing release tag_name")
        }
        val rawReleaseUrl = json.optString("html_url", "").trim()
        val releaseUrl = if (isTrustedHttpsUrl(rawReleaseUrl)) {
            rawReleaseUrl
        } else {
            "https://github.com/richiesamlie/DriveReply/releases"
        }

        val assets = json.optJSONArray("assets")
        var apkDownloadUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                val candidateUrl = asset.optString("browser_download_url")
                if (isSignedReleaseApkAsset(name, candidateUrl)) {
                    apkDownloadUrl = candidateUrl
                    break
                }
            }
        }

        val hasUpdateByTag = compareVersionTags(latestTag, installedTag) > 0
        val suppressLegacyFalsePositive = shouldSuppressLegacyFalsePositive(installedTag, latestTag)

        return UpdateCheckResult(
            latestTag = latestTag,
            releaseUrl = releaseUrl,
            apkDownloadUrl = apkDownloadUrl,
            hasUpdate = hasUpdateByTag && !suppressLegacyFalsePositive
        )
    }

    internal fun isSignedReleaseApkAsset(name: String, downloadUrl: String): Boolean {
        val trimmedName = name.trim()
        val trimmedUrl = downloadUrl.trim()
        if (trimmedName.isEmpty() || trimmedUrl.isEmpty()) return false
        val isSignedReleaseName = trimmedName.endsWith("-release.apk", ignoreCase = true) &&
            !trimmedName.contains("unsigned", ignoreCase = true) &&
            !trimmedName.contains("debug", ignoreCase = true)
        return isSignedReleaseName && isTrustedHttpsUrl(trimmedUrl)
    }

    internal fun isTrustedHttpsUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            val path = uri.rawPath ?: ""
            if (scheme != "https" || host == null) return false
            if (uri.userInfo != null || uri.port != -1) return false
            if (host !in ALLOWED_DOWNLOAD_HOSTS) return false
            if (host == "github.com" && !path.startsWith(EXPECTED_GITHUB_REPO_PREFIX)) return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun compareVersionTags(left: String, right: String): Int {
        val leftParts = parseNumericParts(left)
        val rightParts = parseNumericParts(right)
        val maxSize = maxOf(leftParts.size, rightParts.size)
        for (i in 0 until maxSize) {
            val l = leftParts.getOrElse(i) { 0 }
            val r = rightParts.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        val leftPre = parsePreReleaseSuffix(left)
        val rightPre = parsePreReleaseSuffix(right)
        return when {
            leftPre == null && rightPre == null -> 0
            leftPre == null -> 1
            rightPre == null -> -1
            else -> leftPre.compareTo(rightPre, ignoreCase = true)
        }
    }

    private fun parseNumericParts(tag: String): List<Int> {
        val cleaned = tag.trim().removePrefix("v").removePrefix("V")
        if (cleaned.isBlank()) return listOf(0)
        val versionToken = cleaned.split('-', '+').firstOrNull().orEmpty()
        return versionToken
            .split('.')
            .map { part -> part.toIntOrNull() ?: 0 }
    }

    private fun parsePreReleaseSuffix(tag: String): String? {
        val cleaned = tag.trim().removePrefix("v").removePrefix("V")
        val withoutBuild = cleaned.substringBefore('+')
        val dashIdx = withoutBuild.indexOf('-')
        if (dashIdx < 0) return null
        return withoutBuild.substring(dashIdx + 1).trim().takeIf { it.isNotEmpty() }
    }

    internal fun shouldSuppressLegacyFalsePositive(installedTag: String, latestTag: String): Boolean {
        // Legacy releases up to v1.0.19 were published with static app metadata (1.0 / 1),
        // which appears as installedTag v1.0.1 regardless of actual release tag.
        if (installedTag != "v1.0.1") return false
        val latest = parseNumericParts(latestTag)
        if (latest.size < 3) return false
        return latest[0] == 1 &&
            latest[1] == 0 &&
            latest[2] <= LEGACY_STATIC_METADATA_LAST_TAG_PATCH
    }
}
