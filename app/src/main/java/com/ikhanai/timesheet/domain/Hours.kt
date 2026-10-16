package com.ikhanai.timesheet.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Hours are stored as integer minutes; these helpers convert for input and display. */
object Hours {

    /** Display form: one decimal when exact (4.5), otherwise two (4.25). Uses the locale's separator. */
    fun format(minutes: Int, locale: Locale = Locale.getDefault()): String {
        val hours = minutes / 60.0
        return if (minutes % 6 == 0) {
            String.format(locale, "%.1f", hours)
        } else {
            String.format(locale, "%.2f", hours)
        }
    }

    /** Machine form for CSV and text fields: "8", "4.5", "0.25". Always uses '.'. */
    fun plain(minutes: Int): String =
        BigDecimal(minutes)
            .divide(BigDecimal(60), 2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    /** Parses "4.5" or "4,5" into minutes. Returns null when the text is not a number. */
    fun parse(text: String): Int? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        return value.multiply(BigDecimal(60)).setScale(0, RoundingMode.HALF_UP).toInt()
    }

    /** Rounds to the nearest multiple of [stepMinutes] (halves round up). */
    fun roundToStep(minutes: Int, stepMinutes: Int): Int {
        require(stepMinutes > 0)
        if (minutes <= 0) return minutes
        return ((minutes + stepMinutes / 2) / stepMinutes) * stepMinutes
    }
}
