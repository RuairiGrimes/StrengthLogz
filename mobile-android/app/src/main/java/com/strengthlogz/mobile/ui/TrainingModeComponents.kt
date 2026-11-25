package com.strengthlogz.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.strengthlogz.mobile.model.TrainingMode
import com.strengthlogz.mobile.ui.theme.*

fun trainingModeColor(mode: TrainingMode): Color = when (mode) {
    TrainingMode.BULK -> AccentOrange
    TrainingMode.MAINGAIN -> IncreaseGreen
    TrainingMode.CUT -> BlueAccent
}

fun trainingModeSummaryPoints(mode: TrainingMode): List<String> = when (mode) {
    TrainingMode.BULK -> listOf(
        "Prioritizes absolute strength & progressive overload",
        "Higher weekly muscle volume targets (~120%)",
        "Tracks healthy lean-bulk weight gain (+0.15 to +0.50 kg/wk)",
        "Suggests extra work sets (3–4) and brisk weight jumps"
    )
    TrainingMode.MAINGAIN -> listOf(
        "Balanced strength gains & sustainable recovery",
        "Standard weekly volume targets (baseline 100%)",
        "Rewards steady body weight (±0.15 kg/wk) and clean progression",
        "Even scoring across volume, strength, cadence, and balance"
    )
    TrainingMode.CUT -> listOf(
        "Focuses on strength retention (≥ 95% E1RM) & Relative Strength (× BW)",
        "Recovery-friendly volume (~75%) to protect muscle without burnout",
        "Replaces harsh red loss alerts on expected < 5% dips with calm Retention cues",
        "Celebrates Relative Strength PRs & DOTS gains while weight trends down"
    )
}

@Composable
fun TrainingModePill(
    mode: TrainingMode,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val color = trainingModeColor(mode)
    val baseMod = modifier
        .clip(RoundedCornerShape(8.dp))
        .background(color.copy(alpha = 0.14f))
        .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
    val clickableMod = if (onClick != null) baseMod.clickable(onClick = onClick) else baseMod

    Row(
        modifier = clickableMod.padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 5.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = if (compact) mode.shortLabel else "${mode.shortLabel} mode",
            color = color,
            fontSize = if (compact) 11.5.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Switch training phase",
                tint = color,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
fun TrainingModeSettingsCard(
    currentMode: TrainingMode,
    onSelectMode: (TrainingMode) -> Unit
) {
    val activeColor = trainingModeColor(currentMode)

    StrengthCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Training Phase Mode",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Adapts goals, workouts, PR radar, and summaries to your phase",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TrainingModePill(mode = currentMode, compact = true)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrainingMode.entries.forEach { mode ->
                val selected = mode == currentMode
                val modeColor = trainingModeColor(mode)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) modeColor.copy(alpha = 0.16f) else SurfaceHighlight)
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) modeColor else BorderDark,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectMode(mode) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = mode.shortLabel,
                            color = if (selected) modeColor else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (mode) {
                                TrainingMode.BULK -> "Gain Strength"
                                TrainingMode.MAINGAIN -> "Balanced"
                                TrainingMode.CUT -> "Retain & Rel."
                            },
                            color = if (selected) modeColor.copy(alpha = 0.85f) else TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(activeColor.copy(alpha = 0.08f))
                .border(1.dp, activeColor.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = currentMode.tagline,
                color = activeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            trainingModeSummaryPoints(currentMode).forEach { bullet ->
                Text(
                    text = "• $bullet",
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun TrainingModePickerDialog(
    currentMode: TrainingMode,
    onSelectMode: (TrainingMode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select Training Phase",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "All goals, volume targets, coaching cues, and summaries adapt immediately.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                TrainingMode.entries.forEach { mode ->
                    val selected = mode == currentMode
                    val modeColor = trainingModeColor(mode)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) modeColor.copy(alpha = 0.14f) else SurfaceHighlight)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) modeColor else BorderDark,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onSelectMode(mode)
                                onDismiss()
                            }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mode.label,
                                color = if (selected) modeColor else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active mode",
                                    tint = modeColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mode.tagline,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = TextSecondary)
                    }
                }
            }
        }
    }
}
