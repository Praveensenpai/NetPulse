package com.netpulse.app.service

import kotlin.math.roundToInt

/**
 * Shortens a speed reading to fit the status-bar glyph.
 *
 * Kept separate from [SpeedIconGenerator] because it is pure string logic with
 * no Android types, which makes it directly unit-testable. The glyph can show
 * three characters at a legible size, so the label always keeps the unit letter
 * and shortens only the number.
 */
internal object SpeedIconLabel {

    /** Maximum characters the glyph can show at a legible size. */
    const val MAX_LENGTH = 3

    /**
     * Builds a label of at most [MAX_LENGTH] characters, always keeping the unit.
     *
     * The decimal is dropped and the value rounded ("5.8M" becomes "6M"), since
     * the icon has no room for it and the notification still shows the exact
     * value. Three-digit readings are promoted to the next unit ("988K" becomes
     * "1M") rather than truncated, because truncation misstates the magnitude by
     * up to 1024x.
     */
    fun compact(value: String, unit: String): String {
        val parsed = value.toDoubleOrNull() ?: 0.0
        val letter = unitLetter(unit)

        // Genuinely idle: report zero, not a "less than" bound.
        if (parsed <= 0.0) return "0$letter"

        var number = parsed.roundToInt()
        var current = letter

        // Dividing by 1024 shrinks the number each pass, so this terminates.
        while (number.toString().length > MAX_LENGTH - 1) {
            number = (number / 1024.0).roundToInt()
            current = nextUnit(current)
        }

        // A small but non-zero value that rounded to 0 would read as no traffic
        // at all, so report the lower bound instead.
        if (number == 0) return "<1$current"

        return "$number$current"
    }

    private fun unitLetter(unit: String): String = when {
        unit.startsWith("G", ignoreCase = true) -> "G"
        unit.startsWith("M", ignoreCase = true) -> "M"
        unit.startsWith("K", ignoreCase = true) -> "K"
        else -> "B"
    }

    private fun nextUnit(letter: String): String = when (letter) {
        "B" -> "K"
        "K" -> "M"
        "M" -> "G"
        else -> "T"
    }
}