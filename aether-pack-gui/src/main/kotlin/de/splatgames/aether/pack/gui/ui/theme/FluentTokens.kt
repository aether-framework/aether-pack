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

import androidx.compose.ui.unit.dp

/**
 * Centralized design tokens for Windows 11 Fluent Design consistency.
 * These tokens ensure visual harmony across all UI components.
 */
object FluentTokens {

    /**
     * Elevation/Shadow system matching WinUI 3 specifications.
     * Higher levels indicate more visual prominence.
     */
    object Elevation {
        val level0 = 0.dp      // Flat, no shadow
        val level1 = 2.dp      // Subtle lift (cards at rest)
        val level2 = 4.dp      // Hover state
        val level3 = 8.dp      // Active/focused elements
        val level4 = 16.dp     // Dialogs/popups/flyouts
    }

    /**
     * Corner radius system for consistent rounded shapes.
     */
    object Corner {
        val none = 0.dp        // Sharp corners
        val small = 4.dp       // Buttons, chips, small elements
        val medium = 8.dp      // Cards, dialogs
        val large = 12.dp      // Hero sections, large cards
        val full = 9999.dp     // Pills, circular elements
    }

    /**
     * Spacing scale for consistent margins and padding.
     * Based on 4dp grid system.
     */
    object Spacing {
        val xs = 4.dp          // Tight spacing
        val sm = 8.dp          // Small spacing
        val md = 12.dp         // Medium spacing
        val lg = 16.dp         // Large spacing
        val xl = 24.dp         // Extra large spacing
        val xxl = 32.dp        // Section spacing
        val hero = 48.dp       // Hero section spacing
    }

    /**
     * Animation durations in milliseconds.
     * Matching WinUI 3 motion guidelines.
     */
    object Animation {
        const val instant = 50     // Immediate feedback
        const val fast = 100       // Micro-interactions (hover, press)
        const val normal = 200     // Standard transitions
        const val slow = 300       // Page transitions, sidebar
        const val emphasized = 400 // Hero animations, important changes
    }

    /**
     * Sidebar dimensions for navigation.
     */
    object Sidebar {
        val expandedWidth = 280.dp   // Full sidebar with labels
        val collapsedWidth = 48.dp   // Icon-only mode
        val headerHeight = 48.dp     // Header with hamburger
        val searchHeight = 36.dp     // Search bar height
        val itemHeight = 40.dp       // Navigation item height
        val itemPadding = 4.dp       // Padding around nav items
    }

    /**
     * Component-specific dimensions.
     */
    object Components {
        val iconSizeSmall = 16.dp
        val iconSizeMedium = 20.dp
        val iconSizeLarge = 24.dp
        val iconSizeHero = 48.dp

        val buttonHeightSmall = 28.dp
        val buttonHeightMedium = 32.dp
        val buttonHeightLarge = 40.dp

        val cardMinHeight = 80.dp
        val heroHeight = 180.dp

        val dividerThickness = 1.dp
        val accentIndicatorWidth = 3.dp
        val accentBarHeight = 4.dp
    }
}
