package com.netpulse.app.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.netpulse.app.core.FormattedSpeed

/**
 * Renders the status bar speed glyph.
 *
 * The platform scales a small icon down to roughly 24dp, so every pixel of the
 * source bitmap matters. The value and unit share one line ("1.2M") because two
 * stacked lines left each glyph barely 10dp tall after downscaling.
 */
object SpeedIconGenerator {

    private const val ICON_SIZE = 96
    private const val MAX_VALUE_WIDTH = 92f

    @Synchronized
    fun generateSpeedBitmap(speed: FormattedSpeed): Bitmap {
        // A fresh bitmap per build: the notification holds a reference to this object,
        // so reusing one buffer mutated icons already posted to the status bar.
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = 56f
        }
        // One line ("1.2M") instead of two stacked lines: the platform shrinks a
        // small icon to ~24dp, which left each of two lines barely 10dp tall.
        val label = compactValue(speed.value) + unitLetter(speed.unit)

        val widest = valuePaint.measureText(label)
        if (widest > MAX_VALUE_WIDTH) {
            valuePaint.textSize *= MAX_VALUE_WIDTH / widest
        }

        // Center the single line vertically using the font metrics.
        val metrics = valuePaint.fontMetrics
        val baseline = ICON_SIZE / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, ICON_SIZE / 2f, baseline, valuePaint)

        return bitmap
    }

    /** Trims decimals so the glyph count never exceeds three characters. */
    private fun compactValue(value: String): String {
        if (value.length <= 3) return value
        val intPart = value.substringBefore('.')
        return if (intPart.length <= 3) intPart else intPart.take(3)
    }

    private fun unitLetter(unit: String): String = when {
        unit.startsWith("G", ignoreCase = true) -> "G"
        unit.startsWith("M", ignoreCase = true) -> "M"
        unit.startsWith("K", ignoreCase = true) -> "K"
        else -> "B"
    }
}
