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
 * The platform fits the square bitmap inside a ~37 px status-bar slot, so the
 * glyph's on-screen height is bounded by how much of the 96 px canvas the ink
 * fills. A three-character label ("60K") is width-bound, so a condensed face is
 * used to buy cap height without dropping the unit letter.
 */
object SpeedIconGenerator {

    private const val ICON_SIZE = 96
    private const val MAX_WIDTH = 96f
    private const val MAX_HEIGHT = 92f

    /**
     * No extra horizontal squeeze.
     *
     * The typeface is already condensed, so scaling X as well compounded the
     * compression and made the digits read as vertically stretched. The glyph
     * keeps its natural aspect ratio.
     */
    private const val TEXT_SCALE_X = 1.0f

    /**
     * Worst-case label: three characters, all wide digits.
     *
     * The font size is computed once from this and never varied per reading.
     * Fitting each label independently made short readings ("50K") render tall
     * and long ones ("400K") render short, so the icon changed height with the
     * number.
     */
    private const val WORST_CASE_LABEL = "000B"

    /**
     * Hard cap on glyph count.
     *
     * The bitmap is always 96x96 and the platform scales it by a constant
     * factor, so on-screen height depends only on the canvas font size — and
     * that size is fixed by the worst-case label width. Four characters ("000B")
     * cost height, but a three-character cap meant dropping the unit on most
     * readings, which made the number meaningless.
     */
    private const val MAX_LABEL_LENGTH = 4
    private const val PROBE_TEXT_SIZE = 100f

    @Synchronized
    fun generateSpeedBitmap(speed: FormattedSpeed): Bitmap {
        // A fresh bitmap per build: the notification holds a reference to this object,
        // so reusing one buffer mutated icons already posted to the status bar.
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = PROBE_TEXT_SIZE
            textScaleX = TEXT_SCALE_X
        }
        val label = compactLabel(speed.value, speed.unit)
        // One constant font size for every reading, so the glyph never changes
        // height between "50K" and "400K".
        paint.textSize = fixedTextSize(paint)

        // Centre on measured ink rather than font metrics so the glyph sits
        // optically centred in the square canvas.
        val ink = Rect()
        paint.getTextBounds(label, 0, label.length, ink)
        val baseline = ICON_SIZE / 2f - (ink.top + ink.bottom) / 2f
        canvas.drawText(label, ICON_SIZE / 2f, baseline, paint)

        return bitmap
    }

    /**
     * Text size that fits the worst-case label, computed once.
     *
     * Deriving the size from the worst case rather than the actual reading is
     * what keeps glyph height constant: every label is then drawn at the same
     * size, and shorter labels simply occupy less width.
     */
    private fun fixedTextSize(paint: Paint): Float {
        val probe = Rect()
        paint.getTextBounds(WORST_CASE_LABEL, 0, WORST_CASE_LABEL.length, probe)
        if (probe.width() == 0 || probe.height() == 0) return PROBE_TEXT_SIZE

        val byWidth = MAX_WIDTH / probe.width() * PROBE_TEXT_SIZE
        val byHeight = MAX_HEIGHT / probe.height() * PROBE_TEXT_SIZE
        return minOf(byWidth, byHeight)
    }

    /**
     * Builds a label of at most four characters, always keeping the unit.
     *
     * Dropping the unit made readings unreadable: "260" could mean bytes, KB or
     * MB. The unit letter is therefore reserved first and only the numeric part
     * is shortened, so the label degrades as "1.8K" -> "18K" -> "123K".
     */
    private fun compactLabel(value: String, unit: String): String {
        val letter = unitLetter(unit)
        if (value.length + 1 <= MAX_LABEL_LENGTH) return value + letter

        val intPart = value.substringBefore('.')
        if (intPart.length + 1 <= MAX_LABEL_LENGTH) return intPart + letter

        // Worst case: keep the unit and clamp the integer digits to fit.
        return intPart.take(MAX_LABEL_LENGTH - 1) + letter
    }

    private fun unitLetter(unit: String): String = when {
        unit.startsWith("G", ignoreCase = true) -> "G"
        unit.startsWith("M", ignoreCase = true) -> "M"
        unit.startsWith("K", ignoreCase = true) -> "K"
        else -> "B"
    }
}
