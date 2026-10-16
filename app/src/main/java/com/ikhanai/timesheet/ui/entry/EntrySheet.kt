package com.ikhanai.timesheet.ui.entry

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.focus.onFocusChanged
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
    var nameFocused by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(FormErrors()) }
    var askRename by remember { mutableStateOf<String?>(null) }
    var askMerge by remember { mutableStateOf<Int?>(null) }
    var renameDecision by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit(rename: Boolean?, merge: Boolean?) {
        if (busy) return
        busy = true
        scope.launch {
            val result = onSubmit(EntryForm(name, number, code, hours, system), rename, merge)
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

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errors = errors.copy(name = null)
                },
                label = { Text("Project name") },
                singleLine = true,
                isError = errors.name != null,
                supportingText = errors.name?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { nameFocused = it.isFocused },
            )
            val query = name.trim()
            val suggestions = if (nameFocused && query.isNotEmpty()) {
                projects.filter { p ->
                    !p.archived &&
                        (p.name.contains(query, ignoreCase = true) || p.projectNumber.contains(query, ignoreCase = true)) &&
                        !(p.name == name && p.projectNumber == number && p.oracleCode == code)
                }.take(5)
            } else {
                emptyList()
            }
            if (suggestions.isNotEmpty()) {
                Card {
                    suggestions.forEach { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text("${p.projectNumber} · ${p.oracleCode}") },
                            modifier = Modifier.clickable {
                                name = p.name
                                number = p.projectNumber
                                code = p.oracleCode
                                p.lastSystem?.let { system = it }
                                errors = FormErrors()
                            },
                        )
                    }
                }
            }

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

