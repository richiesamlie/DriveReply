package com.example.drivereply.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.drivereply.DriveReplyApplication
import com.example.drivereply.data.PreferencesManager
import com.example.drivereply.data.ReplyLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WhatsAppNotificationListener : NotificationListenerService() {

    companion object {
        private val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        preferencesManager = (application as DriveReplyApplication).preferencesManager
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return

        // Skip group summary notifications
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // Only process when driving
        if (!DriveReplyService.isDriving.value) return

        val extras = sbn.notification.extras ?: return
        val contactName = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return

        serviceScope.launch {
            // Check group chat preference
            val isGroupConversation = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
            val replyInGroups = preferencesManager.replyInGroupChats.first()
            if (isGroupConversation && !replyInGroups) return@launch

            // Check if already replied to this contact in this driving session
            if (contactName in DriveReplyService.repliedContacts) return@launch

            // Get the active template
            val app = application as DriveReplyApplication
            val template = app.database.messageTemplateDao().getActiveSuspend() ?: return@launch

            // Find the reply action with RemoteInput
            val replyAction = findReplyAction(sbn) ?: return@launch

            // Send the reply
            try {
                val remoteInputs = replyAction.remoteInputs ?: return@launch
                val remoteInput = remoteInputs.firstOrNull() ?: return@launch

                val fillInIntent = android.content.Intent().apply {
                    val replyBundle = Bundle().apply {
                        putCharSequence(remoteInput.resultKey, template.body)
                    }
                    android.app.RemoteInput.addResultsToIntent(replyAction.remoteInputs, this, replyBundle)
                }

                replyAction.actionIntent.send(this@WhatsAppNotificationListener, 0, fillInIntent)

                // Mark contact as replied
                DriveReplyService.repliedContacts.add(contactName)

                // Log the reply
                app.database.replyLogDao().insert(
                    ReplyLogEntry(
                        contactName = contactName,
                        templateName = template.name,
                        messageSent = template.body
                    )
                )
            } catch (_: Exception) {
                // Failed to send reply — PendingIntent may have been revoked
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No action needed
    }

    override fun onListenerDisconnected() {
        requestRebind(
            android.content.ComponentName(this, WhatsAppNotificationListener::class.java)
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun findReplyAction(sbn: StatusBarNotification): Notification.Action? {
        val actions = sbn.notification.actions ?: return null
        return actions.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        }
    }
}
