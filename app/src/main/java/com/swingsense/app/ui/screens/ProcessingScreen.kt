package com.swingsense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swingsense.app.ui.theme.*

@Composable
fun ProcessingScreen(progress: Float, label: String, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("CALCUL DES DONNEES", color = AccentOrange, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(0.6f).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = AccentBlue,
            trackColor = Surface2
        )
        Spacer(Modifier.height(12.dp))
        Text(label, color = TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Relecture image par image de la video haute vitesse",
            color = TextDim, fontSize = 11.sp
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) {
            Text("Annuler - mauvaise prise", color = Bad, fontSize = 13.sp)
        }
    }
}
