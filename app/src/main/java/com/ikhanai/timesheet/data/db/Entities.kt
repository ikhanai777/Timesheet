package com.ikhanai.timesheet.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ikhanai.timesheet.domain.BookingSystem
import com.ikhanai.timesheet.domain.DayType
import com.ikhanai.timesheet.domain.ProjectInfo
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "project",
    indices = [Index(value = ["projectNumber", "oracleCode"], unique = true)],
)
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val projectNumber: String,
    val oracleCode: String,
    val lastSystem: BookingSystem? = null,
    val archived: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toInfo() = ProjectInfo(id, name, projectNumber, oracleCode)
}

@Entity(tableName = "day_sheet")
data class DaySheet(
    @PrimaryKey val date: LocalDate,
    val dayType: DayType = DayType.WORKING,
    /** Snapshot of the Available Hours setting when the sheet was created (FR-6.1). */
    val availableMinutes: Int,
    val note: String? = null,
    val updatedAt: Instant = Instant.now(),
)

@Entity(
    tableName = "time_entry",
    foreignKeys = [
        ForeignKey(
            entity = DaySheet::class,
            parentColumns = ["date"],
            childColumns = ["date"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("date"), Index("projectId")],
)
data class TimeEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val projectId: Long,
    val minutes: Int,
    val system: BookingSystem,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

data class ProjectMinutes(
    val projectId: Long,
    val minutes: Int,
)
