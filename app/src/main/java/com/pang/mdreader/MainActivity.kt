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
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pang.mdreader.model.ReaderTheme
import com.pang.mdreader.ui.navigation.AppNavHost
import com.pang.mdreader.ui.navigation.Routes
import com.pang.mdreader.ui.theme.MdReaderTheme

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

    // Simple theme selection (default warm light, user can change in settings)
    var currentTheme by rememberSaveable { mutableIntStateOf(0) }
    val theme = ReaderTheme.entries.getOrElse(currentTheme) { ReaderTheme.WARM_LIGHT }

    MdReaderTheme(readerTheme = theme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (showBottomNav) {
                    NavigationBar {
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
                        )
                    }
                }
            },
        ) { padding ->
            AppNavHost(
                navController = navController,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
