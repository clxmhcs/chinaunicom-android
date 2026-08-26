package com.clxmhcs.chinaunicom.data.comprehensive

import android.content.Context
import com.clxmhcs.chinaunicom.data.integral.AndroidIntegralDiskCache
import com.clxmhcs.chinaunicom.data.integral.IntegralDiskCache
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class ComprehensiveBusinessStoreState(
    val pointsByAccountID: Map<UUID, Int> = emptyMap(),
)

interface ComprehensiveBusinessStore {
    val state: StateFlow<ComprehensiveBusinessStoreState>
    suspend fun loadCachedPoints(accountIDs: Collection<UUID>)
    fun points(accountID: UUID): Int?
}

/**
 * Source-equivalent Android counterpart of iOS ComprehensiveBusinessStore.
 *
 * This store is deliberately cache-only. Entering the comprehensive root must not create a new
 * carrier refresh authority or trigger ordered-business, phone-bill, quota, balance or integral
 * network requests. It only projects already persisted integral snapshots into per-account points.
 */
class DefaultComprehensiveBusinessStore(
    private val integralCache: IntegralDiskCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ComprehensiveBusinessStore {
    private val _state = MutableStateFlow(ComprehensiveBusinessStoreState())
    override val state: StateFlow<ComprehensiveBusinessStoreState> = _state.asStateFlow()

    private val requestSequence = AtomicLong(0L)

    override suspend fun loadCachedPoints(accountIDs: Collection<UUID>) {
        val requestID = requestSequence.incrementAndGet()
        val snapshots = withContext(ioDispatcher) { integralCache.snapshots(accountIDs) }
        if (requestSequence.get() != requestID || !currentCoroutineContext().isActive) return
        _state.value = ComprehensiveBusinessStoreState(
            pointsByAccountID = snapshots.mapValues { (_, snapshot) -> snapshot.totalAvailable },
        )
    }

    override fun points(accountID: UUID): Int? = _state.value.pointsByAccountID[accountID]
}

object AndroidComprehensiveBusinessStores {
    fun create(context: Context): ComprehensiveBusinessStore = DefaultComprehensiveBusinessStore(
        integralCache = AndroidIntegralDiskCache(context.applicationContext),
    )
}
