package com.strengthlogz.mobile.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strengthlogz.mobile.ui.theme.AccentOrange
import com.strengthlogz.mobile.ui.theme.BorderDark
import com.strengthlogz.mobile.ui.theme.GoldPR
import com.strengthlogz.mobile.ui.theme.SurfaceDark
import com.strengthlogz.mobile.ui.theme.TextPrimary
import kotlin.math.roundToInt

/**
 * Generic scrubbing line chart used by the Body Weight & Strength dashboard.
 * [primary] is the main series; [secondary] is an optional overlay (e.g. 7-day moving average).
 */
@Composable
fun TrendLineChart(
    labels: List<String>,
    primary: List<Float>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    yLabel: (Float) -> String,
    modifier: Modifier = Modifier,
    secondary: List<Float>? = null,
    chartHeight: Dp = 220.dp,
    primaryColor: Color = AccentOrange,
    secondaryColor: Color = GoldPR
) {
    val n = primary.size
    val all = remember(primary, secondary) { primary + (secondary ?: emptyList()) }
    val rawMin = all.minOrNull() ?: 0f
    val rawMax = all.maxOrNull() ?: 1f
    val pad = ((rawMax - rawMin) * 0.12f).coerceAtLeast(rawMax * 0.01f + 0.001f)
    val minVal = rawMin - pad
    val maxVal = rawMax + pad
    val span = (maxVal - minVal).coerceAtLeast(0.001f)

    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#949EB2")
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val leftPad = 96f
    val rightPad = 24f

    fun indexFor(x: Float, width: Float): Int {
        if (n <= 1) return 0
        val plotW = (width - leftPad - rightPad).coerceAtLeast(1f)
        val stepX = plotW / (n - 1)
        return ((x - leftPad) / stepX).roundToInt().coerceIn(0, n - 1)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
            .pointerInput(n) {
                detectTapGestures { offset -> onSelectIndex(indexFor(offset.x, size.width.toFloat())) }
            }
            .pointerInput(n) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSelectIndex(indexFor(change.position.x, size.width.toFloat()))
                }
            }
    ) {
        if (n == 0) return@Canvas
        val topPad = 20f
        val bottomPad = 44f
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val gridSteps = 3
        for (i in 0..gridSteps) {
            val frac = i.toFloat() / gridSteps
            val y = topPad + plotH * (1f - frac)
            drawLine(
                color = BorderDark.copy(alpha = 0.7f),
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1.2f
            )
            drawContext.canvas.nativeCanvas.drawText(yLabel(minVal + span * frac), 6f, y + 8f, textPaint)
        }

        fun xAt(i: Int) = if (n > 1) leftPad + i * (plotW / (n - 1)) else leftPad + plotW / 2f
        fun yAt(v: Float) = topPad + plotH * (1f - ((v - minVal) / span).coerceIn(0f, 1f))

        val pts = primary.mapIndexed { i, v -> Offset(xAt(i), yAt(v)) }

        if (pts.size >= 2) {
            val fill = Path().apply {
                moveTo(pts.first().x, topPad + plotH)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, topPad + plotH)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    listOf(primaryColor.copy(alpha = 0.22f), Color.Transparent),
                    startY = topPad,
                    endY = topPad + plotH
                )
            )
            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, color = primaryColor, style = Stroke(width = 3.6f, cap = StrokeCap.Round))
        }

        if (secondary != null && secondary.size >= 2) {
            val sp = secondary.mapIndexed { i, v -> Offset(xAt(i), yAt(v)) }
            val line = Path().apply {
                moveTo(sp.first().x, sp.first().y)
                sp.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, color = secondaryColor, style = Stroke(width = 4.4f, cap = StrokeCap.Round))
        }

        if (n <= 60) {
            pts.forEach { drawCircle(color = primaryColor, radius = 3.6f, center = it) }
        }

        // x-axis labels
        val first = labels.first()
        drawContext.canvas.nativeCanvas.drawText(first, leftPad, size.height - 8f, textPaint)
        if (n >= 2) {
            val last = labels.last()
            val w = textPaint.measureText(last)
            drawContext.canvas.nativeCanvas.drawText(last, (leftPad + plotW - w).coerceAtLeast(leftPad), size.height - 8f, textPaint)
        }

        val sel = selectedIndex.coerceIn(0, n - 1)
        val sp = pts[sel]
        drawLine(
            color = primaryColor.copy(alpha = 0.85f),
            start = Offset(sp.x, topPad),
            end = Offset(sp.x, topPad + plotH),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        )
        drawCircle(color = primaryColor.copy(alpha = 0.28f), radius = 16f, center = sp)
        drawCircle(color = SurfaceDark, radius = 7f, center = sp)
        drawCircle(color = TextPrimary, radius = 4.5f, center = sp)
    }
}
