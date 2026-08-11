package com.pinktakhyper.deeprednoise.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isPlaying: Boolean,
    volume: Float,
    redness: Float,
    timerMinutes: Int,
    timerSecondsRemaining: Long,
    onPlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onRednessChange: (Float) -> Unit,
    onTimerChange: (Int) -> Unit,
) {
    var showTimerSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val bgColor by animateColorAsState(
        targetValue = if (isPlaying) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.background,
        animationSpec = tween(800),
        label = "bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        bgColor.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = 56.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {

            // Title + status
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Deep Red Noise",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        isPlaying && timerSecondsRemaining > 0 -> {
                            val m = timerSecondsRemaining / 60
                            val s = timerSecondsRemaining % 60
                            "Stopping in %d:%02d".format(m, s)
                        }
                        isPlaying -> "Playing"
                        else -> "Paused"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Play / pause button
            val buttonScale by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0.92f,
                animationSpec = tween(300),
                label = "scale"
            )
            Box(contentAlignment = Alignment.Center) {
                // Glow ring when playing
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            )
                    )
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(112.dp)
                        .scale(buttonScale),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(56.dp),
                    )
                }
            }

            // Sliders section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SliderRow(
                    label = "Volume",
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueLabel = "${(volume * 100).toInt()}%",
                )

                SliderRow(
                    label = "Redness",
                    value = redness,
                    onValueChange = onRednessChange,
                    valueLabel = rednessLabel(redness),
                )
            }

            // Sleep timer trigger button
            val timerLabel = if (timerMinutes == 0) "Sleep Timer" else "$timerMinutes min"
            OutlinedButton(
                onClick = { showTimerSheet = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (timerMinutes == 0)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Bedtime,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(timerLabel, style = MaterialTheme.typography.labelLarge)
            }
        }

        // Sleep timer bottom sheet
        if (showTimerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTimerSheet = false },
                sheetState = sheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Sleep Timer",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(16.dp))
                    val timerRows = listOf(
                        listOf(0, 15, 30, 45),
                        listOf(60, 90, 120),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        timerRows.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                row.forEach { mins ->
                                    val selected = timerMinutes == mins
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            onTimerChange(mins)
                                            showTimerSheet = false
                                        },
                                        label = {
                                            Text(
                                                text = if (mins == 0) "Off" else "$mins min",
                                                style = MaterialTheme.typography.labelMedium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = selected,
                                            borderWidth = 0.5.dp,
                                            selectedBorderWidth = 0.5.dp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Light,
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

private fun rednessLabel(value: Float): String = when {
    value < 0.33f -> "Deep Red"
    value < 0.66f -> "Deeper Red"
    else          -> "Ultra Deep"
}
