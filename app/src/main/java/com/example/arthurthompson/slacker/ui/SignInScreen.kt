package com.example.arthurthompson.slacker.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firebase.ui.auth.FirebaseAuthUI
import com.firebase.ui.auth.configuration.authUIConfiguration
import com.firebase.ui.auth.configuration.auth_provider.AuthProvider
import com.firebase.ui.auth.ui.screens.FirebaseAuthScreen

@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit
) {
    val context = LocalContext.current
    
    val configuration = remember {
        authUIConfiguration {
            this.context = context.applicationContext
            providers {
                provider(
                    AuthProvider.Google(
                        scopes = listOf("email", "profile"),
                        serverClientId = null // Automatically populated from google-services.json
                    )
                )
            }
            isCredentialManagerEnabled = false // Fix for "Invalid FirebaseUser" error
            theme = null // Inherit from app theme
        }
    }

    FirebaseAuthScreen(
        configuration = configuration,
        onSignInSuccess = { result ->
            Log.d("SignInScreen", "Sign in successful: ${result.user?.email}")
            onSignInSuccess()
        },
        onSignInFailure = { exception ->
            Log.e("SignInScreen", "Sign in error: ${exception.message}", exception)
        },
        onSignInCancelled = {
            Log.d("SignInScreen", "Sign in cancelled")
        },
        customMethodPickerLayout = { providers, onProviderClick ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Slacker",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Where work (doesn't) happen.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(48.dp))
                
                providers.forEach { provider ->
                    Button(
                        onClick = { onProviderClick(provider) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Sign in with ${provider.providerName}")
                    }
                }
            }
        }
    )
}
