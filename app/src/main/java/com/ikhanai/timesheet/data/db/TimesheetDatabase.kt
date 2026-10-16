package com.ikhanai.timesheet.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Project::class, DaySheet::class, TimeEntry::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TimesheetDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun daySheetDao(): DaySheetDao
    abstract fun timeEntryDao(): TimeEntryDao

    companion object {
        fun build(context: Context): TimesheetDatabase =
            Room.databaseBuilder(context, TimesheetDatabase::class.java, "timesheet.db").build()
    }
}
