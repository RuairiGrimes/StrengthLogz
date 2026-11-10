package com.strengthlogz.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strengthlogz.mobile.model.BodyWeightEntry
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.BodyWeightUtils
import com.strengthlogz.mobile.util.DateUtils
import com.strengthlogz.mobile.util.SetUtils.formatWeightValue
import kotlinx.coroutines.launch

/** Settings card: current body weight, Health Connect sync and manual entry. */
@Composable
fun BodyWeightCard(
    bodyWeights: List<BodyWeightEntry>,
    healthConnectAvailable: Boolean,
    onSyncHealthConnect: suspend () -> Result<String>,
    onAddManualWeight: (date: String, kg: Double) -> Unit
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    val latest = bodyWeights.maxByOrNull { DateUtils.toSortableIso(it.date) }
    val currentKg = BodyWeightUtils.weightOn(DateUtils.todayDdMonYyyy(), bodyWeights)

    StrengthCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = AccentOrange.copy(alpha = 0.2f), modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MonitorWeight, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                    }
                }
                Text("Body Weight", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Text(
                text = "${formatWeightValue(Math.round(currentKg * 10.0) / 10.0)} kg",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AccentOrange
            )
            Text(
                text = "Latest reading: ${latest?.let { "${it.date} (${it.source})" } ?: "none"} \u2022 ${bodyWeights.size} entries. " +
                    "Used for total-load E1RM and relative strength on bodyweight lifts.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        scope.launch {
                            syncing = true
                            val res = onSyncHealthConnect()
                            status = res.getOrElse { it.message ?: "Health Connect sync failed" }
                            syncing = false
                        }
                    },
                    enabled = healthConnectAvailable && !syncing,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color(0xFF140D07)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(if (syncing) "Syncing..." else "Sync Health Connect", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) }

                OutlinedButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    modifier = Modifier.weight(1f)
                ) { Text("Add manually", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) }
            }

            if (!healthConnectAvailable) {
                Text(
                    "Health Connect is not installed or available on this device.",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
            }
            if (status.isNotBlank()) {
                Text(status, fontSize = 12.sp, color = AccentPeach)
            }
        }
    }

    if (showAddDialog) {
        var dateText by remember { mutableStateOf(DateUtils.todayDdMonYyyy()) }
        var weightText by remember { mutableStateOf("") }
        var useLbs by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Add body weight", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Date (DD-MON-YYYY)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { v -> weightText = v.filter { it.isDigit() || it == '.' }.take(6) },
                        label = { Text(if (useLbs) "Weight (lb)" else "Weight (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("kg", color = if (!useLbs) AccentOrange else TextSecondary)
                        Switch(checked = useLbs, onCheckedChange = { useLbs = it })
                        Text("lb", color = if (useLbs) AccentOrange else TextSecondary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val raw = weightText.toDoubleOrNull()
                        if (raw != null && raw > 0.0) {
                            val kg = if (useLbs) BodyWeightUtils.lbsToKg(raw) else raw
                            onAddManualWeight(DateUtils.toDdMonYyyy(dateText), kg)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color(0xFF140D07))
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

