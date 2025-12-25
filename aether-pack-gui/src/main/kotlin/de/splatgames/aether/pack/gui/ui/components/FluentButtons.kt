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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.LocalContentColor
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import de.splatgames.aether.pack.gui.ui.theme.FluentTokens

/**
 * Fluent-styled accent button with animated hover effects.
 * Primary action button with Aether accent color.
 *
 * @param onClick Click handler
 * @param enabled Whether the button is enabled
 * @param modifier Optional modifier
 * @param content Button content (typically Icon + Text)
 */
@Composable
fun FluentAccentButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> AetherColors.AccentPrimary.copy(alpha = 0.5f)
            isHovered -> AetherColors.AccentHover
            else -> AetherColors.AccentPrimary
        },
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "accentButtonBg"
    )

    // Subtle scale animation on hover
    val scale by animateFloatAsState(
        targetValue = if (isHovered && enabled) 1.02f else 1f,
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "accentButtonScale"
    )

    CompositionLocalProvider(
        LocalContentColor provides Color.White,
        androidx.compose.material3.LocalContentColor provides Color.White
    ) {
        Row(
            modifier = modifier
                .scale(scale)
                .clip(RoundedCornerShape(FluentTokens.Corner.small))
                .background(backgroundColor)
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Fluent-styled secondary button with subtle fill.
 * For secondary actions that are less prominent.
 *
 * @param onClick Click handler
 * @param enabled Whether the button is enabled
 * @param modifier Optional modifier
 * @param content Button content (typically Icon + Text)
 */
@Composable
fun FluentSecondaryButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> FluentTheme.colors.subtleFill.disabled
            isHovered -> FluentTheme.colors.subtleFill.tertiary
            else -> FluentTheme.colors.subtleFill.secondary
        },
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "secondaryButtonBg"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(FluentTokens.Corner.small))
            .background(backgroundColor)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Fluent-styled ghost button for minimal visual impact.
 * Transparent background with hover effect.
 *
 * @param onClick Click handler
 * @param enabled Whether the button is enabled
 * @param modifier Optional modifier
 * @param content Button content
 */
@Composable
fun FluentGhostButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            isHovered -> FluentTheme.colors.subtleFill.secondary
            else -> Color.Transparent
        },
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "ghostButtonBg"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(FluentTokens.Corner.small))
            .background(backgroundColor)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Hero section button with semi-transparent white background.
 * Used in hero/gradient sections for contrast.
 *
 * @param onClick Click handler
 * @param modifier Optional modifier
 * @param content Button content
 */
@Composable
fun FluentHeroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered)
            Color.White.copy(alpha = 0.25f)
        else
            Color.White.copy(alpha = 0.15f),
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "heroButtonBg"
    )

    CompositionLocalProvider(
        LocalContentColor provides Color.White,
        androidx.compose.material3.LocalContentColor provides Color.White
    ) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(FluentTokens.Corner.small))
                .background(backgroundColor)
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
