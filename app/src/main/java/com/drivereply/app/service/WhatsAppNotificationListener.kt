package com.drivereply.app.service

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.drivereply.app.DriveReplyApplication
import com.drivereply.app.data.MessageTemplate
import com.drivereply.app.data.PreferencesManager
import com.drivereply.app.data.ReplyLogEntry
import com.drivereply.app.data.TemplateRule
import com.drivereply.app.util.DebugEventLogger
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WhatsAppNotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "DriveReplyListener"
        internal const val CLOCK_SKEW_TOLERANCE_MS = 5_000L
        private val UNREAD_SUFFIX_REGEX = Regex("""\s*\(\d+(?:\s+new)?\s+messages?\)\s*$""", RegexOption.IGNORE_CASE)
        private val _isListenerConnected = MutableStateFlow(false)
        val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()
    }

    internal data class NotificationMessageSnapshot(
        val messageTimestampMs: Long?,
        val contentSignature: String?,
        val postTimeMs: Long
    )

    internal enum class PreDrivingSkipReason {
        MESSAGE_PREDATES_DRIVING_SESSION,
        UNCHANGED_PRE_DRIVING_NOTIFICATION
    }

    private data class ExtractedMessage(
        val senderName: String?,
        val senderKey: String?,
        val senderUri: String?,
        val hasSender: Boolean,
        val text: String?,
        val timestamp: Long
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var preferencesManager: PreferencesManager
    private val preDrivingSnapshots = ConcurrentHashMap<String, NotificationMessageSnapshot>()

    override fun onCreate() {
        super.onCreate()
        preferencesManager = (application as DriveReplyApplication).preferencesManager
        serviceScope.launch {
            var wasDriving = DriveReplyService.isDriving.value
            DriveReplyService.isDriving.collect { driving ->
                if (wasDriving && !driving) {
                    snapshotActiveNotifications(clearFirst = true)
                }
                wasDriving = driving
            }
        }
        DebugEventLogger.log(TAG, "Notification listener service created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isListenerConnected.value = true
        if (!DriveReplyService.isDriving.value) {
            snapshotActiveNotifications(clearFirst = true)
        } else {
            snapshotActiveNotifications(
                clearFirst = false,
                onlyBeforeMs = DriveReplyService.drivingStartedAtMs.takeIf { it > 0L }
            )
        }
        val startedAt = DriveReplyService.lastStartRequestedAtMs
        if (startedAt > 0L) {
            val latencyMs = System.currentTimeMillis() - startedAt
            DebugEventLogger.log(TAG, "Notification listener connected (bindLatencyMs=$latencyMs)")
        } else {
            DebugEventLogger.log(TAG, "Notification listener connected")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val isSupportedPackage = SupportedMessagingPackages.contains(packageName)
        val isWhatsAppLike = packageName.contains("whatsapp", ignoreCase = true)
        val redactedKey = DebugEventLogger.redact(sbn.key)
        if (isWhatsAppLike || isSupportedPackage) {
            DebugEventLogger.log(
                TAG,
                "Posted package=$packageName supported=$isSupportedPackage key=$redactedKey " +
                    "hasActions=${(sbn.notification.actions?.size ?: 0) > 0}"
            )
        }

        if (!isSupportedPackage) return

        // Skip group summary notifications
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            DebugEventLogger.log(TAG, "Skip group summary package=$packageName, key=$redactedKey")
            return
        }

        val extras = sbn.notification.extras
        if (extras == null) {
            DebugEventLogger.log(TAG, "Skip: notification extras missing package=$packageName, key=$redactedKey")
            return
        }
        val contactName = extractContactName(sbn.notification, extras) ?: sbn.key
        val dedupeKey = buildDedupeKey(sbn, extras, contactName)
        val redactedDedupeKey = DebugEventLogger.redact(dedupeKey)
        val snapshot = extractMessageSnapshot(sbn, extras)

        // Only process when driving; record pre-driving baseline snapshots when idle
        if (!DriveReplyService.isDriving.value) {
            preDrivingSnapshots[dedupeKey] = snapshot
            DebugEventLogger.log(TAG, "Skip: driving mode OFF package=$packageName, key=$redactedKey")
            return
        }

        val skipReason = shouldSkipAsPreDrivingMessage(
            drivingStartedAtMs = DriveReplyService.drivingStartedAtMs,
            current = snapshot,
            baseline = preDrivingSnapshots[dedupeKey]
        )
        if (skipReason != null) {
            DebugEventLogger.log(
                TAG,
                "Skip pre-driving notification reason=$skipReason package=$packageName key=$redactedKey"
            )
            return
        }

        DebugEventLogger.log(TAG, "Notif received package=$packageName, key=$redactedKey")

        serviceScope.launch {
            if (!preferencesManager.isServiceEnabled.first()) {
                DebugEventLogger.log(TAG, "Skip: monitoring service OFF package=$packageName key=$redactedKey")
                return@launch
            }

            if (isSelfMessage(sbn.notification, extras, contactName)) {
                DebugEventLogger.log(TAG, "Skip self-message package=$packageName key=$redactedKey")
                return@launch
            }

            // Check group chat preference
            val isGroupConversation = isLikelyGroupConversation(sbn.packageName, sbn.notification, extras)
            val replyInGroups = preferencesManager.replyInGroupChats.first()
            if (isGroupConversation && !replyInGroups) {
                DebugEventLogger.log(TAG, "Skip group conversation package=$packageName")
                return@launch
            }

            // Check if already replied to this conversation in this driving session or cooldown window
            if (DriveReplyService.hasRepliedToConversation(dedupeKey)) {
                DebugEventLogger.log(TAG, "Skip duplicate conversation key=$redactedDedupeKey")
                return@launch
            }

            val app = application as DriveReplyApplication
            var template: MessageTemplate? = null
            var templateSource = "ACTIVE_DEFAULT"

            // 1. Resolve template using custom rules
            val rules = app.database.templateRuleDao().getRulesForContact(contactName)
            if (rules.isNotEmpty()) {
                val now = java.time.LocalDateTime.now()
                val currentDayOfWeek = now.dayOfWeek.value // 1 = Monday, 7 = Sunday
                val localTime = now.toLocalTime()
                val currentMsSinceMidnight = (localTime.toSecondOfDay() * 1000L) + (localTime.nano / 1000000L)

                val matchingRules = TemplateRule.matchingRules(
                    rules = rules,
                    contactName = contactName,
                    dayOfWeek = currentDayOfWeek,
                    currentMsSinceMidnight = currentMsSinceMidnight
                )

                val matchedTemplate = matchingRules.firstNotNullOfOrNull { rule ->
                    app.database.messageTemplateDao().getById(rule.templateId)
                }
                if (matchedTemplate != null) {
                    template = matchedTemplate
                    templateSource = "RULE_MATCH"
                }
            }

            // Fallback to active global template if no rule matched
            if (template == null) {
                template = app.database.messageTemplateDao().getActiveSuspend()
            }

            if (template == null) {
                DebugEventLogger.log(TAG, "Skip: no active template available")
                return@launch
            }
            DebugEventLogger.log(
                TAG,
                "Template selected template=${template.name} source=$templateSource"
            )

            // Find the reply action with RemoteInput
            val replyAction = findReplyAction(sbn.notification)
            if (replyAction == null) {
                DebugEventLogger.log(TAG, "Skip: no reply action package=$packageName, key=$redactedKey")
                return@launch
            }

            val remoteInputs = replyAction.remoteInputs
            if (remoteInputs == null) {
                DebugEventLogger.log(TAG, "Skip: reply action remote inputs missing package=$packageName, key=$redactedKey")
                return@launch
            }
            if (remoteInputs.isEmpty()) {
                DebugEventLogger.log(TAG, "Skip: reply action has empty remote inputs package=$packageName, key=$redactedKey")
                return@launch
            }

            if (!DriveReplyService.isDriving.value) {
                DebugEventLogger.log(TAG, "Skip: driving ended before send package=$packageName, key=$redactedKey")
                return@launch
            }

            if (!DriveReplyService.markConversationReplied(dedupeKey)) {
                DebugEventLogger.log(TAG, "Skip duplicate conversation key=$redactedDedupeKey (race)")
                return@launch
            }

            val fillInIntent = android.content.Intent().apply {
                val replyBundle = Bundle().apply {
                    remoteInputs.forEach { remoteInput ->
                        putCharSequence(remoteInput.resultKey, template.body)
                    }
                }
                android.app.RemoteInput.addResultsToIntent(replyAction.remoteInputs, this, replyBundle)
                android.app.RemoteInput.setResultsSource(
                    this,
                    android.app.RemoteInput.SOURCE_FREE_FORM_INPUT
                )
            }

            // Send the reply; only roll back dedupe if PendingIntent.send() itself fails.
            try {
                replyAction.actionIntent.send(this@WhatsAppNotificationListener, 0, fillInIntent)
                DebugEventLogger.log(
                    TAG,
                    "Auto-reply sent package=$packageName template=${template.name}"
                )
            } catch (e: CancellationException) {
                DriveReplyService.unmarkConversationReplied(dedupeKey)
                throw e
            } catch (e: Exception) {
                DriveReplyService.unmarkConversationReplied(dedupeKey)
                DebugEventLogger.log(
                    TAG,
                    "Send failed package=$packageName, key=$redactedKey",
                    e
                )
                return@launch
            }

            // Log the reply and enforce retention policy independently from send deduplication.
            try {
                val replyLogDao = app.database.replyLogDao()
                replyLogDao.insert(
                    ReplyLogEntry(
                        contactName = contactName,
                        templateName = template.name,
                        messageSent = template.body,
                        packageName = packageName
                    )
                )
                val retentionDays = preferencesManager.logRetentionDays.first()
                if (retentionDays > 0) {
                    val threshold = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000L)
                    replyLogDao.deleteOlderThan(threshold)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugEventLogger.log(
                    TAG,
                    "Reply log persistence failed package=$packageName, key=$redactedKey",
                    e
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName.contains("whatsapp", ignoreCase = true)) {
            DebugEventLogger.log(TAG, "Notification removed package=$packageName, key=${DebugEventLogger.redact(sbn.key)}")
        }
        if (!DriveReplyService.isDriving.value &&
            SupportedMessagingPackages.contains(packageName) &&
            (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0)
        ) {
            val extras = sbn.notification.extras ?: return
            val contactName = extractContactName(sbn.notification, extras) ?: sbn.key
            preDrivingSnapshots.remove(buildDedupeKey(sbn, extras, contactName))
        }
    }

    override fun onListenerDisconnected() {
        _isListenerConnected.value = false
        DebugEventLogger.log(TAG, "Notification listener disconnected, requesting rebind")
        try {
            requestRebind(
                android.content.ComponentName(this, WhatsAppNotificationListener::class.java)
            )
        } catch (e: Exception) {
            DebugEventLogger.log(TAG, "Failed to request listener rebind", e)
        }
    }

    override fun onDestroy() {
        _isListenerConnected.value = false
        preDrivingSnapshots.clear()
        DebugEventLogger.log(TAG, "Notification listener destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }

    internal fun buildDedupeKey(
        sbn: StatusBarNotification,
        extras: Bundle,
        contactName: String
    ): String {
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return buildDedupeKey(
            packageName = sbn.packageName,
            tag = sbn.tag,
            notificationId = sbn.id,
            conversationTitle = conversationTitle,
            contactName = contactName
        )
    }

    internal fun buildDedupeKey(
        packageName: String,
        tag: String?,
        notificationId: Int,
        conversationTitle: String?,
        contactName: String
    ): String {
        val conversationTag = tag?.trim()?.takeIf { it.isNotEmpty() }
        if (conversationTag != null) {
            return "$packageName|tag:$conversationTag"
        }
        val normalizedConvTitle = conversationTitle?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedConvTitle != null) {
            return "$packageName|conv:${normalizedConvTitle.lowercase(Locale.ROOT)}"
        }
        val normalizedContact = contactName.trim()
        return if (normalizedContact.isNotEmpty()) {
            "$packageName|contact:${normalizedContact.lowercase(Locale.ROOT)}"
        } else {
            "$packageName|id:$notificationId"
        }
    }

    internal fun isSelfMessage(
        notification: Notification?,
        extras: Bundle,
        contactName: String
    ): Boolean {
        val messagingStyle = notification?.let {
            try {
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)
            } catch (_: Exception) {
                null
            }
        }
        @Suppress("DEPRECATION")
        val selfPerson: Person? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
        } else {
            extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON)
        }
        @Suppress("DEPRECATION")
        val selfDisplayName = messagingStyle?.user?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: selfPerson?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        val selfKey = messagingStyle?.user?.key?.trim()?.takeIf { it.isNotEmpty() }
            ?: selfPerson?.key?.trim()?.takeIf { it.isNotEmpty() }
        val selfUri = messagingStyle?.user?.uri?.trim()?.takeIf { it.isNotEmpty() }
            ?: selfPerson?.uri?.trim()?.takeIf { it.isNotEmpty() }

        if (!selfDisplayName.isNullOrEmpty() &&
            contactName.equals(selfDisplayName, ignoreCase = true)
        ) {
            return true
        }

        val messages = extractMessagingMessages(notification, extras)
        if (messages.isNotEmpty()) {
            val latest = messages.last()
            if (!latest.hasSender) return true
            if (!selfKey.isNullOrEmpty() && latest.senderKey == selfKey) return true
            if (!selfUri.isNullOrEmpty() && latest.senderUri == selfUri) return true
            val latestSenderName = latest.senderName?.trim()
            if (!selfDisplayName.isNullOrEmpty() &&
                !latestSenderName.isNullOrEmpty() &&
                latestSenderName.equals(selfDisplayName, ignoreCase = true)
            ) {
                return true
            }
        }

        return false
    }

    private fun extractMessagingMessages(
        notification: Notification?,
        extras: Bundle
    ): List<ExtractedMessage> {
        val compatStyle = notification?.let {
            try {
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)
            } catch (_: Exception) {
                null
            }
        }
        if (compatStyle != null && compatStyle.messages.isNotEmpty()) {
            return compatStyle.messages.map { msg ->
                val person = msg.person
                ExtractedMessage(
                    senderName = person?.name?.toString()?.trim(),
                    senderKey = person?.key?.trim()?.takeIf { it.isNotEmpty() },
                    senderUri = person?.uri?.trim()?.takeIf { it.isNotEmpty() },
                    hasSender = person != null,
                    text = msg.text?.toString(),
                    timestamp = msg.timestamp
                )
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        @Suppress("DEPRECATION")
        val parcelables: Array<Parcelable>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
            } else {
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            }
        return Notification.MessagingStyle.Message.getMessagesFromBundleArray(parcelables).map { msg ->
            val person = msg.senderPerson
            ExtractedMessage(
                senderName = person?.name?.toString()?.trim(),
                senderKey = person?.key?.trim()?.takeIf { it.isNotEmpty() },
                senderUri = person?.uri?.trim()?.takeIf { it.isNotEmpty() },
                hasSender = person != null,
                text = msg.text?.toString(),
                timestamp = msg.timestamp
            )
        }
    }

    internal fun computeContentSignature(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    internal fun extractMessageSnapshot(
        sbn: StatusBarNotification,
        extras: Bundle
    ): NotificationMessageSnapshot {
        val messages = extractMessagingMessages(sbn.notification, extras)
        val notificationWhen = sbn.notification.`when`.takeIf { it > 0L }
        if (messages.isNotEmpty()) {
            val latestMessage = messages.last()
            val messageTimestampMs = messages.maxOfOrNull { it.timestamp }?.takeIf { it > 0L }
                ?: notificationWhen
            val rawSig =
                "msg|${messages.size}|${latestMessage.timestamp}|${latestMessage.senderName ?: ""}|${latestMessage.text ?: ""}"
            return NotificationMessageSnapshot(
                messageTimestampMs = messageTimestampMs,
                contentSignature = computeContentSignature(rawSig),
                postTimeMs = sbn.postTime
            )
        }

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val contentSignature = if (title.isNotEmpty() || text.isNotEmpty() || bigText.isNotEmpty()) {
            computeContentSignature("txt|$title|$text|$bigText")
        } else {
            null
        }
        return NotificationMessageSnapshot(
            messageTimestampMs = notificationWhen,
            contentSignature = contentSignature,
            postTimeMs = sbn.postTime
        )
    }

    internal fun shouldSkipAsPreDrivingMessage(
        drivingStartedAtMs: Long,
        current: NotificationMessageSnapshot,
        baseline: NotificationMessageSnapshot?
    ): PreDrivingSkipReason? {
        if (baseline != null) {
            if (current.contentSignature != null &&
                baseline.contentSignature != null &&
                current.contentSignature == baseline.contentSignature
            ) {
                return PreDrivingSkipReason.UNCHANGED_PRE_DRIVING_NOTIFICATION
            }
            if (current.messageTimestampMs != null &&
                baseline.messageTimestampMs != null &&
                current.messageTimestampMs <= baseline.messageTimestampMs
            ) {
                return PreDrivingSkipReason.UNCHANGED_PRE_DRIVING_NOTIFICATION
            }
        }
        if (drivingStartedAtMs > 0L) {
            val cutoffMs = drivingStartedAtMs - CLOCK_SKEW_TOLERANCE_MS
            val effectiveTimestampMs = current.messageTimestampMs
                ?: current.postTimeMs.takeIf { it > 0L }
            if (effectiveTimestampMs != null && effectiveTimestampMs < cutoffMs) {
                return PreDrivingSkipReason.MESSAGE_PREDATES_DRIVING_SESSION
            }
        }
        return null
    }

    private fun snapshotActiveNotifications(
        clearFirst: Boolean,
        onlyBeforeMs: Long? = null
    ) {
        if (!_isListenerConnected.value) return
        val notifications = try {
            activeNotifications
        } catch (_: Exception) {
            return
        } ?: return

        if (clearFirst) {
            preDrivingSnapshots.clear()
        }
        for (sbn in notifications) {
            if (!SupportedMessagingPackages.contains(sbn.packageName)) continue
            if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            val extras = sbn.notification.extras ?: continue
            val contactName = extractContactName(sbn.notification, extras) ?: sbn.key
            val dedupeKey = buildDedupeKey(sbn, extras, contactName)
            val snapshot = extractMessageSnapshot(sbn, extras)
            if (onlyBeforeMs != null) {
                val effectiveTs = snapshot.messageTimestampMs ?: snapshot.postTimeMs.takeIf { it > 0L }
                if (effectiveTs == null || effectiveTs >= onlyBeforeMs) continue
            }
            preDrivingSnapshots[dedupeKey] = snapshot
        }
    }

    internal fun findReplyAction(notification: Notification): Notification.Action? {
        val candidateActions = buildList {
            notification.actions?.let { addAll(it.asList()) }
            addAll(Notification.WearableExtender(notification).actions)
        }.filter { action ->
            action.actionIntent != null && action.remoteInputs?.isNotEmpty() == true
        }

        return candidateActions.maxByOrNull(::scoreReplyAction)
    }

    internal fun scoreReplyAction(action: Notification.Action): Int {
        var score = 0
        // SEMANTIC_ACTION_REPLY was added in API 28 (P); minSdk = 29, so
        // the field is always present.
        if (action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY) {
            score += 100
        }
        if (action.allowGeneratedReplies) {
            score += 5
        }
        val title = action.title?.toString()?.trim()?.lowercase(Locale.ROOT)
        if (title != null && (title.contains("reply") || title.contains("balas"))) {
            score += 10
        }
        return score
    }

    internal fun extractContactName(
        notification: Notification?,
        extras: Bundle
    ): String? {
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            ?.trim()
            ?.let(::stripUnreadCountSuffix)
            ?.takeIf { it.isNotEmpty() }
        val isGroup = conversationTitle != null &&
            isLikelyGroupConversation("", notification, extras)
        if (isGroup) {
            return conversationTitle
        }

        val directCandidates = listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        ).mapNotNull { value ->
            value?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }
        if (directCandidates.isNotEmpty()) {
            return directCandidates.first()
        }

        val messages = extractMessagingMessages(notification, extras)
        val sender = messages.asReversed().mapNotNull { message ->
            message.senderName?.takeIf { it.isNotEmpty() }
        }.firstOrNull()
        if (!sender.isNullOrEmpty()) return sender

        return null
    }

    private fun stripUnreadCountSuffix(value: String): String {
        return value.replace(UNREAD_SUFFIX_REGEX, "").trim()
    }

    internal fun isLikelyGroupConversation(
        packageName: String,
        notification: Notification?,
        extras: Bundle
    ): Boolean {
        val platformGroupFlag = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            ?.trim()
            ?.let(::stripUnreadCountSuffix)
            ?.takeIf { it.isNotEmpty() }

        val messages = extractMessagingMessages(notification, extras)
        if (messages.isNotEmpty()) {
            val latestMessageSender = messages.asReversed().mapNotNull { message ->
                message.senderName?.takeIf { it.isNotEmpty() }
            }.firstOrNull()
            val titleSenderMismatch = !conversationTitle.isNullOrEmpty() &&
                !latestMessageSender.isNullOrEmpty() &&
                !conversationTitle.equals(latestMessageSender, ignoreCase = true)
            if (packageName.startsWith("com.whatsapp")) {
                return titleSenderMismatch || (platformGroupFlag && latestMessageSender == null)
            }
            if (titleSenderMismatch) return true
        }

        return platformGroupFlag
    }
}
