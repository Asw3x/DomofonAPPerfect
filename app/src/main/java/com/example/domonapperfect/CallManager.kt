package com.example.domonapperfect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CallData(val doorId: String, val callId: String?, val sipAccount: String? = null)

object CallManager {
    private val _incomingCall = MutableStateFlow<CallData?>(null)
    val incomingCall = _incomingCall.asStateFlow()

    private var timerJob: kotlinx.coroutines.Job? = null

    private var lastOpenedDoorId: String? = null
    private var lastOpenedTime: Long = 0

    fun markDoorOpened(doorId: String) {
        lastOpenedDoorId = doorId
        lastOpenedTime = System.currentTimeMillis()
    }

    fun isRecentlyOpened(doorId: String): Boolean {
        return doorId == lastOpenedDoorId && System.currentTimeMillis() - lastOpenedTime < 15000
    }

    fun setIncomingCall(doorId: String, callId: String? = null, sipAccount: String? = null) {
        if (isRecentlyOpened(doorId)) return

        timerJob?.cancel()
        _incomingCall.value = CallData(doorId, callId, sipAccount)
        
        timerJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            kotlinx.coroutines.delay(60_000) // 60 seconds timeout
            if (_incomingCall.value?.callId == callId) {
                _incomingCall.value = null
            }
        }
    }

    fun acceptCall() {
        timerJob?.cancel()
        _incomingCall.value = null
    }

    fun rejectCall() {
        timerJob?.cancel()
        _incomingCall.value = null
    }
}
