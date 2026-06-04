package com.example.domonapperfect.ui.main

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import android.webkit.PermissionRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.example.domonapperfect.data.network.KeyResponse

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    camera: KeyResponse,
    token: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (!camera.httpVideoUrl.isNullOrEmpty() && !camera.httpVideoUrl.endsWith("whep")) {
        val videoUrl = camera.httpVideoUrl
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(videoUrl)
                if (videoUrl.startsWith("rtsp", ignoreCase = true)) {
                    val mediaSource = DefaultMediaSourceFactory(context)
                        .createMediaSource(mediaItem)
                    setMediaSource(mediaSource)
                } else if (token != null) {
                    val dataSourceFactory = DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
                    val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                    setMediaSource(mediaSource)
                } else {
                    setMediaItem(mediaItem)
                }
                prepare()
                playWhenReady = true
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                    Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                exoPlayer.release()
            }
        }

        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                    keepScreenOn = true
                }
            },
            modifier = modifier.fillMaxSize()
        )
    } else if (!camera.webrtcVideoUrl.isNullOrEmpty()) {
        val videoUrl = camera.webrtcVideoUrl
        val ctx = LocalContext.current

        var hasAudioPermission by remember { androidx.compose.runtime.mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) }
        
        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            hasAudioPermission = isGranted
        }
        
        LaunchedEffect(Unit) {
            if (!hasAudioPermission) {
                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }

        if (hasAudioPermission) {
            val rtcManager = remember { WebRtcManager(ctx, token ?: "", videoUrl) }

            DisposableEffect(Unit) {
                onDispose {
                    rtcManager.release()
                }
            }

            AndroidView(
                factory = { context ->
                    org.webrtc.SurfaceViewRenderer(context).apply {
                        init(rtcManager.eglBase.eglBaseContext, null)
                        setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        setEnableHardwareScaler(true)
                        
                        rtcManager.onVideoTrack = { track ->
                            track.addSink(this)
                        }
                    }
                },
                modifier = modifier.fillMaxSize()
            )
        } else {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Требуется разрешение на микрофон для видеосвязи", color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
