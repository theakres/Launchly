package com.akres.launchly.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun Category(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(8.dp),
    )
}

@Composable
fun ActionButton(
    modifier: Modifier,
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit,
    loading: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .background(backgroundColor)
                .clickable(enabled = enabled) {
                    if (!loading) onClick()
                },
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .width(24.dp)
                        .offset { IntOffset(0, 7.5.dp.roundToPx()) },
                color = Color.White,
                trackColor = Color.Transparent,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        content()
    }
}

@Composable
fun DropDown(
    menu: List<Pair<String, () -> Unit>>,
    expanded: MutableState<Boolean>,
) {
    DropdownMenu(
        expanded = expanded.value,
        onDismissRequest = { expanded.value = false },
    ) {
        menu.forEach { (text, action) ->
            DropdownMenuItem(
                text = { Text(text) },
                onClick = {
                    action()
                    expanded.value = false
                },
            )
        }
    }
}

@Composable
fun f(downloadProgress: Float) = "%.2f".format(downloadProgress)

@Composable
fun Info(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis)
