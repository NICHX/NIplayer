package com.nichx.niplayer.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController

private const val PAGE_TRANSITION_MS = 300

@Composable
fun NiNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.Home.ROOT,
    builder: NavGraphBuilder.(NavHostController) -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(PAGE_TRANSITION_MS)) +
                slideInHorizontally(
                    animationSpec = tween(PAGE_TRANSITION_MS),
                    initialOffsetX = { it / 4 },
                )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(PAGE_TRANSITION_MS))
        },
        popEnterTransition = {
            fadeIn(tween(PAGE_TRANSITION_MS)) +
                slideInHorizontally(
                    animationSpec = tween(PAGE_TRANSITION_MS),
                    initialOffsetX = { -it / 4 },
                )
        },
        popExitTransition = {
            fadeOut(tween(PAGE_TRANSITION_MS)) +
                slideOutHorizontally(
                    animationSpec = tween(PAGE_TRANSITION_MS),
                    targetOffsetX = { it / 4 },
                )
        },
        // 系统返回手势（predictive back）统一为滑入/滑出，避免默认 scaleOut(0.7) 缩放
        predictivePopEnterTransition = {
            fadeIn(tween(PAGE_TRANSITION_MS)) +
                slideInHorizontally(
                    animationSpec = tween(PAGE_TRANSITION_MS),
                    initialOffsetX = { -it / 4 },
                )
        },
        predictivePopExitTransition = {
            fadeOut(tween(PAGE_TRANSITION_MS)) +
                slideOutHorizontally(
                    animationSpec = tween(PAGE_TRANSITION_MS),
                    targetOffsetX = { it / 4 },
                )
        },
        builder = { builder(navController) },
    )
}
