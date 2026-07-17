package com.pang.mdreader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pang.mdreader.model.FileNode
import com.pang.mdreader.ui.screen.BrowserScreen
import com.pang.mdreader.ui.screen.ReaderScreen
import com.pang.mdreader.ui.screen.SettingsScreen
import com.pang.mdreader.viewmodel.BrowserViewModel
import com.pang.mdreader.viewmodel.ReaderViewModel
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val BROWSER = "browser"
    const val READER = "reader/{fileTitle}/{fileUri}"
    const val SETTINGS = "settings"

    fun readerRoute(fileNode: FileNode): String {
        val encodedUri = URLEncoder.encode(fileNode.uri.toString(), "UTF-8")
        val encodedTitle = URLEncoder.encode(fileNode.name, "UTF-8")
        return "reader/$encodedTitle/$encodedUri"
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    browserViewModel: BrowserViewModel = viewModel(),
    readerViewModel: ReaderViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.BROWSER,
        modifier = modifier,
    ) {
        composable(Routes.BROWSER) {
            BrowserScreen(
                viewModel = browserViewModel,
                onFileSelected = { fileNode ->
                    readerViewModel.loadFile(fileNode)
                    navController.navigate(Routes.readerRoute(fileNode))
                },
            )
        }

        composable(
            route = Routes.READER,
            arguments = listOf(
                navArgument("fileTitle") { type = NavType.StringType },
                navArgument("fileUri") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val fileUriStr = backStackEntry.arguments?.getString("fileUri") ?: ""
            val fileTitle = backStackEntry.arguments?.getString("fileTitle") ?: "Document"

            val decodedUri = URLDecoder.decode(fileUriStr, "UTF-8")
            val decodedTitle = URLDecoder.decode(fileTitle, "UTF-8")
            val uri = Uri.parse(decodedUri)

            // Construct FileNode from uri
            val fileNode = FileNode(
                uri = uri,
                name = decodedTitle,
                isDirectory = false,
            )

            ReaderScreen(
                viewModel = readerViewModel,
                fileNode = fileNode,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                readerViewModel = readerViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
