package com.netpulse.app.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.netpulse.app.core.FormattedSpeed

object SpeedIconGenerator {

    private const val ICON_SIZE = 64
    private val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    @Synchronized
    fun generateSpeedBitmap(speed: FormattedSpeed): Bitmap {
        bitmap.eraseColor(Color.TRANSPARENT)

        val valueText = speed.value
        val unitText = when {
            speed.unit.startsWith("GB", ignoreCase = true) || speed.unit.startsWith("Gb", ignoreCase = true) -> "G"
            speed.unit.startsWith("MB", ignoreCase = true) || speed.unit.startsWith("Mb", ignoreCase = true) -> "M"
            speed.unit.startsWith("KB", ignoreCase = true) || speed.unit.startsWith("Kb", ignoreCase = true) -> "K"
            else -> "B"
        }

        // Adjust text size based on character length
        valuePaint.textSize = when (valueText.length) {
            1 -> 34f
            2 -> 30f
            3 -> 24f
            else -> 20f
        }
        unitPaint.textSize = 18f

        // Draw value in upper half, unit in lower half
        val centerX = ICON_SIZE / 2f
        canvas.drawText(valueText, centerX, 32f, valuePaint)
        canvas.drawText(unitText, centerX, 54f, unitPaint)

        return bitmap
    }
}
