package com.strengthlogz.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.SetUtils.formatWeightValue
import kotlin.math.abs

private val WHEEL_ITEM_HEIGHT = 42.dp
private const val WHEEL_VISIBLE_ITEMS = 5

/** Weight wheel values: 0 - 400 kg in 1.25 kg steps. */
private val WEIGHT_GRID: List<Double> = (0..320).map { it * 1.25 }

/** Builds the weight values list, inserting [current] if it is not already on the 1.25 kg grid. */
internal fun weightWheelValues(current: Double?): List<Double> {
    if (current == null || current < 0.0 || current > 400.0) return WEIGHT_GRID
    return if (WEIGHT_GRID.any { abs(it - current) < 0.001 }) WEIGHT_GRID
    else (WEIGHT_GRID + current).sorted()
}

/**
 * Vertical snapping wheel. The item centred inside the highlight band is the selection;
 * [onSelected] fires once the wheel comes to rest.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    values: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (values.size - 1).coerceAtLeast(0)))
    val fling = rememberSnapFlingBehavior(listState)
    val sidePadding = WHEEL_ITEM_HEIGHT * (WHEEL_VISIBLE_ITEMS / 2)

    val centerIndex by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - viewportCenter) }?.index
                ?: selectedIndex
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { if (listState.isScrollInProgress) -1 else centerIndex }
            .collect { if (it >= 0) onSelected(it) }
    }

    Box(
        modifier = modifier.height(WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE_ITEMS),
        contentAlignment = Alignment.Center
    ) {
        // Highlight band
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .background(AccentOrange.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
        )
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = sidePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(values, key = { idx, _ -> idx }) { idx, label ->
                val isCentre = idx == centerIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM_HEIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = if (isCentre) 22.sp else 16.sp,
                        fontWeight = if (isCentre) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isCentre) AccentOrange else TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Weight + reps picker. Defaults to scrolling wheels with OK / Cancel; the small keyboard
 * button switches to typed entry. [startInKeyboard] opens directly in typing mode
 * (used for the weight of a brand-new exercise).
 */
@Composable
fun SetValuePickerDialog(
    initialWeight: String,
    initialReps: String,
    hintWeight: Double? = null,
    hintReps: Int? = null,
    focusWeightFirst: Boolean,
    startInKeyboard: Boolean,
    onConfirm: (weight: String, reps: String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyboardMode by remember { mutableStateOf(startInKeyboard) }

    val startWeight = initialWeight.trim().toDoubleOrNull() ?: hintWeight ?: 0.0
    val startReps = initialReps.trim().toIntOrNull() ?: hintReps ?: 8

    val weightValues = remember { weightWheelValues(startWeight) }
    val repValues = remember { (0..60).toList() }
    val weightLabels = remember(weightValues) { weightValues.map { formatWeightValue(it) } }
    val repLabels = remember { repValues.map { it.toString() } }

    var weightIdx by remember {
        mutableIntStateOf(weightValues.indices.minByOrNull { abs(weightValues[it] - startWeight) } ?: 0)
    }
    var repsIdx by remember { mutableIntStateOf(repValues.indexOf(startReps.coerceIn(0, 60))) }

    var weightText by remember { mutableStateOf(initialWeight) }
    var repsText by remember { mutableStateOf(initialReps) }

    val weightFocus = remember { FocusRequester() }
    val repsFocus = remember { FocusRequester() }
    LaunchedEffect(keyboardMode) {
        if (keyboardMode) {
            try {
                (if (focusWeightFirst) weightFocus else repsFocus).requestFocus()
            } catch (_: Exception) { }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Set Weight & Reps", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                IconButton(
                    onClick = {
                        if (!keyboardMode) {
                            // Carry wheel values into the text fields when switching
                            weightText = formatWeightValue(weightValues[weightIdx])
                            repsText = repValues[repsIdx].toString()
                        } else {
                            weightText.trim().toDoubleOrNull()?.let { w ->
                                weightIdx = weightValues.indices.minByOrNull { abs(weightValues[it] - w) } ?: weightIdx
                            }
                            repsText.trim().toIntOrNull()?.let { r ->
                                if (r in repValues) repsIdx = r
                            }
                        }
                        keyboardMode = !keyboardMode
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        if (keyboardMode) Icons.Default.UnfoldMore else Icons.Default.Keyboard,
                        contentDescription = if (keyboardMode) "Use scroll wheels" else "Use keyboard",
                        tint = AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            if (keyboardMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { v -> weightText = v.filter { it.isDigit() || it == '.' }.take(7) },
                        label = { Text("Weight (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(weightFocus)
                    )
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { v -> repsText = v.filter { it.isDigit() }.take(3) },
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(repsFocus)
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("WEIGHT (kg)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        WheelPicker(
                            values = weightLabels,
                            selectedIndex = weightIdx,
                            onSelected = { weightIdx = it }
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("REPS", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        WheelPicker(
                            values = repLabels,
                            selectedIndex = repsIdx,
                            onSelected = { repsIdx = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (keyboardMode) {
                        onConfirm(weightText.trim(), repsText.trim())
                    } else {
                        onConfirm(formatWeightValue(weightValues[weightIdx]), repValues[repsIdx].toString())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color(0xFF140D07)),
                shape = RoundedCornerShape(10.dp)
            ) { Text("OK", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

/** Read-only tappable value box that opens [SetValuePickerDialog] instead of the on-screen keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapValueBox(
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    isHighlightedPr: Boolean = false,
    isFailedAttempt: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isFailedAttempt -> DecreaseRed
        isHighlightedPr -> GoldPR
        else -> BorderDark
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = BgDark,
        border = BorderStroke(if (isHighlightedPr || isFailedAttempt) 1.5.dp else 1.dp, borderColor),
        modifier = modifier.height(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (value.isEmpty()) {
                Text(placeholder, color = TextMuted, fontSize = 13.5.sp, textAlign = TextAlign.Center)
            } else {
                Text(
                    text = value,
                    color = when {
                        isFailedAttempt -> DecreaseRed
                        isHighlightedPr -> GoldPR
                        else -> TextPrimary
                    },
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
