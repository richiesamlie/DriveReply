package com.example.drivereply.service

object SupportedMessagingPackages {

    data class SupportedApp(
        val label: String,
        val packageName: String
    )

    // Notification-listener package allowlist for chat apps with inline reply actions.
    val supportedApps = listOf(
        SupportedApp("WhatsApp", "com.whatsapp"),
        SupportedApp("WhatsApp Business", "com.whatsapp.w4b"),
        SupportedApp("Telegram", "org.telegram.messenger"),
        SupportedApp("Signal", "org.thoughtcrime.securesms"),
        SupportedApp("Messenger", "com.facebook.orca"),
        SupportedApp("Google Messages", "com.google.android.apps.messaging"),
        SupportedApp("Samsung Messages", "com.samsung.android.messaging"),
        SupportedApp("AOSP Messages", "com.android.messaging")
    )

    private val supportedPackages: Set<String> = supportedApps
        .map { it.packageName }
        .toSet()

    fun contains(packageName: String): Boolean = packageName in supportedPackages
}
