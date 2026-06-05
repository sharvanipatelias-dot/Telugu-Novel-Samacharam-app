package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object NotificationHelper {
    private const val CHANNEL_ID = "telugu_fcm_notifications_channel"
    private const val CHANNEL_NAME = "Telugu Application Push News & Updates"
    private const val CHANNEL_DESC = "FCM Push Notifications for Telugu novel releases, live news and community engagement alerts."

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = android.graphics.Color.YELLOW
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("NotificationHelper", "FCM push notification channel created successfully")
        }
    }

    fun showPushNotification(
        context: Context,
        title: String,
        message: String,
        postId: String? = null,
        postType: String? = null
    ) {
        // Enforce notification channel creation just in case
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (postId != null) {
                putExtra("postId", postId)
            }
            if (postType != null) {
                putExtra("postType", postType)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (postId?.hashCode() ?: 0) + (postType?.hashCode() ?: 0) + System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, NotificationDismissReceiver::class.java).apply {
            action = "com.example.ACTION_NOTIFICATION_DISMISSED"
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use the application's actual standard launcher icon rather than a generic system drawable
        val iconRes = com.example.R.mipmap.ic_launcher

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setLights(android.graphics.Color.YELLOW, 500, 1000)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        try {
            notificationManager.notify(notificationId, builder.build())
            Log.d("NotificationHelper", "Successfully dispatched system-level push notification ID: $notificationId")
        } catch (e: SecurityException) {
            Log.e("NotificationHelper", "SecurityException posting notification (check permissions): ${e.message}")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Error posting notification: ${e.message}")
        }
    }

    fun sharePostWithImageAndLink(context: Context, title: String, text: String, imageUrl: String) {
        try {
            val storeLink = "https://play.google.com/store/apps/details?id=${context.packageName}"
            val finalBodyText = "$title\n\n$text\n\nTelugu Novel & Samacharam యాప్ ద్వారా షేర్ చేయబడింది! ఇప్పుడే డౌన్‌లోడ్ చేసుకోండి:\n$storeLink"

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, finalBodyText)
                type = "text/plain"
            }

            if (imageUrl.isNotEmpty()) {
                val imageLoader = coil.ImageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .target { result ->
                        try {
                            val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                val tempFile = java.io.File(context.cacheDir, "shared_temp_image.jpg")
                                java.io.FileOutputStream(tempFile).use { out ->
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                                }
                                val imageUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    tempFile
                                )
                                sendIntent.apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, imageUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("NotificationHelper", "Error caching share image: ${e.message}")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Post Via"))
                    }
                    .build()
                imageLoader.enqueue(request)
            } else {
                context.startActivity(Intent.createChooser(sendIntent, "Share Post Via"))
            }
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to construct custom image share intent: ${e.message}")
        }
    }
}
