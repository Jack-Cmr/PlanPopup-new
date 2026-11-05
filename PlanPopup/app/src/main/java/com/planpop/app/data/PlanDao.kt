package com.planpop.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {

    @Query("SELECT * FROM plans ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE enabled = 1 ORDER BY hour, minute, id")
    suspend fun getEnabled(): List<PlanEntity>

    @Query("SELECT * FROM plans ORDER BY hour, minute, id")
    suspend fun getAll(): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PlanEntity?

    @Insert
    suspend fun insert(plan: PlanEntity): Long

    @Update
    suspend fun update(plan: PlanEntity)

    @Delete
    suspend fun delete(plan: PlanEntity)

    @Query("UPDATE plans SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE plans SET triggeredAt = :triggeredAt WHERE id = :id")
    suspend fun markTriggered(id: Long, triggeredAt: Long)
}

@Dao
interface CompletionDao {

    @Query("SELECT planId FROM completions WHERE date = :date")
    fun observeCompletedPlanIds(date: String): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markCompleted(completion: CompletionEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM completions WHERE planId = :planId AND date = :date)")
    suspend fun isCompleted(planId: Long, date: String): Boolean

    @Query("DELETE FROM completions WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}
