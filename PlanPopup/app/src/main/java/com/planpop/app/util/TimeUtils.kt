package com.planpop.app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

fun formatPlanTime(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

/**
 * Example: 11月10日 周一, or 2027年1月5日 周二 when not in the current year.
 */
fun formatTimelineDate(
    date: LocalDate,
    today: LocalDate = LocalDate.now()
): String {
    val base = if (date.year == today.year) {
        "${date.monthValue}月${date.dayOfMonth}日"
    } else {
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
    return "$base ${weekdayLabel(date.dayOfWeek)}"
}

/**
 * Always includes the year; useful in the editor where the user is choosing an
 * exact calendar day.
 */
fun formatEditorDate(date: LocalDate): String =
    "${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${weekdayLabel(date.dayOfWeek)}"

private fun weekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

/**
 * Returns the next occurrence of [hour]:[minute] strictly after [now]. A plan
 * whose time is exactly "now" is scheduled for tomorrow, which avoids an
 * immediate duplicate trigger when an alarm fires at the start of a minute.
 */
fun nextDailyTriggerMillis(
    hour: Int,
    minute: Int,
    now: ZonedDateTime = ZonedDateTime.now()
): Long {
    var candidate = now
        .withHour(hour)
        .withMinute(minute)
        .withSecond(0)
        .withNano(0)
    if (!candidate.isAfter(now)) {
        candidate = candidate.plusDays(1)
    }
    return candidate.toInstant().toEpochMilli()
}

fun formatTriggerTime(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(TIME_FORMATTER)
