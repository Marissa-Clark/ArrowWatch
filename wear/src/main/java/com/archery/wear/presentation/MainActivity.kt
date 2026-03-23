package com.archery.wear.presentation

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.archery.shared.WatchPhase
import com.archery.wear.SessionViewModel
import com.archery.wear.data.SessionLogger
import com.archery.wear.presentation.theme.ArcheryTheme
import com.archery.wear.sensor.WatchSensorManager
import com.archery.wear.sync.LiveSyncManager

class MainActivity : ComponentActivity() {

    private lateinit var sensorMgr: WatchSensorManager
    private lateinit var sessionLogger: SessionLogger
    private lateinit var liveSyncMgr: LiveSyncManager

    private val viewModel: SessionViewModel by viewModels()

    private var sensorsRunning = false

    // Request permissions opportunistically — app runs regardless of outcome.
    // Sensors silently return 0 if denied; user can grant later via Settings.
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op — sensors work if granted, return 0 if not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        sensorMgr = WatchSensorManager(this)
        sessionLogger = SessionLogger(this)
        liveSyncMgr = LiveSyncManager(this)

        viewModel.sensorManager = sensorMgr
        viewModel.logger = sessionLogger
        viewModel.liveSyncManager = liveSyncMgr

        sensorMgr.onSensorUpdate = { yaw, pitch, roll, gz, steps, gx, gy ->
            sessionLogger.logSensor(System.currentTimeMillis(), yaw, pitch, roll, gz, steps, gx, gy)
        }
        sensorMgr.onStepDetected = { viewModel.onStepDetected() }

        // Ask for permissions; don't gate the UI on the result.
        permLauncher.launch(
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION,
                "android.permission.health.READ_HEART_RATE",
            )
        )

        setContent {
            val phase by viewModel.phase.collectAsState()
            val session by viewModel.session.collectAsState()
            val arrowsPerRound by viewModel.arrowsPerRound.collectAsState()
            val showQuickScore by viewModel.showQuickScore.collectAsState()
            val previousRoundInfo by viewModel.previousRoundInfo.collectAsState()
            val heartRate by sensorMgr.heartRate.collectAsState()

            // Manage sensor lifecycle and screen-on flag with session phase
            LaunchedEffect(phase) {
                when (phase) {
                    WatchPhase.SHOOTING, WatchPhase.SCORING -> {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        if (phase == WatchPhase.SHOOTING) startSensors()
                        // SCORING: keep sensors running, screen stays on
                    }
                    WatchPhase.SUMMARY, WatchPhase.IDLE -> {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        stopSensors()
                    }
                }
            }

            // Forward HR readings to ViewModel
            LaunchedEffect(heartRate) {
                viewModel.recordHeartRate(heartRate)
            }

            // Log every raw HR sensor event to the session CSV
            LaunchedEffect(Unit) {
                sensorMgr.heartRateEvents.collect { bpm ->
                    sessionLogger.logHeartRate(System.currentTimeMillis(), bpm)
                }
            }

            val isApprox = session?.isScoreApprox ?: false

            ArcheryTheme {
                when (phase) {
                    WatchPhase.IDLE -> StartScreen(
                        arrowsPerRound = arrowsPerRound,
                        onArrowsChanged = viewModel::setArrowsPerRound,
                        onStart = viewModel::startSession,
                    )
                    WatchPhase.SHOOTING -> ShootingScreen(
                        shotCount = session?.currentRound?.shots?.size ?: 0,
                        arrowsPerRound = arrowsPerRound,
                        roundNumber = session?.currentRound?.number ?: 1,
                        heartRate = heartRate,
                        previousRoundInfo = previousRoundInfo,
                        totalScore = session?.totalScore ?: 0f,
                        avgPerArrow = session?.avgPerArrow ?: 0f,
                        isApprox = isApprox,
                        showQuickScore = showQuickScore,
                        onQuickScore = viewModel::quickScore,
                        onDismissQuickScore = viewModel::dismissQuickScore,
                        onManualShot = viewModel::manualShot,
                        onEnterScoring = viewModel::enterScoring,
                        onEndSession = viewModel::endSession,
                    )
                    WatchPhase.SCORING -> ScoreScreen(
                        round = session?.currentRound,
                        onScoreArrow = { idx, zone -> viewModel.scoreArrow(idx, zone) },
                        onAddArrow = viewModel::addScoringArrow,
                        onRemoveArrow = viewModel::removeScoringArrow,
                        onSetTotal = viewModel::setRoundTotal,
                        onFinish = viewModel::finishScoring,
                        onSkip = viewModel::skipScoring,
                    )
                    WatchPhase.SUMMARY -> SummaryScreen(
                        session = session,
                        onNewSession = viewModel::newSession,
                    )
                }
            }
        }
    }

    private fun startSensors() {
        if (!sensorsRunning) {
            sensorMgr.start()
            sensorsRunning = true
        }
    }

    private fun stopSensors() {
        if (sensorsRunning) {
            sensorMgr.stop()
            sensorsRunning = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSensors()
        sessionLogger.shutdown()
        liveSyncMgr.shutdown()
    }
}
