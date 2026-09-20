package com.swingsense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.swingsense.app.viewmodel.SwingViewModel

/**
 * Écran de lecture vidéo en slow-motion avec tracé de trajectoire et stats.
 */
@Composable
fun ReplayScreen(
    viewModel: SwingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(true) }
    
    // À adapter avec le contexte réel
    // LaunchedEffect(uiState.swingResults?.videoPath) {
    //     val videoPath = uiState.swingResults?.videoPath
    //     if (videoPath != null && exoPlayer == null) {
    //         exoPlayer = ExoPlayer.Builder(context).build().apply {
    //             setMediaItem(MediaItem.fromUri(videoPath))
    //             prepare()
    //         }
    //     }
    // }
    
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Titre et mode
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
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "▶️ Replay - Slow Motion",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = when (uiState.trajectoryDirection) {
                            com.swingsense.app.analysis.TrajectoryDirection.LEFT -> "Direction: ⬅️ Gauche"
                            com.swingsense.app.analysis.TrajectoryDirection.STRAIGHT -> "Direction: ⬆️ Droit"
                            com.swingsense.app.analysis.TrajectoryDirection.RIGHT -> "Direction: ➡️ Droite"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                IconButton(
                    onClick = { showStats = !showStats }
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Toggle Stats",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        // Conteneur vidéo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            if (exoPlayer != null) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            player = exoPlayer
                            useController = true
                            controllerShowTimeoutMs = 5000
                            controllerHideOnTouch = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📹 Vidéo non disponible",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Chemin vidéo: ${uiState.swingResults?.videoPath ?: "Non défini"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        
        // Stats de trajectoire
        if (showStats && uiState.trajectoryPoints.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Points détectés:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${uiState.trajectoryPoints.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Déviation:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            String.format("%.1f°", uiState.trajectoryDeviationDegrees),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Distance estimée:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${uiState.swingResults?.distance?.toInt() ?: "--"} m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        // Boutons de contrôle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    exoPlayer?.apply {
                        if (isPlaying) pause() else play()
                        isPlaying = !isPlaying
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Button(
                onClick = {
                    exoPlayer?.pause()
                    // viewModel.updateScreen(Screen.ResultsScreen)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Retour")
            }
        }
    }
}