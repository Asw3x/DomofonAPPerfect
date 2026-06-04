package com.example.domonapperfect.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CustomFolder(
    val id: String,
    val name: String,
    val orderIndex: Int = 0
)

@Serializable
data class DoorCustomization(
    val doorId: String,
    val customName: String? = null,
    val folderId: String? = null,
    val orderIndex: Int = 0
)
