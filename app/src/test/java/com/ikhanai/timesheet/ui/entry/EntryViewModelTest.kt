package com.ikhanai.timesheet.ui.entry

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ikhanai.timesheet.data.SettingsRepository
import com.ikhanai.timesheet.data.TimesheetRepository
import com.ikhanai.timesheet.data.db.TimesheetDatabase
import com.ikhanai.timesheet.domain.BookingSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Exercises the entry rules end to end against a real (in-memory) database. Kept to one test
 * method because the DataStore delegate is process-wide.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EntryViewModelTest {

    private lateinit var db: TimesheetDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TimesheetDatabase::class.java)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun EntryViewModel.awaitState(predicate: (EntryUiState) -> Boolean): EntryUiState =
        withTimeout(5_000) { state.first { it != null && predicate(it) }!! }

    @Test
    fun `entry rules - overhead, validation, live update, merge and rename`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repo = TimesheetRepository(db)
        val date = LocalDate.of(2026, 10, 15)
        val vm = EntryViewModel(repo, SettingsRepository(context), SavedStateHandle(mapOf("date" to date.toEpochDay())))
        val collector = launch(Dispatchers.Default) { vm.state.collect {} }

        val empty = vm.awaitState { true }
        assertEquals(false, empty.saved)
        assertEquals(480, empty.overheadMinutes)

        // AC1: A 4h NTIME + B 3h P6 -> 1h overhead.
        val a = EntryForm("Project A", "PRJ-2041", "4100.22.07", "4", BookingSystem.NTIME)
        val b = EntryForm("Project B", "PRJ-2055", "4100.31.02", "3", BookingSystem.P6)
        assertEquals(SubmitResult.Saved(null), vm.submitEntry(a, null))
        vm.awaitState { it.rows.size == 1 }
        assertEquals(SubmitResult.Saved(null), vm.submitEntry(b, null))
        var s = vm.awaitState { it.rows.size == 2 }
        assertTrue(s.saved)
        assertEquals(240, s.ntimeMinutes)
        assertEquals(180, s.p6Minutes)
        assertEquals(60, s.overheadMinutes)

        // AC3: 7h logged; 1.5h more would exceed 8h.
        val tooMuch = vm.submitEntry(a.copy(name = "Project C", number = "PRJ-3", hours = "1.5"), null)
        assertTrue(tooMuch is SubmitResult.Invalid)
        assertEquals(
            "Project hours (8.5) exceed available hours (8.0).",
            (tooMuch as SubmitResult.Invalid).errors.hours,
        )

        // Required fields and rounding to the 0.5 h step.
        val blank = vm.submitEntry(EntryForm("", "", "", "", BookingSystem.NTIME), null) as SubmitResult.Invalid
        assertTrue(blank.errors.name != null && blank.errors.number != null && blank.errors.code != null)
        assertEquals("Enter the number of hours", blank.errors.hours)

        // FR-4.2: same number + code with a different name asks to rename.
        val renamed = a.copy(name = "Project A (renamed)", hours = "0.4")
        assertEquals(SubmitResult.ConfirmRename("Project A"), vm.submitEntry(renamed, null))
        // Same project + same system asks to merge.
        assertEquals(SubmitResult.ConfirmMerge(240), vm.submitEntry(renamed, null, renameDecision = true))
        val merged = vm.submitEntry(renamed, null, renameDecision = true, mergeDecision = true)
        assertEquals(SubmitResult.Saved("Hours rounded to 0.5"), merged)
        s = vm.awaitState { st -> st.rows.any { it.entry.minutes == 270 } }
        assertEquals(2, s.rows.size)
        assertEquals("Project A (renamed)", s.rows.first { it.entry.minutes == 270 }.project!!.name)
        assertEquals(30, s.overheadMinutes)

        // AC7: editing an entry immediately updates overhead (A 4.5h -> 5h: overhead 0.5 -> 0).
        val entryA = s.rows.first { it.entry.minutes == 270 }.entry
        vm.submitEntry(renamed.copy(hours = "5"), entryA.id)
        s = vm.awaitState { it.overheadMinutes == 0 }
        assertEquals(480, s.projectMinutes)

        collector.cancel()
    }
}
