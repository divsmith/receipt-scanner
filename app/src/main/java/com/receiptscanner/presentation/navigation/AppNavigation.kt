package com.receiptscanner.presentation.navigation

import android.net.Uri
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
import com.receiptscanner.presentation.preview.ReceiptPreviewScreen
import com.receiptscanner.presentation.review.TransactionReviewScreen
import com.receiptscanner.presentation.settings.SettingsScreen

object Routes {
    const val CAMERA = "camera"
    const val RECEIPT_PREVIEW = "receipt_preview/{imagePath}/{rotationDegrees}"
    const val REVIEW = "review/{receiptId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun receiptPreview(imagePath: String, rotationDegrees: Int) =
        "receipt_preview/${Uri.encode(imagePath)}/$rotationDegrees"

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
                onNavigateToPreview = { imagePath, rotationDegrees ->
                    navController.navigate(Routes.receiptPreview(imagePath, rotationDegrees))
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
            route = Routes.RECEIPT_PREVIEW,
            arguments = listOf(
                navArgument("imagePath") { type = NavType.StringType },
                navArgument("rotationDegrees") { type = NavType.IntType },
            ),
        ) {
            ReceiptPreviewScreen(
                onNavigateToReview = { receiptId ->
                    navController.navigate(Routes.review(receiptId)) {
                        // Remove the preview screen from back stack so back from review goes to camera
                        popUpTo(Routes.CAMERA)
                    }
                },
                onRetake = { navController.popBackStack() },
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
