package com.example.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.repository.AppRepository

class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.ACTION_NOTIFICATION_DISMISSED") {
            Log.d("NotificationDismiss", "FCM push notification erased by user. Synchronizing view values silently...")
            try {
                val repository = AppRepository.getInstance(context.applicationContext)
                repository.markAllNotificationsAsRead()
            } catch (e: Exception) {
                Log.e("NotificationDismiss", "Exception in clearing tray notifier callback: ${e.message}")
            }
        }
    }
}
