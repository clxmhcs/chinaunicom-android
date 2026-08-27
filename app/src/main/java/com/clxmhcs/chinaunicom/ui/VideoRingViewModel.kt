package com.clxmhcs.chinaunicom.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.login.UnicomVideoRingCredentialValidator
import com.clxmhcs.chinaunicom.core.login.VideoRingAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.network.UnicomVideoRingClient
import com.clxmhcs.chinaunicom.data.CredentialStoreProvider
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import com.clxmhcs.chinaunicom.data.settings.PageEntryRefreshMode
import com.clxmhcs.chinaunicom.data.videoring.AndroidVideoRingDiskCache
import com.clxmhcs.chinaunicom.data.videoring.DefaultVideoRingStore
import com.clxmhcs.chinaunicom.data.videoring.VideoRingEntryMode
import com.clxmhcs.chinaunicom.data.videoring.VideoRingStore
import com.clxmhcs.chinaunicom.data.videoring.VideoRingStoreRefreshPolicy
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

class VideoRingViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val credentialStore = CredentialStoreProvider.create(appContext)
    private val settings = AndroidSettingsRepositories.refreshLogic(appContext)
    private val clientUID = getOrCreateVideoRingClientUID(appContext)

    private val store: VideoRingStore = DefaultVideoRingStore(
        lifecycle = VideoRingAccountCredentialLifecycle(
            validator = UnicomVideoRingCredentialValidator(
                client = UnicomVideoRingClient(clientUID),
            ),
            credentialStore = credentialStore,
        ),
        cache = AndroidVideoRingDiskCache(appContext),
        policyProvider = {
            val policy = settings.loadVideoRingRefreshPolicy()
            VideoRingStoreRefreshPolicy(
                entryMode = when (policy.entryMode) {
                    PageEntryRefreshMode.EVERY_ENTRY -> VideoRingEntryMode.EVERY_ENTRY
                    PageEntryRefreshMode.REFRESH_WHEN_EXPIRED -> VideoRingEntryMode.REFRESH_WHEN_EXPIRED
                    PageEntryRefreshMode.MANUAL_ONLY -> VideoRingEntryMode.MANUAL_ONLY
                },
                cacheValidityMinutes = policy.cacheValidityMinutes,
            )
        },
    )

    val state = store.state

    fun load(account: UnicomAccount) {
        viewModelScope.launch { store.load(account) }
    }

    fun refresh(account: UnicomAccount) {
        viewModelScope.launch { store.refresh(account) }
    }

    fun clear() = store.clear()

    companion object {
        private const val VIDEO_RING_PREFS = "chinaunicom.video.ring.session.v1"
        private const val CLIENT_UID_KEY = "client_uid_10155"

        private fun getOrCreateVideoRingClientUID(context: Context): String {
            val preferences = context.getSharedPreferences(VIDEO_RING_PREFS, Context.MODE_PRIVATE)
            val existing = preferences.getString(CLIENT_UID_KEY, null)?.trim()?.lowercase(Locale.ROOT)
            if (existing != null && existing.length == 36 && existing.all { it.isLetterOrDigit() }) {
                return existing
            }
            val first = UUID.randomUUID().toString().replace("-", "").lowercase(Locale.ROOT)
            val second = UUID.randomUUID().toString().replace("-", "").lowercase(Locale.ROOT)
            val created = first + second.take(4)
            preferences.edit().putString(CLIENT_UID_KEY, created).commit()
            return created
        }
    }
}
