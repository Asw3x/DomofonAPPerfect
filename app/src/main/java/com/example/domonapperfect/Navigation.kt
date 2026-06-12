package com.example.domonapperfect

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.domonapperfect.ui.main.MainScreen

@Composable
fun MainNavigation(application: DomonapApplication) {
  val authRepository = application.authRepository
  val intercomRepository = application.intercomRepository
  val startRoute = if (authRepository.isAuthorized()) Main else Login
  val backStack = rememberNavBackStack(startRoute)

  androidx.compose.runtime.LaunchedEffect(authRepository) {
      authRepository.authStateFlow.collect { isAuthorized ->
          if (!isAuthorized) {
              backStack.clear()
              backStack.add(Login)
          }
      }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Login> {
          val viewModel: com.example.domonapperfect.ui.auth.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
              factory = com.example.domonapperfect.ui.auth.AuthViewModel.Factory(authRepository)
          )
          com.example.domonapperfect.ui.auth.LoginScreen(
              viewModel = viewModel,
              onLoginSuccess = {
                  backStack.clear()
                  backStack.add(Main)
              }
          )
        }
        entry<Main> {
          val viewModel: com.example.domonapperfect.ui.main.IntercomViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
              factory = com.example.domonapperfect.ui.main.IntercomViewModel.Factory(intercomRepository, authRepository)
          )
          com.example.domonapperfect.ui.main.MainScreen(
              viewModel = viewModel,
              onLogout = {
                  authRepository.logout()
                  backStack.clear()
                  backStack.add(Login)
              }
          )
        }
      },
  )
}
