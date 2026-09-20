package com.swingsense.app.vision

import androidx.compose.ui.geometry.Offset

data class TrajectoryPoint(
    val x: Float,
    val y: Float,
    val frameIndex: Int
) {
    fun toOffset(): Offset = Offset(x, y)
}