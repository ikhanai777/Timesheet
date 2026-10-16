package com.ikhanai.timesheet.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface ProjectDao {
    @Query("SELECT * FROM project ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM project WHERE id = :id")
    fun observe(id: Long): Flow<Project?>

    @Query("SELECT * FROM project WHERE id = :id")
    suspend fun get(id: Long): Project?

    @Query("SELECT * FROM project WHERE projectNumber = :number AND oracleCode = :code LIMIT 1")
    suspend fun findByKey(number: String, code: String): Project?

    @Query("SELECT * FROM project")
    suspend fun getAll(): List<Project>

    @Insert
    suspend fun insert(project: Project): Long

    @Insert
    suspend fun insertAll(projects: List<Project>)

    @Update
    suspend fun update(project: Project)

    @Delete
    suspend fun delete(project: Project)

    @Query("DELETE FROM project")
    suspend fun deleteAll()
}

@Dao
interface DaySheetDao {
    @Query("SELECT * FROM day_sheet WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DaySheet>>

    @Query("SELECT * FROM day_sheet WHERE date = :date")
    fun observe(date: LocalDate): Flow<DaySheet?>

    @Query("SELECT * FROM day_sheet WHERE date = :date")
    suspend fun get(date: LocalDate): DaySheet?

    @Query("SELECT * FROM day_sheet")
    suspend fun getAll(): List<DaySheet>

    /** Insert-or-update; never REPLACE, which would cascade-delete the day's entries. */
    @Upsert
    suspend fun upsert(sheet: DaySheet)

    @Insert
    suspend fun insertAll(sheets: List<DaySheet>)

    @Query("UPDATE day_sheet SET availableMinutes = :minutes, updatedAt = :now WHERE date >= :from")
    suspend fun applyAvailableFrom(from: LocalDate, minutes: Int, now: Instant): Int

    @Query("DELETE FROM day_sheet")
    suspend fun deleteAll()
}

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entry WHERE date BETWEEN :from AND :to ORDER BY date, id")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<TimeEntry>>

    @Query("SELECT * FROM time_entry WHERE date = :date ORDER BY id")
    fun observeForDate(date: LocalDate): Flow<List<TimeEntry>>

    @Query("SELECT * FROM time_entry WHERE date = :date ORDER BY id")
    suspend fun getForDate(date: LocalDate): List<TimeEntry>

    @Query("SELECT * FROM time_entry WHERE projectId = :projectId ORDER BY date")
    fun observeForProject(projectId: Long): Flow<List<TimeEntry>>

    @Query("SELECT projectId, SUM(minutes) AS minutes FROM time_entry GROUP BY projectId")
    fun observeProjectMinutes(): Flow<List<ProjectMinutes>>

    @Query("SELECT COUNT(*) FROM time_entry WHERE projectId = :projectId")
    suspend fun countForProject(projectId: Long): Int

    @Query("SELECT MAX(date) FROM time_entry WHERE date < :date")
    suspend fun latestDateBefore(date: LocalDate): LocalDate?

    @Query("SELECT * FROM time_entry")
    suspend fun getAll(): List<TimeEntry>

    @Insert
    suspend fun insert(entry: TimeEntry): Long

    @Insert
    suspend fun insertAll(entries: List<TimeEntry>)

    @Update
    suspend fun update(entry: TimeEntry)

    @Delete
    suspend fun delete(entry: TimeEntry)

    @Query("DELETE FROM time_entry")
    suspend fun deleteAll()
}
