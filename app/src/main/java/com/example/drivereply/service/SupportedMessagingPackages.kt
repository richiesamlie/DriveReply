package com.example.drivereply.service

object SupportedMessagingPackages {

    // Notification-listener package allowlist for chat apps with inline reply actions.
    private val supportedPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms",
        "com.facebook.orca",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.messaging"
    )

    fun contains(packageName: String): Boolean = packageName in supportedPackages
}
