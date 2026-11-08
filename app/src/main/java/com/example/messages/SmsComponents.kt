package com.example.messages

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony

/**
 * Android requires the default SMS app to handle incoming SMS. This one stores
 * the message in the system SMS store so nothing is lost while it is default.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val first = parts.firstOrNull() ?: return
        val body = parts.joinToString("") { it.messageBody ?: "" }

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, first.originatingAddress)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, first.timestampMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
        }
        try {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (_: Exception) {
        }
    }
}

/** Required by the default-SMS-app rules. MMS is not supported by this app. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Intentionally empty.
    }
}

/** Required by the default-SMS-app rules ("respond via message" from the dialer). */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
