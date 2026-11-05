package com.planpop.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.planpop.app.PlanPopupApplication
import com.planpop.app.data.specificDateOrNull
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the notification action buttons even when the user never opens the
 * reminder Activity.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PlanPopupApplication ?: return
        val planId = intent.getLongExtra(AlarmScheduler.EXTRA_PLAN_ID, -1L)
        if (planId <= 0L) return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> {
                        val plan = app.repository.getPlan(planId)
                        // A SPECIFIC plan's completion belongs to its calendar
                        // date, not necessarily "today" after a midnight snooze.
                        val completionDate = plan?.specificDateOrNull()?.toString()
                            ?: LocalDate.now().toString()
                        app.repository.markCompleted(planId, completionDate)
                        app.alarmScheduler.cancelSnooze(planId)
                    }

                    ACTION_SNOOZE -> {
                        app.alarmScheduler.scheduleSnooze(
                            planId,
                            AlarmScheduler.SNOOZE_MINUTES
                        )
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to handle reminder action", error)
            } finally {
                app.notifications.cancel(planId)
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderActionReceiver"
        const val ACTION_COMPLETE = "com.planpop.app.action.COMPLETE"
        const val ACTION_SNOOZE = "com.planpop.app.action.SNOOZE"
    }
}
