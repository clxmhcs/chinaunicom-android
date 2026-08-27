package com.clxmhcs.chinaunicom.data.videoring

import com.clxmhcs.chinaunicom.core.login.VideoRingRequestLifecycle
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberState
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class VideoRingStoreState(
    val accountID: UUID? = null,
    val memberState: VideoRingMemberState? = null,
    val loading: Boolean = false,
    val lastRefreshTime: Instant? = null,
    val errorMessage: String? = null,
)

interface VideoRingStore {
    val state: StateFlow<VideoRingStoreState>
    suspend fun load(account: UnicomAccount)
    suspend fun refresh(account: UnicomAccount)
    fun clear()
}

/**
 * Source-equivalent M9-H store. The iOS feature reloads on entry/pull-to-refresh and has no
 * VideoRing-specific disk cache or refresh-policy settings authority, so Android keeps this state in memory only.
 */
class DefaultVideoRingStore(
    private val lifecycle: VideoRingRequestLifecycle,
    private val now: () -> Instant = Instant::now,
) : VideoRingStore {
    private val _state = MutableStateFlow(VideoRingStoreState())
    override val state: StateFlow<VideoRingStoreState> = _state.asStateFlow()

    private var activeAccountID: UUID? = null
    private var generation: UUID = UUID.randomUUID()

    override fun clear() {
        generation = UUID.randomUUID()
        activeAccountID = null
        _state.value = VideoRingStoreState()
    }

    override suspend fun load(account: UnicomAccount) {
        val request = UUID.randomUUID()
        generation = request
        activeAccountID = account.id
        _state.value = VideoRingStoreState(accountID = account.id, loading = true)
        perform(account, request)
    }

    override suspend fun refresh(account: UnicomAccount) {
        if (activeAccountID != account.id) {
            load(account)
            return
        }
        val request = UUID.randomUUID()
        generation = request
        _state.value = _state.value.copy(loading = true, errorMessage = null)
        perform(account, request)
    }

    private suspend fun perform(account: UnicomAccount, request: UUID) {
        if (!account.isEnabled) {
            if (isCurrent(account.id, request)) {
                _state.value = _state.value.copy(loading = false, errorMessage = "当前号码已停用")
            }
            return
        }
        if (!lifecycle.hasCredentials(account.id)) {
            if (isCurrent(account.id, request)) {
                _state.value = _state.value.copy(loading = false, errorMessage = "当前号码缺少可用凭据")
            }
            return
        }
        try {
            val result = withContext(Dispatchers.IO) {
                lifecycle.fetchValidated(account.id, account.mobile)
            }
            if (!isCurrent(account.id, request)) return
            val expected = normalize(account.mobile)
            if (result.state.phoneNumber != expected) {
                _state.value = _state.value.copy(
                    loading = false,
                    errorMessage = "返回的会员数据与当前选择号码不一致",
                )
                return
            }
            _state.value = VideoRingStoreState(
                accountID = account.id,
                memberState = result.state,
                loading = false,
                lastRefreshTime = now(),
                errorMessage = null,
            )
        } catch (error: Exception) {
            if (!isCurrent(account.id, request)) return
            _state.value = _state.value.copy(
                loading = false,
                errorMessage = error.message ?: error::class.java.simpleName,
            )
        }
    }

    private fun isCurrent(accountID: UUID, request: UUID): Boolean =
        activeAccountID == accountID && generation == request

    private fun normalize(value: String): String = value.filter(Char::isDigit)
}
