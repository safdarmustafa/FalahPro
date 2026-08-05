package com.falahpro.app.auth

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private object AuthRoutes {
    const val LOGIN = "auth_login"
    const val SIGN_UP = "auth_signup"
}

/**
 * Login-flow navigation only (Login ↔ Sign Up). Does not touch AppNavigation.
 */
@Composable
fun AuthNavigation(
    onLoginSuccess: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AuthRoutes.LOGIN
    ) {
        composable(AuthRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = onLoginSuccess,
                onCreateAccount = {
                    navController.navigate(AuthRoutes.SIGN_UP)
                }
            )
        }
        composable(AuthRoutes.SIGN_UP) {
            SignUpScreen(
                onAccountCreated = {
                    navController.popBackStack(AuthRoutes.LOGIN, inclusive = false)
                },
                onBackToLogin = {
                    navController.popBackStack()
                }
            )
        }
    }
}
