package org.ukky.notitrace.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.ukky.notitrace.ui.screen.detail.DetailScreen
import org.ukky.notitrace.ui.screen.detail.JsonViewerScreen
import org.ukky.notitrace.ui.screen.detail.buildJsonShareFileName
import org.ukky.notitrace.ui.screen.home.HomeScreen
import org.ukky.notitrace.ui.screen.onboarding.OnboardingScreen
import org.ukky.notitrace.ui.screen.search.SearchScreen
import org.ukky.notitrace.ui.screen.settings.OssLicensesScreen
import org.ukky.notitrace.ui.screen.settings.SettingsScreen
import org.ukky.notitrace.ui.screen.tag.TagManageScreen

@Composable
fun NotiTraceNavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // ── オンボーディング ────
        composable(Route.Onboarding.route) {
            OnboardingScreen(
                onPermissionGranted = {
                    navController.navigate(Route.Home.route) {
                        popUpTo(Route.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }

        // ── ホーム ────
        composable(Route.Home.route) {
            HomeScreen(
                viewModel = hiltViewModel(),
                onNotificationClick = { id ->
                    navController.navigate(Route.Detail(id).route)
                },
                onSearchClick = { navController.navigate(Route.Search.route) },
                onTagManageClick = { navController.navigate(Route.TagManage.route) },
                onSettingsClick = { navController.navigate(Route.Settings.route) },
            )
        }

        // ── 通知詳細 ────
        composable(
            route = Route.Detail.ROUTE_PATTERN,
            arguments = listOf(navArgument(Route.Detail.ARG_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong(Route.Detail.ARG_ID) ?: 0L
            DetailScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
                onJsonClick = { navController.navigate(Route.JsonViewer(id).route) },
            )
        }

        // ── JSON ビューア ────
        composable(
            route = Route.JsonViewer.ROUTE_PATTERN,
            arguments = listOf(navArgument(Route.JsonViewer.ARG_ID) { type = NavType.LongType }),
        ) {
            val viewModel: org.ukky.notitrace.ui.screen.detail.DetailViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            JsonViewerScreen(
                rawJson = state.rawJson,
                shareFileName = buildJsonShareFileName(
                    packageName = state.notification?.packageName,
                    notificationId = state.notification?.id,
                    lastReceivedAt = state.notification?.lastReceivedAt,
                ),
                onBack = { navController.popBackStack() },
            )
        }

        // ── 検索 ────
        composable(Route.Search.route) {
            SearchScreen(
                viewModel = hiltViewModel(),
                onNotificationClick = { id ->
                    navController.navigate(Route.Detail(id).route)
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── タグ管理 ────
        composable(Route.TagManage.route) {
            TagManageScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
            )
        }

        // ── 設定 ────
        composable(Route.Settings.route) {
            SettingsScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
                onOssLicensesClick = { navController.navigate(Route.OssLicenses.route) },
            )
        }

        // ── OSSライセンス ────
        composable(Route.OssLicenses.route) {
            OssLicensesScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
