package com.ikhanai.timesheet.domain

import java.time.LocalDate

data class ProjectInfo(
    val id: Long,
    val name: String,
    val projectNumber: String,
    val oracleCode: String,
)

data class ExportRow(
    val date: LocalDate,
    val dayType: DayType,
    val projectName: String,
    val projectNumber: String,
    val oracleCode: String,
    val system: String,
    val minutes: Int,
)

/** Builds the rows for CSV/PDF export and the clipboard summary (spec FR-5). */
object ExportFormat {

    const val OVERHEAD_NAME = "Overhead"
    const val CSV_HEADER = "Date,Day Type,Project Name,Project Number,Oracle Code,System,Hours"

    /** One row per entry, plus one Overhead row per working day with overhead > 0. */
    fun rows(
        range: DateRange,
        days: List<DayRecord>,
        projects: Map<Long, ProjectInfo>,
        overheadSystem: BookingSystem?,
    ): List<ExportRow> {
        val rows = mutableListOf<ExportRow>()
        for (day in days.filter { it.date in range }.sortedBy { it.date }) {
            for (entry in day.entries) {
                val project = projects[entry.projectId]
                rows += ExportRow(
                    date = day.date,
                    dayType = day.dayType,
                    projectName = project?.name ?: "Unknown project",
                    projectNumber = project?.projectNumber.orEmpty(),
                    oracleCode = project?.oracleCode.orEmpty(),
                    system = entry.system.label,
                    minutes = entry.minutes,
                )
            }
            val overhead = TimesheetCalculator.overheadMinutes(day)
            if (overhead > 0) {
                rows += ExportRow(
                    date = day.date,
                    dayType = day.dayType,
                    projectName = OVERHEAD_NAME,
                    projectNumber = "",
                    oracleCode = "",
                    system = overheadSystem?.label.orEmpty(),
                    minutes = overhead,
                )
            }
        }
        return rows
    }

    fun toCsv(rows: List<ExportRow>): String = buildString {
        append(CSV_HEADER).append("\r\n")
        for (row in rows) {
            append(
                listOf(
                    row.date.toString(),
                    row.dayType.label,
                    row.projectName,
                    row.projectNumber,
                    row.oracleCode,
                    row.system,
                    Hours.plain(row.minutes),
                ).joinToString(",") { escapeCsv(it) },
            )
            append("\r\n")
        }
    }

    fun escapeCsv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Plain-text summary grouped by system, for pasting into NTIME / P6 (FR-5.3). */
    fun clipboardSummary(
        title: String,
        summary: PeriodSummary,
        projects: Map<Long, ProjectInfo>,
        overheadSystem: BookingSystem?,
        format: (Int) -> String = Hours::format,
    ): String = buildString {
        appendLine(title)
        for (system in BookingSystem.entries) {
            val total = if (system == BookingSystem.NTIME) summary.ntimeMinutes else summary.p6Minutes
            appendLine()
            appendLine("${system.label} (${format(total)} h)")
            val lines = summary.projects.filter {
                (if (system == BookingSystem.NTIME) it.ntimeMinutes else it.p6Minutes) > 0
            }
            for (line in lines) {
                val project = projects[line.projectId]
                val minutes = if (system == BookingSystem.NTIME) line.ntimeMinutes else line.p6Minutes
                val name = project?.let { "${it.name} · ${it.projectNumber} · ${it.oracleCode}" } ?: "Unknown project"
                appendLine("  $name: ${format(minutes)}")
            }
            if (overheadSystem == system && summary.overheadMinutes > 0) {
                appendLine("  $OVERHEAD_NAME: ${format(summary.overheadMinutes)}")
            }
            if (lines.isEmpty() && !(overheadSystem == system && summary.overheadMinutes > 0)) {
                appendLine("  —")
            }
        }
        appendLine()
        if (overheadSystem == null) appendLine("$OVERHEAD_NAME / Department Hours: ${format(summary.overheadMinutes)}")
        if (summary.overtimeMinutes > 0) appendLine("Overtime: ${format(summary.overtimeMinutes)}")
        append("Total: ${format(summary.totalMinutes)}")
    }
}
