package com.example.messages

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.example.messages.data.AppDatabase
import com.example.messages.data.Message
import com.example.messages.data.ScheduledMessage
import com.example.messages.data.SmsSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Sets and cancels the alarms that fire scheduled texts. */
object Scheduler {
    fun schedule(ctx: Context, s: ScheduledMessage) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(ctx, s.id)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.fireAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.fireAt, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.fireAt, pi)
        }
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pendingIntent(ctx, id))
    }

    private fun pendingIntent(ctx: Context, id: Long): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).putExtra("id", id)
        return PendingIntent.getBroadcast(
            ctx,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Fires at the scheduled time: writes the text into the real inbox and shows a notification. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0L)
        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deliver(app, id)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun deliver(ctx: Context, id: Long) {
        val db = AppDatabase.get(ctx)
        val dao = db.scheduledDao()
        val item = dao.get(id) ?: return
        if (item.status != "pending") return

        // Only the default SMS app may write to the phone's inbox.
        if (!SmsSync.isDefault(ctx)) {
            dao.setStatus(id, "missed")
            return
        }

        val ok = Delivery.deliver(
            ctx, item.recipient, item.body, System.currentTimeMillis(), item.id.toInt()
        )
        dao.setStatus(id, if (ok) "sent" else "failed")
    }

}

/** Alarms are cleared on reboot, so set the pending ones again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.get(app).scheduledDao().pending().forEach { Scheduler.schedule(app, it) }
            } finally {
                result.finish()
            }
        }
    }
}

object Notifier {
    private const val CHANNEL = "incoming"

    fun show(ctx: Context, title: String, body: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(1000 + notificationId, notification)
    }
}
