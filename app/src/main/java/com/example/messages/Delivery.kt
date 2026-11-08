package com.example.messages

import android.content.Context
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
     * Returns false if Messages is not the default SMS app or the write could not be verified.
     */
    suspend fun deliver(
        ctx: Context,
        recipient: String,
        body: String,
        millis: Long,
        notificationId: Int
    ): Boolean {
        if (!SmsSync.isDefault(ctx)) return false
        val sysId = SmsSync.insertIncoming(ctx, recipient, body, millis) ?: return false

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
}
