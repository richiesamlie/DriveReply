package com.drivereply.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationExtractorTest {

    @Test
    fun packageFilter_matchesSupportedApps() {
        assertTrue(SupportedMessagingPackages.contains("com.whatsapp"))
        assertTrue(SupportedMessagingPackages.contains("com.whatsapp.w4b"))
        assertTrue(SupportedMessagingPackages.contains("org.telegram.messenger"))
        assertTrue(SupportedMessagingPackages.contains("org.thoughtcrime.securesms"))
        assertTrue(SupportedMessagingPackages.contains("com.facebook.orca"))
        assertTrue(SupportedMessagingPackages.contains("com.google.android.apps.messaging"))
    }

    @Test
    fun packageFilter_ignoresUnsupportedApps() {
        assertFalse(SupportedMessagingPackages.contains("com.instagram.android"))
        assertFalse(SupportedMessagingPackages.contains("com.twitter.android"))
        assertFalse(SupportedMessagingPackages.contains("com.android.mms"))
    }
}
