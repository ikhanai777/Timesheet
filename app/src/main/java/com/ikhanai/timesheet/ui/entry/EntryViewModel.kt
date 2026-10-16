package com.ikhanai.timesheet.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikhanai.timesheet.data.AppSettings
import com.ikhanai.timesheet.data.SettingsRepository
import com.ikhanai.timesheet.data.TimesheetRepository
import com.ikhanai.timesheet.data.db.DaySheet
import com.ikhanai.timesheet.data.db.Project
import com.ikhanai.timesheet.data.db.TimeEntry
import com.ikhanai.timesheet.domain.BookingSystem
import com.ikhanai.timesheet.domain.DayType
import com.ikhanai.timesheet.domain.EntryValidation
import com.ikhanai.timesheet.domain.Hours
import com.ikhanai.timesheet.domain.TimesheetCalculator
import com.ikhanai.timesheet.ui.Fmt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EntryRow(val entry: TimeEntry, val project: Project?)

data class EntryUiState(
    val date: LocalDate,
    val today: LocalDate,
    val sheet: DaySheet?,
    val dayType: DayType,
    val availableMinutes: Int,
    val rows: List<EntryRow>,
    val projects: List<Project>,
    val settings: AppSettings,
) {
    val saved: Boolean get() = sheet != null
    val projectMinutes: Int get() = rows.sumOf { it.entry.minutes }
    val ntimeMinutes: Int get() = rows.filter { it.entry.system == BookingSystem.NTIME }.sumOf { it.entry.minutes }
    val p6Minutes: Int get() = rows.filter { it.entry.system == BookingSystem.P6 }.sumOf { it.entry.minutes }
    val overheadMinutes: Int get() = if (dayType == DayType.WORKING) maxOf(0, availableMinutes - projectMinutes) else 0
    val overtimeMinutes: Int get() = if (dayType == DayType.WORKING) maxOf(0, projectMinutes - availableMinutes) else 0
    val remainingMinutes: Int get() = maxOf(0, availableMinutes - projectMinutes)
    val canGoNext: Boolean get() = settings.allowFutureDates || date.isBefore(today)
}

data class EntryForm(
    val name: String,
    val number: String,
    val code: String,
    val description: String,
    val hours: String,
    val system: BookingSystem,
)

data class FormErrors(
    val name: String? = null,
    val number: String? = null,
    val code: String? = null,
    val description: String? = null,
    val hours: String? = null,
) {
    val any: Boolean get() = name != null || number != null || code != null || description != null || hours != null
}

sealed interface SubmitResult {
    data class Saved(val note: String?) : SubmitResult
    data class Invalid(val errors: FormErrors) : SubmitResult
    data class ConfirmRename(val existingName: String) : SubmitResult
    data class ConfirmMerge(val existingMinutes: Int) : SubmitResult
}

class EntryViewModel(
    private val repository: TimesheetRepository,
    private val settingsRepository: SettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val dateFlow = savedState.getStateFlow(DATE_KEY, LocalDate.now().toEpochDay())

    /** Day type chosen before the day has a sheet. Null means "default for this date". */
    private val pendingDayType = MutableStateFlow<DayType?>(null)

    private val messages = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = messages.receiveAsFlow()

    val state: StateFlow<EntryUiState?> =
        combine(dateFlow, settingsRepository.settings) { epochDay, settings -> LocalDate.ofEpochDay(epochDay) to settings }
            .flatMapLatest { (date, settings) ->
                combine(
                    repository.observeSheet(date),
                    repository.observeEntriesForDate(date),
                    repository.observeProjects(),
                    pendingDayType,
                ) { sheet, entries, projects, pending ->
                    val byId = projects.associateBy { it.id }
                    val defaultType = if (date.dayOfWeek in settings.weekendDays) DayType.WEEKEND else DayType.WORKING
                    EntryUiState(
                        date = date,
                        today = LocalDate.now(),
                        sheet = sheet,
                        dayType = sheet?.dayType ?: pending ?: defaultType,
                        availableMinutes = sheet?.availableMinutes ?: settings.availableMinutes,
                        rows = entries.map { EntryRow(it, byId[it.projectId]) },
                        projects = projects,
                        settings = settings,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDate(date: LocalDate) {
        pendingDayType.value = null
        savedState[DATE_KEY] = date.toEpochDay()
    }

    fun shiftDate(days: Long) {
        val s = state.value ?: return
        val target = s.date.plusDays(days)
        if (days > 0 && !s.settings.allowFutureDates && target.isAfter(s.today)) return
        setDate(target)
    }

    fun setDayType(type: DayType) {
        val s = state.value ?: return
        if (s.sheet == null) {
            pendingDayType.value = type
        } else {
            viewModelScope.launch { repository.saveDay(s.date, type, s.availableMinutes) }
        }
    }

    fun saveDay() {
        val s = state.value ?: return
        viewModelScope.launch {
            repository.saveDay(s.date, s.dayType, s.availableMinutes)
            messages.send("Day saved")
        }
    }

    fun deleteEntry(entry: TimeEntry) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
            messages.send("Entry deleted")
        }
    }

    fun copyPreviousDay() {
        val s = state.value ?: return
        viewModelScope.launch {
            val previous = repository.latestDateWithEntriesBefore(s.date)
            if (previous == null) {
                messages.send("No earlier day with entries to copy")
                return@launch
            }
            val source = repository.entriesFor(previous)
            val total = s.projectMinutes + source.sumOf { it.minutes }
            if (!s.settings.allowOvertime && total > s.availableMinutes) {
                messages.send("Copying would exceed ${Fmt.hours(s.availableMinutes)} available hours")
                return@launch
            }
            val count = repository.copyEntries(previous, s.date, s.dayType, s.availableMinutes)
            messages.send("Copied $count ${if (count == 1) "entry" else "entries"} from ${previous.format(Fmt.dayMonth)}")
        }
    }

    /**
     * Validates and saves an entry (FR-1, FR-2.2, FR-4.2). Returns a confirmation request when the
     * user must decide about renaming a project or merging with an existing entry; call again with
     * the decision.
     */
    suspend fun submitEntry(
        form: EntryForm,
        editingId: Long?,
        renameDecision: Boolean? = null,
        mergeDecision: Boolean? = null,
    ): SubmitResult {
        val s = state.value ?: return SubmitResult.Invalid(FormErrors(hours = "Please try again"))
        val name = form.name.trim()
        val number = form.number.trim()
        val code = form.code.trim()
        val description = form.description.trim()

        val parsed = Hours.parse(form.hours)
        val step = s.settings.stepMinutes
        val minutes = parsed?.let { Hours.roundToStep(it, step) }
        val editing = s.rows.firstOrNull { it.entry.id == editingId }?.entry
        val otherMinutes = s.projectMinutes - (editing?.minutes ?: 0)

        val hoursError = when {
            parsed == null -> "Enter the number of hours"
            minutes == null || minutes <= 0 -> "Hours must be greater than 0."
            else -> when (val v = TimesheetCalculator.validateEntry(
                s.availableMinutes, otherMinutes, minutes, s.settings.allowOvertime,
            )) {
                is EntryValidation.ExceedsAvailable ->
                    "Project hours (${Fmt.hours(v.totalMinutes)}) exceed available hours (${Fmt.hours(v.availableMinutes)})."
                EntryValidation.NotPositive -> "Hours must be greater than 0."
                EntryValidation.Ok -> null
            }
        }
        val errors = FormErrors(
            name = lengthError(name, "Project name", 100),
            number = lengthError(number, "Project number", 30),
            code = lengthError(code, "Oracle code", 30),
            description = lengthError(description, "Task description", MAX_DESCRIPTION),
            hours = hoursError,
        )
        if (errors.any) return SubmitResult.Invalid(errors)
        val finalMinutes = minutes!!

        val existing = repository.findProject(number, code)
        if (existing != null && existing.name != name && renameDecision == null) {
            return SubmitResult.ConfirmRename(existing.name)
        }
        val duplicate = existing?.let { project ->
            s.rows.firstOrNull { it.entry.projectId == project.id && it.entry.system == form.system }?.entry
        }
        if (editing == null && duplicate != null && mergeDecision == null) {
            return SubmitResult.ConfirmMerge(duplicate.minutes)
        }

        val projectId = when {
            existing == null -> repository.createProject(name, number, code)
            renameDecision == true -> {
                repository.updateProject(existing.copy(name = name))
                existing.id
            }
            else -> existing.id
        }
        repository.saveEntry(
            date = s.date,
            dayType = s.dayType,
            availableMinutes = s.availableMinutes,
            editing = editing,
            mergeInto = if (editing == null && mergeDecision == true) duplicate else null,
            projectId = projectId,
            minutes = finalMinutes,
            system = form.system,
            description = description,
        )
        val note = if (parsed != finalMinutes) "Hours rounded to ${Fmt.hours(finalMinutes)}" else null
        return SubmitResult.Saved(note)
    }

    private fun lengthError(value: String, label: String, max: Int): String? = when {
        value.isEmpty() -> "$label is required"
        value.length > max -> "$label must be at most $max characters"
        else -> null
    }

    companion object {
        const val DATE_KEY = "date"
        const val MAX_DESCRIPTION = 200
    }
}
