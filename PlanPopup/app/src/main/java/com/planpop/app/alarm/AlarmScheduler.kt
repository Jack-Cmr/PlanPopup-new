package com.planpop.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.planpop.app.data.PlanEntity
import com.planpop.app.data.isSpecific
import com.planpop.app.data.specificDateTimeOrNull
import com.planpop.app.util.nextDailyTriggerMillis
import java.time.Instant
import java.time.ZoneId

/**
 * Owns every AlarmManager interaction. DAILY plans repeat every day; SPECIFIC
 * plans are armed once for their exact calendar date and time.
 *
 * Daily and snooze alarms use different PendingIntent data URIs, so they never
 * overwrite each other.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Schedule the next occurrence of an enabled plan. */
    fun schedule(plan: PlanEntity) {
        if (!plan.enabled || plan.id <= 0L) {
            cancel(plan.id)
            return
        }
        scheduleNext(plan)
    }

    fun scheduleNext(plan: PlanEntity) {
        if (!plan.enabled || plan.id <= 0L) return
        if (plan.isSpecific) {
            scheduleSpecific(plan)
            return
        }

        val triggerAt = nextDailyTriggerMillis(plan.hour, plan.minute)
        setAlarm(triggerAt, dailyPendingIntent(plan.id))
    }

    /**
     * SPECIFIC is one-shot. Past dates, missing dates and already-triggered
     * plans are ignored. Nothing is scheduled for tomorrow.
     */
    private fun scheduleSpecific(plan: PlanEntity) {
        if (plan.triggeredAt != null) {
            cancelDaily(plan.id)
            return
        }

        val dateTime = plan.specificDateTimeOrNull()
        if (dateTime == null) {
            cancelDaily(plan.id)
            return
        }

        val trigger = dateTime.atZone(ZoneId.systemDefault()).toInstant()
        if (!trigger.isAfter(Instant.now())) {
            // The requested date/time is already in the past.
            cancelDaily(plan.id)
            return
        }

        setAlarm(trigger.toEpochMilli(), dailyPendingIntent(plan.id))
    }

    fun scheduleSnooze(planId: Long, delayMinutes: Long = 10L) {
        if (planId <= 0L) return
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        setAlarm(triggerAt, snoozePendingIntent(planId))
    }

    fun cancel(planId: Long) {
        cancelDaily(planId)
        cancelSnooze(planId)
    }

    fun cancelDaily(planId: Long) {
        alarmManager.cancel(dailyPendingIntent(planId))
    }

    fun cancelSnooze(planId: Long) {
        alarmManager.cancel(snoozePendingIntent(planId))
    }

    fun rescheduleAll(plans: List<PlanEntity>) {
        plans.forEach { plan ->
            if (plan.enabled) schedule(plan) else cancel(plan.id)
        }
    }

    private fun setAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                // Graceful degradation when the Android 12+ special access is
                // not granted: still fires, but the system may defer it in Doze.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Exact alarm denied, falling back to inexact alarm", error)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun dailyPendingIntent(planId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_DAILY,
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_DAILY
                data = Uri.parse("planpop://alarm/daily/$planId")
                putExtra(EXTRA_PLAN_ID, planId)
                putExtra(EXTRA_IS_SNOOZE, false)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun snoozePendingIntent(planId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_SNOOZE,
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE
                data = Uri.parse("planpop://alarm/snooze/$planId")
                putExtra(EXTRA_PLAN_ID, planId)
                putExtra(EXTRA_IS_SNOOZE, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val REQUEST_DAILY = 1001
        private const val REQUEST_SNOOZE = 1002

        const val ACTION_DAILY = "com.planpop.app.action.DAILY_REMINDER"
        const val ACTION_SNOOZE = "com.planpop.app.action.SNOOZE_REMINDER"
        const val EXTRA_PLAN_ID = "extra_plan_id"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"

        const val SNOOZE_MINUTES = 10L
    }
}
