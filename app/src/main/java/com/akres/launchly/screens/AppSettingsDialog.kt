package com.akres.launchly.screens

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AppSettingsDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val uiPrefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    val isSystemDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = remember { mutableStateOf(uiPrefs.getBoolean("dark_theme", isSystemDarkTheme)) }
    val isDynamicColor = remember { mutableStateOf(uiPrefs.getBoolean("dynamic_color", true)) }

    val versionListPrefs = context.getSharedPreferences("version_list_prefs", Context.MODE_PRIVATE)
    val showVersionCode = remember { mutableStateOf(versionListPrefs.getBoolean("show_version_code", true)) }
    val showDividers = remember { mutableStateOf(versionListPrefs.getBoolean("show_dividers", true)) }

    AnimatedDialog(showDialog = showDialog, onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(vertical = 24.dp, horizontal = 0.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(24.dp, 0.dp))
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.weight(1f, fill = false),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "User Interface",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(24.dp, 0.dp),
                    )
                    AppSettingOption(
                        title = "Theme",
                        description = if (isDarkTheme.value) "Dark" else "Light",
                        state = isDarkTheme.value,
                        onCheckedChange = {
                            isDarkTheme.value = it
                            uiPrefs.edit().putBoolean("dark_theme", it).apply()
                        },
                    )
                    AppSettingOption(
                        title = "Dynamic Color",
                        description = "Theme color based on wallpaper",
                        state = isDynamicColor.value,
                        onCheckedChange = {
                            isDynamicColor.value = it
                            uiPrefs.edit().putBoolean("dynamic_color", it).apply()
                        },
                    )
                    Text(
                        "Version List",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 0.dp),
                    )
                    AppSettingOption(
                        title = "Show Version Code",
                        description = "Show version code in version list",
                        state = showVersionCode.value,
                        onCheckedChange = {
                            showVersionCode.value = it
                            versionListPrefs.edit().putBoolean("show_version_code", it).apply()
                        },
                    )
                    AppSettingOption(
                        title = "Show Dividers",
                        description = "Show dividers between versions",
                        state = showDividers.value,
                        onCheckedChange = {
                            showDividers.value = it
                            versionListPrefs.edit().putBoolean("show_dividers", it).apply()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun AppSettingOption(
    title: String,
    description: String,
    state: Boolean? = null,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(24.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state != null) {
            VerticalDivider(
                Modifier
                    .height(30.dp)
                    .padding(16.dp, 0.dp),
            )
            Switch(
                checked = state,
                onCheckedChange = { bool ->
                    onCheckedChange(bool)
                },
                thumbContent = {
                    if (title == "Theme") {
                        if (state) {
                            Icon(Icons.Default.DarkMode, contentDescription = "Dark Mode")
                        } else {
                            Icon(Icons.Default.LightMode, contentDescription = "Light Mode")
                        }
                    }
                },
            )
        }
    }
}
