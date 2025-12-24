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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.*
import de.splatgames.aether.pack.gui.navigation.Navigator
import de.splatgames.aether.pack.gui.navigation.Screen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Keyboard shortcuts handler for the application.
 *
 * Supported shortcuts:
 * - Ctrl+O: Open archive
 * - Ctrl+N: Create new archive
 * - Ctrl+,: Open settings
 * - Ctrl+Home: Go to dashboard
 * - Escape: Go back
 */
object KeyboardShortcuts {
    private val keyEventChannel = Channel<KeyEvent>(Channel.UNLIMITED)

    @OptIn(ExperimentalComposeUiApi::class)
    fun handleKeyEvent(event: KeyEvent, navigator: Navigator): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        val isCtrl = event.isCtrlPressed

        return when {
            // Ctrl+O: Open archive
            isCtrl && event.key == Key.O -> {
                openArchiveDialog(navigator)
                true
            }

            // Ctrl+N: Create new archive
            isCtrl && event.key == Key.N -> {
                navigator.navigate(Screen.CreateWizard)
                true
            }

            // Ctrl+,: Settings
            isCtrl && event.key == Key.Comma -> {
                navigator.navigate(Screen.Settings)
                true
            }

            // Ctrl+Home: Go to dashboard
            isCtrl && event.key == Key.Home -> {
                navigator.goHome()
                true
            }

            // Escape: Go back
            event.key == Key.Escape -> {
                if (navigator.currentScreen != Screen.Dashboard) {
                    navigator.goBack()
                    true
                } else {
                    false
                }
            }

            else -> false
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
}

/**
 * Modifier extension to handle key events with shortcuts.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun androidx.compose.ui.Modifier.handleShortcuts(navigator: Navigator): androidx.compose.ui.Modifier {
    return this.onKeyEvent { event ->
        KeyboardShortcuts.handleKeyEvent(event, navigator)
    }
}
