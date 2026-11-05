package com.planpop.app.data

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlanRepository(
    private val planDao: PlanDao,
    private val completionDao: CompletionDao
) {

    val plans: Flow<List<PlanEntity>> = planDao.observeAll()

    fun observeCompletedPlanIds(date: String): Flow<Set<Long>> =
        completionDao.observeCompletedPlanIds(date).map { it.toSet() }

    suspend fun getAllPlans(): List<PlanEntity> = planDao.getAll()

    suspend fun getEnabledPlans(): List<PlanEntity> = planDao.getEnabled()

    suspend fun getPlan(id: Long): PlanEntity? = planDao.getById(id)

    suspend fun save(plan: PlanEntity): Long {
        return if (plan.id == 0L) {
            planDao.insert(plan)
        } else {
            planDao.update(plan)
            plan.id
        }
    }

    suspend fun setEnabled(plan: PlanEntity, enabled: Boolean) {
        planDao.setEnabled(plan.id, enabled)
    }

    suspend fun markTriggered(
        planId: Long,
        triggeredAt: Long = System.currentTimeMillis()
    ) {
        planDao.markTriggered(planId, triggeredAt)
    }

    suspend fun delete(plan: PlanEntity) {
        planDao.delete(plan)
    }

    suspend fun markCompleted(planId: Long, date: String = LocalDate.now().toString()) {
        completionDao.markCompleted(
            CompletionEntity(
                planId = planId,
                date = date,
                completedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun isCompleted(planId: Long, date: String = LocalDate.now().toString()): Boolean =
        completionDao.isCompleted(planId, date)

    suspend fun pruneCompletions(keepDays: Long = 60) {
        completionDao.deleteOlderThan(LocalDate.now().minusDays(keepDays).toString())
    }
}
