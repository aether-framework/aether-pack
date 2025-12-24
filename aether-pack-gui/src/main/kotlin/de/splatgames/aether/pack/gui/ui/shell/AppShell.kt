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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Icon
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.icons.Icons
import com.konyaco.fluent.icons.regular.Home
import com.konyaco.fluent.icons.regular.FolderOpen
import com.konyaco.fluent.icons.regular.Add
import com.konyaco.fluent.icons.regular.Settings
import de.splatgames.aether.pack.gui.i18n.I18n
import de.splatgames.aether.pack.gui.navigation.Navigator
import de.splatgames.aether.pack.gui.navigation.Screen
import de.splatgames.aether.pack.gui.state.AppState
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
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
 * Main application shell with sidebar navigation and content area.
 */
@Composable
fun AppShell(
    appState: AppState,
    navigator: Navigator,
    i18n: I18n,
    content: @Composable () -> Unit
) {
    var selectedNav by remember { mutableStateOf<NavDestination>(NavDestination.Home) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Sidebar Navigation
        NavigationSidebar(
            selectedNav = selectedNav,
            onNavSelected = { nav ->
                selectedNav = nav
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
    selectedNav: NavDestination,
    onNavSelected: (NavDestination) -> Unit,
    i18n: I18n
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(FluentTheme.colors.background.solid.base)
            .padding(8.dp)
    ) {
        // App Title
        Text(
            text = "Aether Pack",
            style = FluentTheme.typography.subtitle,
            modifier = Modifier.padding(16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Main Navigation Items
        NavItem(
            destination = NavDestination.Home,
            selected = selectedNav == NavDestination.Home,
            onClick = { onNavSelected(NavDestination.Home) },
            label = i18n["nav.home"] ?: "Home"
        )
        NavItem(
            destination = NavDestination.Open,
            selected = selectedNav == NavDestination.Open,
            onClick = { onNavSelected(NavDestination.Open) },
            label = i18n["nav.open"] ?: "Open"
        )
        NavItem(
            destination = NavDestination.Create,
            selected = selectedNav == NavDestination.Create,
            onClick = { onNavSelected(NavDestination.Create) },
            label = i18n["nav.create"] ?: "Create"
        )

        Spacer(modifier = Modifier.weight(1f))

        // Footer - Settings
        NavItem(
            destination = NavDestination.Settings,
            selected = selectedNav == NavDestination.Settings,
            onClick = { onNavSelected(NavDestination.Settings) },
            label = i18n["nav.settings"] ?: "Settings"
        )
    }
}

@Composable
private fun NavItem(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
    label: String
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
        animationSpec = tween(durationMillis = 150)
    )

    // Animated accent indicator height
    val indicatorHeight by animateDpAsState(
        targetValue = if (selected) 16.dp else 0.dp,
        animationSpec = tween(durationMillis = 200)
    )

    // Animated icon color
    val iconColor by animateColorAsState(
        targetValue = if (selected) AetherColors.AccentPrimary else FluentTheme.colors.text.text.primary,
        animationSpec = tween(durationMillis = 150)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .hoverable(interactionSource)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Animated accent indicator
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(indicatorHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(AetherColors.AccentPrimary)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = iconColor
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            style = FluentTheme.typography.body
        )
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
