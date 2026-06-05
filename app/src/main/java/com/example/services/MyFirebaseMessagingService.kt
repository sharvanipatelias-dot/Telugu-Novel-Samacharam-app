package com.example.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.repository.AppRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_Service", "Refreshed FCM registration token received: $token")
        
        // Save the updated token locally
        val sharedPrefs = getSharedPreferences("telugu_fcm_prefs", MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_token", token).apply()

        // Sync or upload to Firebase Firestore if possible
        try {
            val repository = AppRepository.getInstance(applicationContext)
            repository.updateFCMTokenInFirestore(token)
        } catch (e: Exception) {
            Log.e("FCM_Service", "Failed to update FCM token in repository: ${e.message}")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_Service", "Message received from FCM. From sender ID: ${remoteMessage.from}")

        // 1. Process notification payload if present
        var title = remoteMessage.notification?.title
        var message = remoteMessage.notification?.body

        // 2. Process data payload if present (custom key-value pairs)
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            Log.d("FCM_Service", "FCM Custom Data payload found: $data")
            if (title.isNullOrEmpty()) {
                title = data["title"] ?: data["senderName"] ?: "Telugu App Update"
            }
            if (message.isNullOrEmpty()) {
                message = data["message"] ?: data["body"] ?: "New engaging community content uploaded."
            }
        }

        val postId = data["postId"]
        val postType = data["postType"]

        val finalTitle = title ?: "Telugu Novel & News"
        val finalMessage = message ?: "Check out recent live activity engagement!"

        Log.d("FCM_Service", "Showing push notification: Title='$finalTitle', Body='$finalMessage', PostId='$postId', PostType='$postType'")
        
        // Dispatch local system heads-up notification representing the push
        NotificationHelper.showPushNotification(
            context = applicationContext,
            title = finalTitle,
            message = finalMessage,
            postId = postId,
            postType = postType
        )
    }
}
