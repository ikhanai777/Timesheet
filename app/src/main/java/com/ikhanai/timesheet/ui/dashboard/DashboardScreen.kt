package com.ikhanai.timesheet.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ikhanai.timesheet.domain.BookingSystem
import com.ikhanai.timesheet.domain.ExportFormat
import com.ikhanai.timesheet.domain.PeriodType
import com.ikhanai.timesheet.ui.AppViewModels
import com.ikhanai.timesheet.ui.Fmt
import com.ikhanai.timesheet.ui.components.DatePickerModal
import com.ikhanai.timesheet.ui.components.DonutChart
import com.ikhanai.timesheet.ui.components.HoursBar
import com.ikhanai.timesheet.ui.components.Segment
import com.ikhanai.timesheet.ui.components.StackedColumnChart
import com.ikhanai.timesheet.ui.components.SummaryCard
import com.ikhanai.timesheet.ui.components.SystemLegend
import com.ikhanai.timesheet.ui.export.ExportManager
import com.ikhanai.timesheet.ui.theme.LocalSeriesColors
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    onOpenDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    vm: DashboardViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Timesheet") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Export options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            val s = state
                            fun close() { menuOpen = false }
                            DropdownMenuItem(
                                text = { Text("Export CSV") },
                                enabled = s != null,
                                onClick = {
                                    close()
                                    s ?: return@DropdownMenuItem
                                    ExportManager.shareCsv(context, Fmt.fileStem(s.range), exportRows(s))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export PDF") },
                                enabled = s != null,
                                onClick = {
                                    close()
                                    s ?: return@DropdownMenuItem
                                    ExportManager.sharePdf(
                                        context,
                                        Fmt.fileStem(s.range),
                                        Fmt.exportTitle(s.periodType, s.range),
                                        s.summary,
                                        s.projects.mapValues { it.value.toInfo() },
                                        s.settings.overheadSystem,
                                        exportRows(s),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy summary") },
                                enabled = s != null,
                                onClick = {
                                    close()
                                    s ?: return@DropdownMenuItem
                                    val text = ExportFormat.clipboardSummary(
                                        Fmt.exportTitle(s.periodType, s.range),
                                        s.summary,
                                        s.projects.mapValues { it.value.toInfo() },
                                        s.settings.overheadSystem,
                                    )
                                    ExportManager.copyToClipboard(context, "Timesheet summary", text)
                                    Toast.makeText(context, "Summary copied", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val s = state
                    onOpenDay(if (s != null && s.periodType == PeriodType.DAY) s.anchor else LocalDate.now())
                },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add entry") },
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PeriodSelector(s, vm::setPeriodType, vm::shift) { pickDate = true } }
            item { SummaryGrid(s) }
            item { ChartCard(s) }
            if (s.periodType != PeriodType.DAY && s.summary.missingDays.isNotEmpty()) {
                item { MissingDaysCard(s.summary.missingDays, onOpenDay) }
            }
            item { ProjectTable(s, onOpenDay) }
        }
        if (pickDate) {
            DatePickerModal(
                initial = s.anchor,
                maxDate = if (s.settings.allowFutureDates) null else s.today,
                onPick = vm::setAnchor,
                onDismiss = { pickDate = false },
            )
        }
    }
}

private fun exportRows(s: DashboardUiState) =
    ExportFormat.rows(s.range, s.days, s.projects.mapValues { it.value.toInfo() }, s.settings.overheadSystem)

@Composable
private fun PeriodSelector(
    s: DashboardUiState,
    onType: (PeriodType) -> Unit,
    onShift: (Long) -> Unit,
    onPickDate: () -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodType.entries.forEach { type ->
                FilterChip(
                    selected = s.periodType == type,
                    onClick = { onType(type) },
                    label = { Text(type.label) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous ${s.periodType.label.lowercase()}")
            }
            TextButton(onClick = onPickDate, modifier = Modifier.weight(1f)) {
                Text(
                    Fmt.rangeLabel(s.periodType, s.range),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(onClick = { onShift(1) }, enabled = s.canGoNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next ${s.periodType.label.lowercase()}")
            }
        }
    }
}

@Composable
private fun SummaryGrid(s: DashboardUiState) {
    val colors = LocalSeriesColors.current
    val summary = s.summary
    val overheadSuffix = s.settings.overheadSystem?.let { " (incl. overhead)" }.orEmpty()
    val cards = buildList {
        add(Triple("Total hours", Fmt.hours(summary.totalMinutes), null))
        add(
            Triple(
                "NTIME" + if (s.settings.overheadSystem == BookingSystem.NTIME) overheadSuffix else "",
                Fmt.hours(summary.ntimeMinutes),
                colors.ntime,
            ),
        )
        add(
            Triple(
                "P6" + if (s.settings.overheadSystem == BookingSystem.P6) overheadSuffix else "",
                Fmt.hours(summary.p6Minutes),
                colors.p6,
            ),
        )
        add(Triple("Overhead", Fmt.hours(summary.overheadMinutes), colors.overhead))
        if (s.settings.allowOvertime || summary.overtimeMinutes > 0) {
            add(Triple("Overtime", Fmt.hours(summary.overtimeMinutes), colors.overtime))
        }
        add(Triple("Utilisation", summary.utilisation?.let { "${(it * 100).roundToInt()}%" } ?: "—", null))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (title, value, accent) ->
                    SummaryCard(title, value, Modifier.weight(1f), accent)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChartCard(s: DashboardUiState) {
    val colors = LocalSeriesColors.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (s.periodType) {
                PeriodType.DAY -> {
                    val b = s.buckets.first()
                    val overtime = s.summary.overtimeMinutes
                    Text("Day breakdown", style = MaterialTheme.typography.titleSmall)
                    HoursBar(
                        listOf(
                            Segment("NTIME", b.ntimeMinutes, colors.ntime),
                            Segment("P6", b.p6Minutes, colors.p6),
                            Segment("Overhead", b.overheadMinutes, colors.overhead),
                        ),
                        capacityMinutes = s.dayCapacity,
                    )
                    val loggedDay = s.days.any { it.date == s.range.start }
                    Text(
                        if (loggedDay) {
                            "NTIME ${Fmt.hours(b.ntimeMinutes)} · P6 ${Fmt.hours(b.p6Minutes)} · " +
                                "Overhead ${Fmt.hours(b.overheadMinutes)} = ${Fmt.hours(b.totalMinutes)} / " +
                                Fmt.hours(s.dayCapacity) +
                                if (overtime > 0) " (overtime ${Fmt.hours(overtime)})" else ""
                        } else {
                            "This day has not been logged."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SystemLegend()
                }
                PeriodType.WEEK, PeriodType.MONTH -> {
                    Text(
                        if (s.periodType == PeriodType.WEEK) "Hours per day" else "Hours per week",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    StackedColumnChart(
                        s.buckets,
                        referenceMinutes = if (s.periodType == PeriodType.WEEK) s.settings.availableMinutes else null,
                    )
                    SystemLegend()
                    if (s.periodType == PeriodType.MONTH && s.summary.totalMinutes > 0) {
                        HorizontalDivider()
                        Text("Project share", style = MaterialTheme.typography.titleSmall)
                        val slices = s.summary.projects.mapIndexed { i, p ->
                            Segment(
                                s.projects[p.projectId]?.name ?: "Unknown",
                                p.totalMinutes,
                                colors.projectPalette[i % colors.projectPalette.size],
                            )
                        } + listOfNotNull(
                            Segment("Overhead", s.summary.overheadMinutes, colors.overhead)
                                .takeIf { s.summary.overheadMinutes > 0 },
                        )
                        DonutChart(slices)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingDaysCard(days: List<LocalDate>, onOpenDay: (LocalDate) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val n = days.size
            Text(
                "$n ${if (n == 1) "day" else "days"} not logged",
                style = MaterialTheme.typography.titleSmall,
                color = LocalSeriesColors.current.missing,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEach { date ->
                    AssistChip(onClick = { onOpenDay(date) }, label = { Text(date.format(Fmt.dayMonth)) })
                }
            }
        }
    }
}

@Composable
private fun ProjectTable(s: DashboardUiState, onOpenDay: (LocalDate) -> Unit) {
    val summary = s.summary
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "By project",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (summary.totalMinutes == 0) {
                Text(
                    "No hours logged in this period yet.",
                    modifier = Modifier
                        .clickable { onOpenDay(if (s.periodType == PeriodType.DAY) s.anchor else s.today) }
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            TableRow("Project", null, "NTIME", "P6", "Total", header = true)
            HorizontalDivider()
            summary.projects.forEach { p ->
                val project = s.projects[p.projectId]
                TableRow(
                    project?.name ?: "Unknown project",
                    project?.let { "${it.projectNumber} · ${it.oracleCode}" },
                    Fmt.hours(p.ntimeMinutes),
                    Fmt.hours(p.p6Minutes),
                    Fmt.hours(p.totalMinutes),
                )
            }
            val overheadSystem = s.settings.overheadSystem
            TableRow(
                "Overhead",
                "Department Hours",
                if (overheadSystem == BookingSystem.NTIME) Fmt.hours(summary.overheadMinutes) else "—",
                if (overheadSystem == BookingSystem.P6) Fmt.hours(summary.overheadMinutes) else "—",
                Fmt.hours(summary.overheadMinutes),
            )
            HorizontalDivider()
            TableRow(
                "Total",
                null,
                Fmt.hours(summary.ntimeMinutes),
                Fmt.hours(summary.p6Minutes),
                Fmt.hours(summary.totalMinutes),
                header = true,
            )
        }
    }
}

@Composable
private fun TableRow(
    name: String,
    subtitle: String?,
    ntime: String,
    p6: String,
    total: String,
    header: Boolean = false,
) {
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        listOf(ntime, p6, total).forEach {
            Text(
                it,
                modifier = Modifier.padding(start = 4.dp).width(56.dp),
                textAlign = TextAlign.End,
                fontWeight = weight,
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
    }
}
