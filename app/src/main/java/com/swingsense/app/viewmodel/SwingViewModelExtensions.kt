package com.swingsense.app.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Extension pour ajouter des champs au UiState pour Phase 1: Sélection manuelle de balle
 */

enum class BallDetectionMode {
    AUTOMATIC,
    MANUAL
}

// À ajouter dans la data class UiState existante :
// val ballDetectionMode: BallDetectionMode = BallDetectionMode.AUTOMATIC
// val manualBallSelection: Triple<Float, Float, Float>? = null  // (x, y, radius)

// À ajouter dans SwingViewModel :

fun SwingViewModel.setBallDetectionMode(mode: BallDetectionMode) {
    _uiState.update { it.copy(ballDetectionMode = mode) }
}

fun SwingViewModel.setManualBallSelection(cx: Float, cy: Float, radius: Float) {
    _uiState.update { it.copy(manualBallSelection = Triple(cx, cy, radius)) }
}

fun SwingViewModel.clearManualBallSelection() {
    _uiState.update { it.copy(manualBallSelection = null) }
}
