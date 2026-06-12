package com.example.domonapperfect.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.domonapperfect.CallManager
import com.example.domonapperfect.DomonapApplication
import kotlinx.coroutines.launch

@Composable
fun FloatingCallUI(onClose: () -> Unit) {
    val incomingCall by CallManager.incomingCall.collectAsState()
    val callData = incomingCall ?: return

    val context = LocalContext.current
    val application = context.applicationContext as DomonapApplication
    val viewModel: IntercomViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = IntercomViewModel.Factory(application.intercomRepository, application.authRepository)
    )

    val uiState by viewModel.uiState.collectAsState()
    val camera = uiState.keys.find { it.id == callData.doorId || it.doorId == callData.doorId }
    val token = viewModel.token

    LaunchedEffect(Unit) {
        if (uiState.keys.isEmpty()) {
            viewModel.loadKeys()
        }
    }

    var isAnswered by remember { mutableStateOf(false) }
    var isMicEnabled by remember { mutableStateOf(false) }

    val isRingtoneEnabled by viewModel.isRingtoneEnabled.collectAsState()
    DisposableEffect(isRingtoneEnabled, isAnswered) {
        var ringtone: android.media.Ringtone? = null
        if (isRingtoneEnabled && !isAnswered) {
            val ringtoneUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            ringtone = android.media.RingtoneManager.getRingtone(context, ringtoneUri)
            ringtone?.play()
        }
        onDispose {
            ringtone?.stop()
        }
    }

    Card(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val displayName = if (camera != null) {
                uiState.customizations[camera.id]?.customName?.takeIf { it.isNotBlank() } ?: camera.name
            } else {
                "Дверь ID: ${callData.doorId}"
            }
            Text(
                text = "Входящий вызов: $displayName",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (camera != null && (!camera.webrtcVideoUrl.isNullOrEmpty() || !camera.httpVideoUrl.isNullOrEmpty())) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    VideoPlayer(
                        camera = camera,
                        token = token,
                        isMicrophoneEnabled = isMicEnabled,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (uiState.isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("Нет видео", color = Color.Gray)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Reject Button
                Button(
                    onClick = {
                        if (callData.callId != null) {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                application.intercomRepository.notifyCallEnded(callData.callId)
                            }
                        }
                        SipManager.getInstance(context).endCall()
                        CallManager.rejectCall()
                        onClose()
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.size(60.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Отклонить", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                
                // Answer / Mic Toggle Button
                if (camera != null && (!camera.webrtcVideoUrl.isNullOrEmpty() || callData.sipAccount != null)) {
                    Button(
                        onClick = {
                            if (!isAnswered) {
                                isAnswered = true
                                isMicEnabled = true
                                if (callData.sipAccount != null) {
                                    // Normally we would use real password here
                                    SipManager.getInstance(context).register(callData.sipAccount)
                                    SipManager.getInstance(context).acceptCall()
                                }
                            } else {
                                isMicEnabled = !isMicEnabled
                                SipManager.getInstance(context).setMicrophoneMuted(!isMicEnabled)
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAnswered && !isMicEnabled) Color.Gray else Color(0xFF4CAF50)
                        ),
                        modifier = Modifier.size(60.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (isAnswered && !isMicEnabled) Icons.Default.MicOff else Icons.Default.Mic, 
                            contentDescription = "Ответить/Микрофон", 
                            tint = Color.White, 
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                
                // Open Door Button
                Button(
                    onClick = {
                        val delaySec = viewModel.callWindowDelaySeconds.value
                        viewModel.openDoor(callData.doorId)
                        CallManager.markDoorOpened(callData.doorId)
                        if (callData.callId != null) {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                application.intercomRepository.notifyCallEnded(callData.callId)
                            }
                        }
                        SipManager.getInstance(context).endCall()
                        if (delaySec > 0) {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                kotlinx.coroutines.delay(delaySec * 1000L)
                                CallManager.acceptCall()
                                onClose()
                            }
                        } else {
                            CallManager.acceptCall()
                            onClose()
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(60.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Открыть", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}
