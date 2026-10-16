package com.ikhanai.timesheet.data.db

import androidx.room.TypeConverter
import com.ikhanai.timesheet.domain.BookingSystem
import com.ikhanai.timesheet.domain.DayType
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun dateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantToMillis(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun systemToString(system: BookingSystem?): String? = system?.name

    @TypeConverter
    fun stringToSystem(value: String?): BookingSystem? = value?.let(BookingSystem::valueOf)

    @TypeConverter
    fun dayTypeToString(type: DayType?): String? = type?.name

    @TypeConverter
    fun stringToDayType(value: String?): DayType? = value?.let(DayType::valueOf)
}
