package com.pang.mdreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pang.mdreader.ui.navigation.AppNavHost
import com.pang.mdreader.ui.navigation.Routes
import com.pang.mdreader.ui.theme.MdReaderTheme
import com.pang.mdreader.viewmodel.ReaderViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MdReaderApp()
        }
    }
}

@Composable
fun MdReaderApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Determine if we should show bottom nav
    val showBottomNav = currentRoute == Routes.BROWSER || currentRoute == Routes.SETTINGS

    // Shared ReaderViewModel so theme changes from Settings apply globally
    val readerViewModel: ReaderViewModel = viewModel()
    val readerState by readerViewModel.state.collectAsState()

    val layoutDirection = LocalLayoutDirection.current
    MdReaderTheme(readerTheme = readerState.theme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (showBottomNav) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = NavigationBarDefaults.Elevation,
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == Routes.BROWSER,
                            onClick = {
                                if (currentRoute != Routes.BROWSER) {
                                    navController.navigate(Routes.BROWSER) {
                                        popUpTo(Routes.BROWSER) { inclusive = true }
                                    }
                                }
                            },
                            icon = {
                                Icon(Icons.Default.Folder, contentDescription = "文件浏览")
                            },
                            label = { Text("文件") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.secondary,
                                selectedTextColor = MaterialTheme.colorScheme.secondary,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.SETTINGS,
                            onClick = {
                                navController.navigate(Routes.SETTINGS)
                            },
                            icon = {
                                Icon(Icons.Default.Settings, contentDescription = "设置")
                            },
                            label = { Text("设置") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.secondary,
                                selectedTextColor = MaterialTheme.colorScheme.secondary,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            // Only apply bottom padding from Scaffold (top handled per-screen)
            AppNavHost(
                navController = navController,
                readerViewModel = readerViewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = innerPadding.calculateLeftPadding(layoutDirection),
                        end = innerPadding.calculateRightPadding(layoutDirection),
                        bottom = innerPadding.calculateBottomPadding(),
                    ),
            )
        }
    }
}
