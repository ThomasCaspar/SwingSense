package com.swingsense.app.analysis

import com.swingsense.app.vision.TrajectoryPoint
import kotlin.math.atan2
import kotlin.math.sqrt

object TrajectoryPredictor {
    
    fun predictFinalPosition(
        trajectoryPoints: List<TrajectoryPoint>,
        framesAhead: Int = 30,
        frameScale: Float = 100f
    ): TrajectoryPoint {
        if (trajectoryPoints.isEmpty()) return TrajectoryPoint(0f, 0f, 0)
        
        val recentPoints = trajectoryPoints.takeLast(minOf(10, trajectoryPoints.size))
        
        if (recentPoints.size < 2) {
            return trajectoryPoints.last()
        }
        
        val (velocityX, velocityY) = estimateVelocity(recentPoints)
        val decelerationFactor = 0.98f
        
        val lastPoint = trajectoryPoints.last()
        var predictedX = lastPoint.x
        var predictedY = lastPoint.y
        var currentVelX = velocityX
        var currentVelY = velocityY
        
        repeat(framesAhead) {
            predictedX += currentVelX
            predictedY += currentVelY
            currentVelX *= decelerationFactor
            currentVelY *= decelerationFactor
            
            if (sqrt(currentVelX * currentVelX + currentVelY * currentVelY) < 0.5f) {
                return@repeat
            }
        }
        
        return TrajectoryPoint(predictedX, predictedY, trajectoryPoints.size + framesAhead)
    }
    
    private fun estimateVelocity(points: List<TrajectoryPoint>): Pair<Float, Float> {
        val n = points.size
        if (n < 2) return Pair(0f, 0f)
        
        var sumX = 0f
        var sumY = 0f
        var sumXY = 0f
        var sumX2 = 0f
        
        for (i in 0 until n - 1) {
            val dx = points[i + 1].x - points[i].x
            val dy = points[i + 1].y - points[i].y
            
            sumX += dx
            sumY += dy
            sumXY += dx * dy
            sumX2 += dx * dx
        }
        
        val avgX = sumX / (n - 1)
        val avgY = sumY / (n - 1)
        
        return Pair(avgX, avgY)
    }
    
    fun determineDirection(
        startPoint: TrajectoryPoint,
        endPoint: TrajectoryPoint,
        centerX: Float = 960f
    ): TrajectoryDirection {
        val deviationFromCenter = endPoint.x - centerX
        val threshold = 50f
        
        return when {
            deviationFromCenter < -threshold -> TrajectoryDirection.LEFT
            deviationFromCenter > threshold -> TrajectoryDirection.RIGHT
            else -> TrajectoryDirection.STRAIGHT
        }
    }
    
    fun calculateDeviationAngle(trajectoryPoints: List<TrajectoryPoint>): Float {
        if (trajectoryPoints.size < 2) return 0f
        
        val start = trajectoryPoints.first()
        val end = trajectoryPoints.last()
        
        val dx = end.x - start.x
        val dy = end.y - start.y
        val angleRad = atan2(dy, dx)
        val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
        
        return (angleDeg - 90f)
    }
}