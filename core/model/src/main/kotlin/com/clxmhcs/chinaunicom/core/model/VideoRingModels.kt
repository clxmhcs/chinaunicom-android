package com.clxmhcs.chinaunicom.core.model

data class VideoRingMember(
    val id: String,
    val name: String,
    val memberType: String,
    val isMember: Boolean,
    val startTime: String? = null,
    val endTime: String? = null,
)

data class VideoRingMemberState(
    val phoneNumber: String,
    val members: List<VideoRingMember>,
)

data class VideoRingMemberFetchResult(
    val state: VideoRingMemberState,
    val updatedCredentials: AccountCredentials? = null,
)
