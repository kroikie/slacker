package com.example.arthurthompson.slacker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.arthurthompson.slacker.ui.ChatScreen
import com.example.arthurthompson.slacker.ui.ChatViewModel
import com.example.arthurthompson.slacker.ui.SettingsScreen
import com.example.arthurthompson.slacker.ui.SignInScreen
import com.example.arthurthompson.slacker.ui.theme.SlackerTheme
import com.firebase.ui.auth.FirebaseAuthUI
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            SlackerTheme {
                SlackerApp()
            }
        }
    }
}

@Composable
fun SlackerApp() {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val startDestination = if (auth.currentUser == null) "signin" else "chat"
    
    // Create ViewModel at the App level so it's shared between screens
    val chatViewModel: ChatViewModel = viewModel()

    NavHost(navController = navController, startDestination = startDestination) {
        composable("signin") {
            SignInScreen(onSignInSuccess = {
                navController.navigate("chat") {
                    popUpTo("signin") { inclusive = true }
                }
            })
        }
        composable("chat") {
            ChatScreen(
                viewModel = chatViewModel,
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            val scope = rememberCoroutineScope()
            SettingsScreen(
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onLogout = {
                    scope.launch {
                        try {
                            FirebaseAuthUI.getInstance().signOut(navController.context)
                            navController.navigate("signin") {
                                popUpTo("chat") { inclusive = true }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            )
        }
    }
}
