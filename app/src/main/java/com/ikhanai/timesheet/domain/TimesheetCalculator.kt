package com.ikhanai.timesheet.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Per-project totals for a period. NTIME/P6 columns never include overhead. */
data class ProjectTotals(
    val projectId: Long,
    val ntimeMinutes: Int,
    val p6Minutes: Int,
) {
    val totalMinutes: Int get() = ntimeMinutes + p6Minutes
}

data class PeriodSummary(
    val range: DateRange,
    val projectMinutes: Int,
    /** NTIME total; includes overhead when overhead is booked into NTIME. */
    val ntimeMinutes: Int,
    /** P6 total; includes overhead when overhead is booked into P6. */
    val p6Minutes: Int,
    val overheadMinutes: Int,
    val overtimeMinutes: Int,
    val projects: List<ProjectTotals>,
    val missingDays: List<LocalDate>,
    val loggedDays: Int,
) {
    val totalMinutes: Int get() = projectMinutes + overheadMinutes

    /** Project hours ÷ (project hours + overhead), or null when nothing is logged. */
    val utilisation: Double? get() = if (totalMinutes == 0) null else projectMinutes.toDouble() / totalMinutes
}

/** One column of a chart: a day (week view) or a week segment (month view). */
data class ChartBucket(
    val label: String,
    val range: DateRange,
    val ntimeMinutes: Int,
    val p6Minutes: Int,
    val overheadMinutes: Int,
) {
    val totalMinutes: Int get() = ntimeMinutes + p6Minutes + overheadMinutes
}

sealed interface EntryValidation {
    data object Ok : EntryValidation
    data object NotPositive : EntryValidation
    data class ExceedsAvailable(val totalMinutes: Int, val availableMinutes: Int) : EntryValidation
}

/** All overhead and aggregation rules (spec FR-2, FR-3). Pure Kotlin so it is unit-testable. */
object TimesheetCalculator {

    fun projectMinutes(day: DayRecord): Int = day.entries.sumOf { it.minutes }

    /** FR-2: overhead = max(0, available − project hours), only on working days. */
    fun overheadMinutes(day: DayRecord): Int =
        if (day.dayType != DayType.WORKING) 0 else maxOf(0, day.availableMinutes - projectMinutes(day))

    fun overtimeMinutes(day: DayRecord): Int =
        if (day.dayType != DayType.WORKING) 0 else maxOf(0, projectMinutes(day) - day.availableMinutes)

    /**
     * FR-2.2: checks a new or edited entry of [newMinutes] against the day, where [otherMinutes] is the
     * sum of the day's other entries.
     */
    fun validateEntry(
        availableMinutes: Int,
        otherMinutes: Int,
        newMinutes: Int,
        allowOvertime: Boolean,
    ): EntryValidation {
        if (newMinutes <= 0) return EntryValidation.NotPositive
        val total = otherMinutes + newMinutes
        if (!allowOvertime && total > availableMinutes) {
            return EntryValidation.ExceedsAvailable(total, availableMinutes)
        }
        return EntryValidation.Ok
    }

    fun summarize(
        range: DateRange,
        days: List<DayRecord>,
        overheadSystem: BookingSystem?,
        weekendDays: Set<DayOfWeek>,
        today: LocalDate,
    ): PeriodSummary {
        val inRange = days.filter { it.date in range }
        var ntime = 0
        var p6 = 0
        var overhead = 0
        var overtime = 0
        val perProject = linkedMapOf<Long, IntArray>()
        for (day in inRange) {
            for (entry in day.entries) {
                val totals = perProject.getOrPut(entry.projectId) { IntArray(2) }
                when (entry.system) {
                    BookingSystem.NTIME -> {
                        totals[0] += entry.minutes
                        ntime += entry.minutes
                    }
                    BookingSystem.P6 -> {
                        totals[1] += entry.minutes
                        p6 += entry.minutes
                    }
                }
            }
            overhead += overheadMinutes(day)
            overtime += overtimeMinutes(day)
        }
        val projects = perProject
            .map { (id, totals) -> ProjectTotals(id, totals[0], totals[1]) }
            .sortedByDescending { it.totalMinutes }
        return PeriodSummary(
            range = range,
            projectMinutes = ntime + p6,
            ntimeMinutes = ntime + if (overheadSystem == BookingSystem.NTIME) overhead else 0,
            p6Minutes = p6 + if (overheadSystem == BookingSystem.P6) overhead else 0,
            overheadMinutes = overhead,
            overtimeMinutes = overtime,
            projects = projects,
            missingDays = missingDays(range, inRange.map { it.date }.toSet(), weekendDays, today),
            loggedDays = inRange.size,
        )
    }

    /** Working days up to [today] that have no Day Sheet (FR-2.4, FR-3 item 4). */
    fun missingDays(
        range: DateRange,
        loggedDates: Set<LocalDate>,
        weekendDays: Set<DayOfWeek>,
        today: LocalDate,
    ): List<LocalDate> = range.filter { date ->
        !date.isAfter(today) && date.dayOfWeek !in weekendDays && date !in loggedDates
    }

    fun bucket(label: String, range: DateRange, days: List<DayRecord>): ChartBucket {
        var ntime = 0
        var p6 = 0
        var overhead = 0
        for (day in days) {
            if (day.date !in range) continue
            for (entry in day.entries) {
                when (entry.system) {
                    BookingSystem.NTIME -> ntime += entry.minutes
                    BookingSystem.P6 -> p6 += entry.minutes
                }
            }
            overhead += overheadMinutes(day)
        }
        return ChartBucket(label, range, ntime, p6, overhead)
    }

    /** Day: one bucket. Week: one per day. Month: one per (clipped) week. */
    fun chartBuckets(
        type: PeriodType,
        range: DateRange,
        days: List<DayRecord>,
        weekStart: DayOfWeek,
        locale: Locale = Locale.getDefault(),
    ): List<ChartBucket> = when (type) {
        PeriodType.DAY -> listOf(bucket(range.start.dayOfMonth.toString(), range, days))
        PeriodType.WEEK -> range.map { date ->
            bucket(date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), Periods.day(date), days)
        }
        PeriodType.MONTH -> Periods.weekSegments(range, weekStart).map { segment ->
            val label = if (segment.dayCount == 1) {
                "${segment.start.dayOfMonth}"
            } else {
                "${segment.start.dayOfMonth}–${segment.end.dayOfMonth}"
            }
            bucket(label, segment, days)
        }
    }
}
