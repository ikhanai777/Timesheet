package com.ikhanai.timesheet.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ikhanai.timesheet.TimesheetApp
import com.ikhanai.timesheet.ui.dashboard.DashboardViewModel
import com.ikhanai.timesheet.ui.entry.EntryViewModel
import com.ikhanai.timesheet.ui.history.HistoryViewModel
import com.ikhanai.timesheet.ui.projects.ProjectDetailViewModel
import com.ikhanai.timesheet.ui.projects.ProjectsViewModel
import com.ikhanai.timesheet.ui.settings.SettingsViewModel

private fun CreationExtras.app(): TimesheetApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TimesheetApp

object AppViewModels {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { DashboardViewModel(app().container.repository, app().container.settings) }
        initializer {
            EntryViewModel(app().container.repository, app().container.settings, createSavedStateHandle())
        }
        initializer { HistoryViewModel(app().container.repository, app().container.settings) }
        initializer { ProjectsViewModel(app().container.repository) }
        initializer {
            ProjectDetailViewModel(app().container.repository, app().container.settings, createSavedStateHandle())
        }
        initializer { SettingsViewModel(app(), app().container.repository, app().container.settings) }
    }
}
