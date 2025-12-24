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

package de.splatgames.aether.pack.gui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Navigator manages the screen navigation stack.
 *
 * It maintains a stack of screens and provides methods for navigating
 * forward, backward, and to specific screens.
 */
class Navigator {
    private val screenStack = mutableStateListOf<Screen>(Screen.Dashboard)

    /**
     * The currently displayed screen.
     */
    val currentScreen: Screen
        get() = screenStack.last()

    /**
     * Whether navigation back is possible (more than one screen on stack).
     */
    val canGoBack: Boolean
        get() = screenStack.size > 1

    /**
     * The number of screens in the navigation stack.
     */
    val stackSize: Int
        get() = screenStack.size

    /**
     * Navigate to a new screen, pushing it onto the stack.
     *
     * @param screen The screen to navigate to
     */
    fun navigate(screen: Screen) {
        screenStack.add(screen)
    }

    /**
     * Replace the current screen with a new one.
     *
     * @param screen The screen to replace with
     */
    fun navigateReplace(screen: Screen) {
        if (screenStack.isNotEmpty()) {
            screenStack.removeLast()
        }
        screenStack.add(screen)
    }

    /**
     * Navigate back to the previous screen.
     *
     * @return true if navigation occurred, false if already at root
     */
    fun goBack(): Boolean {
        return if (canGoBack) {
            screenStack.removeLast()
            true
        } else {
            false
        }
    }

    /**
     * Navigate to the dashboard, clearing the entire stack.
     */
    fun goHome() {
        screenStack.clear()
        screenStack.add(Screen.Dashboard)
    }

    /**
     * Clear all screens except the root and navigate to a new screen.
     *
     * @param screen The screen to navigate to
     */
    fun navigateClearingStack(screen: Screen) {
        screenStack.clear()
        screenStack.add(screen)
    }
}
