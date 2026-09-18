package com.pblock.app.accountability

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.pblock.app.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RemoteSyncManager(
    private val prefs: PreferencesManager,
    private val accountabilityManager: AccountabilityManager
) {
    private val TAG = "RemoteSyncManager"

    suspend fun startPolling() {
        // Renamed to keep MainActivity unchanged, but we are setting up a listener
        try {
            val isProtected = prefs.isProtected.first()
            if (isProtected) {
                val partnerCodeEncrypted = prefs.partnerCodeEncrypted.first()
                if (partnerCodeEncrypted != null) {
                    val topicId = "pblock_sync_" + Math.abs(partnerCodeEncrypted.hashCode())
                    
                    val database = Firebase.database
                    val ref = database.getReference("users/$topicId/status")
                    
                    Log.i(TAG, "Listening to Firebase Realtime Database at users/$topicId/status")
                    
                    ref.addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val status = snapshot.getValue(String::class.java)
                            if (status == "UNLOCK") {
                                Log.i(TAG, "Firebase UNLOCK command received!")
                                CoroutineScope(Dispatchers.IO).launch {
                                    prefs.setProtected(false)
                                    prefs.clearEmergencyUnlock()
                                    // Reset status so it doesn't loop
                                    ref.setValue("LOCKED")
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e(TAG, "Firebase listener cancelled", error.toException())
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Firebase listener", e)
        }
    }
}
