package com.netpulse.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.netpulse.app.core.SpeedFormatter
import com.netpulse.app.domain.model.SpeedSnapshot

class FloatingOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun syncVisibility(shouldShow: Boolean, alpha: Float) {
        mainHandler.post {
            when {
                shouldShow && !isShowing -> show(alpha)
                !shouldShow && isShowing -> hide()
                isShowing -> applyAlpha(alpha)
            }
        }
    }

    private fun applyAlpha(alpha: Float) {
        val params = layoutParams ?: return
        if (params.alpha == alpha) return
        params.alpha = alpha
        overlayView?.let {
            try {
                windowManager.updateViewLayout(it, params)
            } catch (_: IllegalArgumentException) {
                // View already detached; the next reconcile re-adds it.
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(alpha: Float = 0.85f) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(alpha) }
            return
        }
        if (isShowing || !canDrawOverlays()) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 150
            this.alpha = alpha
        }
        layoutParams = params

        val background = GradientDrawable().apply {
            setColor(Color.argb(220, 20, 24, 33))
            cornerRadius = 32f
            setStroke(2, Color.argb(120, 88, 166, 255))
        }

        val textView = TextView(context).apply {
            this.background = background
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(24, 12, 24, 12)
            text = "↓ 0 B/s"
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        textView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    overlayView?.let { windowManager.updateViewLayout(it, params) }
                    true
                }
                else -> false
            }
        }

        overlayView = textView
        try {
            windowManager.addView(textView, params)
        } catch (_: Exception) {
            overlayView = null
            layoutParams = null
            isShowing = false
            return
        }
        isShowing = true
    }

    fun updateSpeed(speed: SpeedSnapshot, unitInBits: Boolean = false) {
        if (!isShowing) return
        val dl = SpeedFormatter.formatSpeed(speed.downloadBytesPerSec, unitInBits)
        val ul = SpeedFormatter.formatSpeed(speed.uploadBytesPerSec, unitInBits)
        val text = "↓ ${dl.displayString}  ↑ ${ul.displayString}"
        mainHandler.post {
            if (isShowing) overlayView?.text = text
        }
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        if (!isShowing) return
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        layoutParams = null
        isShowing = false
    }
}
