package com.example.domonapperfect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.NotificationManager

class OpenDoorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.domonapperfect.ACTION_OPEN_DOOR") {
            val keyId = intent.getStringExtra("KEY_ID") ?: return
            val doorName = intent.getStringExtra("DOOR_NAME") ?: "Дверь"
            val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)

            // Mark door as opened immediately to prevent incoming calls while network request is pending
            CallManager.markDoorOpened(keyId)

            // Show toast immediately so user knows action was registered
            Toast.makeText(context, "Открываем: $doorName...", Toast.LENGTH_SHORT).show()

            val app = context.applicationContext as? DomonapApplication ?: return
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = app.intercomRepository.openRelay(keyId)
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            Toast.makeText(context, "$doorName успешно открыта!", Toast.LENGTH_LONG).show()
                            // Optionally cancel the notification since the door is open
                            if (notificationId != -1) {
                                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                nm.cancel(notificationId)
                            }
                            CallManager.acceptCall() // dismiss call screen if it was showing
                        } else {
                            val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                            Toast.makeText(context, "Ошибка открытия: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
