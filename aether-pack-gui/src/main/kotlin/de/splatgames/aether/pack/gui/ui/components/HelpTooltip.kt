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

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Icon
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.icons.Icons
import com.konyaco.fluent.icons.regular.QuestionCircle
import de.splatgames.aether.pack.gui.ui.theme.AetherColors

/**
 * A help tooltip component that shows a "?" icon and displays
 * a tooltip with helpful information on hover.
 *
 * @param tooltip The text to display in the tooltip
 * @param modifier Optional modifier
 */
@Composable
fun HelpTooltip(
    tooltip: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(interactionSource),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Regular.QuestionCircle,
                contentDescription = "Help",
                modifier = Modifier.size(16.dp),
                tint = if (isHovered) AetherColors.AccentPrimary else FluentTheme.colors.text.text.secondary
            )
        }

        if (isHovered) {
            Popup(
                alignment = Alignment.TopStart,
                offset = androidx.compose.ui.unit.IntOffset(24, -8),
                properties = PopupProperties(focusable = false)
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .shadow(8.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(FluentTheme.colors.background.solid.base)
                        .padding(12.dp)
                ) {
                    Text(
                        text = tooltip,
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.primary
                    )
                }
            }
        }
    }
}

/**
 * A row that includes a label and a help tooltip.
 *
 * @param label The label text
 * @param tooltip The tooltip text for the help icon
 * @param modifier Optional modifier
 */
@Composable
fun LabelWithHelp(
    label: String,
    tooltip: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )
        HelpTooltip(tooltip = tooltip)
    }
}
