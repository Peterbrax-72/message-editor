package com.example.messages

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.example.messages.data.AppDatabase
import com.example.messages.data.Message
import com.example.messages.data.SmsSync
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Puts an incoming text into the real SMS inbox. Used by "Send now" and by scheduled alarms. */
object Delivery {

    /**
     * Writes the text as an unread incoming message dated [millis] (past or future),
     * mirrors it in the app's own list and shows a notification.
     * Returns false if Messages is not the default SMS app or the write failed.
     */
    suspend fun deliver(
        ctx: Context,
        recipient: String,
        body: String,
        millis: Long,
        notificationId: Int
    ): Boolean {
        if (!SmsSync.isDefault(ctx)) return false
        val sysId = insertIncoming(ctx, recipient, body, millis) ?: return false

        val date = Date(millis)
        AppDatabase.get(ctx).messageDao().insert(
            Message(
                recipient = recipient,
                body = body,
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date),
                time = SimpleDateFormat("HH:mm", Locale.US).format(date),
                folder = "inbox",
                createdAt = millis,
                synced = true,
                sysId = sysId
            )
        )
        Notifier.show(ctx, recipient, body, notificationId)
        return true
    }

    private fun insertIncoming(ctx: Context, address: String, body: String, millis: Long): Long? =
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, millis)
                put(Telephony.Sms.DATE_SENT, millis)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                // Use the thread that already exists for this number so the text joins it.
                try {
                    put(Telephony.Sms.THREAD_ID, Telephony.Threads.getOrCreateThreadId(ctx, address))
                } catch (_: Exception) {
                }
            }
            ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                ?.lastPathSegment?.toLongOrNull()
        } catch (_: Exception) {
            null
        }
}
