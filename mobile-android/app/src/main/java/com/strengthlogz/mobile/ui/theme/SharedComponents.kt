package com.strengthlogz.mobile.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable high-contrast category or state tag pill.
 */
@Composable
fun CategoryPill(
    category: String,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val (bgColor, textColor, borderColor) = when (category.lowercase()) {
        "chest" -> Triple(Color(0x24FF7A45), AccentOrange, AccentOrange.copy(alpha = 0.45f))
        "back" -> Triple(Color(0x2438BDF8), BlueAccent, BlueAccent.copy(alpha = 0.45f))
        "legs" -> Triple(Color(0x2434D399), IncreaseGreen, IncreaseGreen.copy(alpha = 0.45f))
        "shoulders" -> Triple(Color(0x24A78BFA), PurpleAccent, PurpleAccent.copy(alpha = 0.45f))
        "arms" -> Triple(Color(0x24FFD166), GoldPR, GoldPR.copy(alpha = 0.45f))
        "core" -> Triple(Color(0x24F472B6), Color(0xFFF472B6), Color(0xFFF472B6).copy(alpha = 0.45f))
        else -> Triple(Color(0x1F8B93A7), TextSecondary, BorderDark)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (active) textColor else bgColor,
        border = if (active) null else BorderStroke(0.8.dp, borderColor)
    ) {
        Text(
            text = category.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color(0xFF140D07) else textColor,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Golden PR badge with trophy/fire indicator.
 */
@Composable
fun PrTrophyBadge(
    text: String,
    modifier: Modifier = Modifier,
    isNewPr: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isNewPr) Color(0x38FFD166) else Color(0x24FFD166),
        border = BorderStroke(1.dp, if (isNewPr) GoldPR else GoldPR.copy(alpha = 0.5f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
        ) {
            Text(
                text = if (isNewPr) "★" else "PR",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = GoldPR
            )
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GoldPR,
                maxLines = 1
            )
        }
    }
}

/**
 * Animated completion checkmark for workout sets with spring bounce.
 */
@Composable
fun SetCompletionCheckButton(
    isCompleted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isCompleted) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isCompleted) IncreaseGreen else SurfaceHighlight,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "checkBg"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isCompleted) Color(0xFF092317) else TextMuted,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "checkIcon"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(34.dp)
            .scale(scale)
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .border(
                1.dp,
                if (isCompleted) IncreaseGreenDark else BorderDark,
                RoundedCornerShape(9.dp)
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
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Modern card container with subtle gradient rim and dark surface.
 */
@Composable
fun StrengthCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    border: BorderStroke? = BorderStroke(0.8.dp, BorderDark.copy(alpha = 0.65f)),
    gradientBrush: Brush? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
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
                .padding(16.dp),
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
                modifier = Modifier.size(19.dp)
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
        shape = RoundedCornerShape(14.dp),
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
 * Screen Header with modern bold typography, sub-caption, and optional right action.
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
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    letterSpacing = (-0.4).sp
                )
                if (!badgeText.isNullOrBlank()) {
                    Surface(
                        color = SurfaceHighlight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }

        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

