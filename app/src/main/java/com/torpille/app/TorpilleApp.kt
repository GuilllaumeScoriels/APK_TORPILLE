package com.torpille.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class TorpilleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        appContext = applicationContext
        com.torpille.app.notifications.NotificationChannels.create(this)

        Firebase.appCheck.installAppCheckProviderFactory(AppCheckProvider.factory())

    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
