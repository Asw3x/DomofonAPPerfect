package com.example.domonapperfect.ui.main

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory

class SipManager private constructor(context: Context) {

    private var core: Core? = null
    var currentCall: Call? = null
        private set

    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive.asStateFlow()

    init {
        try {
            val factory = Factory.instance()
            factory.setDebugMode(true, "LinphoneSip")
            core = factory.createCore(null, null, context)
            
            core?.addListener(object : CoreListenerStub() {
                override fun onCallStateChanged(
                    c: Core,
                    call: Call,
                    state: Call.State,
                    message: String
                ) {
                    Log.d("SipManager", "Call state changed: $state ($message)")
                    
                    // Show Toasts on the Main Thread for debugging
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "SIP: $state - $message", android.widget.Toast.LENGTH_SHORT).show()
                    }

                    when (state) {
                        Call.State.IncomingReceived -> {
                            // Store the incoming call
                            if (currentCall == null) {
                                currentCall = call
                            }
                        }
                        Call.State.End, Call.State.Error, Call.State.Released -> {
                            if (currentCall == call) {
                                currentCall = null
                                _isCallActive.value = false
                            }
                        }
                        Call.State.Connected, Call.State.StreamsRunning -> {
                            _isCallActive.value = true
                        }
                        else -> {}
                    }
                }
            })
            core?.start()
            Log.d("SipManager", "Linphone Core started successfully.")
        } catch (e: Exception) {
            Log.e("SipManager", "Failed to initialize Linphone Core", e)
        }
    }

    fun register(username: String, password: String = "TODO_CHANGE_ME") {
        val coreInstance = core ?: return
        try {
            try {
                coreInstance.clearAllAuthInfo()
                coreInstance.clearAccounts()
            } catch (e: Exception) {
                // Ignore if methods don't exist
            }
            
            // Authorization: Digest realm="asterisk"
            val authInfo = Factory.instance().createAuthInfo(username, null, password, null, "asterisk", "89.207.217.62")
            coreInstance.addAuthInfo(authInfo)

            val accountParams = coreInstance.createAccountParams()
            val identity = Factory.instance().createAddress("sip:$username@89.207.217.62")
            val server = Factory.instance().createAddress("sip:89.207.217.62;transport=tcp")
            
            if (accountParams != null) {
                accountParams.identityAddress = identity
                accountParams.serverAddress = server
                // registerEnabled is usually true by default or has a different property name
                val account = coreInstance.createAccount(accountParams)
                if (account != null) {
                    coreInstance.addAccount(account)
                    coreInstance.defaultAccount = account
                }
            }
            
            Log.d("SipManager", "Registered SIP account $username")
        } catch (e: Exception) {
            Log.e("SipManager", "Failed to register", e)
        }
    }

    fun acceptCall() {
        try {
            currentCall?.accept()
        } catch (e: Exception) {
            Log.e("SipManager", "Failed to accept call", e)
        }
    }

    fun endCall() {
        try {
            currentCall?.terminate()
        } catch (e: Exception) {
            Log.e("SipManager", "Failed to end call", e)
        }
    }

    fun setMicrophoneMuted(muted: Boolean) {
        try {
            currentCall?.microphoneMuted = muted
        } catch (e: Exception) {
            Log.e("SipManager", "Failed to mute/unmute", e)
        }
    }

    fun release() {
        try {
            core?.stop()
            core = null
        } catch (e: Exception) {
            Log.e("SipManager", "Error releasing SipManager", e)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SipManager? = null

        fun getInstance(context: Context): SipManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SipManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
