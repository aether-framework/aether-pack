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

import androidx.compose.ui.graphics.Color

/**
 * Color palette for the Aether Pack application.
 */
object AetherPackColors {
    // Primary colors - Blue theme
    val Primary = Color(0xFF1976D2)
    val PrimaryLight = Color(0xFF63A4FF)
    val PrimaryDark = Color(0xFF004BA0)
    val OnPrimary = Color.White

    // Secondary colors - Teal accent
    val Secondary = Color(0xFF00897B)
    val SecondaryLight = Color(0xFF4EBAAA)
    val SecondaryDark = Color(0xFF005B4F)
    val OnSecondary = Color.White

    // Light theme colors
    val LightBackground = Color(0xFFFAFAFA)
    val LightSurface = Color.White
    val LightSurfaceVariant = Color(0xFFF5F5F5)
    val LightOnBackground = Color(0xFF1C1B1F)
    val LightOnSurface = Color(0xFF1C1B1F)
    val LightOnSurfaceVariant = Color(0xFF49454F)
    val LightOutline = Color(0xFF79747E)

    // Dark theme colors
    val DarkBackground = Color(0xFF121212)
    val DarkSurface = Color(0xFF1E1E1E)
    val DarkSurfaceVariant = Color(0xFF2D2D2D)
    val DarkOnBackground = Color(0xFFE6E1E5)
    val DarkOnSurface = Color(0xFFE6E1E5)
    val DarkOnSurfaceVariant = Color(0xFFCAC4D0)
    val DarkOutline = Color(0xFF938F99)

    // Semantic colors
    val Success = Color(0xFF4CAF50)
    val SuccessLight = Color(0xFFC8E6C9)
    val OnSuccess = Color.White

    val Warning = Color(0xFFFFC107)
    val WarningLight = Color(0xFFFFF8E1)
    val OnWarning = Color(0xFF1C1B1F)

    val Error = Color(0xFFF44336)
    val ErrorLight = Color(0xFFFFCDD2)
    val OnError = Color.White

    val Info = Color(0xFF2196F3)
    val InfoLight = Color(0xFFBBDEFB)
    val OnInfo = Color.White

    // Special colors for archive status
    val Compressed = Color(0xFF7B1FA2)       // Purple for compressed entries
    val Encrypted = Color(0xFFD84315)        // Deep orange for encrypted entries
    val Ecc = Color(0xFF00695C)              // Teal for ECC-protected entries
}
