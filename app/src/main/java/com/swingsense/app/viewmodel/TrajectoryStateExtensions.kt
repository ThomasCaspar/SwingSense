package com.swingsense.app.viewmodel

import com.swingsense.app.analysis.TrajectoryDirection
import com.swingsense.app.vision.TrajectoryPoint
import kotlinx.coroutines.flow.update

// Extensions pour gérer l'état de trajectoire dans SwingViewModel
// À ajouter à UiState :
// val trajectoryPoints: List<TrajectoryPoint> = emptyList()
// val trajectoryDirection: TrajectoryDirection = TrajectoryDirection.STRAIGHT
// val trajectoryDeviationDegrees: Float = 0f
// val predictedEndPoint: TrajectoryPoint? = null

fun SwingViewModel.setTrajectoryData(
    points: List<TrajectoryPoint>,
    direction: TrajectoryDirection,
    deviationDegrees: Float,
    predictedEnd: TrajectoryPoint?
) {
    _uiState.update {
        it.copy(
            trajectoryPoints = points,
            trajectoryDirection = direction,
            trajectoryDeviationDegrees = deviationDegrees,
            predictedEndPoint = predictedEnd
        )
    }
}
