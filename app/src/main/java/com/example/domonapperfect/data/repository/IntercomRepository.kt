package com.example.domonapperfect.data.repository

import com.example.domonapperfect.data.network.DomonapApi
import com.example.domonapperfect.data.network.DoorKeysRequest
import com.example.domonapperfect.data.network.KeyResponse
import com.example.domonapperfect.data.network.NotifyCallEndedRequest
import com.example.domonapperfect.data.network.OpenRelayRequest

import android.content.SharedPreferences
import com.example.domonapperfect.data.model.CustomFolder
import com.example.domonapperfect.data.model.DoorCustomization
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class IntercomRepository(
    private val api: DomonapApi,
    private val prefs: SharedPreferences
) {
    private val foldersKey = "custom_folders"
    private val customizationsKey = "door_customizations"
    suspend fun getKeys(): Result<List<KeyResponse>> {
        return try {
            val response = api.getKeys(DoorKeysRequest())
            if (response.isSuccessful) {
                Result.success(response.body()?.results ?: emptyList())
            } else {
                val err = response.errorBody()?.string()
                Result.failure(Exception("HTTP ${response.code()}: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun openRelay(keyId: String): Result<Boolean> {
        return try {
            val success = api.openRelayByKeyId(OpenRelayRequest(keyId = keyId))
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun notifyCallEnded(callId: String): Result<Boolean> {
        return try {
            val response = api.notifyCallEnded(NotifyCallEndedRequest(callId = callId))
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCustomFolders(): List<CustomFolder> {
        val json = prefs.getString(foldersKey, null) ?: return emptyList()
        return try {
            Json.decodeFromString(ListSerializer(CustomFolder.serializer()), json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomFolders(folders: List<CustomFolder>) {
        val json = Json.encodeToString(ListSerializer(CustomFolder.serializer()), folders)
        prefs.edit().putString(foldersKey, json).apply()
    }

    fun getDoorCustomizations(): Map<String, DoorCustomization> {
        val json = prefs.getString(customizationsKey, null) ?: return emptyMap()
        return try {
            Json.decodeFromString(MapSerializer(String.serializer(), DoorCustomization.serializer()), json)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveDoorCustomizations(customizations: Map<String, DoorCustomization>) {
        val json = Json.encodeToString(MapSerializer(String.serializer(), DoorCustomization.serializer()), customizations)
        prefs.edit().putString(customizationsKey, json).apply()
    }
}
