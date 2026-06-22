package com.alibaba.mnnllm.android.archive.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alibaba.mnnllm.android.R
import com.alibaba.mnnllm.android.archive.calendar.CalendarRepository

/**
 * Schedules and fires local reminder notifications (PRD module 6, acceptance #4).
 * Uses AlarmManager (exact alarm) + NotificationManager; no extra dependency.
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    const val CHANNEL_ID = "archive_reminders"
    const val EXTRA_ARCHIVE_ID = "archive_id"
    const val EXTRA_TITLE = "title"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "归档提醒", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "本地归档助手的待办提醒" }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    /**
     * Schedules a reminder [reminderMinutes] before the deadline.
     * If the resulting trigger time is in the past, fires nothing.
     */
    fun schedule(
        context: Context,
        archiveId: Long,
        title: String,
        deadline: String?,
        reminderMinutes: Int
    ) {
        val deadlineMs = CalendarRepository.parseDeadline(deadline) ?: return
        val triggerAt = deadlineMs - reminderMinutes * 60_000L
        if (triggerAt <= System.currentTimeMillis()) {
            Log.d(TAG, "reminder time already passed, skip")
            return
        }
        ensureChannel(context)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ARCHIVE_ID, archiveId)
            putExtra(EXTRA_TITLE, title)
        }
        val pi = PendingIntent.getBroadcast(
            context, archiveId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.d(TAG, "scheduled reminder for archive $archiveId at $triggerAt")
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}

/** Receives the alarm and posts a notification that deep-links to the record detail. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val archiveId = intent.getLongExtra(ReminderScheduler.EXTRA_ARCHIVE_ID, -1L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "待办提醒"
        ReminderScheduler.ensureChannel(context)

        // Deep-link to detail
        val openIntent = Intent(context, com.alibaba.mnnllm.android.archive.ArchiveActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_archive_id", archiveId)
        }
        val pi = PendingIntent.getActivity(
            context, archiveId.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("待办提醒")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.notify(archiveId.toInt(), notif)
    }
}
