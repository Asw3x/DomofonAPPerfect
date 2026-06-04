package com.example.domonapperfect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallData(val doorId: String, val callId: String?)

object CallManager {
    private val _incomingCall = MutableStateFlow<CallData?>(null)
    val incomingCall = _incomingCall.asStateFlow()

    fun setIncomingCall(doorId: String, callId: String? = null) {
        _incomingCall.value = CallData(doorId, callId)
    }

    fun acceptCall() {
        _incomingCall.value = null
    }

    fun rejectCall() {
        _incomingCall.value = null
    }
}
