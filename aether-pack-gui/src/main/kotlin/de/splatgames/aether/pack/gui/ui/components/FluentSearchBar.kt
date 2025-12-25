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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Icon
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.icons.Icons
import com.konyaco.fluent.icons.regular.Dismiss
import com.konyaco.fluent.icons.regular.Search
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import de.splatgames.aether.pack.gui.ui.theme.FluentTokens

/**
 * Fluent-styled search bar with animated focus border.
 * Features a search icon, placeholder text, and clear button.
 *
 * @param value Current search query
 * @param onValueChange Callback when query changes
 * @param placeholder Placeholder text when empty
 * @param modifier Optional modifier
 * @param enabled Whether the search bar is enabled
 */
@Composable
fun FluentSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search",
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> FluentTheme.colors.stroke.control.disabled
            isFocused -> AetherColors.AccentPrimary
            else -> FluentTheme.colors.stroke.control.default
        },
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "searchBorderColor"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> FluentTheme.colors.subtleFill.disabled
            isFocused -> FluentTheme.colors.subtleFill.tertiary
            else -> FluentTheme.colors.subtleFill.secondary
        },
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "searchBgColor"
    )

    Row(
        modifier = modifier
            .height(FluentTokens.Sidebar.searchHeight)
            .clip(RoundedCornerShape(FluentTokens.Corner.small))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(FluentTokens.Corner.small)
            )
            .clickable(enabled = enabled) { focusRequester.requestFocus() }
            .padding(horizontal = FluentTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FluentTokens.Spacing.sm)
    ) {
        // Search icon
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(FluentTokens.Components.iconSizeSmall),
            tint = if (enabled)
                FluentTheme.colors.text.text.secondary
            else
                FluentTheme.colors.text.text.disabled
        )

        // Text field
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            // Placeholder
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = FluentTheme.typography.body.copy(
                        color = FluentTheme.colors.text.text.secondary
                    )
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    color = if (enabled)
                        FluentTheme.colors.text.text.primary
                    else
                        FluentTheme.colors.text.text.disabled,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(AetherColors.AccentPrimary),
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        // Clear button (visible when there's content)
        AnimatedVisibility(
            visible = value.isNotEmpty() && enabled,
            enter = fadeIn(animationSpec = tween(FluentTokens.Animation.fast)),
            exit = fadeOut(animationSpec = tween(FluentTokens.Animation.fast))
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(FluentTokens.Corner.small))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onValueChange("") }
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Dismiss,
                    contentDescription = "Clear",
                    modifier = Modifier.size(FluentTokens.Components.iconSizeSmall),
                    tint = FluentTheme.colors.text.text.secondary
                )
            }
        }
    }
}

/**
 * Compact search bar variant for use in toolbars.
 * Smaller height and padding for dense UI areas.
 *
 * @param value Current search query
 * @param onValueChange Callback when query changes
 * @param placeholder Placeholder text when empty
 * @param modifier Optional modifier
 */
@Composable
fun FluentCompactSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search",
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) AetherColors.AccentPrimary
        else FluentTheme.colors.stroke.control.default,
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "compactSearchBorder"
    )

    Row(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(FluentTokens.Corner.small))
            .background(FluentTheme.colors.subtleFill.secondary)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(FluentTokens.Corner.small)
            )
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = FluentTokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FluentTokens.Spacing.xs)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = FluentTheme.colors.text.text.secondary
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = FluentTheme.typography.caption.copy(
                        color = FluentTheme.colors.text.text.secondary
                    )
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = FluentTheme.colors.text.text.primary,
                    fontSize = 12.sp
                ),
                cursorBrush = SolidColor(AetherColors.AccentPrimary),
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        AnimatedVisibility(
            visible = value.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(FluentTokens.Animation.fast)),
            exit = fadeOut(animationSpec = tween(FluentTokens.Animation.fast))
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Dismiss,
                    contentDescription = "Clear",
                    modifier = Modifier.size(12.dp),
                    tint = FluentTheme.colors.text.text.secondary
                )
            }
        }
    }
}
