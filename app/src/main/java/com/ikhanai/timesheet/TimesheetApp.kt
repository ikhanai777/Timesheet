package com.ikhanai.timesheet

import android.app.Application
import android.content.Context
import com.ikhanai.timesheet.data.SettingsRepository
import com.ikhanai.timesheet.data.TimesheetRepository
import com.ikhanai.timesheet.data.db.TimesheetDatabase
import com.ikhanai.timesheet.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Manual dependency container shared by the whole app. */
class AppContainer(context: Context) {
    val database: TimesheetDatabase = TimesheetDatabase.build(context)
    val repository = TimesheetRepository(database)
    val settings = SettingsRepository(context)
}

class TimesheetApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.createChannel(this)
        appScope.launch {
            // A scheduling failure must never stop the app from starting.
            runCatching { ReminderScheduler.apply(this@TimesheetApp, container.settings.settings.first()) }
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as TimesheetApp).container
