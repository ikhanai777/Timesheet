package com.ikhanai.timesheet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class HoursAndExportTest {

    @Test
    fun `parse accepts dot and comma decimals`() {
        assertEquals(270, Hours.parse("4.5"))
        assertEquals(270, Hours.parse("4,5"))
        assertEquals(15, Hours.parse("0.25"))
        assertEquals(480, Hours.parse(" 8 "))
        assertNull(Hours.parse(""))
        assertNull(Hours.parse("abc"))
    }

    @Test
    fun `round to step`() {
        assertEquals(270, Hours.roundToStep(260, 30))
        assertEquals(240, Hours.roundToStep(250, 30))
        assertEquals(255, Hours.roundToStep(255, 15))
        assertEquals(0, Hours.roundToStep(10, 30))
    }

    @Test
    fun `format uses one or two decimals`() {
        assertEquals("4.5", Hours.format(270, Locale.US))
        assertEquals("4.25", Hours.format(255, Locale.US))
        assertEquals("8.0", Hours.format(480, Locale.US))
        assertEquals("4,5", Hours.format(270, Locale.GERMANY))
        assertEquals("4.5", Hours.plain(270))
        assertEquals("8", Hours.plain(480))
        assertEquals("0.25", Hours.plain(15))
    }

    private val projects = mapOf(
        1L to ProjectInfo(1, "Project A", "PRJ-2041", "4100.22.07"),
        2L to ProjectInfo(2, "Project B, Phase 2", "PRJ-2055", "4100.31.02"),
    )

    private fun week(): Pair<DateRange, List<DayRecord>> {
        val range = Periods.week(LocalDate.of(2026, 10, 15), DayOfWeek.MONDAY)
        val days = (0L until 5L).map {
            DayRecord(
                range.start.plusDays(it),
                DayType.WORKING,
                480,
                listOf(EntryLine(1, 240, BookingSystem.NTIME), EntryLine(2, 180, BookingSystem.P6)),
            )
        } + DayRecord(range.start.plusDays(5), DayType.LEAVE, 480, emptyList())
        return range to days
    }

    @Test
    fun `AC11 - CSV has a row per entry plus overhead rows and sums to the dashboard total`() {
        val (range, days) = week()
        val rows = ExportFormat.rows(range, days, projects, overheadSystem = null)
        assertEquals(5 * 2 + 5, rows.size)
        assertEquals(5, rows.count { it.projectName == ExportFormat.OVERHEAD_NAME })

        val csv = ExportFormat.toCsv(rows)
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(ExportFormat.CSV_HEADER, lines.first())
        val hoursSum = lines.drop(1).map { BigDecimal(it.substringAfterLast(',')) }.fold(BigDecimal.ZERO, BigDecimal::add)

        val summary = TimesheetCalculator.summarize(range, days, null, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), range.end)
        assertEquals(0, hoursSum.compareTo(BigDecimal(summary.totalMinutes).divide(BigDecimal(60))))
    }

    @Test
    fun `CSV escapes commas and quotes`() {
        assertEquals("\"Project B, Phase 2\"", ExportFormat.escapeCsv("Project B, Phase 2"))
        assertEquals("\"say \"\"hi\"\"\"", ExportFormat.escapeCsv("say \"hi\""))
        assertEquals("plain", ExportFormat.escapeCsv("plain"))
    }

    @Test
    fun `overhead rows carry the configured system`() {
        val (range, days) = week()
        val rows = ExportFormat.rows(range, days, projects, overheadSystem = BookingSystem.P6)
        assertTrue(rows.filter { it.projectName == ExportFormat.OVERHEAD_NAME }.all { it.system == "P6" })
    }

    @Test
    fun `clipboard summary groups by system`() {
        val (range, days) = week()
        val summary = TimesheetCalculator.summarize(range, days, null, emptySet(), range.end)
        val text = ExportFormat.clipboardSummary("Week", summary, projects, null) { Hours.format(it, Locale.US) }
        assertTrue(text.contains("NTIME (20.0 h)\n  Project A · PRJ-2041 · 4100.22.07: 20.0"))
        assertTrue(text.contains("P6 (15.0 h)\n  Project B, Phase 2 · PRJ-2055 · 4100.31.02: 15.0"))
        assertTrue(text.contains("Overhead / Department Hours: 5.0"))
        assertTrue(text.endsWith("Total: 40.0"))
    }
}
