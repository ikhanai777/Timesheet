package com.ikhanai.timesheet.data

import com.ikhanai.timesheet.data.db.DaySheet
import com.ikhanai.timesheet.data.db.Project
import com.ikhanai.timesheet.data.db.TimeEntry
import com.ikhanai.timesheet.domain.BookingSystem
import com.ikhanai.timesheet.domain.DayType
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/** JSON backup format (FR-6 Backup / restore). */
object BackupSerializer {

    private const val VERSION = 1

    fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put("app", "timesheet")
        root.put("version", VERSION)
        root.put("exportedAt", Instant.now().toString())
        root.put("projects", JSONArray(data.projects.map { p ->
            JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("projectNumber", p.projectNumber)
                .put("oracleCode", p.oracleCode)
                .put("lastSystem", p.lastSystem?.name ?: JSONObject.NULL)
                .put("archived", p.archived)
                .put("createdAt", p.createdAt.toString())
                .put("updatedAt", p.updatedAt.toString())
        }))
        root.put("daySheets", JSONArray(data.daySheets.map { d ->
            JSONObject()
                .put("date", d.date.toString())
                .put("dayType", d.dayType.name)
                .put("availableMinutes", d.availableMinutes)
                .put("note", d.note ?: JSONObject.NULL)
                .put("updatedAt", d.updatedAt.toString())
        }))
        root.put("entries", JSONArray(data.entries.map { e ->
            JSONObject()
                .put("id", e.id)
                .put("date", e.date.toString())
                .put("projectId", e.projectId)
                .put("minutes", e.minutes)
                .put("system", e.system.name)
                .put("createdAt", e.createdAt.toString())
                .put("updatedAt", e.updatedAt.toString())
        }))
        return root.toString(2)
    }

    /** Throws [IllegalArgumentException] (or a JSON exception) for files that are not a Timesheet backup. */
    fun fromJson(text: String): BackupData {
        val root = JSONObject(text)
        require(root.optString("app") == "timesheet") { "Not a Timesheet backup file" }
        require(root.optInt("version") == VERSION) { "Unsupported backup version" }
        val projects = root.getJSONArray("projects").objects().map { o ->
            Project(
                id = o.getLong("id"),
                name = o.getString("name"),
                projectNumber = o.getString("projectNumber"),
                oracleCode = o.getString("oracleCode"),
                lastSystem = o.optStringOrNull("lastSystem")?.let(BookingSystem::valueOf),
                archived = o.optBoolean("archived"),
                createdAt = Instant.parse(o.getString("createdAt")),
                updatedAt = Instant.parse(o.getString("updatedAt")),
            )
        }
        val sheets = root.getJSONArray("daySheets").objects().map { o ->
            DaySheet(
                date = LocalDate.parse(o.getString("date")),
                dayType = DayType.valueOf(o.getString("dayType")),
                availableMinutes = o.getInt("availableMinutes"),
                note = o.optStringOrNull("note"),
                updatedAt = Instant.parse(o.getString("updatedAt")),
            )
        }
        val entries = root.getJSONArray("entries").objects().map { o ->
            TimeEntry(
                id = o.getLong("id"),
                date = LocalDate.parse(o.getString("date")),
                projectId = o.getLong("projectId"),
                minutes = o.getInt("minutes"),
                system = BookingSystem.valueOf(o.getString("system")),
                createdAt = Instant.parse(o.getString("createdAt")),
                updatedAt = Instant.parse(o.getString("updatedAt")),
            )
        }
        return BackupData(projects, sheets, entries)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key) || !has(key)) null else getString(key)
}
