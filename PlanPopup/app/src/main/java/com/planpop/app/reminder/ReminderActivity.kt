package com.planpop.app.reminder

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.planpop.app.PlanPopupApplication
import com.planpop.app.alarm.AlarmScheduler
import com.planpop.app.data.PlanEntity
import com.planpop.app.data.specificDateOrNull
import com.planpop.app.ui.theme.PlanPopupTheme
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent, lock-screen-capable reminder window. It is deliberately a
 * separate task with transparent theme, and is removed from Recents.
 */
class ReminderActivity : ComponentActivity() {

    private lateinit var app: PlanPopupApplication
    private var planId by mutableLongStateOf(-1L)
    private var actionInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as PlanPopupApplication
        planId = intent.getLongExtra(EXTRA_PLAN_ID, -1L)
        if (planId <= 0L) {
            finish()
            return
        }

        configureWindowForLockScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            PlanPopupTheme {
                ReminderRoute(
                    planId = planId,
                    loadPlan = { id -> app.repository.getPlan(id) },
                    onComplete = ::handleComplete,
                    onSnooze = ::handleSnooze,
                    onClose = ::handleClose,
                    onMissing = {
                        app.notifications.cancel(planId)
                        finishReminder()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newPlanId = intent.getLongExtra(EXTRA_PLAN_ID, -1L)
        if (newPlanId <= 0L) return
        if (planId != newPlanId) {
            app.notifications.cancel(planId)
        }
        planId = newPlanId
    }

    @Suppress("DEPRECATION")
    private fun configureWindowForLockScreen() {
        setFinishOnTouchOutside(false)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun handleComplete(plan: PlanEntity) {
        if (actionInProgress) return
        actionInProgress = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    // At a real SPECIFIC trigger triggeredAt is already set, so
                    // completion is stored on the selected date. If the test
                    // button opens a future plan before it fires, use today so
                    // the real reminder is not suppressed.
                    val completionDate = if (plan.triggeredAt != null) {
                        plan.specificDateOrNull()?.toString() ?: LocalDate.now().toString()
                    } else {
                        LocalDate.now().toString()
                    }
                    app.repository.markCompleted(plan.id, completionDate)
                    app.alarmScheduler.cancelSnooze(plan.id)
                }
            }
            app.notifications.cancel(plan.id)
            finishReminder()
        }
    }

    private fun handleSnooze(plan: PlanEntity) {
        if (actionInProgress) return
        actionInProgress = true
        app.alarmScheduler.scheduleSnooze(plan.id, AlarmScheduler.SNOOZE_MINUTES)
        app.notifications.cancel(plan.id)
        finishReminder()
    }

    private fun handleClose() {
        if (actionInProgress) return
        actionInProgress = true
        app.notifications.cancel(planId)
        finishReminder()
    }

    private fun finishReminder() {
        if (isFinishing || isDestroyed) return
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_PLAN_ID = "extra_reminder_plan_id"

        fun createIntent(context: Context, planId: Long): Intent =
            Intent(context, ReminderActivity::class.java).apply {
                putExtra(EXTRA_PLAN_ID, planId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
    }
}
