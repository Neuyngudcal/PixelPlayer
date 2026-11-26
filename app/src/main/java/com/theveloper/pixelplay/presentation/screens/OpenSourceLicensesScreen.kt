package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.LocalInsideFloatingPane
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    onBackClick: () -> Unit,
    embeddedInPane: Boolean = false,
) {
    val libraries by produceLibraries(R.raw.aboutlibraries)

    val insideFloatingPane = LocalInsideFloatingPane.current
    val useTransparentContainer = embeddedInPane || insideFloatingPane

    Scaffold(
        containerColor = if (useTransparentContainer) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embeddedInPane) {
                TopAppBar(
                    windowInsets = if (insideFloatingPane) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (useTransparentContainer) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface
                    ),
                    title = { Text(stringResource(R.string.open_source_licenses_screen_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                )
            }
        },
    ) { contentPadding ->
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + MiniPlayerHeight,
            ),
        )
    }
}
