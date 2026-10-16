package com.ikhanai.timesheet.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ikhanai.timesheet.data.db.Project
import com.ikhanai.timesheet.domain.BookingSystem
import com.ikhanai.timesheet.domain.Hours
import com.ikhanai.timesheet.ui.Fmt
import kotlinx.coroutines.launch

/** Add/Edit Entry bottom sheet (spec 4.3). */
@Composable
fun EntrySheet(
    editing: EntryRow?,
    projects: List<Project>,
    remainingMinutes: Int,
    stepMinutes: Int,
    allowOvertime: Boolean,
    onSubmit: suspend (form: EntryForm, rename: Boolean?, merge: Boolean?) -> SubmitResult,
    onDone: (note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf(editing?.project?.name.orEmpty()) }
    var number by rememberSaveable { mutableStateOf(editing?.project?.projectNumber.orEmpty()) }
    var code by rememberSaveable { mutableStateOf(editing?.project?.oracleCode.orEmpty()) }
    var hours by rememberSaveable {
        mutableStateOf(
            editing?.entry?.minutes?.let(Hours::plain)
                ?: if (remainingMinutes > 0) Hours.plain(remainingMinutes) else "",
        )
    }
    var system by rememberSaveable { mutableStateOf(editing?.entry?.system ?: BookingSystem.NTIME) }
    var description by rememberSaveable { mutableStateOf(editing?.entry?.description.orEmpty()) }
    var errors by remember { mutableStateOf(FormErrors()) }
    var askRename by remember { mutableStateOf<String?>(null) }
    var askMerge by remember { mutableStateOf<Int?>(null) }
    var renameDecision by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit(rename: Boolean?, merge: Boolean?) {
        if (busy) return
        busy = true
        scope.launch {
            val result = onSubmit(EntryForm(name, number, code, description, hours, system), rename, merge)
            busy = false
            when (result) {
                is SubmitResult.Saved -> {
                    sheetState.hide()
                    onDone(result.note)
                }
                is SubmitResult.Invalid -> errors = result.errors
                is SubmitResult.ConfirmRename -> askRename = result.existingName
                is SubmitResult.ConfirmMerge -> askMerge = result.existingMinutes
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (editing == null) "Add project hours" else "Edit entry", style = MaterialTheme.typography.titleLarge)

            ProjectPicker(
                name = name,
                number = number,
                code = code,
                projects = projects,
                error = errors.name,
                onNameChange = {
                    name = it
                    errors = errors.copy(name = null)
                },
                onPick = { p ->
                    name = p.name
                    number = p.projectNumber
                    code = p.oracleCode
                    p.lastSystem?.let { system = it }
                    errors = errors.copy(name = null, number = null, code = null)
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = {
                        number = it
                        errors = errors.copy(number = null)
                    },
                    label = { Text("Project number") },
                    singleLine = true,
                    isError = errors.number != null,
                    supportingText = errors.number?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        errors = errors.copy(code = null)
                    },
                    label = { Text("Oracle code") },
                    singleLine = true,
                    isError = errors.code != null,
                    supportingText = errors.code?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    errors = errors.copy(description = null)
                },
                label = { Text("Task description") },
                placeholder = { Text("What did you work on?") },
                minLines = 2,
                maxLines = 4,
                isError = errors.description != null,
                supportingText = {
                    Text(errors.description ?: "${description.trim().length} / ${EntryViewModel.MAX_DESCRIPTION}")
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = {
                    val current = Hours.parse(hours) ?: 0
                    hours = Hours.plain(maxOf(0, Hours.roundToStep(current, stepMinutes) - stepMinutes))
                    errors = errors.copy(hours = null)
                }) { Icon(Icons.Filled.Remove, "Decrease hours") }
                OutlinedTextField(
                    value = hours,
                    onValueChange = {
                        hours = it
                        errors = errors.copy(hours = null)
                    },
                    label = { Text("Manhours") },
                    singleLine = true,
                    isError = errors.hours != null,
                    supportingText = {
                        Text(
                            errors.hours ?: if (allowOvertime) {
                                "Remaining in day: ${Fmt.hours(remainingMinutes)} h (overtime allowed)"
                            } else {
                                "Remaining: ${Fmt.hours(remainingMinutes)} h"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                FilledTonalIconButton(onClick = {
                    val current = Hours.parse(hours) ?: 0
                    hours = Hours.plain(Hours.roundToStep(current, stepMinutes) + stepMinutes)
                    errors = errors.copy(hours = null)
                }) { Icon(Icons.Filled.Add, "Increase hours") }
            }

            Text("Booking system", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BookingSystem.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = system == option,
                        onClick = { system = option },
                        shape = SegmentedButtonDefaults.itemShape(index, BookingSystem.entries.size),
                    ) { Text(option.label) }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { submit(null, null) }, enabled = !busy) { Text("Save") }
            }
        }
    }

    askRename?.let { existingName ->
        AlertDialog(
            onDismissRequest = { askRename = null },
            title = { Text("Update project name?") },
            text = {
                Text("Project $number · $code is saved as \"$existingName\". Rename it to \"${name.trim()}\"?")
            },
            confirmButton = {
                TextButton(onClick = {
                    askRename = null
                    renameDecision = true
                    submit(true, null)
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askRename = null
                    renameDecision = false
                    name = existingName
                    submit(false, null)
                }) { Text("Keep \"$existingName\"") }
            },
        )
    }

    askMerge?.let { existingMinutes ->
        AlertDialog(
            onDismissRequest = { askMerge = null },
            title = { Text("Merge with existing entry?") },
            text = {
                Text(
                    "This project already has ${Fmt.hoursWithUnit(existingMinutes)} in ${system.label} today. " +
                        "Add these hours to that entry?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askMerge = null
                    submit(renameDecision, true)
                }) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askMerge = null
                    submit(renameDecision, false)
                }) { Text("Keep separate") }
            },
        )
    }
}


/**
 * Project name field with a dropdown of saved projects (FR-4). Picking one fills in the number
 * and Oracle code; typing filters the list, and a new name can still be entered.
 */
@Composable
private fun ProjectPicker(
    name: String,
    number: String,
    code: String,
    projects: List<Project>,
    error: String?,
    onNameChange: (String) -> Unit,
    onPick: (Project) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = projects.filter { !it.archived }
    val picked = active.any { it.name == name && it.projectNumber == number && it.oracleCode == code }
    val query = name.trim()
    val options = if (picked || query.isEmpty()) {
        active
    } else {
        active.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.projectNumber.contains(query, ignoreCase = true) ||
                it.oracleCode.contains(query, ignoreCase = true)
        }
    }
    val showMenu = expanded && options.isNotEmpty()
    ExposedDropdownMenuBox(expanded = showMenu, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                onNameChange(it)
                expanded = true
            },
            label = { Text("Project name") },
            placeholder = { Text(if (active.isEmpty()) "Enter a new project" else "Pick a saved project or type a new one") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = {
                if (active.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu)
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            options.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(p.name)
                            Text(
                                "${p.projectNumber} · ${p.oracleCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onPick(p)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
