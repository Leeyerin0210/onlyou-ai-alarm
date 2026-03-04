package com.nemuria.miya.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.nemuria.miya.ui.theme.MiyaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    currentScreen: String,
    title: String = "",
    onBack: () -> Unit = {},
    onSetting: () -> Unit = {},
) {
    val colors = MiyaTheme.colors

    CenterAlignedTopAppBar(
        title = {
            Text(
                text = if (currentScreen == "home") "MIYA" else "SCHEDULE",
                style = MaterialTheme.typography.titleLarge,
                color = colors.secondary,
            )
        },
        navigationIcon = {
            if (currentScreen == "schedule") {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.secondary,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = colors.secondary,
        ),
    )
}

@Preview
@Composable
fun TopBarPreview() {
    TopBar(
        currentScreen = "schedule",
        title = "미야",
        onBack = {},
        onSetting = {},
    )
}
