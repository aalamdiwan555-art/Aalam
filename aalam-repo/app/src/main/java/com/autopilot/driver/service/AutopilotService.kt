package com.autopilot.driver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
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
import com.autopilot.driver.model.MatchStatus
import com.autopilot.driver.model.PriceConfiguration
import com.autopilot.driver.model.RunState
import com.autopilot.driver.model.RuntimeSnapshot
import com.autopilot.driver.ocr.TextDetector
import com.autopilot.driver.opencv.ImagePreprocessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

class AutopilotService : Service() {
    companion object {
        const val ACTION_START = "com.autopilot.driver.action.START"
        const val ACTION_RESUME = "com.autopilot.driver.action.RESUME"
        const val ACTION_PAUSE = "com.autopilot.driver.action.PAUSE"
        const val ACTION_STOP = "com.autopilot.driver.action.STOP"
        const val ACTION_STATUS = "com.autopilot.driver.action.STATUS"
        const val ACTION_UPDATE = "com.autopilot.driver.action.UPDATE"
        const val EXTRA_PROJECTION_CODE = "projection_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        const val EXTRA_MINIMUM = "minimum"
        const val EXTRA_MAXIMUM = "maximum"

        private const val CHANNEL_ID = "autopilot_monitoring"
        private const val NOTIFICATION_ID = 41
        private const val REQUIRED_STABLE_FRAMES = 2
    }

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val frameMutex = Mutex()
    private var processingJob: Job? = null
    @Volatile
    private var isPaused = false
    @Volatile
    private var snapshot = RuntimeSnapshot(state = RunState.STOPPED)
    private var configuration = PriceConfiguration(100.0, 150.0)
    private var candidateKey: String? = null
    private var candidateFrameCount = 0
    private var actionConsumedKey: String? = null
    @Volatile
    private var sessionGeneration = 0L
    @Volatile
    private var actionInFlight = false

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
            ACTION_STATUS -> publishSnapshot()
        }
        return START_NOT_STICKY
    }

    private fun startMonitoring(intent: Intent) {
        if (snapshot.state in setOf(RunState.STARTING, RunState.RUNNING, RunState.PAUSED, RunState.STOPPING)) {
            publishError("Aalam is already running")
            return
        }
        sessionGeneration += 1
        val generation = sessionGeneration
        configuration = PriceConfiguration(
            intent.getDoubleExtra(EXTRA_MINIMUM, 100.0),
            intent.getDoubleExtra(EXTRA_MAXIMUM, 150.0),
        )
        isPaused = false
        candidateKey = null
        candidateFrameCount = 0
        actionConsumedKey = null
        promoteToForeground("Aalam starting")

        val projectionCode = intent.getIntExtra(EXTRA_PROJECTION_CODE, -1)
        val projectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }
        if (projectionCode == -1 || projectionData == null) {
            publishError("Screen capture permission was not granted", fatal = true)
            return
        }

        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(projectionCode, projectionData)
            if (projection == null) {
                publishError("Unable to create screen capture session", fatal = true)
                return
            }
            captureController = ScreenCaptureController(
                context = this,
                onFrame = ::onFrame,
                onStopped = {
                    if (generation == sessionGeneration && !isPaused && snapshot.state != RunState.STOPPED) {
                        stopMonitoring()
                    }
                },
            )
            captureController.start(projection)
            snapshot = snapshot.copy(state = RunState.RUNNING, errorMessage = null)
            promoteToForeground("Aalam is running")
            startFloatingPanel()
            publishSnapshot()
        } catch (_: Exception) {
            publishError("Aalam could not start screen capture", fatal = true)
        }
    }

    private fun onFrame(bitmap: android.graphics.Bitmap, width: Int, height: Int) {
        val generation = sessionGeneration
        if (isPaused || snapshot.state != RunState.RUNNING || processingJob?.isActive == true) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        processingJob = scope.launch {
            var processorOwnsBitmap = false
            try {
                frameMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    if (generation != sessionGeneration || isPaused || snapshot.state != RunState.RUNNING) {
                        return@withLock
                    }
                    processorOwnsBitmap = true
                    val result = frameProcessor.process(
                        bitmap,
                        configuration,
                        width,
                        height,
                        snapshot,
                    )
                    currentCoroutineContext().ensureActive()
                    if (generation != sessionGeneration || isPaused || snapshot.state != RunState.RUNNING) {
                        return@withLock
                    }
                    snapshot = result.snapshot.copy(state = RunState.RUNNING)
                    val qualified =
                        result.match.status == MatchStatus.MATCH &&
                            result.target.detected &&
                            result.target.confidence >= 0.72f &&
                            (result.price?.confidence ?: 0f) >= 0.72f

                    if (qualified) {
                        val region = result.target.safeRegion
                        val price = result.price?.price?.let {
                            String.format(Locale.US, "%.2f", it)
                        }
                        val key = "${region?.left}:${region?.top}:${region?.right}:${region?.bottom}:$price"
                        if (key == candidateKey) {
                            candidateFrameCount++
                        } else {
                            candidateKey = key
                            candidateFrameCount = 1
                            if (key != actionConsumedKey) actionConsumedKey = null
                        }
                        if (candidateFrameCount >= REQUIRED_STABLE_FRAMES &&
                            !actionInFlight &&
                            key != actionConsumedKey
                        ) {
                            actionInFlight = true
                            val action = clickController.click(result.target, width, height)
                            actionInFlight = false
                            if (generation == sessionGeneration && !isPaused && snapshot.state == RunState.RUNNING) {
                                // A gesture attempt consumes this exact screen state. A new
                                // target or a changed price must be observed before retrying.
                                actionConsumedKey = key
                                candidateFrameCount = 0
                                snapshot = snapshot.copy(
                                    diagnostics = snapshot.diagnostics.copy(
                                        action = if (action.success) ActionStatus.EXECUTED else ActionStatus.FAILED,
                                        lastMessage = action.message,
                                    ),
                                )
                            }
                        }
                    } else {
                        candidateKey = null
                        candidateFrameCount = 0
                        actionConsumedKey = null
                    }

                    snapshot = snapshot.copy(detectedPrice = result.detectedPrice)
                    publishSnapshot()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == sessionGeneration && !isPaused && snapshot.state == RunState.RUNNING) {
                    publishError("Frame analysis failed; monitoring is still safe")
                }
            } finally {
                if (!processorOwnsBitmap && !bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private fun pauseMonitoring() {
        if (snapshot.state == RunState.STOPPED) return
        isPaused = true
        sessionGeneration += 1
        candidateKey = null
        candidateFrameCount = 0
        actionConsumedKey = null
        actionInFlight = false
        processingJob?.cancel()
        snapshot = snapshot.copy(
            state = RunState.PAUSED,
            diagnostics = snapshot.diagnostics.copy(frame = DiagnosticState.STOPPED),
        )
        promoteToForeground("Aalam paused")
        publishSnapshot()
    }

    private fun resumeMonitoring() {
        if (!::captureController.isInitialized || snapshot.state == RunState.STOPPED) {
            publishError("Start Aalam from the app first")
            return
        }
        sessionGeneration += 1
        isPaused = false
        candidateKey = null
        candidateFrameCount = 0
        actionConsumedKey = null
        actionInFlight = false
        snapshot = snapshot.copy(state = RunState.RUNNING, errorMessage = null)
        promoteToForeground("Aalam is running")
        publishSnapshot()
    }

    private fun stopMonitoring() {
        if (snapshot.state == RunState.STOPPED) return
        sessionGeneration += 1
        isPaused = true
        candidateKey = null
        candidateFrameCount = 0
        actionConsumedKey = null
        actionInFlight = false
        processingJob?.cancel()
        if (::captureController.isInitialized) captureController.stop()
        clickController.reset()
        stopFloatingPanel()
        snapshot = RuntimeSnapshot(state = RunState.STOPPED)
        publishSnapshot()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishError(message: String, fatal: Boolean = false) {
        snapshot = snapshot.copy(
            state = RunState.ERROR,
            errorMessage = message,
            diagnostics = snapshot.diagnostics.copy(
                capture = DiagnosticState.ERROR,
                frame = DiagnosticState.STOPPED,
                lastMessage = message,
            ),
        )
        promoteToForeground(message)
        publishSnapshot()
        if (fatal) {
            stopFloatingPanel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startFloatingPanel() {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingPanelService::class.java))
        }
    }

    private fun stopFloatingPanel() {
        stopService(Intent(this, FloatingPanelService::class.java))
    }

    private fun publishSnapshot() {
        val extras = Bundle().apply {
            putString("state", snapshot.state.name)
            putString("error", snapshot.errorMessage)
            snapshot.detectedPrice?.let { putDouble("detectedPrice", it) }
            putString("message", snapshot.diagnostics.lastMessage)
            putFloat("confidence", snapshot.diagnostics.confidence)
            snapshot.diagnostics.latencyMs?.let { putLong("latency", it) }
            putString("ocr", snapshot.diagnostics.ocr.name)
            putString("openCv", snapshot.diagnostics.openCv.name)
            putString("action", snapshot.diagnostics.action.name)
            putString("price", snapshot.diagnostics.price.name)
            putString("priceMatch", snapshot.diagnostics.priceMatch.name)
            putString("target", snapshot.diagnostics.target.name)
            putBoolean("captureOn", snapshot.diagnostics.capture == DiagnosticState.ON)
        }
        sendBroadcast(
            Intent(ACTION_UPDATE).apply {
                setPackage(packageName)
                putExtras(extras)
            },
        )
    }

    private fun promoteToForeground(text: String) {
        val notification = notification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_autopilot)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.notification_channel_description)
                },
            )
        }
    }

    override fun onDestroy() {
        processingJob?.cancel()
        if (::captureController.isInitialized) captureController.stop()
        stopFloatingPanel()
        textDetector.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}