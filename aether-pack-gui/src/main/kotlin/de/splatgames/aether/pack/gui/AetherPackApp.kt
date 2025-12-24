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

package de.splatgames.aether.pack.gui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import de.splatgames.aether.pack.gui.i18n.I18n
import de.splatgames.aether.pack.gui.navigation.Navigator
import de.splatgames.aether.pack.gui.navigation.Screen
import de.splatgames.aether.pack.gui.state.AppState
import de.splatgames.aether.pack.gui.ui.components.DragDropContainer
import de.splatgames.aether.pack.gui.ui.components.handleShortcuts
import de.splatgames.aether.pack.gui.ui.components.ProgressDialog
import de.splatgames.aether.pack.gui.ui.screens.dashboard.DashboardScreen
import de.splatgames.aether.pack.gui.ui.screens.inspector.InspectorScreen
import de.splatgames.aether.pack.gui.ui.screens.wizards.create.CreateArchiveWizard
import de.splatgames.aether.pack.gui.ui.screens.wizards.extract.ExtractWizard
import de.splatgames.aether.pack.gui.ui.screens.wizards.verify.VerifyWizard
import de.splatgames.aether.pack.gui.ui.screens.settings.SettingsScreen
import de.splatgames.aether.pack.gui.ui.shell.AppShell
import de.splatgames.aether.pack.gui.ui.theme.AetherFluentTheme

/**
 * Root composable for the Aether Pack application.
 *
 * @param appState Global application state
 * @param i18n Internationalization provider
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AetherPackApp(appState: AppState, i18n: I18n) {
    val navigator = remember { Navigator() }
    val focusRequester = remember { FocusRequester() }

    // Request focus on app start for keyboard shortcuts
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AetherFluentTheme(darkTheme = appState.settings.isDarkTheme) {
        DragDropContainer(
            onFilesDropped = { files ->
                // Open the first dropped archive
                files.firstOrNull()?.let { path ->
                    navigator.navigate(Screen.Inspector(path))
                }
            },
            i18n = i18n,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .handleShortcuts(navigator)
        ) {
            AppShell(
                appState = appState,
                navigator = navigator,
                i18n = i18n
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Animated screen transitions
                    Crossfade(
                        targetState = navigator.currentScreen,
                        animationSpec = tween(durationMillis = 200)
                    ) { screen ->
                        when (screen) {
                            is Screen.Dashboard -> DashboardScreen(
                                appState = appState,
                                i18n = i18n,
                                navigator = navigator
                            )

                            is Screen.Inspector -> InspectorScreen(
                                archivePath = screen.archivePath,
                                appState = appState,
                                i18n = i18n,
                                navigator = navigator
                            )

                            is Screen.CreateWizard -> CreateArchiveWizard(
                                appState = appState,
                                i18n = i18n,
                                navigator = navigator
                            )

                            is Screen.ExtractWizard -> ExtractWizard(
                                archivePath = screen.archivePath,
                                appState = appState,
                                i18n = i18n,
                                navigator = navigator
                            )

                            is Screen.VerifyWizard -> VerifyWizard(
                                archivePath = screen.archivePath,
                                appState = appState,
                                i18n = i18n,
                                navigator = navigator
                            )

                            is Screen.Settings -> SettingsScreen(
                                appState = appState,
                                i18n = i18n,
                                navigator = navigator
                            )
                        }
                    }

                    // Global progress overlay
                    if (appState.taskState.isRunning) {
                        ProgressDialog(
                            progress = appState.taskState.progress,
                            message = appState.taskState.message,
                            onCancel = { appState.taskState.cancel() },
                            i18n = i18n
                        )
                    }
                }
            }
        }
    }
}
