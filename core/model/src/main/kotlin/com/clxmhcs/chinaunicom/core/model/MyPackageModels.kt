package com.clxmhcs.chinaunicom.core.model

import java.net.URI

enum class MyPackageResourceTab(val rawValue: String, val title: String) {
    MOBILE("mobile", "移网"),
    BROADBAND("broadband", "宽带"),
}

data class MyPackageActivity(
    val id: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val remainingDays: String,
)

data class MyPackageChargeRule(
    val id: String,
    val title: String,
    val value: String,
)

data class MyPackageBroadbandResource(
    val id: String,
    val mobile: String,
    val packageSpeed: String,
    val actualSpeed: String,
    val startDate: String,
    val endDate: String,
)

data class MyPackageMember(
    val id: String,
    val role: String,
    val serviceType: String,
    val maskedNumber: String,
    val userName: String,
    val isPrimary: Boolean,
)

data class MyPackageMemberGroup(
    val id: String,
    val name: String,
    val groupType: String,
    val primaryMembers: List<MyPackageMember>,
    val members: List<MyPackageMember>,
)

data class MyPackageSnapshot(
    val productName: String,
    val productStartDate: String,
    val packageResourceType: String,
    val monthFee: String,
    val packageDescription: String,
    val businessRules: String,
    val monthFeeDescription: String,
    val contractTips: String,
    val cannotCancelPrompt: String,
    val promotionURL: URI?,
    val promotionImageURL: URI?,
    val promotionText: String,
    val activities: List<MyPackageActivity>,
    val mobileRules: List<MyPackageChargeRule>,
    val broadbandResources: List<MyPackageBroadbandResource>,
    val broadbandTips: String,
    val memberGroups: List<MyPackageMemberGroup>,
    val isPrettyNumber: Boolean,
) {
    val displayedMonthFee: String get() = monthFee.trim().ifEmpty { "--" }
}

data class MyPackageFetchResult(
    val snapshot: MyPackageSnapshot,
    val updatedCredentials: AccountCredentials?,
)
