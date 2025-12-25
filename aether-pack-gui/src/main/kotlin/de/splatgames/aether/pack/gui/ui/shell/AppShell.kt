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

package de.splatgames.aether.pack.gui.ui.shell

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Icon
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.icons.Icons
import com.konyaco.fluent.icons.regular.*
import de.splatgames.aether.pack.gui.i18n.I18n
import de.splatgames.aether.pack.gui.navigation.Navigator
import de.splatgames.aether.pack.gui.navigation.Screen
import de.splatgames.aether.pack.gui.state.AppState
import de.splatgames.aether.pack.gui.ui.components.FluentSearchBar
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import de.splatgames.aether.pack.gui.ui.theme.FluentTokens
import de.splatgames.aether.pack.gui.ui.theme.LocalAetherColors
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Navigation destination for the sidebar.
 */
sealed class NavDestination(
    val id: String,
    val labelKey: String,
    val icon: ImageVector
) {
    data object Home : NavDestination("home", "nav.home", Icons.Regular.Home)
    data object Open : NavDestination("open", "nav.open", Icons.Regular.FolderOpen)
    data object Create : NavDestination("create", "nav.create", Icons.Regular.Add)
    data object Settings : NavDestination("settings", "nav.settings", Icons.Regular.Settings)
}

/**
 * State holder for the collapsible sidebar.
 */
class SidebarState {
    var isExpanded by mutableStateOf(true)
    var searchQuery by mutableStateOf("")

    fun toggle() {
        isExpanded = !isExpanded
    }
}

/**
 * Remember a SidebarState instance.
 */
@Composable
fun rememberSidebarState(): SidebarState {
    return remember { SidebarState() }
}

/**
 * Main application shell with collapsible sidebar navigation and content area.
 */
@Composable
fun AppShell(
    appState: AppState,
    navigator: Navigator,
    i18n: I18n,
    content: @Composable () -> Unit
) {
    val sidebarState = rememberSidebarState()

    // Derive selected nav from current screen
    val selectedNav = when (navigator.currentScreen) {
        is Screen.Dashboard -> NavDestination.Home
        is Screen.Inspector, is Screen.ExtractWizard, is Screen.VerifyWizard -> NavDestination.Open
        is Screen.CreateWizard -> NavDestination.Create
        is Screen.Settings -> NavDestination.Settings
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Collapsible Sidebar Navigation
        NavigationSidebar(
            sidebarState = sidebarState,
            selectedNav = selectedNav,
            onNavSelected = { nav ->
                when (nav) {
                    NavDestination.Home -> navigator.goHome()
                    NavDestination.Open -> openArchiveDialog(navigator)
                    NavDestination.Create -> navigator.navigate(Screen.CreateWizard)
                    NavDestination.Settings -> navigator.navigate(Screen.Settings)
                }
            },
            i18n = i18n
        )

        // Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(FluentTheme.colors.background.mica.base)
        ) {
            content()
        }
    }
}

@Composable
private fun NavigationSidebar(
    sidebarState: SidebarState,
    selectedNav: NavDestination,
    onNavSelected: (NavDestination) -> Unit,
    i18n: I18n
) {
    val colors = LocalAetherColors.current

    // Animated sidebar width
    val sidebarWidth by animateDpAsState(
        targetValue = if (sidebarState.isExpanded)
            FluentTokens.Sidebar.expandedWidth
        else
            FluentTokens.Sidebar.collapsedWidth,
        animationSpec = tween(
            durationMillis = FluentTokens.Animation.slow,
            easing = FastOutSlowInEasing
        ),
        label = "sidebarWidth"
    )

    Column(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(colors.sidebarBackground)
            .border(
                width = FluentTokens.Components.dividerThickness,
                color = colors.sidebarBorder,
                shape = RoundedCornerShape(0.dp)
            )
            .padding(FluentTokens.Spacing.sm)
    ) {
        // Header with hamburger menu and app title
        SidebarHeader(
            isExpanded = sidebarState.isExpanded,
            onToggle = { sidebarState.toggle() },
            i18n = i18n
        )

        Spacer(modifier = Modifier.height(FluentTokens.Spacing.sm))

        // Search bar (only visible when expanded)
        AnimatedVisibility(
            visible = sidebarState.isExpanded,
            enter = fadeIn(animationSpec = tween(FluentTokens.Animation.normal)) +
                    expandVertically(animationSpec = tween(FluentTokens.Animation.normal)),
            exit = fadeOut(animationSpec = tween(FluentTokens.Animation.fast)) +
                    shrinkVertically(animationSpec = tween(FluentTokens.Animation.fast))
        ) {
            FluentSearchBar(
                value = sidebarState.searchQuery,
                onValueChange = { sidebarState.searchQuery = it },
                placeholder = i18n["search.placeholder"] ?: "Search",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = FluentTokens.Spacing.sm)
            )
        }

        Spacer(modifier = Modifier.height(FluentTokens.Spacing.xs))

        // Filter nav items based on search
        val navItems = listOf(
            NavDestination.Home,
            NavDestination.Open,
            NavDestination.Create
        )

        val filteredItems = if (sidebarState.searchQuery.isBlank()) {
            navItems
        } else {
            navItems.filter { nav ->
                val label = i18n[nav.labelKey] ?: nav.id
                label.contains(sidebarState.searchQuery, ignoreCase = true)
            }
        }

        // Main Navigation Items
        filteredItems.forEach { nav ->
            NavItem(
                destination = nav,
                selected = selectedNav == nav,
                onClick = { onNavSelected(nav) },
                label = i18n[nav.labelKey] ?: nav.id,
                isExpanded = sidebarState.isExpanded
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Footer - Settings (always visible)
        val settingsVisible = sidebarState.searchQuery.isBlank() ||
                (i18n["nav.settings"] ?: "Settings").contains(sidebarState.searchQuery, ignoreCase = true)

        if (settingsVisible) {
            NavItem(
                destination = NavDestination.Settings,
                selected = selectedNav == NavDestination.Settings,
                onClick = { onNavSelected(NavDestination.Settings) },
                label = i18n["nav.settings"] ?: "Settings",
                isExpanded = sidebarState.isExpanded
            )
        }
    }
}

@Composable
private fun SidebarHeader(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    i18n: I18n
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val hamburgerBg by animateColorAsState(
        targetValue = if (isHovered)
            FluentTheme.colors.subtleFill.secondary
        else
            Color.Transparent,
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "hamburgerBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(FluentTokens.Sidebar.headerHeight)
            .padding(horizontal = FluentTokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hamburger button with tooltip
        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(FluentTokens.Corner.small))
                    .background(hamburgerBg)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onToggle
                    )
                    .hoverable(interactionSource),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Regular.Navigation,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(FluentTokens.Components.iconSizeMedium),
                    tint = FluentTheme.colors.text.text.primary
                )
            }

            // Show tooltip on hover
            if (isHovered) {
                Popup(
                    alignment = Alignment.CenterEnd,
                    offset = androidx.compose.ui.unit.IntOffset(44, 0),
                    properties = PopupProperties(focusable = false)
                ) {
                    Box(
                        modifier = Modifier
                            .shadow(4.dp, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .background(FluentTheme.colors.background.solid.base)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (isExpanded)
                                i18n["nav.collapse"] ?: "Collapse sidebar"
                            else
                                i18n["nav.expand"] ?: "Expand sidebar",
                            style = FluentTheme.typography.caption
                        )
                    }
                }
            }
        }

        // App title (only when expanded)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(FluentTokens.Animation.normal)) +
                    expandHorizontally(animationSpec = tween(FluentTokens.Animation.slow)),
            exit = fadeOut(animationSpec = tween(FluentTokens.Animation.fast)) +
                    shrinkHorizontally(animationSpec = tween(FluentTokens.Animation.fast))
        ) {
            Text(
                text = "Aether Pack",
                style = FluentTheme.typography.subtitle,
                modifier = Modifier.padding(start = FluentTokens.Spacing.md)
            )
        }
    }
}

@Composable
private fun NavItem(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    isExpanded: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Animated background color
    val backgroundColor by animateColorAsState(
        targetValue = when {
            selected -> FluentTheme.colors.subtleFill.secondary
            isHovered -> FluentTheme.colors.subtleFill.tertiary
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = FluentTokens.Animation.fast)
    )

    // Animated accent indicator height
    val indicatorHeight by animateDpAsState(
        targetValue = if (selected) 16.dp else 0.dp,
        animationSpec = tween(durationMillis = FluentTokens.Animation.normal)
    )

    // Animated icon color
    val iconColor by animateColorAsState(
        targetValue = if (selected) AetherColors.AccentPrimary else FluentTheme.colors.text.text.primary,
        animationSpec = tween(durationMillis = FluentTokens.Animation.fast)
    )

    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FluentTokens.Sidebar.itemHeight)
                .clip(RoundedCornerShape(FluentTokens.Corner.small))
                .background(backgroundColor)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .hoverable(interactionSource)
                .padding(horizontal = FluentTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated accent indicator
            Box(
                modifier = Modifier
                    .width(FluentTokens.Components.accentIndicatorWidth)
                    .height(indicatorHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AetherColors.AccentPrimary)
            )

            Spacer(modifier = Modifier.width(if (isExpanded) FluentTokens.Spacing.md else FluentTokens.Spacing.sm))

            Icon(
                imageVector = destination.icon,
                contentDescription = if (!isExpanded) label else null,
                modifier = Modifier.size(FluentTokens.Components.iconSizeMedium),
                tint = iconColor
            )

            // Label (only when expanded)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(FluentTokens.Animation.normal)) +
                        expandHorizontally(animationSpec = tween(FluentTokens.Animation.slow)),
                exit = fadeOut(animationSpec = tween(FluentTokens.Animation.fast)) +
                        shrinkHorizontally(animationSpec = tween(FluentTokens.Animation.fast))
            ) {
                Row {
                    Spacer(modifier = Modifier.width(FluentTokens.Spacing.md))
                    Text(
                        text = label,
                        style = FluentTheme.typography.body
                    )
                }
            }
        }
    }

    // Show tooltip when collapsed and hovered
    Box {
        content()

        // Tooltip popup when collapsed
        if (!isExpanded && isHovered) {
            Popup(
                alignment = Alignment.CenterEnd,
                offset = androidx.compose.ui.unit.IntOffset(
                    (FluentTokens.Sidebar.collapsedWidth.value + 8).toInt(),
                    0
                ),
                properties = PopupProperties(focusable = false)
            ) {
                Box(
                    modifier = Modifier
                        .shadow(4.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(FluentTheme.colors.background.solid.base)
                        .padding(8.dp)
                ) {
                    Text(
                        text = label,
                        style = FluentTheme.typography.caption
                    )
                }
            }
        }
    }
}

private fun openArchiveDialog(navigator: Navigator) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Open Archive"
        fileFilter = FileNameExtensionFilter("APACK Archives (*.apack)", "apack")
        isAcceptAllFileFilterUsed = false
    }

    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val path = chooser.selectedFile.toPath()
        navigator.navigate(Screen.Inspector(path))
    }
}
