package com.example.domonapperfect.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domonapperfect.CallData

@Composable
fun IncomingCallScreen(
    callData: CallData,
    viewModel: IntercomViewModel,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val camera = uiState.keys.find { it.id == callData.doorId || it.doorId == callData.doorId }
    val token = viewModel.token

    LaunchedEffect(Unit) {
        if (uiState.keys.isEmpty()) {
            viewModel.loadKeys()
        }
    }

    val isRingtoneEnabled by viewModel.isRingtoneEnabled.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(isRingtoneEnabled) {
        var ringtone: android.media.Ringtone? = null
        if (isRingtoneEnabled) {
            val ringtoneUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            ringtone = android.media.RingtoneManager.getRingtone(context, ringtoneUri)
            ringtone?.play()
        }
        onDispose {
            ringtone?.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9000000)) // Semi-transparent black
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Входящий вызов",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            val displayName = if (camera != null) {
                uiState.customizations[camera.id]?.customName?.takeIf { it.isNotBlank() } ?: camera.name
            } else {
                "Дверь ID: ${callData.doorId}"
            }
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.LightGray
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (camera != null && (!camera.webrtcVideoUrl.isNullOrEmpty() || !camera.httpVideoUrl.isNullOrEmpty())) {
                Box(modifier = Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 16.dp)) {
                    VideoPlayer(
                        camera = camera,
                        token = token,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (uiState.isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text("Нет видео", color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Reject Button
                Button(
                    onClick = onReject,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Отклонить", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                
                // Accept Button
                Button(
                    onClick = onAccept,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Принять", tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}
