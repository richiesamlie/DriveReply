# DriveReply 🚗💬

DriveReply is a premium, high-performance Android companion application designed to automatically detect when you are driving and reply to incoming messages on your behalf using dynamic, highly customizable reply templates.

Built using **Kotlin**, **Jetpack Compose (Material 3)**, **Room Database**, **Google Play Services Location & Activity Recognition**, and **DataStore Preferences**, the app prioritizes driver safety, battery longevity, and strict local privacy.

---

## Key Features

### 🚘 Intelligent Active Triggers
- **Sensor Activity Transition**: Uses the Google Play Services Transition API to seamlessly enter driving mode when physical sensors detect a vehicle entry/exit.
- **GPS Speed Activation**: Automatically launches driving protection when your speed exceeds a configurable threshold (e.g., 15 km/h, 25 km/h) using low-power background location polling.
- **Car Bluetooth Automation**: Automatically toggles driving protection on or off upon connecting to selected "Car Bluetooth" paired devices.
- **Vehicular Exit Debounce**: Employs a 2-minute delay before exiting the driving state. This keeps the auto-reply active during brief stops (e.g., stoplights, fuel stops) and avoids battery-draining state toggles.

### 💬 Platform & Multi-App Support
- **Multi-App Expansion**: Auto-replies are platform-agnostic! Seamlessly handles and replies to messages on **WhatsApp**, **WhatsApp Business**, **Telegram**, **Signal**, and **Facebook Messenger**.
- **Silent Auto-Replies**: Intercepts incoming notifications using a `NotificationListenerService` and dispatches immediate replies back using Android's native `RemoteInput` framework.
- **Privacy-First Deduplication**: Sends exactly one auto-reply per unique contact per driving session. Ignores group chat notifications by default to prevent spam (customizable in Settings).

### 🎯 Deep Customization Rules
- **Granular Custom Rules**: Define contact-specific message templates, active days of the week, and start/end time windows.
- **Dynamic Matching Engine**: Prioritizes contact-specific rules, then schedule rules, before falling back to the active global template.

### 📊 Safety Analytics Dashboard
- **Circular Safety Score**: A beautiful, hardware-accelerated gauge illustrating your safe driving score based on blocked phone distractions.
- **Spline Protection Chart**: A smooth cubic-bezier line chart drawn using Jetpack Compose `Canvas` showing auto-replies sent over the last 7 days, complete with visual gridlines and alpha-gradients.
- **Donut App Breakdown**: Segment-based circular donut graph with rounded end-caps and responsive legends showing auto-reply ratios across messaging apps.

---

## Architectural Workflow

The following diagram describes how DriveReply orchestrates transition receivers, active triggers, notification listener interceptors, and database queries:

```mermaid
graph TD
    A[DetectedActivity IN_VEHICLE] -->|Trigger State| B[DriveReplyService]
    C[GPS Speed > Threshold] -->|Location Event| B
    D[Bluetooth Connected ACL_CONNECTED] -->|Matched MAC Address| B
    
    B -->|Toggles Driving State| E[MultiAppNotificationListener]
    
    F[Incoming Notifications WhatsApp/Telegram/Signal/Messenger] -->|Intercepted| E
    E -->|Checks Active Triggers & Deduplicates| G{Should Reply?}
    G -->|Yes| H[Consult Database Rules]
    H -->|1. Contact Rule| I[(Room DB)]
    H -->|2. Day & Time Rule| I
    H -->|3. Fallback Active Global| I
    I -->|Returns Template| J[Send RemoteInput Inline Reply]
    J -->|Log Reply & PackageName| I
```

---

## Permissions Guide

To function reliably in the background, DriveReply utilizes:

1. **Activity Recognition** (`android.permission.ACTIVITY_RECOGNITION`)
   - Allows detecting vehicle entry and exit states via physical device sensors.
2. **Notification Listener Access** (`NotificationListenerService`)
   - A system-level permission that allows DriveReply to inspect notification bundles and execute quick-reply actions.
3. **Location (Fine & Background)** (`ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`)
   - Required to perform low-power speed-based background service activation.
4. **Bluetooth Connection** (`BLUETOOTH_CONNECT` for Android 12+)
   - Used to fetch and track paired "Car Bluetooth" MAC addresses.
5. **Notification Post Access** (`android.permission.POST_NOTIFICATIONS` for Android 13+)
   - Standard permission to show the persistent foreground service monitoring indicator.
6. **Battery Optimization Exemption** (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
   - Exempts the background service from aggressive Android Doze limits to guarantee real-time replies.

---

## Getting Started

### Requirements
- Android SDK **API level 29 (Android 10)** or higher.
- Google Play Services enabled on the device.

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/richiesamlie/DriveReply.git
   ```
2. Open the directory `DriveReply` in **Android Studio**.
3. Sync Gradle and build the project.
4. Deploy the debug build to your physical device or a Play Services-supported emulator.

---

## Future Enhancements 🚀

- 🧠 **Context-Aware Local AI Auto-Replies (Pending)**: Incorporate lightweight, privacy-focused on-device models to draft custom contextual replies to complex incoming messages.
- 📱 **Quick Settings Tile**: Add a quick settings tile for fast manual overrides to force-activate or force-disable driving monitoring.

---

## Extensive Documentation

For developer-focused architectural deep dives, database schemas, service binding lifecycles, and troubleshooting guides, please check:
👉 **[Developer & Architecture Documentation](docs/ARCHITECTURE.md)**

## License

This project is licensed under the Apache License 2.0.
