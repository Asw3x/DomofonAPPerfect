package com.example.domonapperfect.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import com.example.domonapperfect.data.network.CallLogDto
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: IntercomViewModel) {
    val callHistory by viewModel.callHistory.collectAsState()
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadCallHistory()
    }

    if (callHistory == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (callHistory!!.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("История вызовов пуста", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items = callHistory!!, key = { it.callId ?: it.startTime ?: it.hashCode() }) { log ->
                CallHistoryCard(log, onImageClick = { url ->
                    fullScreenImageUrl = url
                })
            }
        }
        
        if (fullScreenImageUrl != null) {
            FullScreenImageDialog(
                imageUrl = fullScreenImageUrl!!,
                onDismiss = { fullScreenImageUrl = null }
            )
        }
    }
}

@Composable
fun FullScreenImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        val extraWidth = (scale - 1) * size.width
                        val extraHeight = (scale - 1) * size.height
                        
                        val maxX = extraWidth / 2
                        val maxY = extraHeight / 2
                        
                        offset = androidx.compose.ui.geometry.Offset(
                            x = (offset.x + pan.x * scale).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y * scale).coerceIn(-maxY, maxY)
                        )
                    }
                }
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full Screen Photo",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Close", tint = Color.White) // Temporary icon
            }
        }
    }
}

@Composable
fun CallHistoryCard(log: CallLogDto, onImageClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!log.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = log.photoUrl,
                    contentDescription = "Caller Photo",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable { onImageClick(log.photoUrl) },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.address ?: "Неизвестная дверь",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = formatCallTime(log.startTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            CallStatusIcon(status = log.callStatus)
        }
    }
}

@Composable
fun CallStatusIcon(status: String?) {
    when (status?.lowercase()) {
        "answered", "accepted" -> {
            Icon(Icons.Default.Call, contentDescription = "Принят", tint = Color(0xFF4CAF50)) // Green
        }
        "missed" -> {
            Icon(Icons.Default.CallMissed, contentDescription = "Пропущен", tint = MaterialTheme.colorScheme.error)
        }
        "rejected", "declined" -> {
            Icon(Icons.Default.CallEnd, contentDescription = "Отклонен", tint = MaterialTheme.colorScheme.error)
        }
        "auto_opened" -> {
            Icon(Icons.Default.SmartToy, contentDescription = "Авто-открытие", tint = MaterialTheme.colorScheme.primary)
        }
        else -> {
            Icon(Icons.Default.Call, contentDescription = status ?: "Неизвестно", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun formatCallTime(timeString: String?): String {
    if (timeString.isNullOrBlank()) return "Неизвестное время"
    return try {
        // Assume API returns ISO8601 string like "2026-06-05T14:30:00Z" or "2026-06-05T14:30:00"
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = parser.parse(timeString)
        if (date != null) {
            val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            formatter.format(date)
        } else {
            timeString
        }
    } catch (e: Exception) {
        timeString
    }
}
