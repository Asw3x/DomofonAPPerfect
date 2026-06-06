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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import android.view.WindowManager

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
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

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
}
