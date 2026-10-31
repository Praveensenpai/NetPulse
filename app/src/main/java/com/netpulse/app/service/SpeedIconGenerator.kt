package com.netpulse.app.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.netpulse.app.core.FormattedSpeed

/**
 * Renders the status bar speed glyph.
 *
 * Sizing is governed by an awkward constraint. The bitmap is square and the
 * platform scales it into a slot roughly as wide as the clock is tall, so the
 * scale factor comes from the canvas *width*: on-screen height ends up close to
 * `inkHeight / 96 * 37`. Matching the clock therefore needs ink about 70 units
 * tall and no wider than the 96-unit canvas.
 *
 * A text line like "6.7M" is roughly 2.5x wider than it is tall, so it can never
 * satisfy both. Earlier builds resolved that by squeezing horizontally, which
 * read as stretched, or by shrinking to fit the width, which read as tiny.
 *
 * The label is therefore kept to at most two digits with the unit on its own
 * line underneath. That block is taller than it is wide, so it fills the canvas
 * height at natural width with no horizontal scaling whatsoever.
 */
object SpeedIconGenerator {

    private const val ICON_SIZE = 96

    /** Usable ink box; a hair of margin because the platform crops tightly. */
    private const val MAX_HEIGHT = 92f
    private const val MAX_WIDTH = 94f

    private const val PROBE_TEXT_SIZE = 100f

    /** Unit height relative to the number. */
    private const val UNIT_SCALE = 0.46f
    private const val UNIT_GAP = 1f

    /** Two digits, so the stacked block stays taller than it is wide. */
    private const val MAX_DIGITS = 2

    @Synchronized
    fun generateSpeedBitmap(speed: FormattedSpeed): Bitmap {
        // A fresh bitmap per build: the notification holds a reference to this
        // object, so reusing one buffer mutated icons already posted.
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = PROBE_TEXT_SIZE
        }
        val unitPaint = Paint(numberPaint).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        val number = compactNumber(speed.value)
        val unit = unitLetter(speed.unit)

        // Digit ink at the probe size. getTextBounds is used rather than font
        // metrics: the metrics box carries ascender and descender space that
        // digits never occupy, which previously left the number at half height.
        val probe = Rect()
        numberPaint.getTextBounds("00", 0, 2, probe)
        if (probe.height() <= 0) return bitmap

        // Split the available height between the number and the unit line.
        val numberInkTarget = (MAX_HEIGHT - UNIT_GAP) / (1f + UNIT_SCALE)
        var numberTextSize = numberInkTarget * PROBE_TEXT_SIZE / probe.height()
        numberPaint.textSize = numberTextSize
        unitPaint.textSize = numberTextSize * UNIT_SCALE

        // A two-digit label at this size is narrower than it is tall, so this
        // normally does nothing. It exists only so an unusually wide label can
        // never overflow; scaling both lines by the same factor keeps the
        // proportions intact, unlike a horizontal squeeze.
        val widest = maxOf(numberPaint.measureText(number), unitPaint.measureText(unit))
        if (widest > MAX_WIDTH) {
            val shrink = MAX_WIDTH / widest
            numberTextSize *= shrink
            numberPaint.textSize = numberTextSize
            unitPaint.textSize = numberTextSize * UNIT_SCALE
        }

        val numberInk = Rect()
        numberPaint.getTextBounds(number, 0, number.length, numberInk)
        val unitInk = Rect()
        unitPaint.getTextBounds(unit, 0, unit.length, unitInk)

        val blockHeight = numberInk.height() + UNIT_GAP + unitInk.height()
        val blockTop = (ICON_SIZE - blockHeight) / 2f
        // getTextBounds is relative to the baseline, so subtracting the top of
        // the ink box places that ink edge exactly at blockTop.
        val numberBaseline = blockTop - numberInk.top
        val unitBaseline = blockTop + numberInk.height() + UNIT_GAP - unitInk.top

        val centerX = ICON_SIZE / 2f
        canvas.drawText(number, centerX, numberBaseline, numberPaint)
        canvas.drawText(unit, centerX, unitBaseline, unitPaint)

        return bitmap
    }

    /**
     * Integer part only, at most two digits.
     *
     * The decimal is dropped rather than rounded so a value never appears larger
     * than it is ("3.9" reads as "3", not "4"). The exact figure is one glance
     * away in the app; this is a status-bar abbreviation.
     */
    private fun compactNumber(value: String): String {
        val integerPart = value.substringBefore('.')
        return if (integerPart.length <= MAX_DIGITS) integerPart else integerPart.take(MAX_DIGITS)
    }

    private fun unitLetter(unit: String): String = when {
        unit.startsWith("G", ignoreCase = true) -> "G"
        unit.startsWith("M", ignoreCase = true) -> "M"
        unit.startsWith("K", ignoreCase = true) -> "K"
        else -> "B"
    }
}