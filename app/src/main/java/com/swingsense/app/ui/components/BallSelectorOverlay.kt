package com.swingsense.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * Composant Compose pour la sélection manuelle de la balle.
 * Permet à l'utilisateur de dessiner un cercle autour de la balle et d'ajuster sa position/taille.
 * @param onBallSelected Callback quand la balle est sélectionnée (centerX, centerY, radius)
 */
@Composable
fun BallSelectorOverlay(
    onBallSelected: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDrawing by remember { mutableStateOf(false) }
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }
    var selectedBall by remember { mutableStateOf<Pair<Offset, Float>?>(null) }
    var isDraggingCenter by remember { mutableStateOf(false) }
    var isDraggingEdge by remember { mutableStateOf(false) }
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Vérifier si on clique sur le centre du cercle existant
                        selectedBall?.let { (center, radius) ->
                            val distance = sqrt(
                                (offset.x - center.x) * (offset.x - center.x) +
                                        (offset.y - center.y) * (offset.y - center.y)
                            )
                            
                            isDraggingCenter = distance < radius * 0.3f
                            isDraggingEdge = !isDraggingCenter && distance in (radius * 0.9f)..(radius * 1.1f)
                        }
                        
                        // Sinon, commencer à dessiner un nouveau cercle
                        if (!isDraggingCenter && !isDraggingEdge) {
                            isDrawing = true
                            startPoint = offset
                            currentPoint = offset
                            selectedBall = null
                        }
                    },
                    onDrag = { change, _ ->
                        currentPoint = change.position
                        
                        // Déplacer le centre
                        if (isDraggingCenter) {
                            selectedBall?.let { (center, radius) ->
                                val delta = change.position - currentPoint!!
                                selectedBall = Pair(center + delta, radius)
                            }
                        }
                        // Redimensionner le rayon
                        else if (isDraggingEdge) {
                            selectedBall?.let { (center, radius) ->
                                val newRadius = sqrt(
                                    (change.position.x - center.x) * (change.position.x - center.x) +
                                            (change.position.y - center.y) * (change.position.y - center.y)
                                )
                                selectedBall = Pair(center, newRadius)
                            }
                        }
                    },
                    onDragEnd = {
                        if (isDrawing && startPoint != null && currentPoint != null) {
                            // Calculer le cercle
                            val center = ((startPoint!! + currentPoint!!) / 2f)
                            val radius = sqrt(
                                (currentPoint!!.x - startPoint!!.x) * (currentPoint!!.x - startPoint!!.x) +
                                        (currentPoint!!.y - startPoint!!.y) * (currentPoint!!.y - startPoint!!.y)
                            ) / 2f
                            
                            if (radius > 10f) {
                                selectedBall = Pair(center, radius)
                            }
                        }
                        
                        isDrawing = false
                        isDraggingCenter = false
                        isDraggingEdge = false
                    }
                )
            }
    ) {
        // Afficher le cercle en cours de dessin
        if (isDrawing && startPoint != null && currentPoint != null) {
            val radius = sqrt(
                (currentPoint!!.x - startPoint!!.x) * (currentPoint!!.x - startPoint!!.x) +
                        (currentPoint!!.y - startPoint!!.y) * (currentPoint!!.y - startPoint!!.y)
            ) / 2f
            val center = (startPoint!! + currentPoint!!) / 2f
            
            drawCircle(
                color = Color.Yellow,
                radius = radius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
        }
        
        // Afficher le cercle sélectionné
        selectedBall?.let { (center, radius) ->
            // Cercle principal (vert)
            drawCircle(
                color = Color.Green,
                radius = radius,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )
            
            // Marqueur de centre
            drawCircle(
                color = Color.Green,
                radius = 6.dp.toPx(),
                center = center
            )
            
            // Marqueur d'arête (pour redimensionner)
            drawCircle(
                color = Color.Green,
                radius = 4.dp.toPx(),
                center = Offset(center.x + radius, center.y)
            )
        }
    }
    
    // Bouton de confirmation (à intégrer dans un parent LazyColumn ou Column)
    selectedBall?.let { (center, radius) ->
        // Callback peut être déclenché via un bouton parent
        LaunchedEffect(selectedBall) {
            // Le parent doit exposer un bouton "Confirmer" qui appelle :
            // onBallSelected(center.x, center.y, radius)
        }
    }
}
