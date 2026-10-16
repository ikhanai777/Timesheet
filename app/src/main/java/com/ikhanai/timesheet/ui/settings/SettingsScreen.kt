package com.ikhanai.timesheet.ui.settings

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ikhanai.timesheet.data.AppSettings
import com.ikhanai.timesheet.data.BackupSerializer
import com.ikhanai.timesheet.data.SettingsRepository
import com.ikhanai.timesheet.data.ThemeMode
import com.ikhanai.timesheet.data.TimesheetRepository
import com.ikhanai.timesheet.domain.BookingSystem
import com.ikhanai.timesheet.domain.Hours
import com.ikhanai.timesheet.reminder.ReminderScheduler
import com.ikhanai.timesheet.ui.AppViewModels
import com.ikhanai.timesheet.ui.Fmt
import com.ikhanai.timesheet.ui.components.ChoiceDialog
import com.ikhanai.timesheet.ui.components.DatePickerModal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class SettingsViewModel(
    private val app: Application,
    private val repository: TimesheetRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val messages = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = messages.receiveAsFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            ReminderScheduler.apply(app, settingsRepository.settings.first())
        }
    }

    fun applyAvailableFrom(date: LocalDate) {
        viewModelScope.launch {
            val minutes = settingsRepository.settings.first().availableMinutes
            val count = repository.applyAvailableFrom(date, minutes)
            messages.send("Applied ${Fmt.hours(minutes)} h to $count saved days from ${date.format(Fmt.shortDate)}")
        }
    }

    fun backup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = BackupSerializer.toJson(repository.exportAll())
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }
            }.onSuccess { messages.send("Backup saved") }
                .onFailure { messages.send("Backup failed: ${it.message}") }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                repository.replaceAll(BackupSerializer.fromJson(json))
            }.onSuccess { messages.send("Backup restored") }
                .onFailure { messages.send("Restore failed: ${it.message}") }
        }
    }
}

private enum class Dialog { AVAILABLE, STEP, WEEK_START, WEEKEND, OVERHEAD, THEME, TIME, APPLY_FROM, RESTORE }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.update { it.copy(reminderEnabled = granted) }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(vm::backup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) {
            pendingRestore = it
            dialog = Dialog.RESTORE
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Section("Hours")
            Item("Available hours per day", "${Fmt.hours(s.availableMinutes)} h · applies to new days") { dialog = Dialog.AVAILABLE }
            Item("Apply available hours to saved days…", "Update days already logged from a chosen date") {
                dialog = Dialog.APPLY_FROM
            }
            Item("Hours step", "${Hours.plain(s.stepMinutes)} h") { dialog = Dialog.STEP }
            Toggle("Allow overtime", "Let project hours exceed available hours", s.allowOvertime) { v ->
                vm.update { it.copy(allowOvertime = v) }
            }
            Item(
                "Overhead booking system",
                s.overheadSystem?.label ?: "None (kept separate)",
            ) { dialog = Dialog.OVERHEAD }

            Section("Calendar")
            Item("Week start day", s.weekStart.displayName()) { dialog = Dialog.WEEK_START }
            Item("Weekend days", s.weekendDays.sorted().joinToString { it.displayName() }.ifEmpty { "None" }) {
                dialog = Dialog.WEEKEND
            }
            Toggle("Allow future dates", "Log hours for days after today", s.allowFutureDates) { v ->
                vm.update { it.copy(allowFutureDates = v) }
            }

            Section("Reminder")
            Toggle("Daily reminder", "Notify on working days that aren't logged", s.reminderEnabled) { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.update { it.copy(reminderEnabled = enabled) }
                }
            }
            Item("Reminder time", s.reminderTime.format(DateTimeFormatter.ofPattern("HH:mm"))) { dialog = Dialog.TIME }

            Section("Appearance")
            Item("Theme", s.themeMode.label) { dialog = Dialog.THEME }

            Section("Data")
            Item("Back up to file", "Save all projects and entries as JSON") {
                backupLauncher.launch("timesheet_backup_${LocalDate.now()}.json")
            }
            Item("Restore from file", "Replace all data with a backup") {
                restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }

        when (dialog) {
            Dialog.AVAILABLE -> AvailableHoursDialog(s.availableMinutes, { m -> vm.update { it.copy(availableMinutes = m) } }) {
                dialog = null
            }
            Dialog.STEP -> ChoiceDialog(
                "Hours step", listOf(15, 30, 60), s.stepMinutes, { "${Hours.plain(it)} h" },
                { m -> vm.update { it.copy(stepMinutes = m) } },
            ) { dialog = null }
            Dialog.WEEK_START -> ChoiceDialog(
                "Week start day",
                listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY),
                s.weekStart,
                { it.displayName() },
                { d -> vm.update { it.copy(weekStart = d) } },
            ) { dialog = null }
            Dialog.WEEKEND -> WeekendDialog(s.weekendDays, { days -> vm.update { it.copy(weekendDays = days) } }) {
                dialog = null
            }
            Dialog.OVERHEAD -> ChoiceDialog(
                "Overhead booking system",
                listOf<BookingSystem?>(null, BookingSystem.NTIME, BookingSystem.P6),
                s.overheadSystem,
                { it?.label ?: "None (kept separate)" },
                { sys -> vm.update { it.copy(overheadSystem = sys) } },
            ) { dialog = null }
            Dialog.THEME -> ChoiceDialog(
                "Theme", ThemeMode.entries, s.themeMode, { it.label },
                { t -> vm.update { it.copy(themeMode = t) } },
            ) { dialog = null }
            Dialog.TIME -> TimeDialog(s.reminderTime, { t -> vm.update { it.copy(reminderTime = t) } }) { dialog = null }
            Dialog.APPLY_FROM -> DatePickerModal(
                initial = LocalDate.now(),
                maxDate = null,
                onPick = vm::applyAvailableFrom,
                onDismiss = { dialog = null },
            )
            Dialog.RESTORE -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Restore backup?") },
                text = { Text("All current projects and entries will be replaced by the backup.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingRestore?.let(vm::restore)
                        dialog = null
                    }) { Text("Restore") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
            null -> Unit
        }
    }
}

private fun DayOfWeek.displayName(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Item(title: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun AvailableHoursDialog(current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(Hours.plain(current)) }
    val minutes = Hours.parse(text)
    val valid = minutes != null && minutes in 60..(24 * 60) && minutes % 30 == 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Available hours per day") },
        text = {
            OutlinedTextField(
                text,
                { text = it },
                singleLine = true,
                isError = !valid,
                supportingText = { Text("1 to 24, in steps of 0.5. Days already saved keep their value.") },
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(minutes!!)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WeekendDialog(current: Set<DayOfWeek>, onSave: (Set<DayOfWeek>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weekend days") },
        text = {
            Column {
                DayOfWeek.entries.forEach { day ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = if (day in selected) selected - day else selected + day },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = day in selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(day.displayName())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(selected)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TimeDialog(current: LocalTime, onSave: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(current.hour, current.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time") },
        text = { Column(verticalArrangement = Arrangement.Center) { TimePicker(state) } },
        confirmButton = {
            TextButton(onClick = {
                onSave(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
