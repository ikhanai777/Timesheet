package com.ikhanai.timesheet.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class DateRange(val start: LocalDate, val end: LocalDate) : Iterable<LocalDate> {
    init {
        require(!end.isBefore(start)) { "end $end is before start $start" }
    }

    val dayCount: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    override fun iterator(): Iterator<LocalDate> =
        generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.iterator()
}

enum class PeriodType(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
}

object Periods {

    fun day(date: LocalDate) = DateRange(date, date)

    fun week(date: LocalDate, weekStart: DayOfWeek): DateRange {
        val start = date.with(TemporalAdjusters.previousOrSame(weekStart))
        return DateRange(start, start.plusDays(6))
    }

    fun month(date: LocalDate): DateRange {
        val month = YearMonth.from(date)
        return DateRange(month.atDay(1), month.atEndOfMonth())
    }

    fun range(type: PeriodType, date: LocalDate, weekStart: DayOfWeek): DateRange = when (type) {
        PeriodType.DAY -> day(date)
        PeriodType.WEEK -> week(date, weekStart)
        PeriodType.MONTH -> month(date)
    }

    fun shift(type: PeriodType, date: LocalDate, delta: Long): LocalDate = when (type) {
        PeriodType.DAY -> date.plusDays(delta)
        PeriodType.WEEK -> date.plusWeeks(delta)
        PeriodType.MONTH -> date.plusMonths(delta)
    }

    /** Splits [range] into week segments, clipping the first and last week to the range. */
    fun weekSegments(range: DateRange, weekStart: DayOfWeek): List<DateRange> {
        val segments = mutableListOf<DateRange>()
        var cursor = range.start
        while (!cursor.isAfter(range.end)) {
            val weekEnd = week(cursor, weekStart).end
            val end = if (weekEnd.isAfter(range.end)) range.end else weekEnd
            segments += DateRange(cursor, end)
            cursor = end.plusDays(1)
        }
        return segments
    }
}
