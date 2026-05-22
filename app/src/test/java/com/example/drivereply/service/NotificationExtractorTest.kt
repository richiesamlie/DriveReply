package com.example.drivereply.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationExtractorTest {

    private val supportedPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.thoughtlight.securesms",
        "com.facebook.orca"
    )

    @Test
    fun packageFilter_matchesSupportedApps() {
        assertTrue(supportedPackages.contains("com.whatsapp"))
        assertTrue(supportedPackages.contains("com.whatsapp.w4b"))
        assertTrue(supportedPackages.contains("org.telegram.messenger"))
        assertTrue(supportedPackages.contains("org.thoughtlight.securesms"))
        assertTrue(supportedPackages.contains("com.facebook.orca"))
    }

    @Test
    fun packageFilter_ignoresUnsupportedApps() {
        assertFalse(supportedPackages.contains("com.instagram.android"))
        assertFalse(supportedPackages.contains("com.twitter.android"))
        assertFalse(supportedPackages.contains("com.android.mms"))
        assertFalse(supportedPackages.contains("com.google.android.apps.messaging"))
    }
}
