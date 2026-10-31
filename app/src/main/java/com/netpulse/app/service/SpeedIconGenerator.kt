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
 * The platform scales the small icon by its width into a square status-bar
 * slot, so the bitmap stays square and the ink fills as much of it as the
 * label's own proportions allow. Text is never horizontally scaled: squeezing
 * the digits is what made the glyph look thin and stretched. A wider label is
 * drawn slightly smaller instead, which keeps every character's natural shape.
 */
object SpeedIconGenerator {

    private const val ICON_SIZE = 96

    /**
     * The canvas is filled edge to edge. Any unused margin here is lost twice:
     * once as dead pixels, and again when the platform scales the bitmap down
     * into the status-bar slot.
     */
    private const val MAX_WIDTH = 96f
    private const val MAX_HEIGHT = 96f

    private const val PROBE_TEXT_SIZE = 100f

    @Synchronized
    fun generateSpeedBitmap(speed: FormattedSpeed): Bitmap {
        // A fresh bitmap per build: the notification holds a reference to this
        // object, so reusing one buffer mutated icons already posted.
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = PROBE_TEXT_SIZE
        }
        val label = SpeedIconLabel.compact(speed.value, speed.unit)
        paint.textSize = fittedTextSize(paint, label)

        // Centre on measured ink rather than font metrics so the glyph sits
        // optically centred in the square canvas.
        val ink = Rect()
        paint.getTextBounds(label, 0, label.length, ink)
        val baseline = ICON_SIZE / 2f - (ink.top + ink.bottom) / 2f
        canvas.drawText(label, ICON_SIZE / 2f, baseline, paint)

        return bitmap
    }

    /**
     * Text size that fits [label] inside the canvas in both dimensions.
     *
     * Height alone would overflow the width for three-character labels, and
     * width alone would leave short labels small. Taking the smaller of the two
     * bounds makes the ink touch the limiting edge, so the glyph fills as much
     * of the square as its own proportions allow without being distorted.
     */
    private fun fittedTextSize(paint: Paint, label: String): Float {
        val probe = Rect()
        paint.getTextBounds(label, 0, label.length, probe)
        if (probe.width() == 0 || probe.height() == 0) return PROBE_TEXT_SIZE

        val byHeight = MAX_HEIGHT / probe.height() * PROBE_TEXT_SIZE
        val byWidth = MAX_WIDTH / probe.width() * PROBE_TEXT_SIZE
        return minOf(byHeight, byWidth)
    }
}
