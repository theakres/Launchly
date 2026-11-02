package com.akres.launchly.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable (hide: () -> Unit) -> Unit,
) {
    var animateIn by remember { mutableStateOf(false) }
    var showAnimatedDialog by remember { mutableStateOf(false) }
    LaunchedEffect(showDialog) {
        if (showDialog) showAnimatedDialog = true
    }

    if (!showAnimatedDialog) return
    BasicAlertDialog(
        onDismissRequest = {
            animateIn = false
            onDismissRequest()
        },
        properties = properties,
    ) {
        LaunchedEffect(Unit) {
            animateIn = true
        }
        AnimatedVisibility(
            visible = animateIn,
            enter =
                fadeIn(spring(stiffness = Spring.StiffnessHigh)) +
                    scaleIn(
                        initialScale = .8f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    ),
            exit = slideOutVertically { it / 8 } + fadeOut() + scaleOut(targetScale = .95f),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                content { animateIn = false }
            }
            DisposableEffect(Unit) {
                onDispose {
                    showAnimatedDialog = false
                }
            }
        }
    }
}
