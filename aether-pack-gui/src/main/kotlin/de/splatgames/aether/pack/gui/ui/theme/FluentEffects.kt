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

package de.splatgames.aether.pack.gui.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp

/**
 * Fluent Design elevation/shadow effects.
 * Provides consistent depth perception across UI components.
 */
object FluentEffects {

    /**
     * Applies a Fluent-style elevation shadow to a composable.
     *
     * @param elevation The elevation level in dp
     * @param cornerRadius The corner radius for the shadow shape
     * @return Modifier with shadow applied
     */
    fun Modifier.fluentElevation(
        elevation: Dp,
        cornerRadius: Dp = FluentTokens.Corner.medium
    ): Modifier = this.shadow(
        elevation = elevation,
        shape = RoundedCornerShape(cornerRadius),
        ambientColor = AetherColors.ShadowAmbient,
        spotColor = AetherColors.ShadowKey
    )
}

/**
 * Animated elevation modifier that transitions smoothly between rest and hover states.
 *
 * @param isHovered Whether the component is being hovered
 * @param restLevel Elevation when not hovered
 * @param hoverLevel Elevation when hovered
 * @param cornerRadius Corner radius for the shadow shape
 * @return Modifier with animated shadow
 */
@Composable
fun Modifier.animatedElevation(
    isHovered: Boolean,
    restLevel: Dp = FluentTokens.Elevation.level1,
    hoverLevel: Dp = FluentTokens.Elevation.level2,
    cornerRadius: Dp = FluentTokens.Corner.medium
): Modifier {
    val elevation by animateDpAsState(
        targetValue = if (isHovered) hoverLevel else restLevel,
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "elevation"
    )
    return this.shadow(
        elevation = elevation,
        shape = RoundedCornerShape(cornerRadius),
        ambientColor = AetherColors.ShadowAmbient,
        spotColor = AetherColors.ShadowKey
    )
}
