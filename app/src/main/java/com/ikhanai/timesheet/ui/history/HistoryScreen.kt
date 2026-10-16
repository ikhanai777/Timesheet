package com.ikhanai.timesheet.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ikhanai.timesheet.data.AppSettings
import com.ikhanai.timesheet.data.SettingsRepository
import com.ikhanai.timesheet.data.TimesheetRepository
import com.ikhanai.timesheet.domain.DayRecord
import com.ikhanai.timesheet.domain.DayType
import com.ikhanai.timesheet.domain.Periods
import com.ikhanai.timesheet.domain.TimesheetCalculator
import com.ikhanai.timesheet.ui.AppViewModels
import com.ikhanai.timesheet.ui.Fmt
import com.ikhanai.timesheet.ui.components.LegendItem
import com.ikhanai.timesheet.ui.theme.LocalSeriesColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class DayStatus { COMPLETE, NOT_LOGGED, NON_WORKING, NONE }

data class HistoryUiState(
    val month: YearMonth,
    val today: LocalDate,
    val settings: AppSettings,
    val days: Map<LocalDate, DayRecord>,
) {
    fun status(date: LocalDate): DayStatus {
        val record = days[date]
        return when {
            record != null && record.dayType == DayType.WORKING -> DayStatus.COMPLETE
            record != null -> DayStatus.NON_WORKING
            date.isAfter(today) || date.dayOfWeek in settings.weekendDays -> DayStatus.NONE
            else -> DayStatus.NOT_LOGGED
        }
    }
}

class HistoryViewModel(
    repository: TimesheetRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<HistoryUiState?> =
        combine(month, settingsRepository.settings) { m, s -> m to s }
            .flatMapLatest { (m, s) ->
                repository.observeDays(Periods.month(m.atDay(1))).map { days ->
                    HistoryUiState(m, LocalDate.now(), s, days.associateBy { it.date })
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shift(months: Long) {
        month.value = month.value.plusMonths(months)
    }
}

@Composable
fun HistoryScreen(
    onOpenDay: (LocalDate) -> Unit,
    vm: HistoryViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("History") }) },
    ) { padding ->
        val s = state ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shift(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
                }
                Text(
                    s.month.atDay(1).format(Fmt.monthYear),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { vm.shift(1) },
                    enabled = s.settings.allowFutureDates || s.month.isBefore(YearMonth.from(s.today)),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                }
            }
            Spacer(Modifier.height(8.dp))
            CalendarGrid(s, onOpenDay)
            Spacer(Modifier.height(16.dp))
            Legend()
            Spacer(Modifier.height(16.dp))
            MonthTotals(s)
        }
    }
}

@Composable
private fun CalendarGrid(s: HistoryUiState, onOpenDay: (LocalDate) -> Unit) {
    val colors = LocalSeriesColors.current
    val weekStart = s.settings.weekStart
    val headers = (0L until 7L).map { weekStart.plus(it) }
    val first = s.month.atDay(1)
    val leading = (first.dayOfWeek.value - weekStart.value + 7) % 7
    val cells: List<LocalDate?> = List(leading) { null } + (1..s.month.lengthOfMonth()).map { s.month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            headers.forEach {
                Text(
                    it.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val status = s.status(date)
                        val record = s.days[date]
                        val selectable = s.settings.allowFutureDates || !date.isAfter(s.today)
                        val dotColor: Color? = when (status) {
                            DayStatus.COMPLETE -> colors.complete
                            DayStatus.NOT_LOGGED -> colors.missing
                            DayStatus.NON_WORKING -> colors.leave
                            DayStatus.NONE -> null
                        }
                        val statusText = when (status) {
                            DayStatus.COMPLETE -> "logged"
                            DayStatus.NOT_LOGGED -> "not logged"
                            DayStatus.NON_WORKING -> record?.dayType?.label ?: ""
                            DayStatus.NONE -> ""
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (date == s.today) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable(enabled = selectable) { onOpenDay(date) }
                                .semantics { contentDescription = "${date.format(Fmt.fullDate)} $statusText" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    fontWeight = if (date == s.today) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectable) Color.Unspecified else MaterialTheme.colorScheme.outline,
                                )
                                Box(
                                    Modifier
                                        .padding(top = 2.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor ?: Color.Transparent),
                                )
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val colors = LocalSeriesColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(colors.complete, "Logged")
        LegendItem(colors.missing, "Not logged")
        LegendItem(colors.leave, "Leave / holiday")
    }
}

@Composable
private fun MonthTotals(s: HistoryUiState) {
    val days = s.days.values.toList()
    val project = days.sumOf { TimesheetCalculator.projectMinutes(it) }
    val overhead = days.sumOf { TimesheetCalculator.overheadMinutes(it) }
    val logged = days.count { it.dayType == DayType.WORKING }
    Text(
        "$logged working days logged · Projects ${Fmt.hours(project)} h · Overhead ${Fmt.hours(overhead)} h",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
}
