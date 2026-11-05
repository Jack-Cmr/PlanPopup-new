package com.planpop.app.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.planpop.app.R
import com.planpop.app.data.PlanEntity
import com.planpop.app.data.isSpecific
import com.planpop.app.data.specificDateOrNull
import com.planpop.app.reminder.ReminderActivity
import com.planpop.app.util.formatPlanTime
import com.planpop.app.util.formatTimelineDate

/**
 * Fallback delivery path when overlay permission is unavailable. The channel is
 * HIGH importance (heads-up + full-screen intent when allowed), but is explicitly
 * silent and does not vibrate.
 */
class ReminderNotifications(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val systemManager = context.getSystemService(NotificationManager::class.java)
        val existing = systemManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_reminders_description)
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = null
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        systemManager.createNotificationChannel(channel)
    }

    fun canPostNotifications(): Boolean = canPost(context)

    fun show(plan: PlanEntity) {
        if (!canPostNotifications()) return
        ensureChannel()

        val notificationId = notificationId(plan.id)
        val contentIntent = contentPendingIntent(plan.id)
        val completeIntent = actionPendingIntent(
            planId = plan.id,
            action = ReminderActionReceiver.ACTION_COMPLETE,
            path = "complete"
        )
        val snoozeIntent = actionPendingIntent(
            planId = plan.id,
            action = ReminderActionReceiver.ACTION_SNOOZE,
            path = "snooze"
        )

        val contentText = plan.note.ifBlank {
            context.getString(R.string.notification_fallback_text)
        }
        val scheduleLabel = if (plan.isSpecific) {
            val dateLabel = plan.specificDateOrNull()?.let { formatTimelineDate(it) }
                ?: "指定日期"
            "$dateLabel ${formatPlanTime(plan.hour, plan.minute)}"
        } else {
            "每天 ${formatPlanTime(plan.hour, plan.minute)}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(plan.title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(contentText)
                    .setSummaryText(scheduleLabel)
            )
            .setSubText("$scheduleLabel · 计划提醒")
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setSilent(true)
            .setDefaults(0)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_complete),
                completeIntent
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_snooze),
                snoozeIntent
            )
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS can be revoked between the check and notify().
        }
    }

    fun cancel(planId: Long) {
        notificationManager.cancel(notificationId(planId))
    }

    private fun contentPendingIntent(planId: Long): PendingIntent {
        val intent = ReminderActivity.createIntent(context, planId).apply {
            action = "com.planpop.app.action.OPEN_REMINDER"
            data = Uri.parse("planpop://reminder/open/$planId")
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionPendingIntent(planId: Long, action: String, path: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("planpop://reminder/action/$path/$planId")
            putExtra(AlarmScheduler.EXTRA_PLAN_ID, planId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_ACTION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "plan_reminders_v1"
        private const val REQUEST_OPEN = 2001
        private const val REQUEST_ACTION = 2002

        fun notificationId(planId: Long): Int = (planId and 0x7FFFFFFF).toInt()

        fun canPost(context: Context): Boolean {
            val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
