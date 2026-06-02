package com.example.drivereply.service

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.drivereply.DriveReplyApplication
import com.example.drivereply.data.MessageTemplate
import com.example.drivereply.data.PreferencesManager
import com.example.drivereply.data.ReplyLogEntry
import com.example.drivereply.util.DebugEventLogger
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
        private val _isListenerConnected = MutableStateFlow(false)
        val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        preferencesManager = (application as DriveReplyApplication).preferencesManager
        DebugEventLogger.log(TAG, "Notification listener service created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isListenerConnected.value = true
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
        if (isWhatsAppLike || isSupportedPackage) {
            DebugEventLogger.log(
                TAG,
                "Posted package=$packageName supported=$isSupportedPackage key=${sbn.key} " +
                    "hasActions=${(sbn.notification.actions?.size ?: 0) > 0}"
            )
        }

        if (!isSupportedPackage) return

        // Skip group summary notifications
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            DebugEventLogger.log(TAG, "Skip group summary package=$packageName, key=${sbn.key}")
            return
        }

        // Only process when driving
        if (!DriveReplyService.isDriving.value) {
            DebugEventLogger.log(TAG, "Skip: driving mode OFF package=$packageName, key=${sbn.key}")
            return
        }

        val extras = sbn.notification.extras
        if (extras == null) {
            DebugEventLogger.log(TAG, "Skip: notification extras missing package=$packageName, key=${sbn.key}")
            return
        }
        val contactName = extractContactName(extras) ?: sbn.key
        val dedupeKey = buildDedupeKey(sbn, contactName)
        DebugEventLogger.log(TAG, "Notif received package=$packageName, contact=$contactName, key=${sbn.key}")

        serviceScope.launch {
            val selfDisplayName = extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (!selfDisplayName.isNullOrEmpty() &&
                contactName.equals(selfDisplayName, ignoreCase = true)
            ) {
                DebugEventLogger.log(TAG, "Skip self-message contact=$contactName package=$packageName")
                return@launch
            }

            // Check group chat preference
            val isGroupConversation = isLikelyGroupConversation(sbn.packageName, extras)
            val replyInGroups = preferencesManager.replyInGroupChats.first()
            if (isGroupConversation && !replyInGroups) {
                DebugEventLogger.log(TAG, "Skip group conversation contact=$contactName package=$packageName")
                return@launch
            }

            // Check if already replied to this conversation in this driving session
            if (DriveReplyService.hasRepliedToConversation(dedupeKey)) {
                DebugEventLogger.log(TAG, "Skip duplicate conversation key=$dedupeKey")
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

                // Filter rules that match time/day constraints
                val matchingRules = rules.filter { rule ->
                    // Check days of week
                    val daysMatch = if (!rule.daysOfWeek.isNullOrEmpty()) {
                        val daysList = rule.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
                        currentDayOfWeek in daysList
                    } else {
                        true
                    }

                    // Check time window
                    val timeMatch = if (rule.startTime != null && rule.endTime != null) {
                        if (rule.startTime <= rule.endTime) {
                            currentMsSinceMidnight in rule.startTime..rule.endTime
                        } else {
                            currentMsSinceMidnight >= rule.startTime || currentMsSinceMidnight <= rule.endTime
                        }
                    } else {
                        true
                    }

                    daysMatch && timeMatch
                }

                // Prioritize specific contact rule over general rule (null contactName)
                val bestRule = matchingRules.firstOrNull { it.contactName != null }
                    ?: matchingRules.firstOrNull { it.contactName == null }

                if (bestRule != null) {
                    template = app.database.messageTemplateDao().getById(bestRule.templateId)
                    if (template != null) {
                        templateSource = "RULE_MATCH"
                    }
                }
            }

            // Fallback to active global template if no rule matched
            if (template == null) {
                template = app.database.messageTemplateDao().getActiveSuspend()
            }

            if (template == null) {
                DebugEventLogger.log(TAG, "Skip: no active template available for contact=$contactName")
                return@launch
            }
            DebugEventLogger.log(
                TAG,
                "Template selected contact=$contactName template=${template.name} source=$templateSource"
            )

            // Find the reply action with RemoteInput
            val replyAction = findReplyAction(sbn.notification)
            if (replyAction == null) {
                DebugEventLogger.log(TAG, "Skip: no reply action package=$packageName, key=${sbn.key}")
                return@launch
            }

            // Send the reply
            try {
                val remoteInputs = replyAction.remoteInputs
                if (remoteInputs == null) {
                    DebugEventLogger.log(TAG, "Skip: reply action remote inputs missing package=$packageName, key=${sbn.key}")
                    return@launch
                }
                if (remoteInputs.isEmpty()) {
                    DebugEventLogger.log(TAG, "Skip: reply action has empty remote inputs package=$packageName, key=${sbn.key}")
                    return@launch
                }

                if (!DriveReplyService.markConversationReplied(dedupeKey)) {
                    DebugEventLogger.log(TAG, "Skip duplicate conversation key=$dedupeKey (race)")
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

                replyAction.actionIntent.send(this@WhatsAppNotificationListener, 0, fillInIntent)
                DebugEventLogger.log(
                    TAG,
                    "Auto-reply sent contact=$contactName package=$packageName template=${template.name} dedupeKey=$dedupeKey"
                )

                // Log the reply
                app.database.replyLogDao().insert(
                    ReplyLogEntry(
                        contactName = contactName,
                        templateName = template.name,
                        messageSent = template.body,
                        packageName = packageName
                    )
                )
            } catch (e: Exception) {
                // Failed to send reply — PendingIntent may have been revoked
                DriveReplyService.unmarkConversationReplied(dedupeKey)
                DebugEventLogger.log(
                    TAG,
                    "Send failed package=$packageName, key=${sbn.key}",
                    e
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName.contains("whatsapp", ignoreCase = true)) {
            DebugEventLogger.log(TAG, "Notification removed package=$packageName, key=${sbn.key}")
        }
    }

    override fun onListenerDisconnected() {
        _isListenerConnected.value = false
        DebugEventLogger.log(TAG, "Notification listener disconnected, requesting rebind")
        requestRebind(
            android.content.ComponentName(this, WhatsAppNotificationListener::class.java)
        )
    }

    override fun onDestroy() {
        _isListenerConnected.value = false
        DebugEventLogger.log(TAG, "Notification listener destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildDedupeKey(sbn: StatusBarNotification, contactName: String): String {
        val conversationTag = sbn.tag?.takeIf { it.isNotBlank() }
        return if (conversationTag != null) {
            "${sbn.packageName}|tag:$conversationTag"
        } else {
            "${sbn.packageName}|contact:${contactName.lowercase()}"
        }
    }

    private fun findReplyAction(notification: Notification): Notification.Action? {
        val candidateActions = buildList {
            notification.actions?.let { addAll(it.asList()) }
            addAll(Notification.WearableExtender(notification).actions)
        }.filter { action ->
            action.actionIntent != null && action.remoteInputs?.isNotEmpty() == true
        }

        return candidateActions.maxByOrNull(::scoreReplyAction)
    }

    private fun scoreReplyAction(action: Notification.Action): Int {
        var score = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY
        ) {
            score += 100
        }
        if (action.allowGeneratedReplies) {
            score += 5
        }
        val title = action.title?.toString()?.trim()?.lowercase()
        if (title != null && (title.contains("reply") || title.contains("balas"))) {
            score += 10
        }
        return score
    }

    private fun extractContactName(extras: Bundle): String? {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            )
            val sender = messages.asReversed().mapNotNull { message ->
                message.senderPerson?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: message.sender?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            }.firstOrNull()
            if (!sender.isNullOrEmpty()) return sender
        }

        return null
    }

    private fun isLikelyGroupConversation(packageName: String, extras: Bundle): Boolean {
        val platformGroupFlag = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val latestMessageSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            )
            messages.asReversed().mapNotNull { message ->
                message.senderPerson?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: message.sender?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            }.firstOrNull()
        } else {
            null
        }

        // Strong signal: conversation title and sender differ (common group-chat structure).
        val titleSenderMismatch = !conversationTitle.isNullOrEmpty() &&
            !latestMessageSender.isNullOrEmpty() &&
            !conversationTitle.equals(latestMessageSender, ignoreCase = true)

        // WhatsApp can set EXTRA_IS_GROUP_CONVERSATION aggressively; require corroboration.
        if (packageName.startsWith("com.whatsapp")) {
            return titleSenderMismatch
        }

        return platformGroupFlag || titleSenderMismatch
    }
}
