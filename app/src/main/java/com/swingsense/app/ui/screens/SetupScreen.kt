package com.swingsense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swingsense.app.model.*
import com.swingsense.app.ui.components.*
import com.swingsense.app.ui.theme.*
import com.swingsense.app.viewmodel.UiState

/**
 * Premier ecran : le golfeur choisit CE QU'IL VEUT MESURER.
 * C'est la selection qui determine ou poser le telephone, jamais l'inverse.
 */
@Composable
fun SetupScreen(
    state: UiState,
    onToggleMetric: (Metric) -> Unit,
    onClub: (Club) -> Unit,
    onBall: (BallColor) -> Unit,
    onUnits: (UnitSystem) -> Unit,
    onNext: () -> Unit
) {
    val scroll = rememberScrollState()
    Row(Modifier.fillMaxSize().padding(16.dp)) {

        Column(Modifier.weight(1.4f).verticalScroll(scroll)) {
            Text("SwingSense", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Text("CONFIGURATION", color = AccentOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))

            SectionTitle("Donnees a mesurer - camera face au golfeur")
            MetricFlow(Metric.entries.filter { it.position == CameraPosition.FACE_ON },
                state.selectedMetrics, onToggleMetric)

            Spacer(Modifier.height(16.dp))
            SectionTitle("Donnees a mesurer - camera derriere le golfeur", AccentBlue)
            MetricFlow(Metric.entries.filter { it.position == CameraPosition.DOWN_THE_LINE },
                state.selectedMetrics, onToggleMetric)

            Spacer(Modifier.height(8.dp))
            Text(
                "Une session = une position de telephone. Selectionner une donnee de " +
                    "l'autre famille reinitialise la selection.",
                color = TextDim, fontSize = 11.sp
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("Club utilise")
            MetricFlowGeneric(Club.entries.map { it.label }, state.club.label) { label ->
                Club.entries.first { it.label == label }.let(onClub)
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Unites")
            Row {
                UnitSystem.entries.forEach { u ->
                    ChoiceChip(
                        label = u.label,
                        selected = u == state.units,
                        onClick = { onUnits(u) },
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                }
                Spacer(Modifier.weight(2f))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.units == UnitSystem.METRIC) "Vitesses en km/h, distances en metres."
                else "Vitesses en mph, distances en yards.",
                color = TextDim, fontSize = 11.sp
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("Couleur de la balle")
            MetricFlowGeneric(BallColor.entries.map { it.label }, state.ballColor.label) { label ->
                BallColor.entries.first { it.label == label }.let(onBall)
            }
            Spacer(Modifier.height(24.dp))
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            SectionTitle("Capteur")
            if (state.highSpeedSupported) {
                Text(
                    state.highSpeedOption?.toString() ?: "-",
                    color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cadence maximale detectee sur cet appareil. Toute la precision " +
                        "de l'analyse en depend.",
                    color = TextDim, fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                state.availableOptions.take(4).forEach {
                    Text("- $it", color = TextDim, fontSize = 11.sp)
                }
            } else {
                Text(
                    "Aucune session haute vitesse disponible sur cet appareil. " +
                        "L'analyse sera imprecise.",
                    color = Bad, fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Position requise")
            Text(state.requiredPosition.label, color = TextPrimary, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(state.requiredPosition.help, color = TextDim, fontSize = 12.sp)

            Spacer(Modifier.weight(1f))
            PrimaryButton(
                "Confirmer & demarrer la session",
                enabled = state.selectedMetrics.isNotEmpty(),
                onClick = onNext
            )
        }
    }
}

@Composable
private fun MetricFlow(metrics: List<Metric>, selected: Set<Metric>, onToggle: (Metric) -> Unit) {
    Column {
        metrics.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                row.forEach { m ->
                    ChoiceChip(
                        label = m.label,
                        selected = m in selected,
                        onClick = { onToggle(m) },
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MetricFlowGeneric(labels: List<String>, selected: String, onPick: (String) -> Unit) {
    Column {
        labels.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                row.forEach { l ->
                    ChoiceChip(
                        label = l,
                        selected = l == selected,
                        onClick = { onPick(l) },
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
