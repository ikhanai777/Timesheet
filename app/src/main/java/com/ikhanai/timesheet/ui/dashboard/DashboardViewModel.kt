package com.ikhanai.timesheet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikhanai.timesheet.data.AppSettings
import com.ikhanai.timesheet.data.SettingsRepository
import com.ikhanai.timesheet.data.TimesheetRepository
import com.ikhanai.timesheet.data.db.Project
import com.ikhanai.timesheet.domain.ChartBucket
import com.ikhanai.timesheet.domain.DateRange
import com.ikhanai.timesheet.domain.DayRecord
import com.ikhanai.timesheet.domain.PeriodSummary
import com.ikhanai.timesheet.domain.PeriodType
import com.ikhanai.timesheet.domain.Periods
import com.ikhanai.timesheet.domain.TimesheetCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DashboardUiState(
    val periodType: PeriodType,
    val anchor: LocalDate,
    val range: DateRange,
    val today: LocalDate,
    val settings: AppSettings,
    val summary: PeriodSummary,
    val buckets: List<ChartBucket>,
    val days: List<DayRecord>,
    val projects: Map<Long, Project>,
) {
    val canGoNext: Boolean
        get() = settings.allowFutureDates || !Periods.range(periodType, Periods.shift(periodType, anchor, 1), settings.weekStart).start.isAfter(today)

    /** Available hours for the day chart: the sheet's snapshot, else the current setting. */
    val dayCapacity: Int
        get() = days.firstOrNull { it.date == range.start }?.availableMinutes ?: settings.availableMinutes
}

class DashboardViewModel(
    private val repository: TimesheetRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val periodType = MutableStateFlow(PeriodType.WEEK)
    private val anchor = MutableStateFlow(LocalDate.now())

    val state: StateFlow<DashboardUiState?> =
        combine(periodType, anchor, settingsRepository.settings) { type, date, settings -> Triple(type, date, settings) }
            .flatMapLatest { (type, date, settings) ->
                val range = Periods.range(type, date, settings.weekStart)
                combine(repository.observeDays(range), repository.observeProjects()) { days, projects ->
                    val today = LocalDate.now()
                    DashboardUiState(
                        periodType = type,
                        anchor = date,
                        range = range,
                        today = today,
                        settings = settings,
                        summary = TimesheetCalculator.summarize(
                            range, days, settings.overheadSystem, settings.weekendDays, today,
                        ),
                        buckets = TimesheetCalculator.chartBuckets(type, range, days, settings.weekStart),
                        days = days,
                        projects = projects.associateBy { it.id },
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPeriodType(type: PeriodType) {
        periodType.value = type
    }

    fun shift(delta: Long) {
        anchor.value = Periods.shift(periodType.value, anchor.value, delta)
    }

    fun setAnchor(date: LocalDate) {
        anchor.value = date
    }
}
