package com.example.domonapperfect.ui.main

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.TimeUnit

class WebRtcManager(
    private val context: Context,
    private val token: String,
    private val videoUrl: String
) {
    val eglBase: EglBase = EglBase.create()
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    var onVideoTrack: ((VideoTrack) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()
            
        audioDeviceModule.release()

        startConnection()
    }

    private fun startConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        )
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                Log.d("WebRtcManager", "ICE State: $p0")
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(p0: IceCandidate?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    Log.d("WebRtcManager", "Received VideoTrack")
                    onVideoTrack?.invoke(track)
                }
            }
        })

        // Require receiving audio/video
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))

        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(offer: SessionDescription?) {
                if (offer != null) {
                    Log.d("WebRtcManager", "Offer created")
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d("WebRtcManager", "Local desc set")
                            sendOfferToWhep(offer)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, offer)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    private fun sendOfferToWhep(offer: SessionDescription) {
        scope.launch {
            try {
                var baseUrl = videoUrl
                if (!baseUrl.endsWith("/")) baseUrl += "/"
                baseUrl += "whep"

                Log.d("WebRtcManager", "Sending POST to $baseUrl")
                val request = Request.Builder()
                    .url(baseUrl)
                    .post(offer.description.toRequestBody("application/sdp".toMediaType()))
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("WebRtcManager", "WHEP Error: ${response.code} ${response.message}")
                        return@launch
                    }
                    val answerSdp = response.body?.string()
                    if (!answerSdp.isNullOrEmpty()) {
                        Log.d("WebRtcManager", "Received Answer SDP")
                        val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                        peerConnection?.setRemoteDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                Log.d("WebRtcManager", "Remote desc set successfully")
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {}
                        }, answer)
                    }
                }
            } catch (e: Exception) {
                Log.e("WebRtcManager", "WHEP Exception: ${e.message}")
            }
        }
    }

    fun release() {
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnectionFactory.dispose()
        eglBase.release()
    }
}
