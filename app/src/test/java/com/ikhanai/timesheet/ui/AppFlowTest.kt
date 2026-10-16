package com.ikhanai.timesheet.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onLast
import androidx.test.core.app.ApplicationProvider
import com.ikhanai.timesheet.MainActivity
import com.ikhanai.timesheet.TimesheetApp
import com.ikhanai.timesheet.domain.BookingSystem
import com.ikhanai.timesheet.domain.DayType
import com.ikhanai.timesheet.domain.Periods
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Drives the real app (MainActivity + navigation + Room + DataStore) through the main flow and
 * saves screenshots to app/build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    @Before
    fun seed() = runBlocking {
        val repo = ApplicationProvider.getApplicationContext<TimesheetApp>().container.repository
        val a = repo.createProject("Project A", "PRJ-2041", "4100.22.07")
        val b = repo.createProject("Project B", "PRJ-2055", "4100.31.02")
        val today = LocalDate.now()
        // Log every earlier weekday this month and this week, leaving yesterday unlogged.
        val from = minOf(Periods.month(today).start, Periods.week(today, DayOfWeek.MONDAY).start)
        var d = from
        while (d.isBefore(today.minusDays(1))) {
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) {
                repo.saveEntry(d, DayType.WORKING, 480, null, null, a, if (d.dayOfMonth % 2 == 0) 300 else 240, BookingSystem.NTIME, "Design calculations")
                repo.saveEntry(d, DayType.WORKING, 480, null, null, b, 180, BookingSystem.P6, "Planning update")
            }
            d = d.plusDays(1)
        }
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Draws every window (activity, dialogs, bottom sheets) into one bitmap, like the screen shows it. */
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val decor = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val location = IntArray(2)
        for (view in windowViews()) {
            if (!view.isShown || view.width == 0) continue
            view.getLocationOnScreen(location)
            canvas.save()
            canvas.translate(location[0].toFloat(), location[1].toFloat())
            view.draw(canvas)
            canvas.restore()
        }
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun windowViews(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val field = global.getDeclaredField("mViews").apply { isAccessible = true }
        return (field.get(instance) as List<View>).toList()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label, substring = true))

    private fun addEntry(search: String, suggestion: String, description: String, hours: String, system: BookingSystem) {
        compose.onNodeWithText("Add project").performClick()
        waitForText("Add project hours")
        field("Project name").performTextInput(search)
        waitForText(suggestion)
        compose.onNodeWithText(suggestion).performClick()
        field("Task description").performTextInput(description)
        field("Manhours").performTextReplacement(hours)
        compose.onAllNodesWithText(system.label).onLast().performClick()
        compose.onNodeWithText("Save").performClick()
    }

    @Test
    fun mainFlow() {
        // Dashboard, week view.
        waitForText("Total hours")
        waitForText("Hours per day")
        screenshot("01_dashboard_week")

        // Add today's entries: Project A 4h NTIME, Project B 3h P6.
        compose.onNodeWithText("Add Entry").performClick()
        waitForText("Day entry")
        screenshot("02_entry_empty")

        compose.onNodeWithText("Add project").performClick()
        waitForText("Add project hours")
        // Saved projects are offered in a dropdown without typing anything.
        field("Project name").performClick()
        waitForText("PRJ-2055 · 4100.31.02")
        screenshot("03a_project_dropdown")
        compose.onNodeWithText("PRJ-2041 · 4100.22.07").performClick()
        field("Task description").performTextInput("Reviewed piping layout drawings")
        field("Manhours").performTextReplacement("4")
        screenshot("03_entry_sheet")
        compose.onNodeWithText("Save").performClick()
        waitForText("4.0 h")

        addEntry("PRJ-2055", "PRJ-2055 · 4100.31.02", "Updated P6 schedule", "3", BookingSystem.P6)
        waitForText("3.0 h")
        waitForText("Projects 7.0 · Overhead 1.0", substring = true)
        screenshot("04_entry_day_overhead")

        // Over-allocation is rejected.
        compose.onNodeWithText("Add project").performClick()
        waitForText("Add project hours")
        field("Project name").performTextInput("Project C")
        field("Project number").performTextInput("PRJ-3000")
        field("Oracle code").performTextInput("4100.40.01")
        field("Task description").performTextInput("Extra work")
        field("Manhours").performTextReplacement("2")
        compose.onNodeWithText("Save").performClick()
        waitForText("exceed available hours", substring = true)
        screenshot("05_entry_over_allocation")
        compose.onNodeWithText("Cancel").performClick()

        // Dashboard day / month views.
        compose.onNodeWithText("Dashboard").performClick()
        waitForText("Day")
        compose.onNodeWithText("Day").performClick()
        waitForText("NTIME 4.0 · P6 3.0 · Overhead 1.0", substring = true)
        screenshot("06_dashboard_day")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Department Hours"))
        waitForText("By project")
        screenshot("06b_dashboard_day_table")
        compose.onNode(hasScrollToNodeAction()).performScrollToIndex(0)
        compose.onNodeWithText("Month").performClick()
        waitForText("Hours per week")
        screenshot("07_dashboard_month")

        compose.onNodeWithText("History").performClick()
        waitForText("Logged")
        screenshot("08_history")

        compose.onNodeWithText("Projects").performClick()
        waitForText("Show archived")
        screenshot("09_projects")

        compose.onNodeWithText("Dashboard").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Available hours per day")
        screenshot("10_settings")

        // Dark theme.
        compose.onNodeWithText("Theme").performScrollTo().performClick()
        waitForText("Dark")
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Week").performClick()
        waitForText("Hours per day")
        screenshot("11_dashboard_week_dark")
    }
}
