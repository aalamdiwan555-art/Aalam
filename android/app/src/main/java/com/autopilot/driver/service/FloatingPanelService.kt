package com.autopilot.driver.service

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.autopilot.driver.R

class FloatingPanelService : Service() {
    private var windowManager: WindowManager? = null
    private var panel: View? = null
    private var startX = 0
    private var startY = 0
    private var touchX = 0f
    private var touchY = 0f

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            setBackgroundColor(Color.rgb(11, 22, 26))
        }
        layout.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.rgb(120, 230, 208))
            textSize = 16f
        })
        val status = TextView(this).apply {
            text = "Status: monitoring"
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        layout.addView(status)
        val buttons = LinearLayout(this)
        fun addAction(label: String, action: String) {
            buttons.addView(Button(this).apply {
                text = label
                setOnClickListener {
                    startService(Intent(this@FloatingPanelService, AutopilotService::class.java).setAction(action))
                }
            })
        }
        addAction("Resume", AutopilotService.ACTION_RESUME)
        addAction("Pause", AutopilotService.ACTION_PAUSE)
        addAction("Stop", AutopilotService.ACTION_STOP)
        layout.addView(buttons)
        layout.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX.toInt()
                    startY = event.rawY.toInt()
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = layout.layoutParams as WindowManager.LayoutParams
                    params.x += (event.rawX - touchX).toInt()
                    params.y += (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(layout, params)
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                else -> true
            }
        }
        val type = if (android.os.Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 18
            y = 120
        }
        panel = layout
        windowManager?.addView(layout, params)
    }

    override fun onDestroy() {
        panel?.let { windowManager?.removeView(it) }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}