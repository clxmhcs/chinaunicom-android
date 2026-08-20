package com.clxmhcs.chinaunicom.core.model

import java.time.Instant

data class OrderedBusinessSnapshot(
    val title: String?,
    val queryTime: String?,
    val fetchedAt: Instant,
    val sections: List<OrderedBusinessSection>,
) {
    val totalCount: Int get() = sections.sumOf { it.items.size }
}

data class OrderedBusinessSection(
    val id: String,
    val title: String,
    val icon: String,
    val items: List<OrderedBusinessItem>,
)

data class OrderedBusinessItem(
    val id: String,
    val name: String,
    val subtitle: String?,
    val fee: String?,
    val startDate: String?,
    val endDate: String?,
)

data class OrderedBusinessFetchResult(
    val snapshot: OrderedBusinessSnapshot,
    val updatedCredentials: AccountCredentials?,
)

data class ActivatedOrderedSession(
    val cookie: String,
    val appID: String,
    val tokenOnline: String,
)
