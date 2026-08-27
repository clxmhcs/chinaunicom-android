package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.login.UnicomVideoRingCredentialValidator
import com.clxmhcs.chinaunicom.core.login.VideoRingAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.CredentialStoreProvider
import com.clxmhcs.chinaunicom.data.videoring.DefaultVideoRingStore
import com.clxmhcs.chinaunicom.data.videoring.VideoRingStore
import kotlinx.coroutines.launch

class VideoRingViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialStore = CredentialStoreProvider.create(application.applicationContext)

    private val store: VideoRingStore = DefaultVideoRingStore(
        lifecycle = VideoRingAccountCredentialLifecycle(
            validator = UnicomVideoRingCredentialValidator(),
            credentialStore = credentialStore,
        ),
    )

    val state = store.state

    fun load(account: UnicomAccount) {
        viewModelScope.launch { store.load(account) }
    }

    fun refresh(account: UnicomAccount) {
        viewModelScope.launch { store.refresh(account) }
    }

    fun clear() = store.clear()
}
