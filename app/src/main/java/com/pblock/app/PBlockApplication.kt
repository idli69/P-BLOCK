package com.pblock.app

import android.app.Application
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class PBlockApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            Firebase.database("https://p-block-69-default-rtdb.firebaseio.com").setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Already initialized
        }
    }
}
