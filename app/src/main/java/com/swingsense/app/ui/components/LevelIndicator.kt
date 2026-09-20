package com.swingsense.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swingsense.app.sensors.LevelSensor
import com.swingsense.app.ui.theme.*
import kotlin.math.abs

/**
 * Niveau a bulle : bloc visuel + numerique, utilise sur l'ecran de placement
 * et en rappel discret pendant la calibration.
 */
@Composable
fun LevelIndicator(rollDeg: Float, diameter: Dp = 110.dp, modifier: Modifier = Modifier) {
    val ok = abs(rollDeg) <= LevelSensor.TOLERANCE_DEG
    val warnZone = abs(rollDeg) <= LevelSensor.TOLERANCE_DEG * 3
    val color = if (ok) Ok else if (warnZone) Warn else Bad

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(diameter)) {
            // size ici est celle du DrawScope (px reels du canvas), pas le
            // parametre `diameter` (Dp) - deja converti par le Modifier.size ci-dessus.
            val w = size.width
            val h = size.height
            drawCircle(
                Color(0xFF2A3139), radius = w / 2 - 4,
                center = Offset(w / 2, h / 2), style = Stroke(width = 2f)
            )
            // Reference fixe (pointilles) - "vrai" horizontal
            drawLine(
                Color(0xFF3A4149), Offset(8f, h / 2), Offset(w - 8f, h / 2),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            // Ligne mobile : horizon reel mesure
            rotate(degrees = -rollDeg, pivot = Offset(w / 2, h / 2)) {
                drawLine(color, Offset(12f, h / 2), Offset(w - 12f, h / 2), strokeWidth = 4f)
                drawCircle(color, radius = 6f, center = Offset(w / 2, h / 2))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (ok) "Niveau OK" else "Inclinez le telephone",
            color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
        Text("%+.1f°".format(rollDeg), color = TextDim, fontSize = 11.sp)
    }
}
