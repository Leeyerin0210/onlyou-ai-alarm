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
import com.nemuria.miya.ui.theme.GoldMedium
import com.nemuria.miya.ui.theme.GothicBlack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    currentScreen: String,
    title: String = "",
    onBack: () -> Unit = {},
    onSetting: () -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = if (currentScreen == "home") "MIYA" else "SCHEDULE",
                style = MaterialTheme.typography.titleLarge,
                color = GoldMedium,
            )
        },
        navigationIcon = {
            if (currentScreen == "schedule") {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GoldMedium,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = GothicBlack,
            titleContentColor = GoldMedium,
        ),
    )
}
