package com.planpop.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

const val RECURRENCE_DAILY = "DAILY"
const val RECURRENCE_SPECIFIC = "SPECIFIC"

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val title: String,
    val note: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * DAILY for the original repeating reminder, SPECIFIC for a one-off date.
     * Nullable only because databases created before v2 are migrated in place;
     * null is treated as DAILY.
     */
    val recurrenceType: String? = RECURRENCE_DAILY,
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,

    /**
     * Set once a SPECIFIC reminder has fired. It prevents the one-off alarm
     * from being scheduled again, while still allowing a snooze alarm to fire.
     */
    val triggeredAt: Long? = null
)

val PlanEntity.isSpecific: Boolean
    get() = recurrenceType == RECURRENCE_SPECIFIC

fun PlanEntity.specificDateOrNull(): LocalDate? {
    val y = year ?: return null
    val m = month ?: return null
    val d = day ?: return null
    return runCatching { LocalDate.of(y, m, d) }.getOrNull()
}

fun PlanEntity.specificDateTimeOrNull(): LocalDateTime? =
    specificDateOrNull()?.atTime(hour, minute)

/**
 * A SPECIFIC plan is no longer actionable once it fired or its date/time passed.
 * DAILY plans never expire automatically.
 */
fun PlanEntity.isSpecificExpired(now: LocalDateTime = LocalDateTime.now()): Boolean {
    if (!isSpecific) return false
    if (triggeredAt != null) return true
    val dateTime = specificDateTimeOrNull() ?: return true
    return !dateTime.isAfter(now)
}

/**
 * Keeps DB values coherent when a plan is saved:
 * - DAILY clears all one-off fields.
 * - A future SPECIFIC plan clears a stale triggeredAt so it can be scheduled.
 * - A past SPECIFIC plan keeps its fired marker (if any) and is simply expired.
 */
fun PlanEntity.normalizedForSave(now: LocalDateTime = LocalDateTime.now()): PlanEntity {
    if (!isSpecific) {
        return copy(
            recurrenceType = RECURRENCE_DAILY,
            year = null,
            month = null,
            day = null,
            triggeredAt = null
        )
    }

    val isFuture = specificDateTimeOrNull()?.isAfter(now) == true
    return copy(triggeredAt = if (isFuture) null else triggeredAt)
}

/**
 * One row per plan per local calendar date. The composite key makes marking the
 * same plan completed twice in one day idempotent.
 */
@Entity(
    tableName = "completions",
    primaryKeys = ["planId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("date")]
)
data class CompletionEntity(
    val planId: Long,
    val date: String,
    val completedAt: Long = System.currentTimeMillis()
)
