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
        DebugEventLogger.log(TAG, "Notification listener connected")
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

        val extras = sbn.notification.extras ?: return
        val contactName = extractContactName(extras) ?: sbn.key
        DebugEventLogger.log(TAG, "Notif received package=$packageName, contact=$contactName, key=${sbn.key}")

        serviceScope.launch {
            // Check group chat preference
            val isGroupConversation = isLikelyGroupConversation(sbn.packageName, extras)
            val replyInGroups = preferencesManager.replyInGroupChats.first()
            if (isGroupConversation && !replyInGroups) {
                DebugEventLogger.log(TAG, "Skip group conversation contact=$contactName package=$packageName")
                return@launch
            }

            // Check if already replied to this contact in this driving session
            if (contactName in DriveReplyService.repliedContacts) {
                DebugEventLogger.log(TAG, "Skip duplicate contact=$contactName in current driving session")
                return@launch
            }

            val app = application as DriveReplyApplication
            var template: MessageTemplate? = null

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
                        currentMsSinceMidnight in rule.startTime..rule.endTime
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

            // Find the reply action with RemoteInput
            val replyAction = findReplyAction(sbn.notification)
            if (replyAction == null) {
                DebugEventLogger.log(TAG, "Skip: no reply action package=$packageName, key=${sbn.key}")
                return@launch
            }

            // Send the reply
            try {
                val remoteInputs = replyAction.remoteInputs ?: return@launch
                if (remoteInputs.isEmpty()) return@launch

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

                // Mark contact as replied
                DriveReplyService.repliedContacts.add(contactName)
                DebugEventLogger.log(
                    TAG,
                    "Auto-reply sent contact=$contactName package=$packageName template=${template.name}"
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
