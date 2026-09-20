package com.swingsense.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swingsense.app.model.Confidence
import com.swingsense.app.model.MetricValue
import com.swingsense.app.ui.theme.*

@Composable
fun SectionTitle(text: String, accent: Color = AccentOrange) {
    Text(
        text = text.uppercase(),
        color = accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        color = if (selected) Surface2 else Surface1,
        border = BorderStroke(1.dp, if (selected) AccentBlue else Color(0xFF2A3139))
    ) {
        Text(
            text = label,
            color = if (selected) TextPrimary else TextDim,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

/** Tuile de resultat, dans l'esprit des maquettes fournies. */
@Composable
fun MetricTile(value: MetricValue, modifier: Modifier = Modifier) {
    val accent = when (value.confidence) {
        Confidence.HIGH -> AccentBlue
        Confidence.MEDIUM -> AccentOrange
        Confidence.LOW -> TextDim
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value.metric.label.uppercase(),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.weight(1f)
            )
            ConfidenceDot(value.confidence)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.display, color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Light)
            if (value.unit.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Text(value.unit, color = TextDim, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 5.dp))
            }
        }
    }
}

@Composable
fun ConfidenceDot(c: Confidence) {
    val color = when (c) {
        Confidence.HIGH -> Ok
        Confidence.MEDIUM -> Warn
        Confidence.LOW -> Bad
    }
    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp).fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.Black)
    ) {
        Text(text.uppercase(), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun WarningBlock(warnings: List<String>) {
    if (warnings.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Color(0x22E2C044)).padding(12.dp)
    ) {
        Text("FIABILITE", color = Warn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        warnings.forEach {
            Text("- $it", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
