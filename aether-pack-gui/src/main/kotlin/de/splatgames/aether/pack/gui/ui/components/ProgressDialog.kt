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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Button
import com.konyaco.fluent.component.Text
import de.splatgames.aether.pack.gui.i18n.I18n
import de.splatgames.aether.pack.gui.ui.theme.AetherColors

/**
 * Progress dialog shown during long-running operations.
 * Styled with Fluent Design System.
 *
 * @param progress Current progress (0.0 to 1.0)
 * @param message Current operation message
 * @param onCancel Callback when cancel is clicked
 * @param i18n Internationalization provider
 */
@Composable
fun ProgressDialog(
    progress: Float,
    message: String,
    onCancel: () -> Unit,
    i18n: I18n
) {
    Dialog(onDismissRequest = { /* Prevent dismissal by clicking outside */ }) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(FluentTheme.colors.background.solid.base)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = i18n["progress.title"],
                style = FluentTheme.typography.subtitle
            )

            Spacer(modifier = Modifier.height(24.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AetherColors.AccentPrimary,
                trackColor = FluentTheme.colors.subtleFill.secondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${(progress * 100).toInt()}%",
                style = FluentTheme.typography.body,
                color = FluentTheme.colors.text.text.secondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.secondary,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onCancel) {
                Text(i18n["progress.cancel"])
            }
        }
    }
}

/**
 * Overlay that blocks interaction while showing progress.
 */
@Composable
fun ProgressOverlay(
    visible: Boolean,
    progress: Float,
    message: String,
    onCancel: () -> Unit,
    i18n: I18n,
    modifier: Modifier = Modifier
) {
    if (visible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            ProgressDialog(
                progress = progress,
                message = message,
                onCancel = onCancel,
                i18n = i18n
            )
        }
    }
}
