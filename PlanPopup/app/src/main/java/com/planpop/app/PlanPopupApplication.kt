package com.planpop.app

import android.app.Application
import com.planpop.app.alarm.AlarmScheduler
import com.planpop.app.alarm.ReminderDelivery
import com.planpop.app.alarm.ReminderNotifications
import com.planpop.app.data.AppDatabase
import com.planpop.app.data.PlanRepository
import com.planpop.app.data.SettingsStore

class PlanPopupApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }

    val repository: PlanRepository by lazy {
        PlanRepository(
            planDao = database.planDao(),
            completionDao = database.completionDao()
        )
    }

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(this) }

    val notifications: ReminderNotifications by lazy { ReminderNotifications(this) }

    val reminderDelivery: ReminderDelivery by lazy { ReminderDelivery(this) }

    override fun onCreate() {
        super.onCreate()
        // Create the silent, high-importance channel as early as possible so the
        // channel exists before the first alarm is delivered.
        notifications.ensureChannel()
    }
}
