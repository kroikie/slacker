package com.example.arthurthompson.slacker

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class SlackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Explicitly initialize Firebase first to ensure it's ready
        FirebaseApp.initializeApp(this)

        // Install Play Integrity for production attestation and Debug provider for local testing
        if (BuildConfig.DEBUG) {
            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            Firebase.appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }
}
