package com.swingsense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swingsense.app.model.SwingResult
import com.swingsense.app.ui.components.*
import com.swingsense.app.ui.theme.*

@Composable
fun ResultsScreen(result: SwingResult, onSame: () -> Unit, onNewType: () -> Unit) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {

        Row {
            Text("SwingSense", color = TextDim, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "${result.club.label.uppercase()}  -  ${result.position.shortLabel}  -  ${result.units.label}  -  ${result.fps.toInt()} im/s  -  ${result.trackedFrames} pts",
                color = TextDim, fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(12.dp))

        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            if (result.tiles.isEmpty()) {
                Text("Aucune donnee exploitable sur ce swing.", color = Bad, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
            }
            result.tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    row.forEach { tile ->
                        MetricTile(tile, Modifier.weight(1f).padding(end = 10.dp))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f).padding(end = 10.dp)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            WarningBlock(result.warnings)
            Spacer(Modifier.height(10.dp))
            Text(
                "Pastille verte = mesure optique directe, orange = mesure derivee, " +
                    "rouge = estimation par modele.",
                color = TextDim, fontSize = 11.sp
            )
            result.videoPath?.let {
                Text("Video : $it", color = TextDim, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
        }

        Row {
            Box(Modifier.weight(1f)) { PrimaryButton("Refaire le meme swing", onClick = onSame) }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) { PrimaryButton("Changer d'analyse", onClick = onNewType) }
        }
    }
}
