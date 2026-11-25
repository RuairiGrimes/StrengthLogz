package com.strengthlogz.mobile.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.RelativeStrengthPoint
import com.strengthlogz.mobile.util.SetUtils.formatWeightValue
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

private enum class RelRange(val label: String, val days: Int) {
    M3("3M", 90),
    M6("6M", 182),
    Y1("1Y", 365),
    ALL("All", 0)
}

private fun f2(v: Double): String = String.format(Locale.US, "%.2f", v)

private fun compactTableDate(ddMonYyyy: String): String {
    val parts = ddMonYyyy.trim().split("-")
    if (parts.size != 3) return ddMonYyyy
    val day = parts[0].trimStart('0').ifEmpty { parts[0] }
    val mon = parts[1].lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
    val yr = if (parts[2].length == 4) parts[2].substring(2) else parts[2]
    return "$day $mon $yr"
}

/**
 * Separate, dedicated Relative Strength performance modal featuring an independent
 * E1RM ÷ body weight progression curve and interactive tabular data breakdown.
 */
@Composable
fun RelativeStrengthPerformanceDialog(
    exerciseName: String,
    category: String,
    isBodyweight: Boolean,
    points: List<RelativeStrengthPoint>,
    hasLoggedBodyWeights: Boolean,
    onNavigateBodyWeight: () -> Unit,
    onDismiss: () -> Unit
) {
    var range by remember { mutableStateOf(RelRange.ALL) }
    var prOnly by remember { mutableStateOf(false) }
    var showBwOverlay by remember { mutableStateOf(false) }

    val todayEpoch = remember { LocalDate.now().toEpochDay() }
    val inRangePoints = remember(points, range) {
        if (range.days == 0) points
        else points.filter { todayEpoch - it.epochDay <= range.days }
    }
    val displayedPoints = remember(inRangePoints, prOnly) {
        if (!prOnly) inRangePoints else inRangePoints.filter { it.isRelativePr }
    }

    var selectedIdx by remember(displayedPoints.size, range, prOnly) {
        mutableIntStateOf((displayedPoints.size - 1).coerceAtLeast(0))
    }

    val kpiBasis = inRangePoints.ifEmpty { points }
    val peakPt = remember(kpiBasis) { kpiBasis.maxByOrNull { it.relativeStrength } }
    val latestPt = kpiBasis.lastOrNull()
    val firstPt = kpiBasis.firstOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BgDark
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = SurfaceDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = exerciseName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CategoryPill(category = category)
                                    Text(
                                        text = if (isBodyweight) "Relative strength · Total E1RM ÷ BW" else "Relative strength · E1RM ÷ BW",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceCard)
                                    .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                    }
                }

                if (points.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        StrengthCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MonitorWeight,
                                    contentDescription = null,
                                    tint = BlueAccent,
                                    modifier = Modifier.size(30.dp)
                                )
                                Text(
                                    text = if (!hasLoggedBodyWeights) "No body weight readings logged" else "No completed sets for relative strength",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (!hasLoggedBodyWeights) {
                                        "Log at least one body weight entry to plot strength relative to body weight (× BW) and track ratio changes over time."
                                    } else {
                                        "Log completed working sets (reps > 0) for this exercise to calculate E1RM ÷ body weight."
                                    },
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                if (!hasLoggedBodyWeights) {
                                    Spacer(Modifier.height(4.dp))
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onNavigateBodyWeight()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentOrange,
                                            contentColor = Color(0xFF140D07)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(42.dp)
                                    ) {
                                        Text("Log body weight", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val reversedDisplayed = remember(displayedPoints) { displayedPoints.reversed() }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)
                    ) {
                        // 1. Equal-height 3-KPI strip
                        item(key = "rel_kpis") {
                            val netDelta = if (latestPt != null && firstPt != null) {
                                ((latestPt.relativeStrength - firstPt.relativeStrength) * 100.0).roundToInt() / 100.0
                            } else 0.0
                            val bwNet = if (latestPt != null && firstPt != null) {
                                ((latestPt.bodyWeightKg - firstPt.bodyWeightKg) * 10.0).roundToInt() / 10.0
                            } else 0.0
                            val deltaCaption = latestPt?.deltaVsPrev?.let { d ->
                                "${if (d > 0) "+" else ""}${f2(d)}× vs prev"
                            } ?: "First entry"
                            val bwSign = if (bwNet > 0) "+" else ""
                            val netSign = if (netDelta > 0) "+" else ""

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CompactKpiCard(
                                    label = "Peak relative",
                                    value = peakPt?.let { "${f2(it.relativeStrength)}× BW" } ?: "–",
                                    caption = peakPt?.let { "${formatWeightValue(it.e1rmKg)}kg @ ${formatWeightValue(it.bodyWeightKg)}kg" } ?: "–",
                                    valueColor = GoldPR,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                                CompactKpiCard(
                                    label = "Latest",
                                    value = latestPt?.let { "${f2(it.relativeStrength)}× BW" } ?: "–",
                                    caption = deltaCaption,
                                    valueColor = TextPrimary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                                CompactKpiCard(
                                    label = "Net change",
                                    value = if (kpiBasis.size >= 2) "$netSign${f2(netDelta)}×" else "–",
                                    caption = if (kpiBasis.size >= 2) "BW $bwSign${formatWeightValue(bwNet)}kg" else "Need 2+ sessions",
                                    valueColor = if (netDelta >= 0.0) IncreaseGreen else DecreaseRed,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }

                        // 2. Range & filter controls
                        item(key = "rel_controls") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .background(SurfaceCard, RoundedCornerShape(8.dp))
                                        .border(0.8.dp, BorderDark, RoundedCornerShape(8.dp))
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    RelRange.entries.forEach { r ->
                                        val active = range == r
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (active) SurfaceHighlight else Color.Transparent)
                                                .clickable { range = r }
                                                .padding(horizontal = 9.dp, vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = r.label,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (active) TextPrimary else TextSecondary,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RelToggleChip(
                                        label = "PRs only",
                                        selected = prOnly,
                                        selectedColor = GoldPR,
                                        onClick = { prOnly = !prOnly }
                                    )
                                    RelToggleChip(
                                        label = "BW line",
                                        selected = showBwOverlay,
                                        selectedColor = AccentPeach,
                                        onClick = { showBwOverlay = !showBwOverlay }
                                    )
                                }
                            }
                        }

                        // 3. Dedicated Relative Strength Graph Card
                        item(key = "rel_chart_card") {
                            StrengthCard(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (displayedPoints.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(160.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (prOnly) "No relative strength PRs in this date range" else "No sessions in this date range",
                                                color = TextSecondary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    } else {
                                        val safeIdx = selectedIdx.coerceIn(0, displayedPoints.size - 1)
                                        val pt = displayedPoints[safeIdx]

                                        // Inspected Relative Point HUD
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(BgDark)
                                                .border(
                                                    1.dp,
                                                    if (pt.isRelativePr) GoldPR.copy(alpha = 0.55f) else BorderDark,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = pt.displayDate,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = AccentPeach,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        style = TabularStyle
                                                    )
                                                    if (pt.isRelativePr) {
                                                        PrTrophyBadge(text = "Rel PR", isNewPr = true)
                                                    }
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = { if (safeIdx > 0) selectedIdx = safeIdx - 1 },
                                                        enabled = safeIdx > 0,
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                            contentDescription = "Older",
                                                            tint = if (safeIdx > 0) TextPrimary else TextMuted,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            val peakIdx = displayedPoints.indices.maxByOrNull { displayedPoints[it].relativeStrength } ?: safeIdx
                                                            selectedIdx = peakIdx
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(26.dp)
                                                    ) {
                                                        Icon(Icons.Default.Star, contentDescription = null, tint = GoldPR, modifier = Modifier.size(12.dp))
                                                        Spacer(Modifier.width(3.dp))
                                                        Text("Peak", fontSize = 11.5.sp, color = GoldPR, fontWeight = FontWeight.SemiBold)
                                                    }
                                                    IconButton(
                                                        onClick = { if (safeIdx < displayedPoints.size - 1) selectedIdx = safeIdx + 1 },
                                                        enabled = safeIdx < displayedPoints.size - 1,
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                            contentDescription = "Newer",
                                                            tint = if (safeIdx < displayedPoints.size - 1) TextPrimary else TextMuted,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "${f2(pt.relativeStrength)}× BW",
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (pt.isRelativePr) GoldPR else BlueAccent,
                                                        style = TabularStyle,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                    val delta = pt.deltaVsPrev
                                                    if (delta != null && delta != 0.0) {
                                                        Text(
                                                            text = "${if (delta > 0) "+" else ""}${f2(delta)}×",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (delta > 0) IncreaseGreen else DecreaseRed,
                                                            style = TabularStyle,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                }

                                                Spacer(Modifier.width(8.dp))

                                                Text(
                                                    text = "E1RM ${formatWeightValue(pt.e1rmKg)}kg ÷ ${formatWeightValue(pt.bodyWeightKg)}kg",
                                                    fontSize = 12.sp,
                                                    color = TextSecondary,
                                                    style = TabularStyle,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Text(
                                                text = "Top set ${pt.topSetSummary} · BW reading ${pt.bodyWeightDate}",
                                                fontSize = 11.5.sp,
                                                color = TextMuted,
                                                style = TabularStyle,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        RelativeStrengthChart(
                                            points = displayedPoints,
                                            selectedIndex = safeIdx,
                                            onSelectIndex = { selectedIdx = it },
                                            showBwOverlay = showBwOverlay,
                                            chartHeight = 220.dp
                                        )

                                        if (showBwOverlay) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(Modifier.size(8.dp).clip(CircleShape).background(BlueAccent))
                                                    Text("Relative (× BW)", fontSize = 11.sp, color = TextSecondary)
                                                }
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(Modifier.width(12.dp).height(2.dp).background(TextMuted))
                                                    Text("Body weight (normalized)", fontSize = 11.sp, color = TextMuted)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Tabular Performance Breakdown
                        item(key = "rel_table_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Relative strength table",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${displayedPoints.size} datapoints",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    style = TabularStyle
                                )
                            }
                        }

                        if (displayedPoints.isNotEmpty()) {
                            item(key = "rel_table_card") {
                                val safeIdx = selectedIdx.coerceIn(0, displayedPoints.size - 1)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SurfaceCard,
                                    border = BorderStroke(1.dp, BorderDark),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Column headers
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(SurfaceDark)
                                                .padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Date",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.weight(1.25f)
                                            )
                                            Text(
                                                text = "E1RM",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextSecondary,
                                                textAlign = TextAlign.End,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.weight(0.85f)
                                            )
                                            Text(
                                                text = "BW",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextSecondary,
                                                textAlign = TextAlign.End,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.weight(0.75f)
                                            )
                                            Text(
                                                text = "×BW",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextSecondary,
                                                textAlign = TextAlign.End,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.weight(0.9f)
                                            )
                                            Text(
                                                text = "Δ",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextSecondary,
                                                textAlign = TextAlign.End,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.weight(0.85f)
                                            )
                                        }

                                        HorizontalDivider(color = BorderDark, thickness = 0.8.dp)

                                        reversedDisplayed.forEachIndexed { revIdx, rowPt ->
                                            val origIdx = displayedPoints.size - 1 - revIdx
                                            val isSelected = origIdx == safeIdx

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (isSelected) AccentOrange.copy(alpha = 0.09f)
                                                        else Color.Transparent
                                                    )
                                                    .drawBehind {
                                                        if (isSelected) {
                                                            drawRect(
                                                                color = AccentOrange,
                                                                topLeft = Offset.Zero,
                                                                size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                                                            )
                                                        }
                                                    }
                                                    .clickable { selectedIdx = origIdx }
                                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1.25f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        if (rowPt.isRelativePr) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(6.dp)
                                                                    .clip(CircleShape)
                                                                    .background(GoldPR)
                                                            )
                                                        }
                                                        Text(
                                                            text = compactTableDate(rowPt.displayDate),
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                            color = if (isSelected) TextPrimary else TextSecondary,
                                                            style = TabularStyle,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    Text(
                                                        text = formatWeightValue(rowPt.e1rmKg),
                                                        fontSize = 12.sp,
                                                        color = TextSecondary,
                                                        textAlign = TextAlign.End,
                                                        style = TabularStyle,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.weight(0.85f)
                                                    )

                                                    Text(
                                                        text = formatWeightValue(rowPt.bodyWeightKg),
                                                        fontSize = 12.sp,
                                                        color = TextSecondary,
                                                        textAlign = TextAlign.End,
                                                        style = TabularStyle,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.weight(0.75f)
                                                    )

                                                    Text(
                                                        text = "${f2(rowPt.relativeStrength)}×",
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (rowPt.isRelativePr) GoldPR else TextPrimary,
                                                        textAlign = TextAlign.End,
                                                        style = TabularStyle,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.weight(0.9f)
                                                    )

                                                    val d = rowPt.deltaVsPrev
                                                    val dText = when {
                                                        d == null -> "—"
                                                        d > 0.0 -> "+${f2(d)}"
                                                        d < 0.0 -> f2(d)
                                                        else -> "0.00"
                                                    }
                                                    val dColor = when {
                                                        d == null || d == 0.0 -> TextMuted
                                                        d > 0.0 -> IncreaseGreen
                                                        else -> DecreaseRed
                                                    }
                                                    Text(
                                                        text = dText,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = dColor,
                                                        textAlign = TextAlign.End,
                                                        style = TabularStyle,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.weight(0.85f)
                                                    )
                                                }

                                                if (isSelected) {
                                                    val pctStr = rowPt.pctVsFirst?.let { pct ->
                                                        " · ${if (pct > 0) "+" else ""}${formatWeightValue(pct)}% vs start"
                                                    } ?: ""
                                                    val titlePrefix = if (rowPt.workoutTitle.isNotBlank()) "${rowPt.workoutTitle} · " else ""
                                                    Text(
                                                        text = "$titlePrefix${rowPt.topSetSummary} · BW ${rowPt.bodyWeightDate}$pctStr",
                                                        fontSize = 11.sp,
                                                        color = AccentPeach,
                                                        style = TabularStyle,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )
                                                }
                                            }

                                            if (revIdx < reversedDisplayed.lastIndex) {
                                                HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp)
                                            }
                                        }
                                    }
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
private fun RelToggleChip(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) selectedColor.copy(alpha = 0.16f) else SurfaceCard,
        border = BorderStroke(0.8.dp, if (selected) selectedColor.copy(alpha = 0.6f) else BorderDark),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) selectedColor else TextSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun RelativeStrengthChart(
    points: List<RelativeStrengthPoint>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    showBwOverlay: Boolean,
    chartHeight: Dp = 220.dp
) {
    val values = remember(points) { points.map { it.relativeStrength.toFloat() } }
    val bwValues = remember(points) { points.map { it.bodyWeightKg.toFloat() } }

    val rawMin = values.minOrNull() ?: 0f
    val rawMax = values.maxOrNull() ?: 1f
    val dataSpan = (rawMax - rawMin).coerceAtLeast(0.08f)
    val pad = (dataSpan * 0.16f).coerceAtLeast(0.02f)
    val minVal = (rawMin - pad).coerceAtLeast(0f)
    val maxVal = rawMax + pad
    val span = (maxVal - minVal).coerceAtLeast(0.04f)

    val bwRawMin = bwValues.minOrNull() ?: 0f
    val bwRawMax = bwValues.maxOrNull() ?: 1f
    val bwSpan = (bwRawMax - bwRawMin).coerceAtLeast(1f)
    val bwMin = bwRawMin - bwSpan * 0.15f
    val bwMax = bwRawMax + bwSpan * 0.15f
    val bwTotalSpan = (bwMax - bwMin).coerceAtLeast(1f)

    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#A0A4AE")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
    }

    val calloutPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#60A5FA")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val prCalloutPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#FFD166")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val leftPad = 92f
    val rightPad = 24f

    fun idxFromX(x: Float, width: Float): Int {
        if (points.size <= 1) return 0
        val plotW = (width - leftPad - rightPad).coerceAtLeast(1f)
        val stepX = plotW / (points.size - 1)
        return ((x - leftPad) / stepX).roundToInt().coerceIn(0, points.size - 1)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(chartHeight)
            .pointerInput(points.size) {
                detectTapGestures { offset -> onSelectIndex(idxFromX(offset.x, size.width.toFloat())) }
            }
            .pointerInput(points.size) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSelectIndex(idxFromX(change.position.x, size.width.toFloat()))
                }
            }
    ) {
        if (points.isEmpty()) return@Canvas
        val topPad = 28f
        val bottomPad = 42f
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val gridSteps = 3
        for (i in 0..gridSteps) {
            val frac = i.toFloat() / gridSteps
            val y = topPad + plotH * (1f - frac)
            val labelVal = minVal + span * frac
            drawLine(
                color = BorderDark.copy(alpha = 0.7f),
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1.1f
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${f2(labelVal.toDouble())}×",
                6f,
                y + 8f,
                textPaint
            )
        }

        val stepX = if (points.size > 1) plotW / (points.size - 1) else 0f
        val coords = values.mapIndexed { idx, v ->
            val x = if (points.size > 1) leftPad + idx * stepX else leftPad + plotW / 2f
            val norm = ((v - minVal) / span).coerceIn(0f, 1f)
            Offset(x, topPad + plotH * (1f - norm))
        }

        // Peak relative PR line
        val peakRelVal = values.maxOrNull() ?: 0f
        val peakNorm = ((peakRelVal - minVal) / span).coerceIn(0f, 1f)
        val peakY = topPad + plotH * (1f - peakNorm)
        drawLine(
            color = GoldPR.copy(alpha = 0.35f),
            start = Offset(leftPad, peakY),
            end = Offset(leftPad + plotW, peakY),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        )

        // Optional normalized body weight dashed overlay
        if (showBwOverlay && points.size >= 2) {
            val bwCoords = bwValues.mapIndexed { idx, bw ->
                val x = leftPad + idx * stepX
                val norm = ((bw - bwMin) / bwTotalSpan).coerceIn(0f, 1f)
                Offset(x, topPad + plotH * (1f - norm))
            }
            val bwPath = Path().apply {
                moveTo(bwCoords.first().x, bwCoords.first().y)
                bwCoords.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = bwPath,
                color = TextMuted.copy(alpha = 0.7f),
                style = Stroke(
                    width = 2.6f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
            )
        }

        if (coords.size >= 2) {
            val fillPath = Path().apply {
                moveTo(coords.first().x, topPad + plotH)
                coords.forEach { lineTo(it.x, it.y) }
                lineTo(coords.last().x, topPad + plotH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(BlueAccent.copy(alpha = 0.20f), Color.Transparent),
                    startY = topPad,
                    endY = topPad + plotH
                )
            )

            for (i in 0 until coords.size - 1) {
                val p0 = coords[i]
                val p1 = coords[i + 1]
                val v0 = values[i]
                val v1 = values[i + 1]
                val segColor = when {
                    v1 > v0 -> IncreaseGreen
                    v1 < v0 -> DecreaseRed
                    else -> BlueAccent
                }
                drawLine(
                    color = segColor,
                    start = p0,
                    end = p1,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }
        }

        // X-axis dates
        val firstDate = compactTableDate(points.first().displayDate)
        drawContext.canvas.nativeCanvas.drawText(firstDate, leftPad, size.height - 8f, textPaint)
        if (points.size >= 3) {
            val midIdx = points.size / 2
            val midDate = compactTableDate(points[midIdx].displayDate)
            val midW = textPaint.measureText(midDate)
            drawContext.canvas.nativeCanvas.drawText(
                midDate,
                coords[midIdx].x - midW / 2f,
                size.height - 8f,
                textPaint
            )
        }
        if (points.size >= 2) {
            val lastDate = compactTableDate(points.last().displayDate)
            val lastW = textPaint.measureText(lastDate)
            drawContext.canvas.nativeCanvas.drawText(
                lastDate,
                (leftPad + plotW - lastW).coerceAtLeast(leftPad),
                size.height - 8f,
                textPaint
            )
        }

        val safeSel = selectedIndex.coerceIn(0, coords.size - 1)
        val selCoord = coords[safeSel]
        drawLine(
            color = AccentOrange.copy(alpha = 0.8f),
            start = Offset(selCoord.x, topPad),
            end = Offset(selCoord.x, topPad + plotH),
            strokeWidth = 1.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        )

        coords.forEachIndexed { idx, pt ->
            val isPr = points[idx].isRelativePr
            val isSel = idx == safeSel
            if (isSel) {
                drawCircle(
                    color = (if (isPr) GoldPR else BlueAccent).copy(alpha = 0.25f),
                    radius = 15f,
                    center = pt
                )
            }
            if (isPr) {
                drawCircle(color = SurfaceDark, radius = 8.5f, center = pt)
                drawCircle(color = GoldPR, radius = 6.5f, center = pt)
            } else {
                drawCircle(color = SurfaceDark, radius = 6f, center = pt)
                drawCircle(color = if (isSel) AccentOrange else BlueAccent, radius = 4.2f, center = pt)
            }

            if (isSel) {
                val p = points[idx]
                val callout = "${f2(p.relativeStrength)}×"
                val paint = if (isPr) prCalloutPaint else calloutPaint
                val lw = paint.measureText(callout)
                val lx = (pt.x - lw / 2f).coerceIn(leftPad, (leftPad + plotW - lw).coerceAtLeast(leftPad))
                val ly = (pt.y - 18f).coerceAtLeast(22f)
                drawContext.canvas.nativeCanvas.drawText(callout, lx, ly, paint)
            }
        }
    }
}
