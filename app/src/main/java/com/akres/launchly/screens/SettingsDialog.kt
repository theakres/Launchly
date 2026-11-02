package com.akres.launchly.screens

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage

@SuppressLint("ApplySharedPref")
@Composable
fun SettingsDialog(
    showDialog: Boolean,
    navHostController: NavHostController,
    userProfileInfo: List<String?>,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current

    var showAppSettings by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    AnimatedDialog(showDialog = showDialog, onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(vertical = 24.dp, horizontal = 24.dp)) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            MaterialTheme.shapes.extraLarge,
                        ).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(19.dp),
            ) {
                if (userProfileInfo.size == 3) {
                    Row(
                        modifier =
                            Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        AsyncImage(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                            model = userProfileInfo[2] ?: "",
                            contentDescription = "User Profile",
                        )
                        Column {
                            Text(
                                text = userProfileInfo[0] ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                            Text(
                                text = userProfileInfo[1] ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            showConfirmationDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Log out")
                    }
                }
            }
            Column(modifier = Modifier.padding(0.dp, 8.dp)) {
                SettingOption("Settings", Icons.Default.Settings) {
                    showAppSettings = true
                }
            }
        }
    }

    AppSettingsDialog(showAppSettings, onDismissRequest = {
        showAppSettings = false
    })

    ConfirmationDialog(
        title = "Log out",
        description = "Are you sure you want to log out?",
        onConfirmation = {
            showConfirmationDialog = false
            context
                .getSharedPreferences("accountData", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            context
                .getSharedPreferences("onboarding", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("onboarding_complete", false)
                .commit()
            navHostController.navigate("onboarding")
        },
        showDialog = showConfirmationDialog,
        onDismiss = {
            showConfirmationDialog = false
        },
        confirmText = "Log out",
    )
}

@Composable
private fun SettingOption(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clickable {
                    onClick()
                }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            title,
            Modifier.padding(end = 12.dp).size(24.dp),
        )
        Text(title)
    }
}
