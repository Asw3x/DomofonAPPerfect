package com.example.domonapperfect.ui.main

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    isMicrophoneEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (!camera.httpVideoUrl.isNullOrEmpty() && !camera.httpVideoUrl.endsWith("whep")) {
        val videoUrl = camera.httpVideoUrl
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        var isVideoPlaying by remember { mutableStateOf(false) }
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
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onRenderedFirstFrame() {
                        isVideoPlaying = true
                    }
                })
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

        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = {
                    PlayerView(context).apply {
                        player = exoPlayer
                        useController = true
                        keepScreenOn = true
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            if (!isVideoPlaying && !camera.videoPreview.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(camera.videoPreview)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Превью камеры",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else if (!camera.webrtcVideoUrl.isNullOrEmpty()) {
        val videoUrl = camera.webrtcVideoUrl
        val ctx = LocalContext.current

        var audioPermissionState by remember { androidx.compose.runtime.mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) }
        
        val registryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
        
        if (registryOwner != null && !audioPermissionState) {
            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                audioPermissionState = isGranted
            }
            LaunchedEffect(Unit) {
                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }

        // Always show video, but WebRtcManager will only send audio if we actually have permission.
        // Or wait, if we pass audioPermissionState to WebRtcManager, it can decide whether to add SEND_RECV.
        // For now, we will just initialize WebRtcManager. If permission is missing, audio capture might just fail silently or log error.
        
        val rtcManager = remember(audioPermissionState) { WebRtcManager(ctx, token ?: "", videoUrl, audioPermissionState) }
        var isVideoPlaying by remember { mutableStateOf(false) }

        LaunchedEffect(isMicrophoneEnabled, audioPermissionState) {
            if (audioPermissionState) {
                rtcManager.setMicrophoneEnabled(isMicrophoneEnabled)
            }
        }

        DisposableEffect(rtcManager) {
            onDispose {
                rtcManager.release()
            }
        }

        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { context ->
                    org.webrtc.SurfaceViewRenderer(context).apply {
                        init(rtcManager.eglBase.eglBaseContext, object : org.webrtc.RendererCommon.RendererEvents {
                            override fun onFirstFrameRendered() {
                                isVideoPlaying = true
                            }
                            override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {}
                        })
                        setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        setEnableHardwareScaler(true)
                        
                        rtcManager.onVideoTrack = { track ->
                            track.addSink(this)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            if (!isVideoPlaying && !camera.videoPreview.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(camera.videoPreview)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Превью камеры",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            if (!audioPermissionState) {
                Text(
                    text = "Микрофон отключен (нет прав)",
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
            }
        }
    }
}
