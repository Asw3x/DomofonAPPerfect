package com.example.domonapperfect.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domonapperfect.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthState())
    val uiState: StateFlow<AuthState> = _uiState.asStateFlow()

    init {
        if (authRepository.isAuthorized()) {
            _uiState.value = _uiState.value.copy(isLoggedIn = true)
        }
    }

    fun onPhoneChanged(phone: String) {
        // Ensure +7 prefix isn't completely deleted easily
        val newPhone = if (!phone.startsWith("+7") && phone.isNotEmpty()) "+7$phone" else phone
        _uiState.value = _uiState.value.copy(phone = newPhone)
    }

    private fun parsePhone(phone: String): Pair<Int, Long>? {
        val digits = phone.filter { it.isDigit() }
        if (digits.length == 11 && (digits.startsWith("7") || digits.startsWith("8"))) {
            return Pair(7, digits.substring(1).toLong())
        }
        if (digits.length == 10) {
            return Pair(7, digits.toLong())
        }
        return null
    }

    fun onCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(code = code)
    }

    fun requestCode() {
        val phone = _uiState.value.phone
        val parsed = parsePhone(phone)
        if (parsed == null) {
            _uiState.value = _uiState.value.copy(error = "Invalid phone format. Need 10 digits.")
            return
        }
        val (countryCode, number) = parsed
        
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            val result = authRepository.requestCode(countryCode, number)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, codeSent = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to send code"
                )
            }
        }
    }

    fun login() {
        val phone = _uiState.value.phone
        val code = _uiState.value.code
        
        val parsed = parsePhone(phone)
        if (parsed == null || code.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Invalid phone or empty code")
            return
        }
        val (countryCode, number) = parsed

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val result = authRepository.confirmAuthorization(countryCode, number, code)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "An error occurred"
                )
            }
        }
    }
    
    fun logout() {
        authRepository.logout()
        _uiState.value = AuthState()
    }

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authRepository) as T
        }
    }
}

data class AuthState(
    val phone: String = "+7",
    val code: String = "",
    val isLoading: Boolean = false,
    val codeSent: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)
