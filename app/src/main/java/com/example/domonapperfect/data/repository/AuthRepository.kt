package com.example.domonapperfect.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.domonapperfect.data.network.AuthorizeBody
import com.example.domonapperfect.data.network.ConfirmAuthorizationBody
import com.example.domonapperfect.data.network.DomonapApi
import com.example.domonapperfect.data.network.PhoneNumberDto

class AuthRepository(
    private val api: DomonapApi,
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("domonap_prefs", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("auth_token", null)
        set(value) {
            prefs.edit().putString("auth_token", value).apply()
        }

    suspend fun requestCode(countryCode: Int, number: Long): Result<Unit> {
        return try {
            val response = api.authorize(AuthorizeBody(PhoneNumberDto(countryCode, number)))
            if (response.isSuccessful) {
                val rawBody = response.body()?.string()
                android.util.Log.d("Auth", "Authorize Success Body: $rawBody")
                Result.success(Unit)
            } else {
                val errBody = response.errorBody()?.string()
                android.util.Log.e("Auth", "Authorize Error ${response.code()}: $errBody")
                Result.failure(Exception("HTTP ${response.code()}: $errBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmAuthorization(countryCode: Int, number: Long, code: String): Result<Unit> {
        return try {
            val deviceToken = fetchFirebaseToken()
            val response = api.confirmAuthorization(ConfirmAuthorizationBody(phoneNumber = PhoneNumberDto(countryCode, number), confirmCode = code, deviceToken = deviceToken))
            if (response.isSuccessful) {
                val rawBody = response.body()?.string() ?: ""
                android.util.Log.d("Auth", "Confirm Success Body: $rawBody")
                try {
                    val jsonObject = org.json.JSONObject(rawBody)
                    val completeToken = jsonObject.optJSONObject("completeToken")
                    val accessToken = completeToken?.optString("accessToken")
                    if (!accessToken.isNullOrBlank()) {
                        token = accessToken
                        Result.success(Unit)
                    } else if (jsonObject.has("token")) {
                        token = jsonObject.optString("token")
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Token not found in response: $rawBody"))
                    }
                } catch (e: Exception) {
                    Result.failure(Exception("Failed to parse token: ${e.message}"))
                }
            } else {
                val errBody = response.errorBody()?.string()
                android.util.Log.e("Auth", "Confirm Error ${response.code()}: $errBody")
                Result.failure(Exception("HTTP ${response.code()}: $errBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun isAuthorized(): Boolean = token != null
    
    fun logout() {
        token = null
    }

    fun isAutoOpenEnabled(): Boolean = prefs.getBoolean("auto_open_enabled", false)

    fun setAutoOpenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_open_enabled", enabled).apply()
    }

    fun isCallNotificationOnly(): Boolean {
        return prefs.getBoolean("call_notification_only", false)
    }

    fun setCallNotificationOnly(enabled: Boolean) {
        prefs.edit().putBoolean("call_notification_only", enabled).apply()
    }
    
    fun isOpenButtonOnLeft(): Boolean {
        return prefs.getBoolean("open_button_on_left", false)
    }

    fun setOpenButtonOnLeft(enabled: Boolean) {
        prefs.edit().putBoolean("open_button_on_left", enabled).apply()
    }

    fun isRingtoneEnabled(): Boolean {
        return prefs.getBoolean("ringtone_enabled", true)
    }

    fun setRingtoneEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("ringtone_enabled", enabled).apply()
    }

    private suspend fun fetchFirebaseToken(): String? = kotlin.coroutines.suspendCoroutine { cont ->
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    android.util.Log.w("Auth", "Fetching FCM registration token failed", task.exception)
                    cont.resumeWith(Result.success(null))
                    return@addOnCompleteListener
                }
                cont.resumeWith(Result.success(task.result))
            }
        } catch (e: Exception) {
            cont.resumeWith(Result.success(null))
        }
    }
}
