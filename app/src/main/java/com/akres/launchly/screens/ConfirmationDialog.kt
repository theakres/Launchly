package com.akres.launchly.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConfirmationDialog(
    showDialog: Boolean,
    title: String,
    description: String,
    onConfirmation: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String,
) {
    AnimatedDialog(showDialog = showDialog, onDismissRequest = onDismiss) { hide ->
        Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = {
                        hide()
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = {
                        onConfirmation()
                        hide()
                    },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(confirmText)
                }
            }
        }
    }
}
