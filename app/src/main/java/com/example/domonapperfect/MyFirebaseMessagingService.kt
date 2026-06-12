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
        val sipAccount = data.entries.firstOrNull { it.key.equals("sipaccount", ignoreCase = true) }?.value
        val callId = data.entries.firstOrNull { it.key.equals("callid", ignoreCase = true) }?.value ?: sipAccount
            
        Log.d("FCM", "Parsed push: doorId=$doorId, callId=$callId, sipAccount=$sipAccount")
        
        // If it's a "Call ended" or "Call answered elsewhere" push, dismiss the call screen
        if (title?.contains("завершён", ignoreCase = true) == true || 
            title?.contains("завершен", ignoreCase = true) == true ||
            title?.contains("принят", ignoreCase = true) == true ||
            title?.contains("отвечен", ignoreCase = true) == true) {
            CallManager.rejectCall()
            return
        }

        if (doorId != null) {
            handleIncomingCall(doorId, callId, sipAccount)
        } else {
            Log.d("FCM", "Ignored push without doorId: ${message.data}")
            // Silently ignore instead of showing "Unknown Push"
        }
    }

    private fun handleIncomingCall(doorId: String, callId: String?, sipAccount: String?) {
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

                if (app.authRepository.isDoNotDisturbEnabled()) {
                    Log.d("FCM", "Do Not Disturb is enabled. Rejecting call $callId")
                    if (callId != null) {
                        app.intercomRepository.notifyCallEnded(callId)
                    }
                    return@launch
                }

                if (CallManager.isRecentlyOpened(keyIdToOpen)) {
                    Log.d("FCM", "Door $keyIdToOpen was recently opened. Ignoring incoming call push.")
                    return@launch
                }

                CallManager.setIncomingCall(keyIdToOpen, callId, sipAccount)

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Currently no CallService, we can just launch MainActivity directly for manual open
                val callIntent = Intent(this@MyFirebaseMessagingService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("from_call_notification", true)
                }
                val pendingIntent = PendingIntent.getActivity(this@MyFirebaseMessagingService, 0, callIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.FULL_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                    "DomonapPerfect:IncomingCall"
                )
                wakeLock.acquire(3000)

                val notificationId = keyIdToOpen.hashCode()

                // Action to Open Door
                val openIntent = Intent(this@MyFirebaseMessagingService, OpenDoorReceiver::class.java).apply {
                    action = "com.example.domonapperfect.ACTION_OPEN_DOOR"
                    putExtra("KEY_ID", keyIdToOpen)
                    val customName = app.intercomRepository.getDoorCustomizations()[keyIdToOpen]?.customName
                    putExtra("DOOR_NAME", if (!customName.isNullOrBlank()) customName else (targetKey?.name ?: "Дверь"))
                    putExtra("NOTIFICATION_ID", notificationId)
                }
                val openPendingIntent = PendingIntent.getBroadcast(
                    this@MyFirebaseMessagingService,
                    notificationId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(this@MyFirebaseMessagingService, "call_channel")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Звонок в домофон!")
                    .setContentText("Нажмите, чтобы впустить гостя")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setAutoCancel(true)
                    .addAction(android.R.drawable.ic_lock_idle_lock, "Открыть дверь", openPendingIntent)

                if (!isCallNotificationOnly) {
                    builder.setFullScreenIntent(pendingIntent, true)
                    
                    val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    val isScreenOn = powerManager.isInteractive
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        if (android.provider.Settings.canDrawOverlays(this@MyFirebaseMessagingService)) {
                            if (isScreenOn) {
                                // Screen is unlocked and we have overlay permission -> show floating window
                                try {
                                    val serviceIntent = Intent(this@MyFirebaseMessagingService, FloatingCallService::class.java)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        startForegroundService(serviceIntent)
                                    } else {
                                        startService(serviceIntent)
                                    }
                                } catch (e: Exception) {
                                    Log.e("FCM", "Failed to start FloatingCallService", e)
                                    startActivity(callIntent)
                                }
                            } else {
                                // Screen is off -> force wake and show full screen app
                                try {
                                    startActivity(callIntent)
                                } catch (e: Exception) {
                                    Log.e("FCM", "Failed to force start activity", e)
                                }
                            }
                        }
                    } else {
                        try {
                            startActivity(callIntent)
                        } catch (e: Exception) {
                            Log.e("FCM", "Failed to force start activity", e)
                        }
                    }
                } else {
                    builder.setContentIntent(pendingIntent)
                }

                notificationManager.notify(notificationId, builder.build())
            }
        }
    }

    private fun createNotificationChannels() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
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
}
