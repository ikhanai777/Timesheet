package com.ikhanai.timesheet.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ikhanai.timesheet.data.db.TimeEntry
import com.ikhanai.timesheet.domain.DayType
import com.ikhanai.timesheet.ui.AppViewModels
import com.ikhanai.timesheet.ui.Fmt
import com.ikhanai.timesheet.ui.components.DatePickerModal
import com.ikhanai.timesheet.ui.components.HoursBar
import com.ikhanai.timesheet.ui.components.Segment
import com.ikhanai.timesheet.ui.components.SystemBadge
import com.ikhanai.timesheet.ui.components.SystemLegend
import com.ikhanai.timesheet.ui.theme.LocalSeriesColors
import kotlinx.coroutines.launch

private const val NEW_ENTRY = -1L

@Composable
fun EntryScreen(
    onBack: (() -> Unit)?,
    vm: EntryViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // null = closed, NEW_ENTRY = adding, otherwise the id of the entry being edited.
    var sheetFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TimeEntry?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Day entry") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DateHeader(
                    s,
                    onPrev = { vm.shiftDate(-1) },
                    onNext = { vm.shiftDate(1) },
                    onPickDate = { pickDate = true },
                    onDayType = vm::setDayType,
                )
            }
            item { DayBar(s) }
            if (s.dayType != DayType.WORKING && s.rows.isNotEmpty()) {
                item {
                    Notice(
                        Icons.Filled.Warning,
                        "This day is marked as ${s.dayType.label}. Entries are kept, but no overhead is counted.",
                    )
                }
            }
            if (!s.saved) {
                item {
                    Notice(
                        Icons.Filled.Info,
                        "This day isn't logged yet. Add a project, or tap Save day to log " +
                            if (s.dayType == DayType.WORKING) "a full day of overhead." else "it as ${s.dayType.label}.",
                    )
                }
            }
            items(s.rows, key = { it.entry.id }) { row ->
                EntryCard(row, onEdit = { sheetFor = row.entry.id }, onDelete = { confirmDelete = row.entry })
            }
            item { OverheadRow(s) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { sheetFor = NEW_ENTRY }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add project")
                        }
                        OutlinedButton(
                            onClick = vm::copyPreviousDay,
                            enabled = s.rows.isEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.ContentCopy, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Copy previous", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (!s.saved) {
                        Button(onClick = vm::saveDay, modifier = Modifier.fillMaxWidth()) { Text("Save day") }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Check, null, tint = LocalSeriesColors.current.complete)
                            Spacer(Modifier.width(6.dp))
                            Text("Day logged · changes save automatically", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        val editingId = sheetFor
        if (editingId != null) {
            val editing = s.rows.firstOrNull { it.entry.id == editingId }
            EntrySheet(
                editing = editing,
                projects = s.projects,
                remainingMinutes = s.remainingMinutes + (editing?.entry?.minutes ?: 0),
                stepMinutes = s.settings.stepMinutes,
                allowOvertime = s.settings.allowOvertime,
                onSubmit = { form, rename, merge ->
                    vm.submitEntry(form, editing?.entry?.id, rename, merge)
                },
                onDone = { note ->
                    sheetFor = null
                    if (note != null) scope.launch { snackbar.showSnackbar(note) }
                },
                onDismiss = { sheetFor = null },
            )
        }
        if (pickDate) {
            DatePickerModal(
                initial = s.date,
                maxDate = if (s.settings.allowFutureDates) null else s.today,
                onPick = vm::setDate,
                onDismiss = { pickDate = false },
            )
        }
        confirmDelete?.let { entry ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text("Delete entry?") },
                text = { Text("${Fmt.hoursWithUnit(entry.minutes)} in ${entry.system.label} will be removed.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteEntry(entry)
                        confirmDelete = null
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun DateHeader(
    s: EntryUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
    onDayType: (DayType) -> Unit,
) {
    var typeMenu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
        TextButton(onClick = onPickDate, modifier = Modifier.weight(1f)) {
            Text(s.date.format(Fmt.fullDate), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        IconButton(onClick = onNext, enabled = s.canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
        }
    }
    Box {
        OutlinedButton(onClick = { typeMenu = true }) {
            Text("Day type: ${s.dayType.label}")
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
            DayType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        typeMenu = false
                        onDayType(type)
                    },
                )
            }
        }
    }
}

@Composable
private fun DayBar(s: EntryUiState) {
    val colors = LocalSeriesColors.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HoursBar(
                listOf(
                    Segment("NTIME", s.ntimeMinutes, colors.ntime),
                    Segment("P6", s.p6Minutes, colors.p6),
                    Segment("Overhead", s.overheadMinutes, colors.overhead),
                ),
                capacityMinutes = s.availableMinutes,
            )
            Text(
                "Projects ${Fmt.hours(s.projectMinutes)} · Overhead ${Fmt.hours(s.overheadMinutes)} / " +
                    "${Fmt.hours(s.availableMinutes)} available" +
                    if (s.overtimeMinutes > 0) " · Overtime ${Fmt.hours(s.overtimeMinutes)}" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            SystemLegend()
        }
    }
}

@Composable
private fun EntryCard(row: EntryRow, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.project?.name ?: "Unknown project",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.project?.let { "${it.projectNumber} · ${it.oracleCode}" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(Fmt.hoursWithUnit(row.entry.minutes), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    SystemBadge(row.entry.system)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit entry") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete entry") }
        }
    }
}

@Composable
private fun OverheadRow(s: EntryUiState) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Overhead (auto)", style = MaterialTheme.typography.titleSmall)
                Text(
                    s.settings.overheadSystem?.let { "Department Hours · booked in ${it.label}" } ?: "Department Hours",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Fmt.hoursWithUnit(s.overheadMinutes), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
