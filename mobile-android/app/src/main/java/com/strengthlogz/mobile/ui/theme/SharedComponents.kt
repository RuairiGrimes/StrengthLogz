package com.strengthlogz.mobile.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

val TabularStyle = TextStyle(fontFeatureSettings = "tnum")
val CardHairline = BorderStroke(1.dp, BorderDark)
private val CraftEaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Quiet sentence-case category pill with a subtle category indicator dot.
 */
@Composable
fun CategoryPill(
    category: String,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val dotColor = when (category.trim().lowercase(Locale.US)) {
        "chest" -> AccentOrange
        "back" -> BlueAccent
        "legs" -> IncreaseGreen
        "shoulders" -> PurpleAccent
        "arms" -> GoldPR
        "core" -> Color(0xFFF472B6)
        else -> TextSecondary
    }
    val displayLabel = category.trim().ifBlank { "Other" }
        .lowercase(Locale.US)
        .replaceFirstChar { it.titlecase(Locale.US) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (active) dotColor else SurfaceHighlight,
        border = if (active) null else BorderStroke(0.8.dp, BorderDark)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            if (!active) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = displayLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) Color(0xFF140D07) else TextSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Quiet gold PR badge without excessive weight or glow.
 */
@Composable
fun PrTrophyBadge(
    text: String,
    modifier: Modifier = Modifier,
    isNewPr: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (isNewPr) Color(0x28FFD166) else Color(0x1AFFD166),
        border = BorderStroke(0.8.dp, if (isNewPr) GoldPR.copy(alpha = 0.7f) else GoldPR.copy(alpha = 0.4f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = GoldPR,
                maxLines = 1,
                softWrap = false,
                style = TabularStyle
            )
        }
    }
}

/**
 * Crisp completion checkmark for workout sets with smooth ease-out feedback.
 */
@Composable
fun SetCompletionCheckButton(
    isCompleted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isCompleted) 1.0f else 0.98f,
        animationSpec = tween(durationMillis = 160, easing = CraftEaseOut),
        label = "checkScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isCompleted) IncreaseGreen else SurfaceHighlight,
        animationSpec = tween(durationMillis = 160, easing = CraftEaseOut),
        label = "checkBg"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isCompleted) Color(0xFF0A1F14) else TextMuted,
        animationSpec = tween(durationMillis = 160, easing = CraftEaseOut),
        label = "checkIcon"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(34.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                1.dp,
                if (isCompleted) IncreaseGreenDark else BorderDark,
                RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = if (isCompleted) "Completed" else "Mark Complete",
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Clean dark card container with quiet hairline border and optional custom padding.
 */
@Composable
fun StrengthCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    border: BorderStroke? = BorderStroke(1.dp, BorderDark.copy(alpha = 0.85f)),
    gradientBrush: Brush? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1.0f,
        animationSpec = tween(durationMillis = 120, easing = CraftEaseOut),
        label = "cardPress"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = shape,
        color = SurfaceCard,
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (gradientBrush != null) Modifier.background(gradientBrush)
                    else Modifier
                )
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Modern Search Bar with leading icon and instant clear button.
 */
@Composable
fun ModernSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                color = TextMuted,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = if (query.isNotBlank()) AccentOrange else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                trailingContent?.invoke()
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = AccentOrange,
            unfocusedBorderColor = BorderDark,
            focusedContainerColor = SurfaceDark,
            unfocusedContainerColor = SurfaceDark,
            cursorColor = AccentOrange
        )
    )
}

/**
 * Screen Header that protects title space and avoids wrapping or squeezing.
 */
@Composable
fun ModernScreenHeader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!badgeText.isNullOrBlank()) {
                    Surface(
                        color = SurfaceHighlight,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, BorderDark)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            maxLines = 1,
                            softWrap = false,
                            style = TabularStyle,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

