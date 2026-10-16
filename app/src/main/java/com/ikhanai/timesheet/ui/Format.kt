package com.ikhanai.timesheet.ui

import com.ikhanai.timesheet.domain.DateRange
import com.ikhanai.timesheet.domain.Hours
import com.ikhanai.timesheet.domain.PeriodType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object Fmt {
    val fullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun hours(minutes: Int): String = Hours.format(minutes)

    fun hoursWithUnit(minutes: Int): String = "${Hours.format(minutes)} h"

    fun rangeLabel(type: PeriodType, range: DateRange): String = when (type) {
        PeriodType.DAY -> range.start.format(fullDate)
        PeriodType.WEEK -> {
            val sameYear = range.start.year == range.end.year
            val start = range.start.format(if (sameYear) shortDate else DateTimeFormatter.ofPattern("d MMM yyyy"))
            "$start – ${range.end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        }
        PeriodType.MONTH -> range.start.format(monthYear)
    }

    fun exportTitle(type: PeriodType, range: DateRange): String = "Timesheet · ${type.label} · ${rangeLabel(type, range)}"

    fun fileStem(range: DateRange): String =
        if (range.start == range.end) "timesheet_${range.start}" else "timesheet_${range.start}_to_${range.end}"
}

/** Material3 date pickers work in UTC-midnight millis. */
fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
