package com.example.drivereply.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure-JVM pieces of GitHubReleaseChecker.
 *
 * The HTTP / JSON parts of the checker are exercised through the live API
 * by the in-app settings flow; here we cover the deterministic version
 * comparison + legacy-false-positive suppression that gate the
 * "Update available" decision.
 */
class GitHubReleaseCheckerTest {

    // ------------------------------------------------------------------
    //  compareVersionTags
    // ------------------------------------------------------------------

    @Test
    fun compare_equalTags_returnsZero() {
        assertEquals(0, GitHubReleaseChecker.compareVersionTags("v1.0.5", "v1.0.5"))
    }

    @Test
    fun compare_equalTagsDifferentPrefix_returnsZero() {
        assertEquals(0, GitHubReleaseChecker.compareVersionTags("V1.0.5", "v1.0.5"))
    }

    @Test
    fun compare_majorBump() {
        assertTrue(GitHubReleaseChecker.compareVersionTags("v2.0.0", "v1.99.99") > 0)
    }

    @Test
    fun compare_minorBump() {
        assertTrue(GitHubReleaseChecker.compareVersionTags("v1.2.0", "v1.1.99") > 0)
    }

    @Test
    fun compare_patchBump() {
        assertTrue(GitHubReleaseChecker.compareVersionTags("v1.0.10", "v1.0.9") > 0)
    }

    @Test
    fun compare_leftBehind_returnsNegative() {
        assertTrue(GitHubReleaseChecker.compareVersionTags("v1.0.5", "v1.0.6") < 0)
    }

    @Test
    fun compare_shorterRightIsZeroPadded() {
        // 1.0.5 vs 1.0 — treat missing third segment as 0.
        assertTrue(GitHubReleaseChecker.compareVersionTags("v1.0.5", "v1.0") > 0)
    }

    @Test
    fun compare_ignoresPreReleaseSuffix() {
        // "v1.0.6-rc1" is split at '-' so we compare "1.0.6" against "1.0.5".
        assertTrue(GitHubReleaseChecker.compareVersionTags("v1.0.6-rc1", "v1.0.5") > 0)
    }

    @Test
    fun compare_handlesBlankTag() {
        // Empty tag parses to [0]; equal.
        assertEquals(0, GitHubReleaseChecker.compareVersionTags("", ""))
        // Non-empty vs empty: non-empty wins.
        assertTrue(GitHubReleaseChecker.compareVersionTags("v1", "") > 0)
    }

    // ------------------------------------------------------------------
    //  Legacy false-positive suppression
    // ------------------------------------------------------------------

    @Test
    fun legacySuppress_legacyInstalledTag_vsPreRefactorLatest_isSuppressed() {
        // Installed v1.0.1 was the static metadata of all builds <= v1.0.19
        // (per the comment in GitHubReleaseChecker). When the latest release
        // is v1.0.19, we should NOT show "update available" because the
        // user is actually up to date — they just have the legacy tag.
        val installed = "v1.0.1"
        val latest = "v1.0.19"
        // First: tag comparison would say "update available".
        assertTrue(GitHubReleaseChecker.compareVersionTags(latest, installed) > 0)
        // Second: legacy suppress kicks in.
        val suppressed = GitHubReleaseChecker.shouldSuppressLegacyFalsePositive(installed, latest)
        assertTrue(suppressed)
    }

    @Test
    fun legacySuppress_legacyInstalledTag_vsPostRefactorLatest_isNotSuppressed() {
        val installed = "v1.0.1"
        val latest = "v1.0.20" // > LEGACY_STATIC_METADATA_LAST_TAG_PATCH (19)
        assertFalse(
            GitHubReleaseChecker.shouldSuppressLegacyFalsePositive(installed, latest)
        )
    }

    @Test
    fun legacySuppress_doesNotTriggerForNonLegacyInstalls() {
        // A fresh install that already shows v1.0.5 should never be suppressed.
        assertFalse(
            GitHubReleaseChecker.shouldSuppressLegacyFalsePositive("v1.0.5", "v1.0.6")
        )
    }
}
