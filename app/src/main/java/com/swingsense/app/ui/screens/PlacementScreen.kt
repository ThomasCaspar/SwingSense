package com.swingsense.app.ui.screens

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.swingsense.app.model.CameraPosition
import com.swingsense.app.sensors.LevelSensor
import com.swingsense.app.ui.components.LevelIndicator
import com.swingsense.app.ui.components.PrimaryButton
import com.swingsense.app.ui.components.SectionTitle
import com.swingsense.app.ui.theme.*
import kotlin.math.abs

/**
 * Placement du telephone : apercu camera REEL (pas un schema) avec l'horizon
 * mesure superpose, plus le niveau a bulle dans le panneau lateral.
 *
 * Retour de terrain : un schema abstrait ne suffisait pas a bien cadrer -
 * l'utilisateur a besoin de voir ce que la camera voit vraiment (la balle
 * est-elle dans le champ ? le cadrage laisse-t-il assez de marge ?) en plus
 * de savoir si le telephone est droit. D'ou l'apercu camera ici, avant meme
 * de lancer la capture de fond et la detection de la balle (ecran suivant).
 */
@Composable
fun PlacementScreen(
    position: CameraPosition,
    rollDeg: Float,
    levelAvailable: Boolean,
    onSurfaceReady: (android.view.Surface) -> Unit,
    onReady: () -> Unit,
    onBack: () -> Unit
) {
    Row(Modifier.fillMaxSize()) {

        Box(Modifier.weight(2f).fillMaxHeight().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.setFixedSize(1280, 720)
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(h: SurfaceHolder) = onSurfaceReady(h.surface)
                            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) = Unit
                            override fun surfaceDestroyed(h: SurfaceHolder) = Unit
                        })
                    }
                }
            )

            // Horizon superpose sur l'image reelle : s'aligne sur un repere
            // visible dans la scene (bord d'un tapis, ligne de terrain...) en
            // plus de la valeur numerique du panneau lateral.
            if (levelAvailable) {
                val ok = abs(rollDeg) <= LevelSensor.TOLERANCE_DEG
                val warnZone = abs(rollDeg) <= LevelSensor.TOLERANCE_DEG * 3
                val color = if (ok) Ok else if (warnZone) Warn else Bad
                Canvas(Modifier.fillMaxSize()) {
                    val cx = this.size.width / 2
                    val cy = this.size.height / 2
                    // Reference fixe (vrai horizontal de l'ecran), pointillee
                    drawLine(
                        Color(0x66FFFFFF), Offset(cx - 140, cy), Offset(cx + 140, cy),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                    // Horizon mesure, incline selon le roll reel
                    rotate(degrees = -rollDeg, pivot = Offset(cx, cy)) {
                        drawLine(color, Offset(cx - 160, cy), Offset(cx + 160, cy), strokeWidth = 3f)
                        drawCircle(color, radius = 5f, center = Offset(cx, cy))
                    }
                }
            }

            Box(
                Modifier.align(Alignment.BottomStart).padding(14.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color(0x99000000))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "Alignez l'horizon sur un repere reel de la scene (bord du tapis, ligne au sol...)",
                    color = TextDim, fontSize = 11.sp
                )
            }
        }

        Column(
            Modifier.weight(1f).fillMaxHeight().background(Bg).padding(18.dp)
        ) {
            SectionTitle("Placement du telephone")
            Text(position.label, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(10.dp))
            Text(position.help, color = TextDim, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Posez le telephone bien stable (trepied ou support). " +
                    "Un telephone qui bouge fausse toute la calibration metrique.",
                color = Warn, fontSize = 11.sp
            )

            Spacer(Modifier.height(18.dp))
            if (levelAvailable) {
                LevelIndicator(rollDeg = rollDeg, diameter = 90.dp)
            } else {
                Text(
                    "Niveau a bulle indisponible sur cet appareil - alignez le " +
                        "telephone a l'oeil sur l'horizon.",
                    color = TextDim, fontSize = 11.sp
                )
            }

            Spacer(Modifier.weight(1f))
            PrimaryButton("Je suis en place", onClick = onReady)
        }
    }
}
