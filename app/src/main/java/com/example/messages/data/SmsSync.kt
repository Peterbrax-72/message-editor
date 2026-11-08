package com.example.messages.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.Locale

/** One row of the phone's shared SMS store. */
data class SystemSms(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int
)

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

    private fun buildValues(ctx: Context, address: String, body: String, date: Long, type: Int, read: Boolean) =
        ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.DATE_SENT, date)
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.SEEN, if (read) 1 else 0)
            put(Telephony.Sms.TYPE, type)
            put(Telephony.Sms.LOCKED, 0)
            if (type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
            }
            // Join the conversation that already exists for this number.
            try {
                put(Telephony.Sms.THREAD_ID, Telephony.Threads.getOrCreateThreadId(ctx, address))
            } catch (_: Exception) {
            }
        }

    /** True only if the row really exists in the phone's SMS store with the same text. */
    fun exists(ctx: Context, sysId: Long, body: String): Boolean = try {
        ctx.contentResolver.query(
            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, sysId),
            arrayOf(Telephony.Sms.BODY),
            null,
            null,
            null
        )?.use { it.moveToFirst() && it.getString(0) == body } ?: false
    } catch (_: Exception) {
        false
    }

    /** Tells other apps (Google Messages etc.) that the SMS store changed. */
    private fun notifyOthers(ctx: Context) {
        try {
            ctx.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
            ctx.contentResolver.notifyChange(Uri.parse("content://mms-sms/conversations"), null)
        } catch (_: Exception) {
        }
    }

    /**
     * Inserts the message into the system store, or updates it if it was written before,
     * then checks it is really there. Returns the system row id, or null if it failed.
     */
    fun upsert(ctx: Context, m: Message): Long? = try {
        val cr = ctx.contentResolver
        val type = typeOf(m.folder)
        val values = buildValues(ctx, m.recipient, m.body, millis(m), type, read = true)

        val updated = if (m.sysId != 0L) {
            cr.update(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, m.sysId), values, null, null)
        } else 0

        val id = if (updated > 0) {
            m.sysId
        } else {
            cr.insert(folderUri(m.folder), values)?.lastPathSegment?.toLongOrNull()
        }

        if (id != null && exists(ctx, id, m.body)) {
            notifyOthers(ctx)
            id
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    /** Writes an unread incoming text dated [millis] and checks it is really there. */
    fun insertIncoming(ctx: Context, address: String, body: String, millis: Long): Long? = try {
        val values = buildValues(ctx, address, body, millis, Telephony.Sms.MESSAGE_TYPE_INBOX, read = false)
        val id = ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            ?.lastPathSegment?.toLongOrNull()
        if (id != null && exists(ctx, id, body)) {
            notifyOthers(ctx)
            id
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    /** Newest messages in the phone's SMS store, from every app that uses it. */
    fun readHistory(ctx: Context, limit: Int = 300): List<SystemSms> = try {
        val out = ArrayList<SystemSms>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                out.add(
                    SystemSms(
                        id = c.getLong(0),
                        address = c.getString(1) ?: "",
                        body = c.getString(2) ?: "",
                        date = c.getLong(3),
                        type = c.getInt(4)
                    )
                )
            }
        }
        out
    } catch (_: Exception) {
        emptyList()
    }

    /** Most recent distinct numbers from existing conversations, newest first. */
    fun recentAddresses(ctx: Context, limit: Int = 15): List<String> = try {
        val out = LinkedHashSet<String>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            val col = c.getColumnIndex(Telephony.Sms.ADDRESS)
            var scanned = 0
            while (col >= 0 && c.moveToNext() && out.size < limit && scanned < 3000) {
                c.getString(col)?.takeIf { it.isNotBlank() }?.let { out.add(it) }
                scanned++
            }
        }
        out.toList()
    } catch (_: Exception) {
        emptyList()
    }

    fun delete(ctx: Context, sysId: Long) {
        try {
            ctx.contentResolver.delete(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, sysId), null, null
            )
            notifyOthers(ctx)
        } catch (_: Exception) {
        }
    }
}
