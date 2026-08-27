package com.clxmhcs.chinaunicom.data.videoring

import com.clxmhcs.chinaunicom.core.login.VideoRingRequestLifecycle
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberState
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class VideoRingEntryMode { EVERY_ENTRY, REFRESH_WHEN_EXPIRED, MANUAL_ONLY }

data class VideoRingStoreRefreshPolicy(
    val entryMode: VideoRingEntryMode = VideoRingEntryMode.EVERY_ENTRY,
    val cacheValidityMinutes: Int = 60,
)

data class VideoRingStoreState(
    val accountID: UUID? = null,
    val memberState: VideoRingMemberState? = null,
    val loading: Boolean = false,
    val lastRefreshTime: Instant? = null,
    val restoredFromCache: Boolean = false,
    val errorMessage: String? = null,
)

interface VideoRingStore {
    val state: StateFlow<VideoRingStoreState>
    suspend fun load(account: UnicomAccount)
    suspend fun refresh(account: UnicomAccount)
    fun clear()
}

/** Source-equivalent store for the active iOS VideoRingInlineMemberService. */
class DefaultVideoRingStore(
    private val lifecycle: VideoRingRequestLifecycle,
    private val cache: VideoRingDiskCache,
    private val policyProvider: () -> VideoRingStoreRefreshPolicy = { VideoRingStoreRefreshPolicy() },
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
        val expected = normalize(account.mobile)

        val cached = withContext(Dispatchers.IO) { cache.load(account.id) }
            ?.takeIf { it.memberState.phoneNumber == expected }
        _state.value = VideoRingStoreState(
            accountID = account.id,
            memberState = cached?.memberState,
            loading = false,
            lastRefreshTime = cached?.fetchedAt,
            restoredFromCache = cached != null,
        )

        val policy = policyProvider().normalized()
        val shouldRefresh = when (policy.entryMode) {
            VideoRingEntryMode.EVERY_ENTRY -> true
            VideoRingEntryMode.MANUAL_ONLY -> false
            VideoRingEntryMode.REFRESH_WHEN_EXPIRED -> {
                if (cached == null) true
                else {
                    val elapsed = Duration.between(cached.fetchedAt, now())
                    elapsed.isNegative || elapsed.toMinutes() >= policy.cacheValidityMinutes
                }
            }
        }

        if (!shouldRefresh) {
            if (cached == null && policy.entryMode == VideoRingEntryMode.MANUAL_ONLY && isCurrent(account.id, request)) {
                _state.value = _state.value.copy(
                    errorMessage = "当前设置为仅手动刷新，暂无本地视频彩铃缓存。点击刷新即可联网查询。",
                )
            }
            return
        }
        perform(account, request, preserveCache = cached != null)
    }

    override suspend fun refresh(account: UnicomAccount) {
        val request = UUID.randomUUID()
        generation = request
        activeAccountID = account.id
        val preserve = _state.value.accountID == account.id && _state.value.memberState != null
        if (!preserve) {
            _state.value = VideoRingStoreState(accountID = account.id, loading = true)
        } else {
            _state.value = _state.value.copy(loading = true, errorMessage = null)
        }
        perform(account, request, preserveCache = preserve)
    }

    private suspend fun perform(account: UnicomAccount, request: UUID, preserveCache: Boolean) {
        if (!account.isEnabled) {
            publishError(account.id, request, "当前号码已停用", preserveCache)
            return
        }
        val expected = normalize(account.mobile)
        if (expected.length != 11 || !expected.startsWith("1")) {
            publishError(account.id, request, "当前号码格式不正确", preserveCache)
            return
        }
        if (!lifecycle.hasCredentials(account.id)) {
            publishError(account.id, request, "当前号码缺少可用凭据", preserveCache)
            return
        }

        if (isCurrent(account.id, request)) {
            _state.value = _state.value.copy(loading = true, errorMessage = null)
        }
        try {
            val result = withContext(Dispatchers.IO) {
                lifecycle.fetchValidated(account.id, account.mobile)
            }
            if (!isCurrent(account.id, request)) return
            if (result.state.phoneNumber != expected) {
                publishError(account.id, request, "返回的会员数据与当前选择号码不一致", preserveCache)
                return
            }
            val fetchedAt = now()
            withContext(Dispatchers.IO) {
                runCatching { cache.save(account.id, VideoRingCacheRecord(result.state, fetchedAt)) }
            }
            if (!isCurrent(account.id, request)) return
            _state.value = VideoRingStoreState(
                accountID = account.id,
                memberState = result.state,
                loading = false,
                lastRefreshTime = fetchedAt,
                restoredFromCache = false,
                errorMessage = null,
            )
        } catch (error: Exception) {
            if (!isCurrent(account.id, request)) return
            publishError(account.id, request, error.message ?: error::class.java.simpleName, preserveCache)
        }
    }

    private fun publishError(accountID: UUID, request: UUID, message: String, preserveCache: Boolean) {
        if (!isCurrent(accountID, request)) return
        _state.value = if (preserveCache) {
            _state.value.copy(loading = false, errorMessage = message)
        } else {
            VideoRingStoreState(accountID = accountID, loading = false, errorMessage = message)
        }
    }

    private fun VideoRingStoreRefreshPolicy.normalized() =
        copy(cacheValidityMinutes = cacheValidityMinutes.coerceAtLeast(1))

    private fun isCurrent(accountID: UUID, request: UUID): Boolean =
        activeAccountID == accountID && generation == request

    private fun normalize(value: String): String = value.filter(Char::isDigit)
}
