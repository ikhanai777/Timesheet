package com.ikhanai.timesheet.ui.projects

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikhanai.timesheet.data.SettingsRepository
import com.ikhanai.timesheet.data.TimesheetRepository
import com.ikhanai.timesheet.data.db.Project
import com.ikhanai.timesheet.domain.BookingSystem
import com.ikhanai.timesheet.domain.Periods
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ProjectListItem(val project: Project, val minutes: Int)

data class ProjectsUiState(
    val query: String,
    val showArchived: Boolean,
    val items: List<ProjectListItem>,
)

/** Validates name / number / code. Returns an error message or null. */
fun validateProjectFields(name: String, number: String, code: String): String? = when {
    name.isBlank() || number.isBlank() || code.isBlank() -> "Name, project number and Oracle code are required"
    name.trim().length > 100 -> "Project name must be at most 100 characters"
    number.trim().length > 30 || code.trim().length > 30 -> "Number and Oracle code must be at most 30 characters"
    else -> null
}

class ProjectsViewModel(private val repository: TimesheetRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val showArchived = MutableStateFlow(false)

    val state: StateFlow<ProjectsUiState> =
        combine(
            repository.observeProjects(),
            repository.observeProjectMinutes(),
            query,
            showArchived,
        ) { projects, minutes, q, archived ->
            val byId = minutes.associate { it.projectId to it.minutes }
            val items = projects
                .filter { archived || !it.archived }
                .filter {
                    q.isBlank() || it.name.contains(q, true) || it.projectNumber.contains(q, true) ||
                        it.oracleCode.contains(q, true)
                }
                .map { ProjectListItem(it, byId[it.id] ?: 0) }
            ProjectsUiState(q, archived, items)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState("", false, emptyList()))

    fun setQuery(value: String) {
        query.value = value
    }

    fun setShowArchived(value: Boolean) {
        showArchived.value = value
    }

    /** Returns an error message, or null on success. */
    suspend fun addProject(name: String, number: String, code: String): String? {
        validateProjectFields(name, number, code)?.let { return it }
        if (repository.findProject(number.trim(), code.trim()) != null) {
            return "A project with this number and Oracle code already exists"
        }
        repository.createProject(name.trim(), number.trim(), code.trim())
        return null
    }
}

data class WeekBar(val label: String, val ntimeMinutes: Int, val p6Minutes: Int)

data class ProjectDetailUiState(
    val project: Project,
    val totalMinutes: Int,
    val ntimeMinutes: Int,
    val p6Minutes: Int,
    val entryCount: Int,
    val lastWeeks: List<WeekBar>,
)

class ProjectDetailViewModel(
    private val repository: TimesheetRepository,
    settingsRepository: SettingsRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val projectId: Long = checkNotNull(savedState["projectId"])

    val state: StateFlow<ProjectDetailUiState?> =
        combine(
            repository.observeProject(projectId),
            repository.observeEntriesForProject(projectId),
            settingsRepository.settings,
        ) { project, entries, settings ->
            project ?: return@combine null
            val labels = DateTimeFormatter.ofPattern("d/M")
            val today = LocalDate.now()
            val weeks = (11 downTo 0).map { back ->
                val range = Periods.week(today.minusWeeks(back.toLong()), settings.weekStart)
                val inWeek = entries.filter { it.date in range }
                WeekBar(
                    range.start.format(labels),
                    inWeek.filter { it.system == BookingSystem.NTIME }.sumOf { it.minutes },
                    inWeek.filter { it.system == BookingSystem.P6 }.sumOf { it.minutes },
                )
            }
            ProjectDetailUiState(
                project = project,
                totalMinutes = entries.sumOf { it.minutes },
                ntimeMinutes = entries.filter { it.system == BookingSystem.NTIME }.sumOf { it.minutes },
                p6Minutes = entries.filter { it.system == BookingSystem.P6 }.sumOf { it.minutes },
                entryCount = entries.size,
                lastWeeks = weeks,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Returns an error message, or null on success. */
    suspend fun update(name: String, number: String, code: String): String? {
        val project = state.value?.project ?: return "Project not found"
        validateProjectFields(name, number, code)?.let { return it }
        return try {
            repository.updateProject(project.copy(name = name.trim(), projectNumber = number.trim(), oracleCode = code.trim()))
            null
        } catch (e: SQLiteConstraintException) {
            "A project with this number and Oracle code already exists"
        }
    }

    suspend fun setArchived(archived: Boolean) {
        val project = state.value?.project ?: return
        repository.updateProject(project.copy(archived = archived))
    }

    /** FR-4 / spec 7: projects with entries cannot be deleted. Returns true if deleted. */
    suspend fun delete(): Boolean {
        val project = state.value?.project ?: return false
        if (repository.countEntries(project.id) > 0) return false
        repository.deleteProject(project)
        return true
    }
}
