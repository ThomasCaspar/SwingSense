package com.swingsense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.swingsense.app.audio.Announcer
import com.swingsense.app.data.SwingResults
import com.swingsense.app.ui.theme.SwingSenseTheme
import com.swingsense.app.viewmodel.SwingViewModel

/**
 * Écran de résultats amélioré avec TTS et lecteur vidéo.
 */
@Composable
fun ResultsScreenV2(
    viewModel: SwingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val results = uiState.swingResults ?: return
    
    var announcer by remember { mutableStateOf<Announcer?>(null) }
    var hasAnnounced by remember { mutableStateOf(false) }
    
    // Initialiser l'Announcer et faire l'annonce au chargement
    LaunchedEffect(results) {
        if (!hasAnnounced && announcer == null) {
            // Créer l'announcer (à adapter avec Context réel)
            // announcer = Announcer(context)
            // announcer?.announceSwingResults(
            //     clubHeadSpeed = results.clubHeadSpeed,
            //     ballSpeed = results.ballSpeed,
            //     smash = results.smashFactor,
            //     distance = results.distance,
            //     direction = "Droit"
            // )
            hasAnnounced = true
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            announcer?.release()
        }
    }
    
    SwingSenseTheme {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📊 Résultats du Swing",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    IconButton(
                        onClick = {
                            announcer?.announceSwingResults(
                                clubHeadSpeed = results.clubHeadSpeed,
                                ballSpeed = results.ballSpeed,
                                smash = results.smashFactor,
                                distance = results.distance,
                                direction = "Droit"
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Réécouter",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // Cartes de résultats
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Vitesse tête du club
                results.clubHeadSpeed?.let {
                    ResultCard(
                        title = "Vitesse tête du club",
                        value = "${it.toInt()} km/h",
                        icon = "🚀"
                    )
                }
                
                // Vitesse de la balle
                results.ballSpeed?.let {
                    ResultCard(
                        title = "Vitesse de la balle",
                        value = "${it.toInt()} km/h",
                        icon = "⚡"
                    )
                }
                
                // Smash factor
                results.smashFactor?.let {
                    ResultCard(
                        title = "Facteur de contact",
                        value = String.format("%.2f", it),
                        icon = "🎯"
                    )
                }
                
                // Distance
                results.distance?.let {
                    ResultCard(
                        title = "Distance estimée",
                        value = "${it.toInt()} m",
                        icon = "📏"
                    )
                }
                
                // Direction
                ResultCard(
                    title = "Direction",
                    value = "Droit",
                    icon = "⬆️"
                )
            }
            
            // Boutons d'action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        // Naviguer vers replay
                        // viewModel.updateScreen(Screen.ReplayScreen)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = results.videoPath != null
                ) {
                    Text("▶️ Replay")
                }
                
                Button(
                    onClick = {
                        // Naviguer vers nouveau swing
                        // viewModel.resetForNewSwing()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📹 Nouveau")
                }
            }
        }
    }
}

@Composable
fun ResultCard(
    title: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineLarge
            )
        }
    }
}