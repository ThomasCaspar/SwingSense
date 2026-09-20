package com.swingsense.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.swingsense.app.ui.AppRoot
import com.swingsense.app.ui.components.PrimaryButton
import com.swingsense.app.ui.theme.SwingSenseTheme
import com.swingsense.app.ui.theme.TextDim
import com.swingsense.app.ui.theme.TextPrimary

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            SwingSenseTheme {
                var granted by remember { mutableStateOf(hasPermissions()) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { granted = hasPermissions() }

                if (granted) {
                    AppRoot()
                } else {
                    PermissionScreen {
                        launcher.launch(arrayOf(Manifest.permission.CAMERA))
                    }
                }
            }
        }
    }

    private fun hasPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("SwingSense", color = TextPrimary, fontSize = 26.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "La camera sert a filmer le swing en haute vitesse et a detecter " +
                "la balle a l'adresse.",
            color = TextDim, fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(0.5f)) {
            PrimaryButton("Autoriser", onClick = onRequest)
        }
    }
}
