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

package de.splatgames.aether.pack.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*

/**
 * Represents the state of a background task.
 *
 * Provides progress tracking, cancellation support, and error handling
 * for long-running operations like archive creation, extraction, and verification.
 */
class TaskState {
    /**
     * Whether a task is currently running.
     */
    var isRunning by mutableStateOf(false)
        private set

    /**
     * Current progress (0.0 to 1.0).
     */
    var progress by mutableStateOf(0f)
        private set

    /**
     * Current progress message.
     */
    var message by mutableStateOf("")
        private set

    /**
     * Whether the task has been cancelled.
     */
    var isCancelled by mutableStateOf(false)
        private set

    /**
     * Error message if the task failed.
     */
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * The current job, if any.
     */
    private var currentJob: Job? = null

    /**
     * Start a new background task.
     *
     * @param scope The coroutine scope
     * @param task The task to execute
     */
    fun start(scope: CoroutineScope, task: suspend TaskContext.() -> Unit) {
        if (isRunning) return

        isRunning = true
        isCancelled = false
        progress = 0f
        message = ""
        error = null

        val context = TaskContext(this)

        currentJob = scope.launch(Dispatchers.IO) {
            try {
                context.task()
            } catch (e: CancellationException) {
                // Task was cancelled
                withContext(Dispatchers.Main) {
                    isCancelled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.message ?: "Unknown error"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isRunning = false
                }
            }
        }
    }

    /**
     * Cancel the current task.
     */
    fun cancel() {
        isCancelled = true
        currentJob?.cancel()
    }

    /**
     * Update progress from within the task.
     *
     * @param value Progress value (0.0 to 1.0)
     * @param msg Progress message
     */
    internal suspend fun updateProgress(value: Float, msg: String) {
        withContext(Dispatchers.Main) {
            progress = value.coerceIn(0f, 1f)
            message = msg
        }
    }

    /**
     * Check if cancellation has been requested.
     *
     * @throws CancellationException if cancelled
     */
    internal fun checkCancellation() {
        if (isCancelled) {
            throw CancellationException("Task cancelled by user")
        }
    }

    /**
     * Reset the task state for a new operation.
     */
    fun reset() {
        isRunning = false
        progress = 0f
        message = ""
        isCancelled = false
        error = null
        currentJob = null
    }
}

/**
 * Context for executing a background task with progress updates.
 */
class TaskContext internal constructor(private val state: TaskState) {
    /**
     * Update the task progress.
     *
     * @param progress Progress value (0.0 to 1.0)
     * @param message Progress message
     */
    suspend fun updateProgress(progress: Float, message: String) {
        state.updateProgress(progress, message)
    }

    /**
     * Check if the task has been cancelled.
     *
     * @throws CancellationException if cancelled
     */
    fun checkCancellation() {
        state.checkCancellation()
    }

    /**
     * Whether cancellation has been requested.
     */
    val isCancelled: Boolean
        get() = state.isCancelled
}
