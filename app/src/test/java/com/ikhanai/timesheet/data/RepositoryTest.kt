package com.ikhanai.timesheet.data

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ikhanai.timesheet.data.db.TimesheetDatabase
import com.ikhanai.timesheet.domain.BookingSystem
import com.ikhanai.timesheet.domain.DayType
import com.ikhanai.timesheet.domain.Periods
import com.ikhanai.timesheet.domain.TimesheetCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RepositoryTest {

    private lateinit var db: TimesheetDatabase
    private lateinit var repo: TimesheetRepository
    private val day = LocalDate.of(2026, 10, 15)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TimesheetDatabase::class.java)
            .build()
        repo = TimesheetRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addEntry(projectId: Long, minutes: Int, system: BookingSystem, date: LocalDate = day) =
        repo.saveEntry(date, DayType.WORKING, 480, null, null, projectId, minutes, system, "Task")

    @Test
    fun `saving an entry creates the day sheet and observeDays feeds the calculator`() = runBlocking {
        val a = repo.createProject("Project A", "PRJ-2041", "4100.22.07")
        val b = repo.createProject("Project B", "PRJ-2055", "4100.31.02")
        addEntry(a, 240, BookingSystem.NTIME)
        addEntry(b, 180, BookingSystem.P6)

        assertEquals(480, repo.getSheet(day)!!.availableMinutes)
        val days = repo.observeDays(Periods.day(day)).first()
        assertEquals(1, days.size)
        assertEquals("Task", days.single().entries.first().description)
        assertEquals(60, TimesheetCalculator.overheadMinutes(days.single()))
        assertEquals(BookingSystem.P6, db.projectDao().get(b)!!.lastSystem)
    }

    @Test
    fun `migration from version 1 keeps entries and adds an empty description`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { v1 ->
            v1.execSQL("CREATE TABLE `project` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `projectNumber` TEXT NOT NULL, `oracleCode` TEXT NOT NULL, `lastSystem` TEXT, `archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
            v1.execSQL("CREATE UNIQUE INDEX `index_project_projectNumber_oracleCode` ON `project` (`projectNumber`, `oracleCode`)")
            v1.execSQL("CREATE TABLE `day_sheet` (`date` INTEGER NOT NULL, `dayType` TEXT NOT NULL, `availableMinutes` INTEGER NOT NULL, `note` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))")
            v1.execSQL("CREATE TABLE `time_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, `projectId` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, `system` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`date`) REFERENCES `day_sheet`(`date`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`projectId`) REFERENCES `project`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            v1.execSQL("CREATE INDEX `index_time_entry_date` ON `time_entry` (`date`)")
            v1.execSQL("CREATE INDEX `index_time_entry_projectId` ON `time_entry` (`projectId`)")
            v1.execSQL("INSERT INTO project VALUES (1, 'A', 'PRJ-1', '100', 'NTIME', 0, 0, 0)")
            v1.execSQL("INSERT INTO day_sheet VALUES (${day.toEpochDay()}, 'WORKING', 480, NULL, 0)")
            v1.execSQL("INSERT INTO time_entry VALUES (1, ${day.toEpochDay()}, 1, 240, 'NTIME', 0, 0)")
            v1.version = 1
        }
        val migrated = Room.databaseBuilder(context, TimesheetDatabase::class.java, name)
            .addMigrations(TimesheetDatabase.MIGRATION_1_2)
            .build()
        runBlocking {
            val entry = migrated.timeEntryDao().getForDate(day).single()
            assertEquals(240, entry.minutes)
            assertEquals("", entry.description)
        }
        migrated.close()
    }

    @Test
    fun `project number plus oracle code is unique`() = runBlocking {
        repo.createProject("A", "PRJ-1", "100")
        try {
            repo.createProject("A again", "PRJ-1", "100")
            fail("Expected unique constraint violation")
        } catch (expected: SQLiteConstraintException) {
        }
        // Same number with a different code is a different project.
        repo.createProject("A other code", "PRJ-1", "200")
        assertNotNull(repo.findProject("PRJ-1", "200"))
    }

    @Test
    fun `projects with entries cannot be deleted`() = runBlocking {
        val a = repo.createProject("A", "PRJ-1", "100")
        addEntry(a, 60, BookingSystem.NTIME)
        assertEquals(1, repo.countEntries(a))
        try {
            repo.deleteProject(db.projectDao().get(a)!!)
            fail("Expected foreign key violation")
        } catch (expected: SQLiteConstraintException) {
        }
    }

    @Test
    fun `merge adds to the existing entry and edit replaces it`() = runBlocking {
        val a = repo.createProject("A", "PRJ-1", "100")
        addEntry(a, 120, BookingSystem.NTIME)
        val existing = repo.entriesFor(day).single()
        repo.saveEntry(day, DayType.WORKING, 480, null, existing, a, 60, BookingSystem.NTIME, "Site visit")
        assertEquals(180, repo.entriesFor(day).single().minutes)
        assertEquals("Task; Site visit", repo.entriesFor(day).single().description)

        val merged = repo.entriesFor(day).single()
        repo.saveEntry(day, DayType.WORKING, 480, merged, null, a, 300, BookingSystem.P6, "Design review")
        val edited = repo.entriesFor(day).single()
        assertEquals(300, edited.minutes)
        assertEquals(BookingSystem.P6, edited.system)
        assertEquals("Design review", edited.description)
    }

    @Test
    fun `saveDay keeps the available hours snapshot and applyAvailableFrom updates later days`() = runBlocking {
        repo.saveDay(day, DayType.WORKING, 480)
        repo.saveDay(day, DayType.LEAVE, 540)
        val sheet = repo.getSheet(day)!!
        assertEquals(480, sheet.availableMinutes)
        assertEquals(DayType.LEAVE, sheet.dayType)

        repo.saveDay(day.plusDays(1), DayType.WORKING, 480)
        assertEquals(1, repo.applyAvailableFrom(day.plusDays(1), 540))
        assertEquals(480, repo.getSheet(day)!!.availableMinutes)
        assertEquals(540, repo.getSheet(day.plusDays(1))!!.availableMinutes)
    }

    @Test
    fun `day type change keeps entries`() = runBlocking {
        val a = repo.createProject("A", "PRJ-1", "100")
        addEntry(a, 60, BookingSystem.NTIME)
        repo.saveDay(day, DayType.LEAVE, 480)
        assertEquals(1, repo.entriesFor(day).size)
    }

    @Test
    fun `copy previous day duplicates entries`() = runBlocking {
        val a = repo.createProject("A", "PRJ-1", "100")
        addEntry(a, 240, BookingSystem.NTIME, day.minusDays(3))
        addEntry(a, 60, BookingSystem.P6, day.minusDays(3))
        assertEquals(day.minusDays(3), repo.latestDateWithEntriesBefore(day))
        assertNull(repo.latestDateWithEntriesBefore(day.minusDays(3)))
        assertEquals(2, repo.copyEntries(day.minusDays(3), day, DayType.WORKING, 480))
        assertEquals(listOf(240, 60), repo.entriesFor(day).map { it.minutes })
        assertEquals(listOf("Task", "Task"), repo.entriesFor(day).map { it.description })
    }

    @Test
    fun `backup round trip restores everything`() = runBlocking {
        val a = repo.createProject("A, \"quoted\"", "PRJ-1", "100")
        addEntry(a, 240, BookingSystem.NTIME)
        repo.saveDay(day.plusDays(1), DayType.PUBLIC_HOLIDAY, 480)
        val json = BackupSerializer.toJson(repo.exportAll())

        repo.replaceAll(BackupData(emptyList(), emptyList(), emptyList()))
        assertTrue(repo.exportAll().projects.isEmpty())

        repo.replaceAll(BackupSerializer.fromJson(json))
        val restored = repo.exportAll()
        assertEquals("A, \"quoted\"", restored.projects.single().name)
        assertEquals(2, restored.daySheets.size)
        assertEquals(240, restored.entries.single().minutes)
        assertEquals("Task", restored.entries.single().description)
        assertEquals(DayType.PUBLIC_HOLIDAY, repo.getSheet(day.plusDays(1))!!.dayType)
    }
}
