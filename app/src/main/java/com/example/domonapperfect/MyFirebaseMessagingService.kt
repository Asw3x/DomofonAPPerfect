package com.example.domonapperfect

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        // Typically, we don't need to send it here immediately because it's requested during login.
        // But if it refreshes, we might need to send it to the server.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "Message received from: ${message.from}")
        Log.d("FCM", "Data payload: ${message.data}")

        val data = message.data
        
        // Find keys case-insensitively
        val doorId = data.entries.firstOrNull { it.key.equals("doorid", ignoreCase = true) }?.value
            ?: data.entries.firstOrNull { it.key.equals("keyid", ignoreCase = true) }?.value
        val title = data.entries.firstOrNull { it.key.equals("title", ignoreCase = true) }?.value
        val callId = data.entries.firstOrNull { it.key.equals("callid", ignoreCase = true) }?.value
            ?: data.entries.firstOrNull { it.key.equals("sipaccount", ignoreCase = true) }?.value
            
        Log.d("FCM", "Parsed push: doorId=$doorId, callId=$callId")
        
        // If it's a "Call ended" push, dismiss the call screen
        if (title?.contains("завершён", ignoreCase = true) == true || title?.contains("завершен", ignoreCase = true) == true) {
            CallManager.rejectCall()
            return
        }

        if (doorId != null) {
            handleIncomingCall(doorId, callId)
        } else {
            Log.e("FCM", "Received push without doorId: ${message.data}")
            // Fallback for debugging
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = NotificationCompat.Builder(this, "auto_open_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Unknown Push")
                .setContentText(message.data.toString())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.data.toString()))
                .setAutoCancel(true)
            notificationManager.notify(message.hashCode(), builder.build())
        }
    }

    private fun handleIncomingCall(doorId: String, callId: String?) {
        val app = applicationContext as DomonapApplication
        val autoOpen = app.authRepository.isAutoOpenEnabled()
        val isCallNotificationOnly = app.authRepository.isCallNotificationOnly()

        if (autoOpen) {
            CoroutineScope(Dispatchers.IO).launch {
                val keys = app.intercomRepository.getKeys().getOrNull() ?: emptyList()
                val targetKey = keys.find { it.doorId == doorId || it.id == doorId }
                val keyIdToOpen = targetKey?.id ?: doorId
                
                app.intercomRepository.openRelay(keyIdToOpen)
                
                if (callId != null) {
                    app.intercomRepository.notifyCallEnded(callId)
                }
                
                val customName = app.intercomRepository.getDoorCustomizations()[keyIdToOpen]?.customName
                val doorNameStr = if (!customName.isNullOrBlank()) customName else (targetKey?.name ?: "Дверь")
                val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val builder = NotificationCompat.Builder(this@MyFirebaseMessagingService, "auto_open_channel")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Авто-открытие сработало \uD83D\uDD13")
                    .setContentText("$doorNameStr открыта в $timeStr")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                
                notificationManager.notify(doorId.hashCode() + 1000, builder.build())
            }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val keys = app.intercomRepository.getKeys().getOrNull() ?: emptyList()
                val targetKey = keys.find { it.doorId == doorId || it.id == doorId }
                val keyIdToOpen = targetKey?.id ?: doorId

                CallManager.setIncomingCall(keyIdToOpen, callId)

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Currently no CallService, we can just launch MainActivity directly for manual open
                val callIntent = Intent(this@MyFirebaseMessagingService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(this@MyFirebaseMessagingService, 0, callIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                val builder = NotificationCompat.Builder(this@MyFirebaseMessagingService, "call_channel")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Звонок в домофон!")
                    .setContentText("Нажмите, чтобы впустить гостя")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setAutoCancel(true)

                if (!isCallNotificationOnly) {
                    builder.setFullScreenIntent(pendingIntent, true)
                } else {
                    builder.setContentIntent(pendingIntent)
                }

                notificationManager.notify(keyIdToOpen.hashCode(), builder.build())
            }
        }
    }

    private fun createNotificationChannels() {
        val callChannel = NotificationChannel(
            "call_channel",
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH
        )
        val autoOpenChannel = NotificationChannel(
            "auto_open_channel",
            "Auto Open Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(callChannel)
        manager.createNotificationChannel(autoOpenChannel)
    }
}
