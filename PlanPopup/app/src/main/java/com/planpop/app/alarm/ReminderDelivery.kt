package com.planpop.app.alarm

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.planpop.app.data.PlanEntity
import com.planpop.app.reminder.ReminderActivity

/**
 * Chooses the visible delivery path:
 *  1. Overlay permission -> transparent Activity directly over other apps/lock screen.
 *  2. Otherwise -> high-priority silent notification with full-screen intent.
 *  3. Last resort (both denied) -> best-effort Activity start; may be blocked by
 *     Android's background Activity restrictions, which is why the permission
 *     onboarding exists.
 */
class ReminderDelivery(private val context: Context) {

    operator fun invoke(plan: PlanEntity) {
        deliver(plan)
    }

    fun deliver(plan: PlanEntity) {
        val overlayGranted = Settings.canDrawOverlays(context)
        val notificationGranted = ReminderNotifications.canPost(context)

        if (overlayGranted) {
            if (startReminderActivity(plan.id)) return
            if (notificationGranted) {
                ReminderNotifications(context).show(plan)
                return
            }
        } else if (notificationGranted) {
            ReminderNotifications(context).show(plan)
            return
        }

        // Both primary paths are unavailable. Try the Activity anyway; some OEM
        // builds and exact-alarm broadcasts still permit this.
        startReminderActivity(plan.id)
    }

    private fun startReminderActivity(planId: Long): Boolean {
        return try {
            context.startActivity(ReminderActivity.createIntent(context, planId))
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to start reminder Activity", error)
            false
        }
    }

    companion object {
        private const val TAG = "ReminderDelivery"
    }
}
