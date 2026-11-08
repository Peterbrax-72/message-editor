package com.example.messages.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.Locale

/** Reads/writes the phone's shared SMS store (what other messaging apps display). */
object SmsSync {

    fun isDefault(ctx: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName

    private fun millis(m: Message): Long = try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("${m.date} ${m.time}")?.time
            ?: m.createdAt
    } catch (_: Exception) {
        m.createdAt
    }

    private fun typeOf(folder: String): Int = when (folder) {
        "inbox" -> Telephony.Sms.MESSAGE_TYPE_INBOX
        "outbox" -> Telephony.Sms.MESSAGE_TYPE_SENT
        else -> Telephony.Sms.MESSAGE_TYPE_DRAFT
    }

    private fun folderUri(folder: String) = when (folder) {
        "inbox" -> Telephony.Sms.Inbox.CONTENT_URI
        "outbox" -> Telephony.Sms.Sent.CONTENT_URI
        else -> Telephony.Sms.Draft.CONTENT_URI
    }

    /**
     * Inserts the message into the system store, or updates it if it was written before.
     * Returns the system row id, or null if the write failed (e.g. not the default app).
     */
    fun upsert(ctx: Context, m: Message): Long? = try {
        val cr = ctx.contentResolver
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, m.recipient)
            put(Telephony.Sms.BODY, m.body)
            put(Telephony.Sms.DATE, millis(m))
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, typeOf(m.folder))
        }

        val updated = if (m.sysId != 0L) {
            cr.update(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, m.sysId), values, null, null)
        } else 0

        if (updated > 0) {
            m.sysId
        } else {
            cr.insert(folderUri(m.folder), values)?.lastPathSegment?.toLongOrNull()
        }
    } catch (_: Exception) {
        null
    }

    fun delete(ctx: Context, sysId: Long) {
        try {
            ctx.contentResolver.delete(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, sysId), null, null
            )
        } catch (_: Exception) {
        }
    }
}
