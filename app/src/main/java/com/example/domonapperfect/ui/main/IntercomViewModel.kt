package com.example.domonapperfect.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domonapperfect.data.network.KeyResponse
import com.example.domonapperfect.data.repository.IntercomRepository
import com.example.domonapperfect.data.network.CallLogDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.example.domonapperfect.data.repository.AuthRepository
import com.example.domonapperfect.data.model.CustomFolder
import com.example.domonapperfect.data.model.DoorCustomization
import java.util.UUID

class IntercomViewModel(
    private val repository: IntercomRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        IntercomState(
            keys = repository.getCachedKeys(),
            folders = repository.getCustomFolders(),
            customizations = repository.getDoorCustomizations()
        )
    )
    val uiState: StateFlow<IntercomState> = _uiState.asStateFlow()

    private val _callHistory = MutableStateFlow<List<CallLogDto>?>(null)
    val callHistory: StateFlow<List<CallLogDto>?> = _callHistory.asStateFlow()

    val token: String?
        get() = authRepository.token

    private val _autoOpenEnabled = MutableStateFlow(authRepository.isAutoOpenEnabled())
    val autoOpenEnabled: StateFlow<Boolean> = _autoOpenEnabled.asStateFlow()

    fun setAutoOpen(enabled: Boolean) {
        authRepository.setAutoOpenEnabled(enabled)
        _autoOpenEnabled.value = enabled
    }

    private val _isCallNotificationOnly = MutableStateFlow(authRepository.isCallNotificationOnly())
    val isCallNotificationOnly: StateFlow<Boolean> = _isCallNotificationOnly.asStateFlow()

    fun setCallNotificationOnly(enabled: Boolean) {
        authRepository.setCallNotificationOnly(enabled)
        _isCallNotificationOnly.value = enabled
    }

    private val _isOpenButtonOnLeft = MutableStateFlow(authRepository.isOpenButtonOnLeft())
    val isOpenButtonOnLeft: StateFlow<Boolean> = _isOpenButtonOnLeft.asStateFlow()

    fun setOpenButtonOnLeft(enabled: Boolean) {
        authRepository.setOpenButtonOnLeft(enabled)
        _isOpenButtonOnLeft.value = enabled
    }

    private val _isRingtoneEnabled = MutableStateFlow(authRepository.isRingtoneEnabled())
    val isRingtoneEnabled: StateFlow<Boolean> = _isRingtoneEnabled.asStateFlow()

    fun setRingtoneEnabled(enabled: Boolean) {
        authRepository.setRingtoneEnabled(enabled)
        _isRingtoneEnabled.value = enabled
    }

    fun loadKeys() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = repository.getKeys()
            result.onSuccess { keys ->
                val folders = repository.getCustomFolders()
                val customizations = repository.getDoorCustomizations()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    keys = keys,
                    folders = folders,
                    customizations = customizations
                )
            }.onFailure { e ->
                val tk = authRepository.token
                val tkPrefix = if (tk.isNullOrBlank()) "null/empty" else tk.take(5) + "..."
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "${e.message} (token: $tkPrefix)"
                )
            }
        }
    }

    fun loadCallHistory() {
        viewModelScope.launch {
            val result = repository.getCallHistory(1, 30)
            result.onSuccess { logs ->
                _callHistory.value = logs
            }.onFailure {
                if (_callHistory.value == null) {
                    _callHistory.value = emptyList()
                }
            }
        }
    }

    fun createFolder(name: String) {
        val newFolder = CustomFolder(id = UUID.randomUUID().toString(), name = name)
        val updatedFolders = _uiState.value.folders + newFolder
        repository.saveCustomFolders(updatedFolders)
        _uiState.value = _uiState.value.copy(folders = updatedFolders)
    }

    fun renameFolder(folderId: String, newName: String) {
        val updatedFolders = _uiState.value.folders.map { if (it.id == folderId) it.copy(name = newName) else it }
        repository.saveCustomFolders(updatedFolders)
        _uiState.value = _uiState.value.copy(folders = updatedFolders)
    }

    fun deleteFolder(folderId: String) {
        val updatedFolders = _uiState.value.folders.filter { it.id != folderId }
        repository.saveCustomFolders(updatedFolders)
        
        val updatedCustomizations = _uiState.value.customizations.mapValues { entry ->
            if (entry.value.folderId == folderId) entry.value.copy(folderId = null) else entry.value
        }
        repository.saveDoorCustomizations(updatedCustomizations)
        
        _uiState.value = _uiState.value.copy(folders = updatedFolders, customizations = updatedCustomizations)
    }

    fun updateDoorCustomization(doorId: String, customName: String?, folderId: String?) {
        val current = _uiState.value.customizations[doorId] ?: DoorCustomization(doorId = doorId)
        val updated = current.copy(customName = customName, folderId = folderId)
        val newMap = _uiState.value.customizations + (doorId to updated)
        repository.saveDoorCustomizations(newMap)
        _uiState.value = _uiState.value.copy(customizations = newMap)
    }

    private fun swapOrder(doorId: String, isUp: Boolean) {
        val currentFolderId = _uiState.value.customizations[doorId]?.folderId
        
        val folderKeys = _uiState.value.keys.filter {
            _uiState.value.customizations[it.id]?.folderId == currentFolderId
        }.sortedBy { _uiState.value.customizations[it.id]?.orderIndex ?: 0 }
        
        val currentIndex = folderKeys.indexOfFirst { it.id == doorId }
        if (currentIndex == -1) return
        
        val targetIndex = if (isUp) currentIndex - 1 else currentIndex + 1
        if (targetIndex < 0 || targetIndex >= folderKeys.size) return
        
        val targetDoorId = folderKeys[targetIndex].id
        
        val newCustomizations = _uiState.value.customizations.toMutableMap()
        folderKeys.forEachIndexed { index, keyResponse ->
            val cust = newCustomizations[keyResponse.id] ?: DoorCustomization(doorId = keyResponse.id)
            newCustomizations[keyResponse.id] = cust.copy(orderIndex = index)
        }
        
        val currentCust = newCustomizations[doorId]!!
        val targetCust = newCustomizations[targetDoorId]!!
        
        newCustomizations[doorId] = currentCust.copy(orderIndex = targetIndex)
        newCustomizations[targetDoorId] = targetCust.copy(orderIndex = currentIndex)
        
        repository.saveDoorCustomizations(newCustomizations)
        _uiState.value = _uiState.value.copy(customizations = newCustomizations)
    }

    fun moveDoorUp(doorId: String) {
        swapOrder(doorId, true)
    }

    fun moveDoorDown(doorId: String) {
        swapOrder(doorId, false)
    }

    fun openDoor(keyId: String) {
        if (_uiState.value.openingKeys.contains(keyId)) return
        _uiState.value = _uiState.value.copy(openingKeys = _uiState.value.openingKeys + keyId)
        
        com.example.domonapperfect.CallManager.markDoorOpened(keyId)
        
        viewModelScope.launch {
            val result = repository.openRelay(keyId)
            val errorMsg = result.exceptionOrNull()?.message
            val actionMessage = if (result.isSuccess) {
                "Дверь успешно открыта!"
            } else if (errorMsg?.contains("429") == true) {
                "Слишком частые запросы. Подождите пару секунд."
            } else {
                "Ошибка открытия двери: $errorMsg"
            }
            _uiState.value = _uiState.value.copy(
                actionMessage = actionMessage,
                openingKeys = _uiState.value.openingKeys - keyId
            )
        }
    }

    fun clearActionMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }

    private val _isDoNotDisturbEnabled = MutableStateFlow(authRepository.isDoNotDisturbEnabled())
    val isDoNotDisturbEnabled: StateFlow<Boolean> = _isDoNotDisturbEnabled.asStateFlow()

    fun setDoNotDisturbEnabled(enabled: Boolean) {
        authRepository.setDoNotDisturbEnabled(enabled)
        _isDoNotDisturbEnabled.value = enabled
    }

    private val _callWindowDelaySeconds = MutableStateFlow(authRepository.getCallWindowDelaySeconds())
    val callWindowDelaySeconds: StateFlow<Int> = _callWindowDelaySeconds.asStateFlow()

    fun setCallWindowDelaySeconds(seconds: Int) {
        authRepository.setCallWindowDelaySeconds(seconds)
        _callWindowDelaySeconds.value = seconds
    }

    class Factory(
        private val intercomRepository: IntercomRepository,
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(IntercomViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return IntercomViewModel(intercomRepository, authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class IntercomState(
    val keys: List<KeyResponse> = emptyList(),
    val folders: List<CustomFolder> = emptyList(),
    val customizations: Map<String, DoorCustomization> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null,
    val openingKeys: Set<String> = emptySet()
)
