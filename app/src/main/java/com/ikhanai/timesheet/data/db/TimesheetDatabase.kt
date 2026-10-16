package com.ikhanai.timesheet.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Project::class, DaySheet::class, TimeEntry::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TimesheetDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun daySheetDao(): DaySheetDao
    abstract fun timeEntryDao(): TimeEntryDao

    companion object {
        fun build(context: Context): TimesheetDatabase =
            Room.databaseBuilder(context, TimesheetDatabase::class.java, "timesheet.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2 adds a task description to each entry. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE time_entry ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
