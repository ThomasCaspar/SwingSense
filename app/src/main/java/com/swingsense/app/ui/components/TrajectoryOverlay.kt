package com.swingsense.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.swingsense.app.analysis.TrajectoryDirection
import kotlin.math.sqrt

@Composable
fun TrajectoryOverlay(
    trajectoryPoints: List<Offset>,
    predictedEndPoint: Offset? = null,
    direction: TrajectoryDirection = TrajectoryDirection.STRAIGHT,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (trajectoryPoints.size < 2) return@Canvas
        
        drawTrajectoryLine(trajectoryPoints)
        drawTrajectoryPoints(trajectoryPoints)
        
        if (predictedEndPoint != null) {
            drawPrediction(trajectoryPoints.last(), predictedEndPoint, direction)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrajectoryLine(
    points: List<Offset>
) {
    for (i in 0 until points.size - 1) {
        drawLine(
            color = Color.Red,
            start = points[i],
            end = points[i + 1],
            strokeWidth = 4.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrajectoryPoints(
    points: List<Offset>
) {
    val step = maxOf(1, points.size / 10)
    
    points.forEachIndexed { index, point ->
        if (index % step == 0 || index == points.lastIndex) {
            drawCircle(color = Color.White, radius = 6.dp.toPx(), center = point)
            drawCircle(
                color = Color.Red,
                radius = 6.dp.toPx(),
                center = point,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPrediction(
    lastPoint: Offset,
    predictedEnd: Offset,
    direction: TrajectoryDirection
) {
    val dashInterval = 12.dp.toPx()
    val distance = sqrt(
        (predictedEnd.x - lastPoint.x) * (predictedEnd.x - lastPoint.x) +
                (predictedEnd.y - lastPoint.y) * (predictedEnd.y - lastPoint.y)
    )
    
    val steps = (distance / dashInterval).toInt()
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val startX = lastPoint.x + (predictedEnd.x - lastPoint.x) * t
        val startY = lastPoint.y + (predictedEnd.y - lastPoint.y) * t
        val nextT = (i + 0.5f) / steps
        val endX = lastPoint.x + (predictedEnd.x - lastPoint.x) * nextT
        val endY = lastPoint.y + (predictedEnd.y - lastPoint.y) * nextT
        
        drawLine(
            color = Color.Cyan,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 2.dp.toPx()
        )
    }
    
    drawCircle(
        color = Color.Cyan,
        radius = 8.dp.toPx(),
        center = predictedEnd,
        style = Stroke(width = 2.dp.toPx())
    )
}