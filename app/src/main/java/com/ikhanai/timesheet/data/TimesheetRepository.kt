package com.ikhanai.timesheet.data

import androidx.room.withTransaction
import com.ikhanai.timesheet.data.db.DaySheet
import com.ikhanai.timesheet.data.db.Project
import com.ikhanai.timesheet.data.db.ProjectMinutes
import com.ikhanai.timesheet.data.db.TimeEntry
import com.ikhanai.timesheet.data.db.TimesheetDatabase
import com.ikhanai.timesheet.domain.BookingSystem
import com.ikhanai.timesheet.domain.DateRange
import com.ikhanai.timesheet.domain.DayRecord
import com.ikhanai.timesheet.domain.DayType
import com.ikhanai.timesheet.domain.EntryLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate

data class BackupData(
    val projects: List<Project>,
    val daySheets: List<DaySheet>,
    val entries: List<TimeEntry>,
)

class TimesheetRepository(private val db: TimesheetDatabase) {

    private val projectDao = db.projectDao()
    private val daySheetDao = db.daySheetDao()
    private val entryDao = db.timeEntryDao()

    // ---- Observation ----

    fun observeProjects(): Flow<List<Project>> = projectDao.observeAll()

    fun observeProject(id: Long): Flow<Project?> = projectDao.observe(id)

    fun observeProjectMinutes(): Flow<List<ProjectMinutes>> = entryDao.observeProjectMinutes()

    fun observeEntriesForProject(projectId: Long): Flow<List<TimeEntry>> = entryDao.observeForProject(projectId)

    fun observeSheet(date: LocalDate): Flow<DaySheet?> = daySheetDao.observe(date)

    fun observeEntriesForDate(date: LocalDate): Flow<List<TimeEntry>> = entryDao.observeForDate(date)

    /** All saved days in [range] with their entries, ready for [com.ikhanai.timesheet.domain.TimesheetCalculator]. */
    fun observeDays(range: DateRange): Flow<List<DayRecord>> =
        combine(
            daySheetDao.observeRange(range.start, range.end),
            entryDao.observeRange(range.start, range.end),
        ) { sheets, entries ->
            val byDate = entries.groupBy { it.date }
            sheets.map { sheet ->
                DayRecord(
                    date = sheet.date,
                    dayType = sheet.dayType,
                    availableMinutes = sheet.availableMinutes,
                    entries = byDate[sheet.date].orEmpty().map { EntryLine(it.projectId, it.minutes, it.system, it.description) },
                )
            }
        }

    // ---- Projects ----

    suspend fun findProject(number: String, code: String): Project? = projectDao.findByKey(number, code)

    suspend fun createProject(name: String, number: String, code: String): Long =
        projectDao.insert(Project(name = name, projectNumber = number, oracleCode = code))

    /** Throws [android.database.sqlite.SQLiteConstraintException] if number + code is already used. */
    suspend fun updateProject(project: Project) = projectDao.update(project.copy(updatedAt = Instant.now()))

    suspend fun countEntries(projectId: Long): Int = entryDao.countForProject(projectId)

    suspend fun deleteProject(project: Project) = projectDao.delete(project)

    // ---- Days and entries ----

    suspend fun getSheet(date: LocalDate): DaySheet? = daySheetDao.get(date)

    suspend fun entriesFor(date: LocalDate): List<TimeEntry> = entryDao.getForDate(date)

    suspend fun latestDateWithEntriesBefore(date: LocalDate): LocalDate? = entryDao.latestDateBefore(date)

    /**
     * Saves one entry and creates the day's sheet if needed, in one transaction.
     * [mergeInto] adds the hours to an existing entry; [editing] replaces an existing entry.
     */
    suspend fun saveEntry(
        date: LocalDate,
        dayType: DayType,
        availableMinutes: Int,
        editing: TimeEntry?,
        mergeInto: TimeEntry?,
        projectId: Long,
        minutes: Int,
        system: BookingSystem,
        description: String,
    ) = db.withTransaction {
        ensureSheet(date, dayType, availableMinutes)
        val now = Instant.now()
        when {
            mergeInto != null -> entryDao.update(
                mergeInto.copy(
                    minutes = mergeInto.minutes + minutes,
                    description = mergeDescriptions(mergeInto.description, description),
                    updatedAt = now,
                ),
            )
            editing != null -> entryDao.update(
                editing.copy(
                    projectId = projectId,
                    minutes = minutes,
                    system = system,
                    description = description,
                    updatedAt = now,
                ),
            )
            else -> entryDao.insert(
                TimeEntry(date = date, projectId = projectId, minutes = minutes, system = system, description = description),
            )
        }
        projectDao.get(projectId)?.let {
            projectDao.update(it.copy(lastSystem = system, archived = false, updatedAt = now))
        }
    }

    suspend fun deleteEntry(entry: TimeEntry) = entryDao.delete(entry)

    /** Creates or updates the day's sheet. An existing sheet keeps its Available Hours snapshot. */
    suspend fun saveDay(date: LocalDate, dayType: DayType, availableMinutes: Int) = db.withTransaction {
        val existing = daySheetDao.get(date)
        daySheetDao.upsert(
            existing?.copy(dayType = dayType, updatedAt = Instant.now())
                ?: DaySheet(date = date, dayType = dayType, availableMinutes = availableMinutes),
        )
    }

    suspend fun copyEntries(from: LocalDate, to: LocalDate, dayType: DayType, availableMinutes: Int): Int =
        db.withTransaction {
            val source = entryDao.getForDate(from)
            ensureSheet(to, dayType, availableMinutes)
            entryDao.insertAll(
                source.map {
                    TimeEntry(
                        date = to,
                        projectId = it.projectId,
                        minutes = it.minutes,
                        system = it.system,
                        description = it.description,
                    )
                },
            )
            source.size
        }

    suspend fun applyAvailableFrom(from: LocalDate, minutes: Int): Int =
        daySheetDao.applyAvailableFrom(from, minutes, Instant.now())

    private fun mergeDescriptions(existing: String, added: String): String = when {
        added.isBlank() || existing.contains(added) -> existing
        existing.isBlank() -> added
        else -> "$existing; $added"
    }

    private suspend fun ensureSheet(date: LocalDate, dayType: DayType, availableMinutes: Int) {
        if (daySheetDao.get(date) == null) {
            daySheetDao.upsert(DaySheet(date = date, dayType = dayType, availableMinutes = availableMinutes))
        }
    }

    // ---- Backup ----

    suspend fun exportAll(): BackupData = db.withTransaction {
        BackupData(projectDao.getAll(), daySheetDao.getAll(), entryDao.getAll())
    }

    suspend fun replaceAll(data: BackupData) = db.withTransaction {
        entryDao.deleteAll()
        daySheetDao.deleteAll()
        projectDao.deleteAll()
        projectDao.insertAll(data.projects)
        daySheetDao.insertAll(data.daySheets)
        entryDao.insertAll(data.entries)
    }
}
