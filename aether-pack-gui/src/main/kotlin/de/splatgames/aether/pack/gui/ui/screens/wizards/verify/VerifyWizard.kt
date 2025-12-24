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

package de.splatgames.aether.pack.gui.ui.screens.wizards.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Button
import com.konyaco.fluent.component.Icon
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.icons.Icons
import com.konyaco.fluent.icons.regular.*
import de.splatgames.aether.pack.core.AetherPackReader
import de.splatgames.aether.pack.gui.i18n.I18n
import de.splatgames.aether.pack.gui.navigation.Navigator
import de.splatgames.aether.pack.gui.state.AppState
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Result of verifying a single entry.
 */
data class EntryVerificationResult(
    val name: String,
    val valid: Boolean,
    val errorMessage: String? = null
)

/**
 * Wizard for verifying archive integrity.
 * Redesigned with Fluent Design System styling.
 */
@Composable
fun VerifyWizard(
    archivePath: Path,
    appState: AppState,
    i18n: I18n,
    navigator: Navigator
) {
    var isVerifying by remember { mutableStateOf(true) }
    var verificationComplete by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<EntryVerificationResult>>(emptyList()) }
    var currentEntry by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Start verification automatically
    LaunchedEffect(archivePath) {
        try {
            val verificationResults = mutableListOf<EntryVerificationResult>()

            withContext(Dispatchers.IO) {
                AetherPackReader.open(archivePath).use { reader ->
                    val entries = reader.entries.toList()

                    entries.forEachIndexed { index, entry ->
                        // Update UI state on main thread
                        withContext(Dispatchers.Main) {
                            currentEntry = entry.name
                            progress = (index + 1).toFloat() / entries.size
                        }

                        try {
                            // Read all bytes to verify checksums
                            reader.readAllBytes(entry)
                            verificationResults.add(
                                EntryVerificationResult(entry.name, true)
                            )
                        } catch (e: Exception) {
                            verificationResults.add(
                                EntryVerificationResult(entry.name, false, e.message)
                            )
                        }
                    }
                }
            }

            results = verificationResults
            verificationComplete = true
        } catch (e: Exception) {
            errorMessage = e.message ?: i18n["error.unknown"]
        } finally {
            isVerifying = false
        }
    }

    val failedCount = results.count { !it.valid }
    val allValid = results.isNotEmpty() && failedCount == 0

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FluentTheme.colors.background.solid.base)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = i18n["wizard.verify.title"],
                    style = FluentTheme.typography.subtitle
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Regular.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AetherColors.AccentPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = archivePath.fileName.toString(),
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = { navigator.goBack() }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Regular.Dismiss,
                    contentDescription = i18n["common.close"],
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(24.dp)
        ) {
            when {
                errorMessage != null -> {
                    ErrorContent(errorMessage!!, i18n)
                }
                isVerifying -> {
                    VerifyingContent(
                        progress = progress,
                        currentEntry = currentEntry,
                        i18n = i18n
                    )
                }
                verificationComplete -> {
                    ResultsContent(
                        results = results,
                        allValid = allValid,
                        failedCount = failedCount,
                        i18n = i18n
                    )
                }
            }
        }

        // Footer
        if (verificationComplete || errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FluentTheme.colors.background.solid.base)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = { navigator.goBack() }) {
                    Text(i18n["common.close"])
                }
            }
        }
    }
}

@Composable
private fun VerifyingContent(
    progress: Float,
    currentEntry: String,
    i18n: I18n
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            CircularProgressIndicator(
                color = AetherColors.AccentPrimary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = i18n["wizard.verify.verifying"],
                style = FluentTheme.typography.subtitle
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = i18n.format("wizard.verify.progress", currentEntry),
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.secondary
            )
            Spacer(modifier = Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = AetherColors.AccentPrimary,
                trackColor = FluentTheme.colors.subtleFill.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.secondary
            )
        }
    }
}

@Composable
private fun ResultsContent(
    results: List<EntryVerificationResult>,
    allValid: Boolean,
    failedCount: Int,
    i18n: I18n
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Result summary card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (allValid) AetherColors.Success.copy(alpha = 0.1f)
                    else AetherColors.Error.copy(alpha = 0.1f)
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (allValid) Icons.Regular.CheckmarkCircle else Icons.Regular.ErrorCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (allValid) AetherColors.Success else AetherColors.Error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = if (allValid) {
                        i18n["wizard.verify.success"]
                    } else {
                        i18n.format("wizard.verify.failed", failedCount, results.size)
                    },
                    style = FluentTheme.typography.subtitle,
                    color = if (allValid) AetherColors.Success else AetherColors.Error
                )
                Text(
                    text = "${results.size} ${i18n["archive.entries"].lowercase()}",
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Results list
        Text(
            text = i18n["inspector.entries"],
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(FluentTheme.colors.background.card.default)
        ) {
            items(results) { result ->
                ResultRow(result = result, i18n = i18n)
            }
        }
    }
}

@Composable
private fun ResultRow(
    result: EntryVerificationResult,
    i18n: I18n
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isHovered) FluentTheme.colors.subtleFill.secondary else Color.Transparent)
            .hoverable(interactionSource)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (result.valid) Icons.Regular.Checkmark else Icons.Regular.Dismiss,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (result.valid) AetherColors.Success else AetherColors.Error
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.name,
                style = FluentTheme.typography.body
            )
            if (!result.valid && result.errorMessage != null) {
                Text(
                    text = result.errorMessage,
                    style = FluentTheme.typography.caption,
                    color = AetherColors.Error
                )
            }
        }
        Text(
            text = if (result.valid) i18n["wizard.verify.entry_ok"] else i18n["wizard.verify.entry_failed"],
            style = FluentTheme.typography.caption,
            color = if (result.valid) AetherColors.Success else AetherColors.Error
        )
    }
}

@Composable
private fun ErrorContent(message: String, i18n: I18n) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Regular.ErrorCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = AetherColors.Error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = i18n["common.error"],
                style = FluentTheme.typography.subtitle,
                color = AetherColors.Error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = FluentTheme.typography.body,
                color = FluentTheme.colors.text.text.secondary
            )
        }
    }
}
