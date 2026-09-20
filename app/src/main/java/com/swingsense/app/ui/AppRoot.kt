package com.swingsense.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swingsense.app.sensors.LevelSensor
import com.swingsense.app.ui.screens.*
import com.swingsense.app.ui.theme.Bg
import com.swingsense.app.viewmodel.Step
import com.swingsense.app.viewmodel.SwingViewModel

@Composable
fun AppRoot(vm: SwingViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    // Niveau a bulle : actif seulement pendant le placement et la calibration,
    // la ou l'utilisateur regarde encore l'ecran et peut corriger la position.
    val context = LocalContext.current
    val levelSensor = remember { LevelSensor(context) }
    DisposableEffect(state.step) {
        levelSensor.onReading = { r -> vm.updateLevel(r.rollDeg, r.pitchDeg) }
        vm.setLevelAvailable(levelSensor.available)
        if (state.step == Step.PLACEMENT || state.step == Step.CALIBRATION) {
            levelSensor.start()
        }
        onDispose { levelSensor.stop() }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        when (state.step) {
            Step.SETUP -> SetupScreen(
                state = state,
                onToggleMetric = vm::toggleMetric,
                onClub = vm::setClub,
                onBall = vm::setBallColor,
                onUnits = vm::setUnits,
                onNext = { vm.goTo(Step.PLACEMENT) }
            )

            Step.PLACEMENT -> {
                PlacementScreen(
                    position = state.requiredPosition,
                    rollDeg = state.rollDeg,
                    levelAvailable = state.levelAvailable,
                    onSurfaceReady = { surface -> vm.startPreview(surface) },
                    onReady = { vm.goTo(Step.CALIBRATION) },
                    onBack = { vm.goTo(Step.SETUP) }
                )
                DisposableEffect(Unit) { onDispose { vm.stopPreview() } }
            }

            Step.CALIBRATION -> {
                CalibrationScreen(
                    state = state,
                    onSurfaceReady = { surface -> vm.startCalibration(surface) },
                    onTap = vm::onCalibrationTap,
                    onClearSeed = vm::clearSeed,
                    onRecaptureBackground = vm::recaptureBackground,
                    onCancel = { vm.goTo(Step.SETUP) }
                )
                DisposableEffect(Unit) { onDispose { vm.stopCalibration() } }
            }

            Step.WAITING -> WaitingScreen(state = state, onFinish = vm::finishSwing, onCancel = vm::cancelSwing)

            Step.PROCESSING -> ProcessingScreen(state.progress, state.progressLabel, onCancel = vm::cancelAnalysis)

            Step.RESULTS -> state.result?.let {
                ResultsScreen(it, onSame = vm::repeatSameSwing, onNewType = vm::newSwingType)
            }
        }
    }
}
