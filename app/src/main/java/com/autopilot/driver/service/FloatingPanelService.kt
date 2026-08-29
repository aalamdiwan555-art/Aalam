package com.autopilot.driver.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.autopilot.driver.R
import com.autopilot.driver.model.RunState

class FloatingPanelService : Service() {
    private var windowManager: WindowManager? = null
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var touchX = 0f
    private var touchY = 0f

    private val runtimeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AutopilotService.ACTION_UPDATE) return
            val state = runCatching {
                RunState.valueOf(intent.getStringExtra("state") ?: RunState.STOPPED.name)
            }.getOrDefault(RunState.STOPPED)
            updateState(state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerRuntimeReceiver()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 12)
            background = roundedBackground(Color.rgb(12, 29, 34), 22f)
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val runButton = iconButton(android.R.drawable.ic_media_play, "Run auto clicker") {
            sendAction(AutopilotService.ACTION_RESUME)
        }
        controls.addView(runButton, LinearLayout.LayoutParams(44, 44))

        val pauseButton = iconButton(android.R.drawable.ic_media_pause, "Pause auto clicker") {
            sendAction(AutopilotService.ACTION_PAUSE)
        }
        controls.addView(
            pauseButton,
            LinearLayout.LayoutParams(44, 44).apply { topMargin = 4 },
        )

        val cancelButton = iconButton(android.R.drawable.ic_menu_close_clear_cancel, "Cancel auto clicker") {
            sendAction(AutopilotService.ACTION_STOP)
        }
        controls.addView(
            cancelButton,
            LinearLayout.LayoutParams(44, 44).apply { topMargin = 4 },
        )
        root.addView(controls)

        root.setOnTouchListener { _, event ->
            val params = panelParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x += (event.rawX - touchX).toInt()
                    params.y += (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(root, params)
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }

                else -> false
            }
        }

        val type = if (Build.VERSION.SDK_INT >= 26) {
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
        panelParams = params
        panel = root
        windowManager?.addView(root, params)
    }

    private fun iconButton(icon: Int, description: String, action: () -> Unit): ImageButton =
        ImageButton(this).apply {
            setImageResource(icon)
            contentDescription = description
            setColorFilter(Color.rgb(120, 230, 208))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { action() }
        }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            setStroke(1, Color.rgb(39, 66, 72))
        }

    private fun sendAction(action: String) {
        startService(Intent(this, AutopilotService::class.java).setAction(action))
    }

    private fun updateState(state: RunState) {
        if (state == RunState.STOPPED || state == RunState.ERROR) {
            stopSelf()
        }
    }

    private fun registerRuntimeReceiver() {
        val filter = IntentFilter(AutopilotService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.registerReceiver(this, runtimeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(runtimeReceiver, filter)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(runtimeReceiver) }
        panel?.let { view -> runCatching { windowManager?.removeView(view) } }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}