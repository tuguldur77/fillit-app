package com.fillit.app

import android.app.Application
import com.fillit.app.model.SessionManager
import com.fillit.app.notifications.NotificationHelper
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp

class FillItApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        SessionManager.init(this)
        CrashLogger.init()
        NotificationHelper.init(this)

        if (!Places.isInitialized() && BuildConfig.MAPS_API_KEY.isNotBlank()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }
    }
}
