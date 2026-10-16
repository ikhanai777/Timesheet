package com.ikhanai.timesheet.domain

import java.time.LocalDate

enum class BookingSystem(val label: String) {
    NTIME("NTIME"),
    P6("P6"),
}

enum class DayType(val label: String) {
    WORKING("Working"),
    LEAVE("Leave"),
    PUBLIC_HOLIDAY("Public Holiday"),
    WEEKEND("Weekend"),
}

/** One project line of a day, reduced to what the calculations need. */
data class EntryLine(
    val projectId: Long,
    val minutes: Int,
    val system: BookingSystem,
)

/** A saved Day Sheet with its entries. Days without a Day Sheet have no record. */
data class DayRecord(
    val date: LocalDate,
    val dayType: DayType,
    val availableMinutes: Int,
    val entries: List<EntryLine>,
)
