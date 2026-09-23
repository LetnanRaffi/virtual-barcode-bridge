package com.raffitech.scanbridge.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raffitech.scanbridge.ui.theme.Accent
import com.raffitech.scanbridge.ui.theme.ActionBlue
import com.raffitech.scanbridge.ui.theme.Background
import com.raffitech.scanbridge.ui.theme.Outline
import com.raffitech.scanbridge.ui.theme.Success
import com.raffitech.scanbridge.ui.theme.Surface
import com.raffitech.scanbridge.ui.theme.SurfaceRaised
import com.raffitech.scanbridge.ui.theme.TextPrimary
import com.raffitech.scanbridge.ui.theme.TextSecondary

@Composable
fun Brand(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("SCAN", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 15.sp)
        Text("BRIDGE", color = Accent, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 15.sp)
    }
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = TextPrimary),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ConnectionCard(
    title: String,
    description: String,
    icon: ImageVector,
    recommended: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "card press")
    Card(
        modifier = Modifier.fillMaxWidth().scale(scale).clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, if (recommended) Accent.copy(alpha = .7f) else Outline),
        colors = CardDefaults.cardColors(containerColor = if (recommended) SurfaceRaised else Surface),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(66.dp).background(if (recommended) Accent.copy(alpha = .20f) else SurfaceRaised, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = if (recommended) Accent else TextPrimary, modifier = Modifier.size(31.dp)) }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    if (recommended) Text(
                        "Disarankan", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.background(ActionBlue, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SurfacePanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Outline.copy(alpha = .7f)),
    ) { Box(Modifier.padding(18.dp)) { content() } }
}

@Composable
fun StatusPill(label: String, connected: Boolean) {
    Row(
        Modifier.background(if (connected) Success.copy(alpha = .11f) else Accent.copy(alpha = .11f), CircleShape)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(if (connected) Success else Accent, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (connected) Success else Accent, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
