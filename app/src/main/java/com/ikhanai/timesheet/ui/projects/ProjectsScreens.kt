package com.ikhanai.timesheet.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ikhanai.timesheet.domain.ChartBucket
import com.ikhanai.timesheet.domain.Periods
import com.ikhanai.timesheet.ui.AppViewModels
import com.ikhanai.timesheet.ui.Fmt
import com.ikhanai.timesheet.ui.components.StackedColumnChart
import com.ikhanai.timesheet.ui.components.SummaryCard
import com.ikhanai.timesheet.ui.components.SystemLegend
import com.ikhanai.timesheet.ui.theme.LocalSeriesColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ProjectsScreen(
    onOpenProject: (Long) -> Unit,
    vm: ProjectsViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Projects") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add project") }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Search name, number or code") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show archived", modifier = Modifier.weight(1f))
                    Switch(checked = state.showArchived, onCheckedChange = vm::setShowArchived)
                }
            }
            if (state.items.isEmpty()) {
                item {
                    Text(
                        "No projects yet. Projects are saved automatically when you first log hours against them, " +
                            "or you can add one with the + button.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.items, key = { it.project.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.project.name + if (item.project.archived) " (archived)" else "") },
                    supportingContent = { Text("${item.project.projectNumber} · ${item.project.oracleCode}") },
                    trailingContent = { Text(Fmt.hoursWithUnit(item.minutes)) },
                    modifier = Modifier.clickable { onOpenProject(item.project.id) },
                )
                HorizontalDivider()
            }
        }
    }

    if (adding) {
        ProjectDialog(
            title = "Add project",
            initialName = "",
            initialNumber = "",
            initialCode = "",
            onSave = { name, number, code -> vm.addProject(name, number, code) },
            onDismiss = { adding = false },
        )
    }
}

@Composable
fun ProjectDetailScreen(
    onBack: () -> Unit,
    vm: ProjectDetailViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state?.project?.name ?: "Project") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    val project = state?.project
                    if (project != null) {
                        IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Edit project") }
                        IconButton(onClick = { scope.launch { vm.setArchived(!project.archived) } }) {
                            if (project.archived) {
                                Icon(Icons.Filled.Unarchive, "Unarchive project")
                            } else {
                                Icon(Icons.Filled.Archive, "Archive project")
                            }
                        }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete project") }
                    }
                },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        val colors = LocalSeriesColors.current
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${s.project.projectNumber} · ${s.project.oracleCode}", style = MaterialTheme.typography.titleMedium)
            if (s.project.archived) {
                Text("Archived — hidden from suggestions", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Lifetime", Fmt.hours(s.totalMinutes), Modifier.weight(1f))
                SummaryCard("NTIME", Fmt.hours(s.ntimeMinutes), Modifier.weight(1f), colors.ntime)
                SummaryCard("P6", Fmt.hours(s.p6Minutes), Modifier.weight(1f), colors.p6)
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Last 12 weeks", style = MaterialTheme.typography.titleSmall)
                    val today = LocalDate.now()
                    StackedColumnChart(
                        s.lastWeeks.map {
                            ChartBucket(it.label, Periods.day(today), it.ntimeMinutes, it.p6Minutes, 0)
                        },
                        referenceMinutes = null,
                    )
                    SystemLegend()
                }
            }
            Text("${s.entryCount} entries", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    val s = state
    if (editing && s != null) {
        ProjectDialog(
            title = "Edit project",
            initialName = s.project.name,
            initialNumber = s.project.projectNumber,
            initialCode = s.project.oracleCode,
            onSave = { name, number, code -> vm.update(name, number, code) },
            onDismiss = { editing = false },
        )
    }
    if (confirmDelete && s != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete project?") },
            text = { Text("\"${s.project.name}\" will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        if (vm.delete()) {
                            onBack()
                        } else {
                            val result = snackbar.showSnackbar(
                                "This project has logged hours and can't be deleted.",
                                actionLabel = "Archive",
                            )
                            if (result == SnackbarResult.ActionPerformed) vm.setArchived(true)
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectDialog(
    title: String,
    initialName: String,
    initialNumber: String,
    initialCode: String,
    onSave: suspend (String, String, String) -> String?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var number by rememberSaveable { mutableStateOf(initialNumber) }
    var code by rememberSaveable { mutableStateOf(initialCode) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text("Project name") }, singleLine = true)
                OutlinedTextField(number, { number = it; error = null }, label = { Text("Project number") }, singleLine = true)
                OutlinedTextField(code, { code = it; error = null }, label = { Text("Oracle code") }, singleLine = true)
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val result = onSave(name, number, code)
                    if (result == null) onDismiss() else error = result
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
