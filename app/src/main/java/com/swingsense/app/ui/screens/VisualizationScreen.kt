package com.swingsense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swingsense.app.ui.components.TrajectoryOverlay
import com.swingsense.app.viewmodel.SwingViewModel

@Composable
fun VisualizationScreen(
    viewModel: SwingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Visualisation du Swing",
                style = MaterialTheme.typography.headlineMedium
            )
            TextButton(onClick = { /* Navigate back */ }) {
                Text("Accueil")
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                )
        ) {
            if (uiState.trajectoryPoints.isNotEmpty()) {
                TrajectoryOverlay(
                    trajectoryPoints = uiState.trajectoryPoints.map { it.toOffset() },
                    predictedEndPoint = uiState.predictedEndPoint?.toOffset(),
                    direction = uiState.trajectoryDirection,
                    modifier = Modifier.fillMaxSize()
                )
                
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(
                            text = when (uiState.trajectoryDirection) {
                                com.swingsense.app.analysis.TrajectoryDirection.LEFT -> "DIRECTION: GAUCHE"
                                com.swingsense.app.analysis.TrajectoryDirection.STRAIGHT -> "DIRECTION: DROIT"
                                com.swingsense.app.analysis.TrajectoryDirection.RIGHT -> "DIRECTION: DROITE"
                            },
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Aucune trajectoire detectee")
                    Text(
                        "Enregistrez un swing pour voir la trajectoire",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.trajectoryPoints.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Points detectes:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${uiState.trajectoryPoints.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Deviation:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            String.format("%.1f deg", uiState.trajectoryDeviationDegrees),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(modifier = Modifier.weight(1f), onClick = { /* New swing */ }) {
                        Text("Nouveau Swing")
                    }
                    Button(modifier = Modifier.weight(1f), onClick = { /* Replay */ }) {
                        Text("Rejouer")
                    }
                }
            }
        }
    }
}