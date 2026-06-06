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
                            viewModel.openDoor(callData.doorId)
                            if (callData.callId != null) {
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    application.intercomRepository.notifyCallEnded(callData.callId)
                                }
                            }
                            CallManager.acceptCall()
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
            }
        }
      }
    }
    // Removed CallService
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
}
