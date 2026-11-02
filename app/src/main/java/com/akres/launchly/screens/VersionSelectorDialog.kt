package com.akres.launchly.screens

import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.akres.launchly.utils.Architecture
import com.akres.launchly.utils.VersionData
import com.akres.launchly.utils.VersionsViewModel

@Composable
fun VersionSelector(
    showDialog: Boolean,
    versionsViewModels: List<VersionsViewModel>,
    addedVersions: Set<String> = emptySet(),
    onConfirmation: (versions: List<VersionData>) -> Unit,
) {
    val archs = (Build.SUPPORTED_ABIS.map { Architecture.fromString(it).arch }).filter { it != "unknown" }
    var selectedArch by remember { mutableStateOf(Architecture.ARM64) }
    val selectedVersions = remember { mutableStateListOf<VersionData>() }
    var searchQuery by remember { mutableStateOf("") }

    val context = LocalContext.current

    val versionListPrefs = context.getSharedPreferences("version_list_prefs", Context.MODE_PRIVATE)
    val showVersionCode = versionListPrefs.getBoolean("show_version_code", true)
    val showDividers = versionListPrefs.getBoolean("show_dividers", true)

    AnimatedDialog(
        showDialog = showDialog,
        onDismissRequest = {
            selectedArch = Architecture.ARM64
            selectedVersions.clear()
            searchQuery = ""
            onConfirmation(emptyList())
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) { hide ->
        Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp)) {
            Text(
                "Select Minecraft Version",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Version") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
            }
            TabRow(selectedTabIndex = selectedArch.ordinal) {
                archs.forEachIndexed { index, title ->
                    Tab(
                        selected = index == selectedArch.ordinal,
                        onClick = { selectedArch = Architecture.entries.toTypedArray()[index] },
                        text = { Text(title) },
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                val viewModel = versionsViewModels[selectedArch.ordinal]
                val versionData by viewModel.versionData.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()
                val error by viewModel.error.collectAsState()

                when {
                    isLoading -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }

                    error != null -> {
                        Text("Error: $error", Modifier.align(Alignment.Center))
                    }

                    else -> {
                        val filteredVersions =
                            versionData.filter {
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.code.toString().contains(searchQuery)
                            }
                        LazyColumn {
                            items(
                                items = filteredVersions,
                                key = { it.code },
                            ) { version ->
                                val added = "${version.code}|${version.architecture.arch}" in addedVersions
                                VersionRow(
                                    version = version,
                                    selected = version in selectedVersions,
                                    added = added,
                                    onClick = {
                                        if (version in selectedVersions) selectedVersions.remove(version) else selectedVersions.add(version)
                                    },
                                    showVersionCode = showVersionCode,
                                )
                                if (showDividers) HorizontalDivider()
                            }
                        }
                    }
                }
            }
            TextButton(
                onClick = {
                    val ordered =
                        versionsViewModels.flatMap { vm -> vm.versionData.value.filter { it in selectedVersions } }
                    onConfirmation(ordered)
                    selectedArch = Architecture.ARM64
                    selectedVersions.clear()
                    searchQuery = ""
                    hide()
                },
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .padding(top = 16.dp),
            ) {
                Text(if (selectedVersions.isEmpty()) "Close" else "Confirm")
            }
        }
    }
}

@Composable
fun VersionRow(
    version: VersionData,
    selected: Boolean,
    added: Boolean = false,
    onClick: () -> Unit,
    showVersionCode: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (added) 0.5f else 1f)
            .clickable(enabled = !added, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(version.name, style = MaterialTheme.typography.bodyLarge)
            if (showVersionCode) {
                Text(
                    "Version Code: ${version.code}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (added) {
                Text(
                    "Already added",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Checkbox(
            checked = selected,
            enabled = !added,
            onCheckedChange = { onClick() },
        )
    }
}
