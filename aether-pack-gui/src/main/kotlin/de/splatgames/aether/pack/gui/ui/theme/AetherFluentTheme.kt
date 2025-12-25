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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.LocalContentColor as FluentLocalContentColor
import com.konyaco.fluent.darkColors
import com.konyaco.fluent.lightColors
import androidx.compose.material3.LocalContentColor as Material3LocalContentColor

/**
 * Aether Pack color palette based on Windows Fluent Design.
 */
object AetherColors {
    // Aether Violett Accent
    val AccentPrimary = Color(0xFF7C4DFF)
    val AccentHover = Color(0xFF9575FF)
    val AccentPressed = Color(0xFF6200EA)
    val AccentSubtle = Color(0xFF2D2640)

    // Semantic Colors
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFFA500)
    val Error = Color(0xFFE74856)
    val Info = Color(0xFF60CDFF)

    // Archive Status Colors
    val Compressed = Color(0xFFB388FF)
    val Encrypted = Color(0xFFFF8A65)
    val Ecc = Color(0xFF4DB6AC)
}

// Material3 dark color scheme for Material components
private val DarkMaterialColors = darkColorScheme(
    primary = AetherColors.AccentPrimary,
    onPrimary = Color.White,
    secondary = AetherColors.AccentHover,
    onSecondary = Color.White,
    background = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF2D2D2D),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF3C3C3C),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    error = AetherColors.Error,
    onError = Color.White
)

// Material3 light color scheme for Material components
private val LightMaterialColors = lightColorScheme(
    primary = AetherColors.AccentPrimary,
    onPrimary = Color.White,
    secondary = AetherColors.AccentHover,
    onSecondary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = AetherColors.Error,
    onError = Color.White
)

/**
 * Aether Pack Fluent Theme wrapper.
 * Uses dark theme by default with Aether accent colors.
 * Also wraps Material3 theme for Material components compatibility.
 */
@Composable
fun AetherFluentTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val fluentColors = if (darkTheme) darkColors() else lightColors()
    val materialColors = if (darkTheme) DarkMaterialColors else LightMaterialColors
    val contentColor = if (darkTheme) Color.White else Color(0xFF1C1B1F)

    MaterialTheme(colorScheme = materialColors) {
        FluentTheme(colors = fluentColors) {
            CompositionLocalProvider(
                Material3LocalContentColor provides contentColor,
                FluentLocalContentColor provides contentColor
            ) {
                content()
            }
        }
    }
}
