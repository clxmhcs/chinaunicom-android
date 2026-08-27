package com.clxmhcs.chinaunicom.core.model

data class VideoRingMember(
    val id: String,
    val name: String,
    val memberType: String,
    val isMember: Boolean,
    val startTime: String? = null,
    val endTime: String? = null,
)

data class VideoRingBenefit(
    val id: String,
    val name: String,
    val imageURL: String? = null,
    val price: String? = null,
    val received: Boolean? = null,
)

data class VideoRingMemberState(
    val phoneNumber: String,
    val members: List<VideoRingMember>,
    val benefits: List<VideoRingBenefit>,
    val isEnabled: Boolean = false,
)

data class VideoRingMemberFetchResult(
    val state: VideoRingMemberState,
    val updatedCredentials: AccountCredentials? = null,
)
