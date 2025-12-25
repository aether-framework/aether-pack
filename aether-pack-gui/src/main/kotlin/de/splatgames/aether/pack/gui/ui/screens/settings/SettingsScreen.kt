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

package de.splatgames.aether.pack.gui.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import de.splatgames.aether.pack.gui.i18n.I18n
import de.splatgames.aether.pack.gui.navigation.Navigator
import de.splatgames.aether.pack.gui.state.AppState
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import java.util.*
import javax.swing.JFileChooser

/**
 * Settings screen for application configuration.
 */
@Composable
fun SettingsScreen(
    appState: AppState,
    i18n: I18n,
    navigator: Navigator
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState)
    ) {
        // Page Title
        Text(
            text = i18n["settings.title"],
            style = FluentTheme.typography.title
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Appearance Section
        SettingsSection(title = i18n["settings.appearance"]) {
            // Theme Setting
            SettingsRow(
                icon = Icons.Regular.DarkTheme,
                title = i18n["settings.theme"],
                description = if (appState.settings.isDarkTheme) i18n["settings.theme.dark"] else i18n["settings.theme.light"]
            ) {
                FluentSwitch(
                    checked = appState.settings.isDarkTheme,
                    onCheckedChange = { checked ->
                        appState.settings.isDarkTheme = checked
                        appState.saveSettings()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Language Setting - uses dropdown for better scalability
            val availableLocales = remember { I18n.getAvailableLocales() }
            val currentLocale = appState.settings.locale
            val currentLanguageName = currentLocale.getDisplayLanguage(currentLocale)
                .replaceFirstChar { it.uppercase() }

            SettingsRow(
                icon = Icons.Regular.LocalLanguage,
                title = i18n["settings.language"],
                description = currentLanguageName
            ) {
                LanguageDropdown(
                    selectedLocale = currentLocale,
                    availableLocales = availableLocales,
                    onLocaleSelected = { locale ->
                        appState.settings.locale = locale
                        appState.saveSettings()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Defaults Section
        SettingsSection(title = i18n["settings.defaults"]) {
            // Default Compression
            val compressionOptions = listOf("none", "zstd", "lz4")

            SettingsRow(
                icon = Icons.Regular.ArrowMinimize,
                title = i18n["settings.default_compression"],
                description = i18n["compression.${appState.settings.defaultCompression}"]
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    compressionOptions.forEach { compression ->
                        val isSelected = compression == appState.settings.defaultCompression
                        SelectableButton(
                            selected = isSelected,
                            onClick = {
                                appState.settings.defaultCompression = compression
                                appState.saveSettings()
                            },
                            label = i18n["compression.$compression"]
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Default Chunk Size
            val chunkSizeOptions = listOf(64, 128, 256, 512, 1024)

            SettingsRow(
                icon = Icons.Regular.Grid,
                title = i18n["settings.default_chunk_size"],
                description = "${appState.settings.defaultChunkSizeKb} KB"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunkSizeOptions.forEach { size ->
                        val isSelected = size == appState.settings.defaultChunkSizeKb
                        SelectableButton(
                            selected = isSelected,
                            onClick = {
                                appState.settings.defaultChunkSizeKb = size
                                appState.saveSettings()
                            },
                            label = "$size KB"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Default Output Directory
            SettingsRow(
                icon = Icons.Regular.Folder,
                title = i18n["settings.default_output_dir"],
                description = appState.settings.defaultOutputDir.toString()
            ) {
                Button(
                    onClick = {
                        val chooser = JFileChooser().apply {
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            currentDirectory = appState.settings.defaultOutputDir.toFile()
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            appState.settings.defaultOutputDir = chooser.selectedFile.toPath()
                            appState.saveSettings()
                        }
                    }
                ) {
                    Text(i18n["common.browse"])
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Behavior Section
        SettingsSection(title = i18n["settings.behavior"]) {
            // Confirm Overwrite
            SettingsRow(
                icon = Icons.Regular.Warning,
                title = i18n["settings.confirm_overwrite"],
                description = if (appState.settings.confirmOverwrite) i18n["flag.yes"] else i18n["flag.no"]
            ) {
                FluentSwitch(
                    checked = appState.settings.confirmOverwrite,
                    onCheckedChange = { checked ->
                        appState.settings.confirmOverwrite = checked
                        appState.saveSettings()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Max Recent Files
            val recentFilesOptions = listOf(5, 10, 15, 20)

            SettingsRow(
                icon = Icons.Regular.History,
                title = i18n["settings.max_recent_files"],
                description = appState.settings.maxRecentFiles.toString()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentFilesOptions.forEach { count ->
                        val isSelected = count == appState.settings.maxRecentFiles
                        SelectableButton(
                            selected = isSelected,
                            onClick = {
                                appState.settings.maxRecentFiles = count
                                appState.saveSettings()
                            },
                            label = count.toString()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // About Section
        SettingsSection(title = i18n["common.about"]) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Regular.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Aether Pack",
                        style = FluentTheme.typography.bodyStrong
                    )
                    Text(
                        text = i18n["app.version"].replace("{0}", "0.2.0"),
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun FluentSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = AetherColors.AccentPrimary,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFF4A4A4A),
            uncheckedBorderColor = Color(0xFF6A6A6A)
        )
    )
}

@Composable
private fun SelectableButton(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    val backgroundColor = if (selected) AetherColors.AccentPrimary else FluentTheme.colors.subtleFill.secondary
    val textColor = if (selected) Color.White else FluentTheme.colors.text.text.primary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = FluentTheme.typography.body,
            color = textColor
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(FluentTheme.colors.background.card.default)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = FluentTheme.typography.body
            )
            Text(
                text = description,
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.secondary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        action()
    }
}

@Composable
private fun LanguageDropdown(
    selectedLocale: Locale,
    availableLocales: List<Locale>,
    onLocaleSelected: (Locale) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box {
        // Dropdown trigger button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isHovered) FluentTheme.colors.subtleFill.tertiary
                    else FluentTheme.colors.subtleFill.secondary
                )
                .hoverable(interactionSource)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = selectedLocale.getDisplayLanguage(selectedLocale)
                    .replaceFirstChar { it.uppercase() },
                style = FluentTheme.typography.body
            )
            Icon(
                imageVector = Icons.Regular.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(FluentTheme.colors.background.solid.base)
        ) {
            availableLocales.forEach { locale ->
                val itemInteractionSource = remember { MutableInteractionSource() }
                val isItemHovered by itemInteractionSource.collectIsHoveredAsState()
                val isSelected = locale.language == selectedLocale.language

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                isSelected -> AetherColors.AccentPrimary.copy(alpha = 0.15f)
                                isItemHovered -> FluentTheme.colors.subtleFill.secondary
                                else -> Color.Transparent
                            }
                        )
                        .hoverable(itemInteractionSource)
                        .clickable {
                            onLocaleSelected(locale)
                            expanded = false
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = locale.getDisplayLanguage(locale)
                            .replaceFirstChar { it.uppercase() },
                        style = FluentTheme.typography.body,
                        color = if (isSelected) AetherColors.AccentPrimary
                               else FluentTheme.colors.text.text.primary
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Regular.Checkmark,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AetherColors.AccentPrimary
                        )
                    }
                }
            }
        }
    }
}
