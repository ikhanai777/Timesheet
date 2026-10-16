package com.ikhanai.timesheet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import java.util.Locale

class TimesheetCalculatorTest {

    private val weekend = setOf(SATURDAY, SUNDAY)
    private val projectA = 1L
    private val projectB = 2L

    // Thursday
    private val thu = LocalDate.of(2026, 10, 15)

    private fun day(
        date: LocalDate,
        vararg entries: EntryLine,
        type: DayType = DayType.WORKING,
        available: Int = 480,
    ) = DayRecord(date, type, available, entries.toList())

    private fun ntime(project: Long, hours: Double) = EntryLine(project, (hours * 60).toInt(), BookingSystem.NTIME)
    private fun p6(project: Long, hours: Double) = EntryLine(project, (hours * 60).toInt(), BookingSystem.P6)

    private fun summarize(range: DateRange, days: List<DayRecord>, overheadSystem: BookingSystem? = null, today: LocalDate = LocalDate.of(2026, 12, 31)) =
        TimesheetCalculator.summarize(range, days, overheadSystem, weekend, today)

    @Test
    fun `AC1 - project A 4h NTIME and project B 3h P6 leaves 1h overhead`() {
        val d = day(thu, ntime(projectA, 4.0), p6(projectB, 3.0))
        val s = summarize(Periods.day(thu), listOf(d))
        assertEquals(240, s.ntimeMinutes)
        assertEquals(180, s.p6Minutes)
        assertEquals(60, s.overheadMinutes)
        assertEquals(480, s.totalMinutes)
    }

    @Test
    fun `AC2 - working day with no entries is a full day of overhead`() {
        val s = summarize(Periods.day(thu), listOf(day(thu)))
        assertEquals(480, s.overheadMinutes)
        assertEquals(480, s.totalMinutes)
    }

    @Test
    fun `AC3 - adding 1h to 7_5h is rejected`() {
        val result = TimesheetCalculator.validateEntry(480, 450, 60, allowOvertime = false)
        assertEquals(EntryValidation.ExceedsAvailable(510, 480), result)
    }

    @Test
    fun `exactly filling the day is allowed and leaves no overhead`() {
        assertEquals(EntryValidation.Ok, TimesheetCalculator.validateEntry(480, 420, 60, allowOvertime = false))
        assertEquals(0, TimesheetCalculator.overheadMinutes(day(thu, ntime(projectA, 8.0))))
    }

    @Test
    fun `zero or negative hours are rejected`() {
        assertEquals(EntryValidation.NotPositive, TimesheetCalculator.validateEntry(480, 0, 0, false))
        assertEquals(EntryValidation.NotPositive, TimesheetCalculator.validateEntry(480, 0, -30, false))
    }

    @Test
    fun `overtime allowed - excess reported as overtime and overhead is zero`() {
        assertEquals(EntryValidation.Ok, TimesheetCalculator.validateEntry(480, 450, 60, allowOvertime = true))
        val d = day(thu, ntime(projectA, 9.0))
        val s = summarize(Periods.day(thu), listOf(d))
        assertEquals(0, s.overheadMinutes)
        assertEquals(60, s.overtimeMinutes)
        assertEquals(540, s.totalMinutes)
    }

    @Test
    fun `AC4 - same project split across systems`() {
        val d = day(thu, ntime(projectA, 2.0), p6(projectA, 2.0))
        val s = summarize(Periods.day(thu), listOf(d))
        val row = s.projects.single()
        assertEquals(120, row.ntimeMinutes)
        assertEquals(120, row.p6Minutes)
        assertEquals(240, row.totalMinutes)
        assertEquals(240, s.overheadMinutes)
    }

    @Test
    fun `AC5 - weekly roll-up over five working days`() {
        val week = Periods.week(thu, MONDAY)
        val days = (0L until 5L).map { day(week.start.plusDays(it), ntime(projectA, 4.0), p6(projectB, 3.0)) }
        val s = summarize(week, days)
        assertEquals(20 * 60, s.projects.first { it.projectId == projectA }.totalMinutes)
        assertEquals(15 * 60, s.projects.first { it.projectId == projectB }.totalMinutes)
        assertEquals(20 * 60, s.ntimeMinutes)
        assertEquals(15 * 60, s.p6Minutes)
        assertEquals(5 * 60, s.overheadMinutes)
        assertEquals(40 * 60, s.totalMinutes)
        assertEquals(listOf(projectA, projectB), s.projects.map { it.projectId })
    }

    @Test
    fun `AC6 - month totals equal the sum of day totals inside the month only`() {
        val october = Periods.month(thu)
        val days = listOf(
            day(LocalDate.of(2026, 9, 30), ntime(projectA, 8.0)), // outside, same week as Oct 1
            day(LocalDate.of(2026, 10, 1), ntime(projectA, 5.0)),
            day(LocalDate.of(2026, 10, 2), p6(projectB, 6.0)),
            day(LocalDate.of(2026, 10, 31), ntime(projectA, 2.0), type = DayType.WEEKEND),
        )
        val s = summarize(october, days)
        val dayTotals = days.filter { it.date in october }.sumOf {
            TimesheetCalculator.projectMinutes(it) + TimesheetCalculator.overheadMinutes(it)
        }
        assertEquals(dayTotals, s.totalMinutes)
        assertEquals((5 + 6 + 2) * 60 + (3 + 2) * 60, s.totalMinutes)
    }

    @Test
    fun `AC8 - unlogged working days add nothing and are listed as missing`() {
        val week = Periods.week(thu, MONDAY) // Mon 12 – Sun 18 Oct
        val days = listOf(day(LocalDate.of(2026, 10, 12), ntime(projectA, 8.0)))
        val s = TimesheetCalculator.summarize(week, days, null, weekend, today = thu)
        assertEquals(480, s.totalMinutes)
        assertEquals(
            listOf(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14), thu),
            s.missingDays,
        )
    }

    @Test
    fun `AC9 - leave day contributes no overhead`() {
        val s = summarize(Periods.day(thu), listOf(day(thu, type = DayType.LEAVE)))
        assertEquals(0, s.overheadMinutes)
        assertEquals(0, s.totalMinutes)
        assertEquals(0, TimesheetCalculator.overheadMinutes(day(thu, type = DayType.PUBLIC_HOLIDAY)))
    }

    @Test
    fun `AC10 - overhead uses each day's own available hours snapshot`() {
        val old = day(thu, ntime(projectA, 7.0), available = 480)
        val new = day(thu.plusDays(1), ntime(projectA, 7.0), available = 540)
        assertEquals(60, TimesheetCalculator.overheadMinutes(old))
        assertEquals(120, TimesheetCalculator.overheadMinutes(new))
    }

    @Test
    fun `overhead is added to the configured booking system`() {
        val d = day(thu, ntime(projectA, 4.0), p6(projectB, 3.0))
        val inNtime = summarize(Periods.day(thu), listOf(d), overheadSystem = BookingSystem.NTIME)
        assertEquals(300, inNtime.ntimeMinutes)
        assertEquals(180, inNtime.p6Minutes)
        assertEquals(480, inNtime.totalMinutes)
        val inP6 = summarize(Periods.day(thu), listOf(d), overheadSystem = BookingSystem.P6)
        assertEquals(240, inP6.p6Minutes)
        // Per-project columns never include overhead.
        assertEquals(180, inP6.projects.first { it.projectId == projectB }.p6Minutes)
    }

    @Test
    fun `utilisation is project hours over total`() {
        val s = summarize(Periods.day(thu), listOf(day(thu, ntime(projectA, 6.0))))
        assertEquals(0.75, s.utilisation!!, 1e-9)
        assertNull(summarize(Periods.day(thu), emptyList()).utilisation)
    }

    @Test
    fun `week ranges follow the configured start day`() {
        assertEquals(DateRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)), Periods.week(thu, MONDAY))
        assertEquals(DateRange(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 17)), Periods.week(thu, SUNDAY))
        assertEquals(DateRange(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 16)), Periods.week(thu, SATURDAY))
        // A date that is itself the start day.
        val sunday = LocalDate.of(2026, 10, 11)
        assertEquals(sunday, Periods.week(sunday, SUNDAY).start)
    }

    @Test
    fun `month ranges handle leap years`() {
        assertEquals(LocalDate.of(2028, 2, 29), Periods.month(LocalDate.of(2028, 2, 10)).end)
        assertEquals(LocalDate.of(2027, 2, 28), Periods.month(LocalDate.of(2027, 2, 10)).end)
    }

    @Test
    fun `month chart has one bucket per clipped week`() {
        val october = Periods.month(thu) // Oct 1 2026 is a Thursday
        val buckets = TimesheetCalculator.chartBuckets(PeriodType.MONTH, october, emptyList(), MONDAY, Locale.UK)
        assertEquals(listOf("1–4", "5–11", "12–18", "19–25", "26–31"), buckets.map { it.label })
        assertEquals(october.dayCount, buckets.sumOf { it.range.dayCount })
    }

    @Test
    fun `week chart has one bucket per day with overhead`() {
        val week = Periods.week(thu, MONDAY)
        val days = listOf(day(thu, ntime(projectA, 4.0), p6(projectB, 3.0)))
        val buckets = TimesheetCalculator.chartBuckets(PeriodType.WEEK, week, days, MONDAY, Locale.UK)
        assertEquals(7, buckets.size)
        val thursday = buckets[3]
        assertEquals("Thu", thursday.label)
        assertEquals(240, thursday.ntimeMinutes)
        assertEquals(180, thursday.p6Minutes)
        assertEquals(60, thursday.overheadMinutes)
        assertEquals(0, buckets[0].totalMinutes)
    }

    @Test
    fun `missing days skip weekends and future dates`() {
        val week = Periods.week(thu, MONDAY)
        val missing = TimesheetCalculator.missingDays(week, emptySet(), setOf(FRIDAY, SATURDAY), today = thu)
        assertEquals(4, missing.size)
        assertEquals(DayOfWeek.MONDAY, missing.first().dayOfWeek)
    }
}
