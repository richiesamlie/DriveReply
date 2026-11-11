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
    private val ALLOWED_DOWNLOAD_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "api.github.com"
    )

    suspend fun checkForUpdates(installedTag: String): Result<UpdateCheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("GitHub API returned HTTP $responseCode")
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseReleaseJson(body, installedTag)
            } finally {
                connection.disconnect()
            }
        }
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
            scheme == "https" && host != null && (
                host in ALLOWED_DOWNLOAD_HOSTS ||
                    host.endsWith(".githubusercontent.com")
                )
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
        return 0
    }

    private fun parseNumericParts(tag: String): List<Int> {
        val cleaned = tag.trim().removePrefix("v").removePrefix("V")
        if (cleaned.isBlank()) return listOf(0)
        val versionToken = cleaned.split('-', '+').firstOrNull().orEmpty()
        return versionToken
            .split('.')
            .map { part -> part.toIntOrNull() ?: 0 }
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
