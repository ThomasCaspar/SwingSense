package com.swingsense.app.ui.screens

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.swingsense.app.analysis.CalibrationGuidance
import com.swingsense.app.camera.CalibrationPhase
import com.swingsense.app.ui.components.SectionTitle
import com.swingsense.app.ui.theme.*
import com.swingsense.app.viewmodel.UiState

/**
 * Calibration en deux temps, revue apres retours de terrain (l'ancien
 * seuillage colorimetrique echouait sur l'herbe).
 *
 * 1. CAPTURING_BACKGROUND : le golfeur ne pose pas encore la balle ; l'app
 *    photographie le sol tel qu'il est (herbe, tapis, ombres). C'est cette
 *    reference qui remplace la detection par couleur.
 * 2. AWAITING_BALL / verrouillage : des que la balle est posee, elle differe
 *    du fond et ressort immediatement du diff, quelle que soit la couleur du
 *    terrain en dessous. Le cercle affiche en direct permet de verifier -
 *    et d'ajuster si besoin.
 *
 * Le pointage au doigt reste disponible, mais en simple aide facultative :
 * il ne bloque plus rien.
 */
@Composable
fun CalibrationScreen(
    state: UiState,
    onSurfaceReady: (android.view.Surface) -> Unit,
    onTap: (Float, Float) -> Unit,
    onClearSeed: () -> Unit,
    onRecaptureBackground: () -> Unit,
    onCancel: () -> Unit
) {
    Row(Modifier.fillMaxSize()) {

        Box(
            Modifier
                .weight(2f)
                .fillMaxHeight()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onTap(offset.x / size.width, offset.y / size.height)
                    }
                }
        ) {
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

            Canvas(Modifier.fillMaxSize()) {
                // Secteur de recherche designe au doigt (aide facultative)
                state.seed?.let { seed ->
                    val half = size.width * 0.12f
                    val c = Offset(seed.x * size.width, seed.y * size.height)
                    drawRect(
                        color = AccentBlue.copy(alpha = 0.9f),
                        topLeft = Offset(c.x - half, c.y - half),
                        size = GeomSize(half * 2, half * 2),
                        style = Stroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                        )
                    )
                    drawLine(AccentBlue, Offset(c.x - 14, c.y), Offset(c.x + 14, c.y), strokeWidth = 2f)
                    drawLine(AccentBlue, Offset(c.x, c.y - 14), Offset(c.x, c.y + 14), strokeWidth = 2f)
                }

                if (state.calibrationPhase != CalibrationPhase.CAPTURING_BACKGROUND) {
                    val det = state.detection ?: return@Canvas
                    val sx = size.width / state.analysisWidth
                    val sy = size.height / state.analysisHeight
                    val c = Offset(det.cx.toFloat() * sx, det.cy.toFloat() * sy)
                    val r = (det.radiusPx * sx).toFloat().coerceAtLeast(6f)
                    val color = if (state.guidance == CalibrationGuidance.OK) Ok else Warn
                    drawCircle(color, radius = r * 2.2f, center = c, style = Stroke(width = 3f))
                    drawCircle(color, radius = 4f, center = c)
                }
            }

            if (state.calibrationPhase == CalibrationPhase.CAPTURING_BACKGROUND) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(18.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Ne posez pas encore la balle - capture du fond...",
                        color = AccentOrange, fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                }
            } else if (state.detection == null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(18.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Posez la balle dans le champ",
                        color = Ok, fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(
            Modifier.weight(1f).fillMaxHeight().background(Bg).padding(18.dp)
        ) {
            SectionTitle("Calibration de la balle")

            when (state.calibrationPhase) {
                CalibrationPhase.CAPTURING_BACKGROUND -> {
                    Text(
                        "Capture du fond en cours",
                        color = AccentOrange, fontSize = 17.sp, fontWeight = FontWeight.Light
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ne posez pas encore la balle : l'app memorise le sol tel qu'il est " +
                            "(herbe, tapis, ombres), pour detecter ensuite la balle par " +
                            "difference plutot que par sa couleur.",
                        color = TextDim, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { state.backgroundProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = AccentOrange,
                        trackColor = Surface2
                    )
                }
                CalibrationPhase.AWAITING_BALL, CalibrationPhase.TRACKING -> {
                    Text(
                        state.guidance.message,
                        color = if (state.guidance == CalibrationGuidance.OK) Ok else TextPrimary,
                        fontSize = 17.sp, fontWeight = FontWeight.Light
                    )
                    Spacer(Modifier.height(10.dp))
                    state.detection?.let {
                        Text("Rayon apparent : %.1f px".format(it.radiusPx), color = TextDim, fontSize = 12.sp)
                        Text("Cible : 7 a 22 px", color = TextDim, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Verrouillage", color = TextDim, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.lockProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Ok,
                        trackColor = Surface2
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row {
                TextButton(onClick = onRecaptureBackground) {
                    Text("Recapturer le fond", color = AccentOrange, fontSize = 12.sp)
                }
            }
            Row {
                TextButton(onClick = onClearSeed, enabled = state.seed != null) {
                    Text("Effacer le pointage", color = AccentBlue, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "La balle est reperee par difference au fond capture juste avant. " +
                    "Si un autre objet est detecte a la place (chaussure, club), touchez " +
                    "la balle a l'ecran pour restreindre la recherche a son secteur.",
                color = TextDim, fontSize = 11.sp, lineHeight = 16.sp
            )

            Spacer(Modifier.weight(1f))
            if (state.levelAvailable) {
                val rollOk = kotlin.math.abs(state.rollDeg) <= com.swingsense.app.sensors.LevelSensor.TOLERANCE_DEG
                Text(
                    "Niveau : %+.1f° %s".format(state.rollDeg, if (rollOk) "(OK)" else "(a corriger)"),
                    color = if (rollOk) Ok else Warn, fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
            }
            state.error?.let {
                Text(it, color = Bad, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Balle : ${state.ballColor.label}  -  Club : ${state.club.label}  -  ${state.units.label}",
                color = TextDim, fontSize = 11.sp
            )
        }
    }
}
