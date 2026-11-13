package com.strengthlogz.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.SetUtils
import kotlin.math.roundToInt

enum class EquipmentType(val displayName: String) {
    BARBELL("Barbell (20kg)"),
    ADJUSTABLE_DUMBBELL("Dumbbell (5kg bar)")
}

data class PlateCount(val weight: Double, val count: Int, val color: Color, val heightFraction: Float)

@Composable
fun PlateCalculatorDialog(
    initialWeight: Double = 60.0,
    onDismiss: () -> Unit,
    onApplyWeight: (Double) -> Unit
) {
    var selectedEquipment by remember {
        mutableStateOf(
            if (initialWeight in 5.0..40.0) EquipmentType.ADJUSTABLE_DUMBBELL else EquipmentType.BARBELL
        )
    }
    var targetWeightInput by remember {
        mutableStateOf(if (initialWeight > 0) SetUtils.formatWeightValue(initialWeight) else "60")
    }

    val currentWeight = targetWeightInput.toDoubleOrNull() ?: 0.0

    // Barbell presets: 20kg standard, 15kg short
    var barbellBarWeight by remember { mutableDoubleStateOf(20.0) }

    val barWeight = if (selectedEquipment == EquipmentType.BARBELL) barbellBarWeight else 5.0
    val availablePlates = if (selectedEquipment == EquipmentType.BARBELL) {
        listOf(
            Triple(20.0, Color(0xFF2563EB), 1.0f),  // Blue
            Triple(15.0, Color(0xFFEAB308), 0.88f), // Yellow
            Triple(10.0, Color(0xFF16A34A), 0.75f), // Green
            Triple(5.0, Color(0xFFF1F5F9), 0.62f),  // White
            Triple(2.5, Color(0xFFDC2626), 0.50f),  // Red
            Triple(1.25, Color(0xFF94A3B8), 0.40f)  // Silver
        )
    } else {
        // Adjustable Dumbbells: 5kg bar with 2.5kg and 1.25kg plates
        listOf(
            Triple(2.5, Color(0xFFDC2626), 0.75f),  // Red
            Triple(1.25, Color(0xFF94A3B8), 0.55f)  // Silver
        )
    }

    // Per-side math
    val neededPlatesWeight = (currentWeight - barWeight).coerceAtLeast(0.0)
    val weightPerSide = neededPlatesWeight / 2.0

    // Greedy breakdown
    var remainingPerSide = weightPerSide
    val plateStack = mutableListOf<PlateCount>()
    for ((pWeight, pColor, pHeight) in availablePlates) {
        if (remainingPerSide >= pWeight) {
            val count = (remainingPerSide / pWeight).toInt()
            if (count > 0) {
                plateStack.add(PlateCount(pWeight, count, pColor, pHeight))
                remainingPerSide = ((remainingPerSide - count * pWeight) * 100.0).roundToInt() / 100.0
            }
        }
    }

    val loadedPerSide = plateStack.sumOf { it.weight * it.count }
    val totalLoadedWeight = barWeight + (loadedPerSide * 2.0)
    val isExactMatch = kotlin.math.abs(totalLoadedWeight - currentWeight) < 0.05
    val isTooLight = currentWeight < barWeight

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(22.dp),
            color = SurfaceCard,
            border = BorderStroke(1.2.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AccentOrange.copy(alpha = 0.2f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = AccentOrange,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Plate Calculator",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Barbell & Dumbbell plate solver",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(SurfaceDark, CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Equipment Toggle Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgDark, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EquipmentType.entries.forEach { eq ->
                        val isSelected = selectedEquipment == eq
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedEquipment = eq },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) AccentOrange else Color.Transparent
                        ) {
                            Text(
                                text = eq.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF140D07) else TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 7.dp)
                            )
                        }
                    }
                }

                // If Barbell, show bar weight picker (20kg / 15kg)
                if (selectedEquipment == EquipmentType.BARBELL) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bar:", fontSize = 11.5.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                        listOf(20.0 to "20kg (Standard)", 15.0 to "15kg (Women's)").forEach { (bw, label) ->
                            val isSel = barbellBarWeight == bw
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) SurfaceDark else BgDark,
                                border = BorderStroke(1.dp, if (isSel) AccentOrange else BorderDark),
                                modifier = Modifier.clickable { barbellBarWeight = bw }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) AccentOrange else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "5kg Dumbbell Bar • 2.5kg & 1.25kg plates",
                        fontSize = 11.5.sp,
                        color = AccentPeach,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Target Weight Input Row with Steppers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = {
                            val nw = ((currentWeight - 2.5) * 2.0).roundToInt() / 2.0
                            if (nw >= barWeight) targetWeightInput = SetUtils.formatWeightValue(nw)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Text("-2.5", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    Spacer(Modifier.width(10.dp))

                    Surface(
                        color = BgDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, AccentOrange),
                        modifier = Modifier.width(130.dp).height(46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            BasicTextField(
                                value = targetWeightInput,
                                onValueChange = { targetWeightInput = it },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.width(70.dp)
                            )
                            Text("kg", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentPeach)
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    OutlinedButton(
                        onClick = {
                            val nw = ((currentWeight + 2.5) * 2.0).roundToInt() / 2.0
                            targetWeightInput = SetUtils.formatWeightValue(nw)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Text("+2.5", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                // Quick Increment Pills
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-5.0, -1.25, 1.25, 5.0).forEach { delta ->
                        val label = if (delta > 0) "+${SetUtils.formatWeightValue(delta)}" else SetUtils.formatWeightValue(delta)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BgDark,
                            border = BorderStroke(0.6.dp, BorderDark),
                            modifier = Modifier.clickable {
                                val nw = ((currentWeight + delta) * 2.0).roundToInt() / 2.0
                                if (nw >= 0) targetWeightInput = SetUtils.formatWeightValue(nw)
                            }
                        ) {
                            Text(
                                text = "${label}kg",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Visual Barbell / Dumbbell Sleeve Representation
                if (!isTooLight && plateStack.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgDark)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerY = size.height / 2f
                            val shaftThickness = 8f
                            val sleeveThickness = 13f
                            val collarX = 36f
                            val collarW = 10f
                            val platesStartX = collarX + collarW + 4f
                            val plateW = 14f
                            val plateGap = 3f
                            val totalPlatesCount = plateStack.sumOf { it.count }
                            val platesEndX = platesStartX + totalPlatesCount * (plateW + plateGap)
                            val sleeveEndX = (platesEndX + 24f).coerceAtMost(size.width)

                            // Draw inner barbell shaft on the left
                            drawRect(
                                color = Color(0xFF64748B),
                                topLeft = Offset(0f, centerY - shaftThickness / 2f),
                                size = Size(collarX, shaftThickness)
                            )

                            // Draw loading sleeve from collar to capped end on the right
                            drawRoundRect(
                                color = Color(0xFF475569),
                                topLeft = Offset(collarX + collarW, centerY - sleeveThickness / 2f),
                                size = Size((sleeveEndX - (collarX + collarW)).coerceAtLeast(16f), sleeveThickness),
                                cornerRadius = CornerRadius(4f, 4f)
                            )

                            // Draw Collar stopper separating shaft and sleeve
                            drawRoundRect(
                                color = Color(0xFF94A3B8),
                                topLeft = Offset(collarX, centerY - 24f),
                                size = Size(collarW, 48f),
                                cornerRadius = CornerRadius(3f, 3f)
                            )

                            // Draw plates stacked from collar outwards (heaviest inside, lightest outside)
                            var currentX = platesStartX
                            val maxH = size.height - 10f

                            plateStack.forEach { pc ->
                                repeat(pc.count) {
                                    val plateH = maxH * pc.heightFraction
                                    drawRoundRect(
                                        color = pc.color,
                                        topLeft = Offset(currentX, centerY - plateH / 2f),
                                        size = Size(plateW, plateH),
                                        cornerRadius = CornerRadius(2.5f, 2.5f)
                                    )
                                    // Plate stripe border
                                    drawRoundRect(
                                        color = Color(0x33000000),
                                        topLeft = Offset(currentX, centerY - plateH / 2f),
                                        size = Size(plateW, plateH),
                                        cornerRadius = CornerRadius(2.5f, 2.5f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f)
                                    )
                                    currentX += plateW + plateGap
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Breakdown Card
                Surface(
                    color = BgDark,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PLATES NEEDED EACH SIDE",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextSecondary,
                            letterSpacing = 0.6.sp
                        )

                        Spacer(Modifier.height(10.dp))

                        if (isTooLight) {
                            Text(
                                text = "⚠️ Weight is less than the ${SetUtils.formatWeightValue(barWeight)}kg bar",
                                color = DecreaseRed,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (plateStack.isEmpty()) {
                            Text(
                                text = "Empty Bar Only (${SetUtils.formatWeightValue(barWeight)} kg)",
                                color = AccentPeach,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            // Plate row chips
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(plateStack) { pc ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Surface(
                                            color = pc.color.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.5.dp, pc.color),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "${SetUtils.formatWeightValue(pc.weight)}kg",
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = pc.color
                                                )
                                                if (pc.count > 1) {
                                                    Text(
                                                        text = "× ${pc.count}",
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = TextPrimary
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${pc.count} plate${if (pc.count > 1) "s" else ""}",
                                            fontSize = 9.5.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "Total per side: ${SetUtils.formatWeightValue(loadedPerSide)} kg  •  Bar: ${SetUtils.formatWeightValue(barWeight)} kg",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )

                            if (!isExactMatch) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "⚠️ Nearest achievable: ${SetUtils.formatWeightValue(totalLoadedWeight)} kg (diff: ${SetUtils.formatWeightValue(currentWeight - totalLoadedWeight)}kg)",
                                    fontSize = 11.sp,
                                    color = GoldPR
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Text("Cancel", color = TextSecondary, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val validWeight = if (isExactMatch) currentWeight else totalLoadedWeight
                            if (validWeight > 0) {
                                onApplyWeight(validWeight)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentOrange,
                            contentColor = Color(0xFF140D07)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply to Set", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}
