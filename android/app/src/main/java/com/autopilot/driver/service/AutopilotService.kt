package com.autopilot.driver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.autopilot.driver.MainActivity
import com.autopilot.driver.R
import com.autopilot.driver.automation.ClickController
import com.autopilot.driver.capture.ScreenCaptureController
import com.autopilot.driver.detector.TargetDetector
import com.autopilot.driver.matcher.PriceMatcher
import com.autopilot.driver.matcher.PriceParser
import com.autopilot.driver.matcher.TargetPhrases
import com.autopilot.driver.model.ActionStatus
import com.autopilot.driver.model.DiagnosticState
import com.autopilot.driver.model.PriceConfiguration
import com.autopilot.driver.model.RunState
import com.autopilot.driver.model.RuntimeSnapshot
import com.autopilot.driver.ocr.TextDetector
import com.autopilot.driver.opencv.ImagePreprocessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AutopilotService : Service() {
    companion object {
        const val ACTION_START = "com.autopilot.driver.action.START"
        const val ACTION_RESUME = "com.autopilot.driver.action.RESUME"
        const val ACTION_PAUSE = "com.autopilot.driver.action.PAUSE"
        const val ACTION_STOP = "com.autopilot.driver.action.STOP"
        const val ACTION_UPDATE = "com.autopilot.driver.action.UPDATE"
        const val EXTRA_PROJECTION_CODE = "projection_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        const val EXTRA_MINIMUM = "minimum"
        const val EXTRA_MAXIMUM = "maximum"
        private const val CHANNEL_ID = "autopilot_monitoring"
        private const val NOTIFICATION_ID = 41
    }

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val frameMutex = Mutex()
    private var processingJob: Job? = null
    private var isPaused = false
    private var snapshot = RuntimeSnapshot(state = RunState.STARTING)
    private var configuration = PriceConfiguration(100.0, 150.0)
    private lateinit var captureController: ScreenCaptureController
    private lateinit var frameProcessor: FrameProcessor
    private lateinit var clickController: ClickController
    private lateinit var textDetector: TextDetector

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        clickController = ClickController()
        textDetector = TextDetector()
        frameProcessor = FrameProcessor(
            textDetector = textDetector,
            priceParser = PriceParser(),
            priceMatcher = PriceMatcher(),
            targetDetector = TargetDetector(TargetPhrases.defaults),
            preprocessor = ImagePreprocessor(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring(intent)
            ACTION_RESUME -> resumeMonitoring()
            ACTION_PAUSE -> pauseMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_NOT_STICKY
    }

    private fun startMonitoring(intent: Intent) {
        configuration = PriceConfiguration(
            intent.getDoubleExtra(EXTRA_MINIMUM, 100.0),
            intent.getDoubleExtra(EXTRA_MAXIMUM, 150.0),
        )
        isPaused = false
        startForeground(NOTIFICATION_ID, notification("Autopilot starting"))
        val code = intent.getIntExtra(EXTRA_PROJECTION_CODE, -1)
        val data = intent.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)
        if (code == -1 || data == null) {
            publishError("Screen capture permission was not granted")
            return
        }
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(code, data)
            captureController = ScreenCaptureController(
                context = this,
                onFrame = ::onFrame,
                onStopped = { publishError("Screen capture stopped") },
            )
            captureController.start(projection)
            snapshot = snapshot.copy(state = RunState.RUNNING, errorMessage = null)
            updateNotification("Autopilot is monitoring")
            publishSnapshot()
        } catch (_: Exception) {
            publishError("Autopilot could not start screen capture")
        }
    }

    private fun onFrame(bitmap: android.graphics.Bitmap, width: Int, height: Int) {
        if (isPaused || processingJob?.isActive == true) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        processingJob = scope.launch {
            frameMutex.withLock {
                try {
                    val result = frameProcessor.process(bitmap, configuration, width, height, snapshot)
                    snapshot = result.snapshot.copy(state = RunState.RUNNING)
                    if (!isPaused && result.match.status == com.autopilot.driver.model.MatchStatus.MATCH &&
                        result.target.detected && result.target.confidence >= 0.72f &&
                        (result.price?.confidence ?: 0f) >= 0.72f
                    ) {
                        val action = clickController.click(result.target, width, height)
                        snapshot = snapshot.copy(
                            diagnostics = snapshot.diagnostics.copy(
                                action = if (action.success) ActionStatus.EXECUTED else ActionStatus.FAILED,
                                lastMessage = action.message,
                            ),
                        )
                    }
                    snapshot = snapshot.copy(detectedPrice = result.detectedPrice)
                    publishSnapshot()
                } catch (_: Exception) {
                    publishError("Frame analysis failed; monitoring is still safe")
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
        }
    }

    private fun pauseMonitoring() {
        isPaused = true
        processingJob?.cancel()
        snapshot = snapshot.copy(
            state = RunState.PAUSED,
            diagnostics = snapshot.diagnostics.copy(frame = DiagnosticState.STOPPED),
        )
        updateNotification("Autopilot paused")
        publishSnapshot()
    }

    private fun resumeMonitoring() {
        if (!::captureController.isInitialized || snapshot.state == RunState.STOPPED) {
            publishError("Start Autopilot from the app first")
            return
        }
        isPaused = false
        snapshot = snapshot.copy(state = RunState.RUNNING, errorMessage = null)
        updateNotification("Autopilot is monitoring")
        publishSnapshot()
    }

    private fun stopMonitoring() {
        isPaused = true
        processingJob?.cancel()
        if (::captureController.isInitialized) captureController.stop()
        clickController.reset()
        snapshot = RuntimeSnapshot(state = RunState.STOPPED)
        publishSnapshot()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishError(message: String) {
        snapshot = snapshot.copy(
            state = RunState.ERROR,
            errorMessage = message,
            diagnostics = snapshot.diagnostics.copy(
                capture = DiagnosticState.ERROR,
                frame = DiagnosticState.STOPPED,
                lastMessage = message,
            ),
        )
        updateNotification(message)
        publishSnapshot()
    }

    private fun publishSnapshot() {
        sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).apply {
            putStringExtra("state", snapshot.state.name)
            putStringExtra("error", snapshot.errorMessage)
            snapshot.detectedPrice?.let { putDoubleExtra("detectedPrice", it) }
            putStringExtra("message", snapshot.diagnostics.lastMessage)
            putFloatExtra("confidence", snapshot.diagnostics.confidence)
            snapshot.diagnostics.latencyMs?.let { putLongExtra("latency", it) }
            putStringExtra("action", snapshot.diagnostics.action.name)
            putStringExtra("price", snapshot.diagnostics.price.name)
            putStringExtra("priceMatch", snapshot.diagnostics.priceMatch.name)
            putStringExtra("target", snapshot.diagnostics.target.name)
            putBooleanExtra("captureOn", snapshot.diagnostics.capture == DiagnosticState.ON)
        })
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_autopilot)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = getString(R.string.notification_channel_description) },
            )
        }
    }

    override fun onDestroy() {
        if (::captureController.isInitialized) captureController.stop()
        textDetector.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}