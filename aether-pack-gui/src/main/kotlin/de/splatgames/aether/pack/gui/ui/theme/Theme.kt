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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Light color scheme for Aether Pack.
 */
private val LightColorScheme = lightColorScheme(
    primary = AetherPackColors.Primary,
    onPrimary = AetherPackColors.OnPrimary,
    primaryContainer = AetherPackColors.PrimaryLight,
    onPrimaryContainer = AetherPackColors.PrimaryDark,
    secondary = AetherPackColors.Secondary,
    onSecondary = AetherPackColors.OnSecondary,
    secondaryContainer = AetherPackColors.SecondaryLight,
    onSecondaryContainer = AetherPackColors.SecondaryDark,
    background = AetherPackColors.LightBackground,
    onBackground = AetherPackColors.LightOnBackground,
    surface = AetherPackColors.LightSurface,
    onSurface = AetherPackColors.LightOnSurface,
    surfaceVariant = AetherPackColors.LightSurfaceVariant,
    onSurfaceVariant = AetherPackColors.LightOnSurfaceVariant,
    outline = AetherPackColors.LightOutline,
    error = AetherPackColors.Error,
    onError = AetherPackColors.OnError,
    errorContainer = AetherPackColors.ErrorLight
)

/**
 * Dark color scheme for Aether Pack.
 */
private val DarkColorScheme = darkColorScheme(
    primary = AetherPackColors.PrimaryLight,
    onPrimary = AetherPackColors.PrimaryDark,
    primaryContainer = AetherPackColors.Primary,
    onPrimaryContainer = AetherPackColors.OnPrimary,
    secondary = AetherPackColors.SecondaryLight,
    onSecondary = AetherPackColors.SecondaryDark,
    secondaryContainer = AetherPackColors.Secondary,
    onSecondaryContainer = AetherPackColors.OnSecondary,
    background = AetherPackColors.DarkBackground,
    onBackground = AetherPackColors.DarkOnBackground,
    surface = AetherPackColors.DarkSurface,
    onSurface = AetherPackColors.DarkOnSurface,
    surfaceVariant = AetherPackColors.DarkSurfaceVariant,
    onSurfaceVariant = AetherPackColors.DarkOnSurfaceVariant,
    outline = AetherPackColors.DarkOutline,
    error = AetherPackColors.Error,
    onError = AetherPackColors.OnError,
    errorContainer = AetherPackColors.ErrorLight
)

/**
 * Typography for Aether Pack.
 */
private val AetherPackTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

/**
 * Main theme composable for the Aether Pack application.
 *
 * @param darkTheme Whether to use dark theme
 * @param useSystemTheme Whether to follow system theme preference
 * @param content The content to display
 */
@Composable
fun AetherPackTheme(
    darkTheme: Boolean = false,
    useSystemTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val effectiveDarkTheme = if (useSystemTheme) {
        isSystemInDarkTheme()
    } else {
        darkTheme
    }

    val colorScheme = if (effectiveDarkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AetherPackTypography,
        shapes = Shapes(),
        content = content
    )
}
