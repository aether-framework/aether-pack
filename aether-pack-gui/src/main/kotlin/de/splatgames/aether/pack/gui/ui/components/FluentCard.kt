/*
 * Copyright (c) 2025 Splatgames.de Software and Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.splatgames.aether.pack.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Text
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import de.splatgames.aether.pack.gui.ui.theme.FluentTokens
import de.splatgames.aether.pack.gui.ui.theme.LocalAetherColors
import de.splatgames.aether.pack.gui.ui.theme.animatedElevation

/**
 * Fluent Design card with proper elevation, hover states, and consistent styling.
 * Provides a container for content with modern Windows 11 aesthetics.
 *
 * @param modifier Optional modifier
 * @param onClick Optional click handler. If null, card is not clickable.
 * @param elevation Rest elevation level
 * @param hoverElevation Elevation when hovered (only applies if onClick is provided)
 * @param content Card content
 */
@Composable
fun FluentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: Dp = FluentTokens.Elevation.level1,
    hoverElevation: Dp = FluentTokens.Elevation.level2,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAetherColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered && onClick != null)
            colors.cardBackgroundHover
        else
            colors.cardBackground,
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "cardBackground"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(FluentTokens.Corner.medium))
            .animatedElevation(
                isHovered = isHovered && onClick != null,
                restLevel = elevation,
                hoverLevel = hoverElevation
            )
            .background(backgroundColor)
            .then(
                if (onClick != null) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                        .hoverable(interactionSource)
                } else {
                    Modifier.hoverable(interactionSource)
                }
            )
            .padding(FluentTokens.Spacing.lg),
        content = content
    )
}

/**
 * Section card with title bar and optional help tooltip.
 * Used for grouping related settings or content.
 *
 * @param title Section title
 * @param tooltip Optional tooltip text for help icon
 * @param modifier Optional modifier
 * @param content Section content
 */
@Composable
fun FluentSectionCard(
    title: String,
    tooltip: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    FluentCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FluentTokens.Spacing.sm)
        ) {
            Text(
                text = title,
                style = FluentTheme.typography.bodyStrong,
                color = AetherColors.AccentPrimary
            )
            if (tooltip != null) {
                HelpTooltip(tooltip = tooltip)
            }
        }
        Spacer(modifier = Modifier.height(FluentTokens.Spacing.md))
        content()
    }
}

/**
 * Hero card with gradient background for prominent sections.
 * Used for welcome banners and featured content.
 *
 * @param modifier Optional modifier
 * @param content Card content
 */
@Composable
fun FluentHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FluentTokens.Components.heroHeight)
            .clip(RoundedCornerShape(FluentTokens.Corner.large))
            .animatedElevation(
                isHovered = false,
                restLevel = FluentTokens.Elevation.level2,
                hoverLevel = FluentTokens.Elevation.level2,
                cornerRadius = FluentTokens.Corner.large
            )
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        AetherColors.GradientStart,
                        AetherColors.GradientEnd
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .padding(FluentTokens.Spacing.xl),
        content = content
    )
}
