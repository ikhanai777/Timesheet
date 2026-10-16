package com.ikhanai.timesheet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ikhanai.timesheet.data.ThemeMode
import com.ikhanai.timesheet.ui.TimesheetNavHost
import com.ikhanai.timesheet.ui.theme.TimesheetTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** Set when launched from the reminder notification. */
    private var openDate by mutableStateOf<LocalDate?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val settingsFlow = appContainer.settings.settings
        setContent {
            val settings by settingsFlow.collectAsState(initial = null)
            TimesheetTheme(settings?.themeMode ?: ThemeMode.SYSTEM) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TimesheetNavHost(openDate = openDate, onOpenDateHandled = { openDate = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val epochDay = intent?.getLongExtra(EXTRA_OPEN_DATE, Long.MIN_VALUE) ?: Long.MIN_VALUE
        if (epochDay != Long.MIN_VALUE) {
            openDate = LocalDate.ofEpochDay(epochDay)
            intent?.removeExtra(EXTRA_OPEN_DATE)
        }
    }

    companion object {
        const val EXTRA_OPEN_DATE = "open_date"
    }
}
