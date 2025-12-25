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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.LocalContentColor as FluentLocalContentColor
import com.konyaco.fluent.darkColors
import com.konyaco.fluent.lightColors
import androidx.compose.material3.LocalContentColor as Material3LocalContentColor

/**
 * Theme-aware color palette for Aether Pack.
 * Provides different colors for light and dark themes.
 */
data class AetherColorScheme(
    // Accent colors
    val accentPrimary: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val accentSubtle: Color,

    // Semantic colors
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,

    // Archive status colors
    val compressed: Color,
    val encrypted: Color,
    val ecc: Color,

    // Card backgrounds
    val cardBackground: Color,
    val cardBackgroundHover: Color,
    val cardBackgroundPressed: Color,

    // Sidebar colors
    val sidebarBackground: Color,
    val sidebarBorder: Color,

    // Elevated surfaces
    val surfaceElevated: Color,

    // Shadow colors
    val shadowAmbient: Color,
    val shadowKey: Color,

    // Gradient colors
    val gradientStart: Color,
    val gradientEnd: Color
)

/**
 * Dark theme color scheme
 */
private val DarkAetherColors = AetherColorScheme(
    accentPrimary = Color(0xFF7C4DFF),
    accentHover = Color(0xFF9575FF),
    accentPressed = Color(0xFF6200EA),
    accentSubtle = Color(0xFF2D2640),

    success = Color(0xFF4CAF50),
    warning = Color(0xFFFFA500),
    error = Color(0xFFE74856),
    info = Color(0xFF60CDFF),

    compressed = Color(0xFFB388FF),
    encrypted = Color(0xFFFF8A65),
    ecc = Color(0xFF4DB6AC),

    cardBackground = Color(0xFF2D2D2D),
    cardBackgroundHover = Color(0xFF363636),
    cardBackgroundPressed = Color(0xFF404040),

    sidebarBackground = Color(0xFF1C1C1C),
    sidebarBorder = Color(0xFF3A3A3A),

    surfaceElevated = Color(0xFF383838),

    shadowAmbient = Color(0x26000000),
    shadowKey = Color(0x1A000000),

    gradientStart = Color(0xFF7C4DFF),
    gradientEnd = Color(0xFF3D2980)
)

/**
 * Light theme color scheme
 */
private val LightAetherColors = AetherColorScheme(
    accentPrimary = Color(0xFF7C4DFF),
    accentHover = Color(0xFF6A3DE8),
    accentPressed = Color(0xFF5E35B1),
    accentSubtle = Color(0xFFEDE7F6),

    success = Color(0xFF388E3C),
    warning = Color(0xFFE65100),
    error = Color(0xFFD32F2F),
    info = Color(0xFF0288D1),

    compressed = Color(0xFF7C4DFF),
    encrypted = Color(0xFFE64A19),
    ecc = Color(0xFF00897B),

    cardBackground = Color(0xFFFFFFFF),
    cardBackgroundHover = Color(0xFFF5F5F5),
    cardBackgroundPressed = Color(0xFFEEEEEE),

    sidebarBackground = Color(0xFFF3F3F3),
    sidebarBorder = Color(0xFFE0E0E0),

    surfaceElevated = Color(0xFFFFFFFF),

    shadowAmbient = Color(0x1A000000),
    shadowKey = Color(0x0D000000),

    gradientStart = Color(0xFF7C4DFF),
    gradientEnd = Color(0xFF9575CD)
)

/**
 * CompositionLocal for accessing theme-aware Aether colors
 */
val LocalAetherColors = staticCompositionLocalOf { DarkAetherColors }

/**
 * Access the current Aether color scheme.
 * Use this instead of the deprecated AetherColors object.
 */
object AetherColors {
    // Static accent colors (same in both themes)
    val AccentPrimary = Color(0xFF7C4DFF)
    val AccentHover = Color(0xFF9575FF)
    val AccentPressed = Color(0xFF6200EA)

    // Semantic colors (slightly different in light theme, but these are commonly used)
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFFA500)
    val Error = Color(0xFFE74856)
    val Info = Color(0xFF60CDFF)

    // Archive status colors
    val Compressed = Color(0xFFB388FF)
    val Encrypted = Color(0xFFFF8A65)
    val Ecc = Color(0xFF4DB6AC)

    // Shadow colors (same in both themes)
    val ShadowAmbient = Color(0x26000000)   // 15% black
    val ShadowKey = Color(0x1A000000)       // 10% black

    // Theme-aware colors - these should be accessed via LocalAetherColors.current
    // Keeping deprecated static variants for gradual migration
    @Deprecated("Use LocalAetherColors.current.cardBackground instead")
    val CardBackground = Color(0xFF2D2D2D)
    @Deprecated("Use LocalAetherColors.current.cardBackgroundHover instead")
    val CardBackgroundHover = Color(0xFF363636)
    @Deprecated("Use LocalAetherColors.current.sidebarBackground instead")
    val SidebarBackground = Color(0xFF1C1C1C)
    @Deprecated("Use LocalAetherColors.current.sidebarBorder instead")
    val SidebarBorder = Color(0xFF3A3A3A)

    // Gradient colors (same in both themes)
    val GradientStart = Color(0xFF7C4DFF)
    val GradientEnd = Color(0xFF3D2980)
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
    val aetherColors = if (darkTheme) DarkAetherColors else LightAetherColors
    val contentColor = if (darkTheme) Color.White else Color(0xFF1C1B1F)

    MaterialTheme(colorScheme = materialColors) {
        FluentTheme(colors = fluentColors) {
            CompositionLocalProvider(
                LocalAetherColors provides aetherColors,
                Material3LocalContentColor provides contentColor,
                FluentLocalContentColor provides contentColor
            ) {
                content()
            }
        }
    }
}
