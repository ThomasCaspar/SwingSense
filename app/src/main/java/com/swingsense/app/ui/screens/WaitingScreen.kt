package com.swingsense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swingsense.app.ui.components.PrimaryButton
import com.swingsense.app.ui.theme.*
import com.swingsense.app.viewmodel.UiState

/**
 * Ecran d'attente du swing.
 *
 * Volontairement minimaliste : pendant cette phase la camera enregistre a
 * 240 im/s et n'envoie aucune image a l'application (contrainte materielle
 * de la session haute vitesse). Il n'y a donc rien a afficher en direct -
 * et c'est aussi pour ca qu'il n'y a plus de detection acoustique ici : elle
 * ne peut de toute facon rien voir non plus, et sur un practice partage elle
 * ne peut pas distinguer l'impact du golfeur voisin du sien (meme "clic",
 * aucun moyen fiable de les separer sur un micro mono).
 *
 * L'enregistrement tourne simplement pendant une duree fixe, largement
 * suffisante pour couvrir adresse + swing ; l'analyse hors ligne qui suit
 * retrouve automatiquement OU se trouve le swing dans cette fenetre, quel
 * que soit le moment exact ou il a eu lieu. "Swing termine" ecourte juste
 * l'attente une fois que c'est fait ; ce n'est pas necessaire au bon
 * fonctionnement.
 */
@Composable
fun WaitingScreen(state: UiState, onFinish: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "SWING QUAND VOUS ÊTES PRÊT",
            color = AccentOrange,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Enregistrement ${state.highSpeedOption?.fps ?: 0} im/s en cours",
            color = TextDim, fontSize = 14.sp
        )

        Spacer(Modifier.height(28.dp))
        Text("${state.countdownSec}", color = TextPrimary, fontSize = 72.sp, fontWeight = FontWeight.Thin)
        Text("secondes avant arret automatique", color = TextDim, fontSize = 12.sp)

        Spacer(Modifier.height(34.dp))
        Box(Modifier.fillMaxWidth(0.4f)) {
            PrimaryButton("Swing terminé - analyser", onClick = onFinish)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onCancel) {
            Text("Annuler ce swing (déclenché par erreur)", color = Bad, fontSize = 13.sp)
        }
    }
}
