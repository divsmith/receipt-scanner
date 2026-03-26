package com.receiptscanner.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.receiptscanner.presentation.camera.CameraScreen
import com.receiptscanner.presentation.history.ReceiptHistoryScreen
import com.receiptscanner.presentation.review.TransactionReviewScreen
import com.receiptscanner.presentation.settings.SettingsScreen

object Routes {
    const val CAMERA = "camera"
    const val REVIEW = "review/{receiptId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun review(receiptId: String) = "review/$receiptId"
}

private const val NAV_ANIM_DURATION = 300

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CAMERA,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(NAV_ANIM_DURATION))
        },
        exitTransition = {
            fadeOut(tween(NAV_ANIM_DURATION))
        },
        popEnterTransition = {
            fadeIn(tween(NAV_ANIM_DURATION))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(NAV_ANIM_DURATION))
        },
    ) {
        composable(Routes.CAMERA) {
            CameraScreen(
                onReceiptCaptured = { receiptId ->
                    navController.navigate(Routes.review(receiptId))
                },
                onNavigateToHistory = {
                    navController.navigate(Routes.HISTORY)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }
        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument("receiptId") { type = NavType.StringType }),
        ) {
            TransactionReviewScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Routes.HISTORY) {
            ReceiptHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
