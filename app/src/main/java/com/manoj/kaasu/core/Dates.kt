package com.manoj.kaasu.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class TimeSlot(val label: String, val bit: Int) {
    MORNING("Morning", 1),
    AFTERNOON("Afternoon", 2),
    EVENING("Evening", 4),
    NIGHT("Night", 8);

    companion object {
        fun fromMask(mask: Int): Set<TimeSlot> = entries.filter { mask and it.bit != 0 }.toSet()
        fun toMask(slots: Set<TimeSlot>): Int = slots.fold(0) { acc, slot -> acc or slot.bit }
    }
}

/** Hours (0-23) where each part of the day starts. Night wraps past midnight. */
data class SlotBoundaries(
    val morningStart: Int = 5,
    val afternoonStart: Int = 11,
    val eveningStart: Int = 16,
    val nightStart: Int = 21,
) {
    fun slotFor(hour: Int): TimeSlot = when (hour) {
        in morningStart until afternoonStart -> TimeSlot.MORNING
        in afternoonStart until eveningStart -> TimeSlot.AFTERNOON
        in eveningStart until nightStart -> TimeSlot.EVENING
        else -> TimeSlot.NIGHT
    }

    fun slotFor(millis: Long): TimeSlot = slotFor(Dates.toLocalDateTime(millis).hour)

    fun isValid(): Boolean = morningStart < afternoonStart && afternoonStart < eveningStart && eveningStart < nightStart
}

object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dayMonthFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val fullFormat = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
    private val dayHeaderFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val weekdayShort = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

    fun now(): Long = System.currentTimeMillis()
    fun today(): LocalDate = LocalDate.now(zone)
    fun toLocalDateTime(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    fun toLocalDate(millis: Long): LocalDate = toLocalDateTime(millis).toLocalDate()
    fun epochDay(millis: Long): Long = toLocalDate(millis).toEpochDay()
    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endOfDay(date: LocalDate): Long = startOfDay(date.plusDays(1)) - 1
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun monthStart(date: LocalDate): LocalDate = date.withDayOfMonth(1)

    fun time(millis: Long): String = timeFormat.format(toLocalDateTime(millis))
    fun dayMonth(date: LocalDate): String = dayMonthFormat.format(date)
    fun full(millis: Long): String = fullFormat.format(toLocalDateTime(millis))
    fun month(date: LocalDate): String = monthFormat.format(date)
    fun weekdayShort(date: LocalDate): String = weekdayShort.format(date)

    fun dayHeader(date: LocalDate): String {
        val today = today()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> dayHeaderFormat.format(date)
        }
    }

    fun weekRangeLabel(start: LocalDate): String {
        val end = start.plusDays(6)
        return "${dayMonth(start)} – ${dayMonth(end)}"
    }

    fun relative(millis: Long): String {
        val minutes = (now() - millis) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} h ago"
            else -> dayMonth(toLocalDate(millis))
        }
    }
}
