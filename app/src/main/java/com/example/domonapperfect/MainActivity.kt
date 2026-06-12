package com.example.domonapperfect

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import com.example.domonapperfect.theme.DomonapPerfectTheme
import com.example.domonapperfect.ui.main.IncomingCallScreen
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.WindowManager
import android.net.Uri
import android.provider.Settings
import android.os.PowerManager
import android.content.Context
import android.app.NotificationManager

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (intent?.action == "com.example.domonapperfect.ACTION_SHORTCUT_OPEN_DOOR") {
        val receiverIntent = Intent(this, OpenDoorReceiver::class.java).apply {
            action = "com.example.domonapperfect.ACTION_OPEN_DOOR"
            intent.extras?.let { putExtras(it) }
        }
        sendBroadcast(receiverIntent)
        finish()
        return
    }

    // Wake up screen and show over lock screen
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    } else {
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    
    checkAndRequestPermissions()
    updateShortcuts()

    enableEdgeToEdge()
    setContent {
      val application = application as DomonapApplication
      val incomingCall by CallManager.incomingCall.collectAsState()

      DomonapPerfectTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                MainNavigation(application)
                
                incomingCall?.let { callData ->
                    val viewModel: com.example.domonapperfect.ui.main.IntercomViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = com.example.domonapperfect.ui.main.IntercomViewModel.Factory(application.intercomRepository, application.authRepository)
                    )
                    
                    IncomingCallScreen(
                        callData = callData,
                        viewModel = viewModel,
                        onAccept = {
                            val delaySec = viewModel.callWindowDelaySeconds.value
                            viewModel.openDoor(callData.doorId)
                            CallManager.markDoorOpened(callData.doorId)
                            if (callData.callId != null) {
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    application.intercomRepository.notifyCallEnded(callData.callId)
                                }
                            }
                            if (delaySec > 0) {
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    kotlinx.coroutines.delay(delaySec * 1000L)
                                    CallManager.acceptCall()
                                }
                            } else {
                                CallManager.acceptCall()
                            }
                        },
                        onReject = {
                            if (callData.callId != null) {
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    application.intercomRepository.notifyCallEnded(callData.callId)
                                }
                            }
                            CallManager.rejectCall()
                        }
                    )
                }

                androidx.compose.runtime.LaunchedEffect(incomingCall) {
                    if (incomingCall == null && intent?.getBooleanExtra("from_call_notification", false) == true) {
                        finishAndRemoveTask()
                    }
                }
            }
        }
      }
    }
    // Removed CallService
  }

  override fun onNewIntent(intent: Intent) {
      super.onNewIntent(intent)
      if (intent.action == "com.example.domonapperfect.ACTION_SHORTCUT_OPEN_DOOR") {
          val receiverIntent = Intent(this, OpenDoorReceiver::class.java).apply {
              action = "com.example.domonapperfect.ACTION_OPEN_DOOR"
              intent.extras?.let { putExtras(it) }
          }
          sendBroadcast(receiverIntent)
          
          // Clear any lingering call since they explicitly pressed a shortcut
          CallManager.rejectCall()
          
          // Move task to back so it doesn't interrupt them unnecessarily,
          // or just leave it. Moving to back makes the shortcut feel more seamless.
          moveTaskToBack(true)
      }
  }

  private fun checkAndRequestPermissions() {
      // 1. Notifications
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
              ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
          }
      }

      // 2. Full Screen Intent (Android 14+)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
          val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
          if (!notificationManager.canUseFullScreenIntent()) {
              try {
                  startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
              } catch (e: Exception) {
                  // Ignore if not supported
              }
          }
      }

      // 3. Overlay permission (SYSTEM_ALERT_WINDOW)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          if (!Settings.canDrawOverlays(this)) {
              try {
                  val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                  startActivity(intent)
              } catch (e: Exception) {
                  // Ignore if not supported
              }
          }
      }

    // 4. Ignore Battery Optimizations
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
          if (!pm.isIgnoringBatteryOptimizations(packageName)) {
              try {
                  val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                  intent.data = Uri.parse("package:$packageName")
                  startActivity(intent)
              } catch (e: Exception) {
                  // Ignore if not supported
              }
          }
      }
  }

  private fun updateShortcuts() {
      lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
          val app = application as DomonapApplication
          if (!app.authRepository.isAuthorized()) return@launch

          val keysResult = app.intercomRepository.getKeys()
          val keys = keysResult.getOrNull() ?: return@launch
          val customizations = app.intercomRepository.getDoorCustomizations()

          val sortedKeys = keys.sortedBy { customizations[it.id]?.orderIndex ?: 0 }.take(4)
          
          val shortcuts = sortedKeys.map { key ->
              val customName = customizations[key.id]?.customName
              val displayName = if (!customName.isNullOrBlank()) customName else key.name

              val intent = Intent(this@MainActivity, OpenDoorReceiver::class.java).apply {
                  action = "com.example.domonapperfect.ACTION_OPEN_DOOR"
                  putExtra("KEY_ID", key.id)
                  putExtra("DOOR_NAME", displayName)
              }

              // Since shortcuts prefer activities, we can route it through MainActivity, 
              // or just use receiver. If receiver fails on some launchers, we use MainActivity.
              val activityIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                  action = "com.example.domonapperfect.ACTION_SHORTCUT_OPEN_DOOR"
                  putExtra("KEY_ID", key.id)
                  putExtra("DOOR_NAME", displayName)
              }

              androidx.core.content.pm.ShortcutInfoCompat.Builder(this@MainActivity, key.id)
                  .setShortLabel(displayName)
                  .setLongLabel("Открыть $displayName")
                  .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this@MainActivity, android.R.drawable.ic_lock_idle_lock))
                  .setIntent(activityIntent)
                  .build()
          }

          androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(this@MainActivity, shortcuts)
      }
  }
}
