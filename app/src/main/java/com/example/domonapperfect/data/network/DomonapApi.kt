package com.example.domonapperfect.data.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Response

// Data transfer objects for Auth
@kotlinx.serialization.Serializable
data class PhoneNumberDto(val countryCode: Int, val number: Long)

@kotlinx.serialization.Serializable
data class AuthorizeBody(
    val phoneNumber: PhoneNumberDto
)

@kotlinx.serialization.Serializable
data class ConfirmAuthorizationBody(
    val phoneNumber: PhoneNumberDto,
    val confirmCode: String,
    val deviceToken: String? = null
)

@kotlinx.serialization.Serializable
data class AuthResponse(val token: String, val refreshToken: String)

@kotlinx.serialization.Serializable
data class KeyResponse(
    val id: String,
    val name: String,
    val httpVideoUrl: String? = null,
    val webrtcVideoUrl: String? = null,
    val videoPreview: String? = null,
    val doorId: String
)

@kotlinx.serialization.Serializable
data class OpenRelayRequest(val keyId: String)

@kotlinx.serialization.Serializable
data class OpenRelayDoorRequest(val doorId: String)

@kotlinx.serialization.Serializable
data class DoorKeysRequest(
    val currentPage: Int = 1,
    val perPage: Int = 100,
    val keysType: String = "Active",
    val search: String? = null
)

@kotlinx.serialization.Serializable
data class NotifyCallEndedRequest(val callId: String)

interface DomonapApi {
    @POST("sso-api/Authorization/Authorize")
    suspend fun authorize(@Body request: AuthorizeBody): retrofit2.Response<okhttp3.ResponseBody>

    @POST("sso-api/Authorization/ConfirmAuthorization")
    suspend fun confirmAuthorization(@Body body: ConfirmAuthorizationBody): retrofit2.Response<okhttp3.ResponseBody>

    // Client API
    @POST("client-api/Device/OpenRelayByKeyId")
    suspend fun openRelayByKeyId(@Body request: OpenRelayRequest): Boolean

    @POST("client-api/Device/OpenRelayByDoorId")
    suspend fun openRelayByDoorId(@Body request: OpenRelayDoorRequest): Boolean

    @POST("client-api/CallLog/GetCallLogs")
    suspend fun getCallLogs(@Body request: CallLogRequest): Response<PagedResult<CallLogDto>>

    @POST("client-api/Key/GetPagedKeysByKeysType")
    suspend fun getKeys(@Body request: DoorKeysRequest): retrofit2.Response<KeyResponseList>

    @POST("communication-api/Call/NotifyCallEnded")
    suspend fun notifyCallEnded(@Body request: NotifyCallEndedRequest): retrofit2.Response<okhttp3.ResponseBody>
}

@kotlinx.serialization.Serializable
data class KeyResponseList(
    val results: List<KeyResponse> = emptyList()
)
@kotlinx.serialization.Serializable
data class ConfirmAuthorizationResponse(
    val token: String? = null
)
