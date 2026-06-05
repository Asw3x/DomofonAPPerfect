package com.example.domonapperfect.data.network

import kotlinx.serialization.Serializable

@Serializable
data class CallLogRequest(
    val currentPage: Int = 1,
    val perPage: Int = 20,
    val missedCalls: Boolean = false
)

@Serializable
data class PagedResult<T>(
    val results: List<T>,
    val currentPage: Int? = null,
    val pageCount: Int? = null,
    val pageSize: Int? = null,
    val rowCount: Int? = null
)

@Serializable
data class DomofonDoorAddressDto(
    val address: String? = null,
    val id: String? = null
)

@Serializable
data class CallLogDto(
    val address: String? = null,
    val answerer: String? = null,
    val answererAvatar: String? = null,
    val callId: String? = null,
    val callStatus: String? = null,
    val doorId: String? = null,
    val endTime: String? = null,
    val photoUrl: String? = null,
    val propertyId: String? = null,
    val startTime: String? = null,
    val videoPreview: String? = null,
    val webrtcVideoUrl: String? = null,
    val doorAddresses: List<DomofonDoorAddressDto>? = null
)
