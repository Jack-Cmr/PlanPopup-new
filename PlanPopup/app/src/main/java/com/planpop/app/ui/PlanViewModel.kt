package com.planpop.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.planpop.app.PlanPopupApplication
import com.planpop.app.alarm.AlarmScheduler
import com.planpop.app.data.PlanEntity
import com.planpop.app.data.PlanRepository
import com.planpop.app.data.normalizedForSave
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(
    private val repository: PlanRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val currentDate = MutableStateFlow(LocalDate.now())

    val plans = repository.plans.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val completedToday = currentDate
        .flatMapLatest { date -> repository.observeCompletedPlanIds(date.toString()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    private var saveInFlight = false

    init {
        viewModelScope.launch {
            runCatching { repository.pruneCompletions() }
        }
    }

    fun refreshToday() {
        currentDate.value = LocalDate.now()
    }

    fun savePlan(plan: PlanEntity, onSaved: () -> Unit) {
        if (saveInFlight) return
        saveInFlight = true
        viewModelScope.launch {
            try {
                // Clears one-off fields for DAILY and clears a stale triggeredAt
                // when an expired SPECIFIC plan is moved to a future date.
                val normalized = plan.normalizedForSave()
                val id = repository.save(normalized)
                val saved = normalized.copy(id = id)
                // The PendingIntent is keyed by plan id, so cancel is safe even
                // for a newly inserted row.
                alarmScheduler.cancel(saved.id)
                if (saved.enabled) {
                    alarmScheduler.schedule(saved)
                }
                refreshToday()
                onSaved()
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to save plan", error)
                _messages.tryEmit("保存失败，请重试")
            } finally {
                saveInFlight = false
            }
        }
    }

    fun setEnabled(plan: PlanEntity, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setEnabled(plan, enabled)
                if (enabled) {
                    alarmScheduler.schedule(plan.copy(enabled = true))
                } else {
                    alarmScheduler.cancel(plan.id)
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to toggle plan", error)
                _messages.tryEmit("操作失败，请重试")
            }
        }
    }

    fun deletePlan(plan: PlanEntity) {
        viewModelScope.launch {
            try {
                alarmScheduler.cancel(plan.id)
                repository.delete(plan)
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to delete plan", error)
                _messages.tryEmit("删除失败，请重试")
            }
        }
    }

    fun rescheduleAll() {
        viewModelScope.launch {
            runCatching {
                val allPlans = repository.getAllPlans()
                alarmScheduler.rescheduleAll(allPlans)
            }.onFailure { error ->
                Log.e(TAG, "Failed to reschedule all alarms", error)
            }
        }
    }

    companion object {
        private const val TAG = "PlanViewModel"

        fun factory(app: PlanPopupApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlanViewModel(
                    repository = app.repository,
                    alarmScheduler = app.alarmScheduler
                )
            }
        }
    }
}
