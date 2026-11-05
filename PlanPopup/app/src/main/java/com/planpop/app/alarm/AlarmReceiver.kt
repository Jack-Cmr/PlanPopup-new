package com.planpop.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.planpop.app.PlanPopupApplication
import com.planpop.app.data.isSpecific
import com.planpop.app.data.specificDateOrNull
import com.planpop.app.data.specificDateTimeOrNull
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PlanPopupApplication ?: return
        val planId = intent.getLongExtra(AlarmScheduler.EXTRA_PLAN_ID, -1L)
        if (planId <= 0L) return
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val plan = app.repository.getPlan(planId)
                if (plan == null || !plan.enabled) {
                    app.alarmScheduler.cancel(planId)
                    app.notifications.cancel(planId)
                    return@launch
                }

                if (!isSnooze) {
                    if (plan.isSpecific) {
                        // A one-off plan must never be armed again after it has
                        // fired. Mark it before delivery so a process death
                        // cannot cause a duplicate next day.
                        if (plan.triggeredAt != null ||
                            plan.specificDateTimeOrNull() == null
                        ) {
                            app.alarmScheduler.cancelDaily(planId)
                            app.notifications.cancel(planId)
                            return@launch
                        }
                        app.repository.markTriggered(planId, System.currentTimeMillis())
                    } else {
                        // Re-arm the next daily occurrence before showing this
                        // one, so tomorrow survives process death.
                        app.alarmScheduler.scheduleNext(plan)
                    }
                }
                // Snooze alarms do not reschedule anything. They are allowed even
                // after a SPECIFIC plan has been marked triggered, which is what
                // makes "稍后 10 分钟" work for one-off plans.

                // A completion from a notification action may race with a snooze
                // alarm. Do not show a reminder for an already-done plan. A
                // SPECIFIC plan's completion date is its selected date, even if
                // a snooze crosses midnight.
                val completionDate = if (plan.isSpecific) {
                    plan.specificDateOrNull()?.toString() ?: LocalDate.now().toString()
                } else {
                    LocalDate.now().toString()
                }
                if (app.repository.isCompleted(planId, completionDate)) {
                    app.notifications.cancel(planId)
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    app.reminderDelivery.deliver(plan)
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to deliver alarm for plan $planId", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
