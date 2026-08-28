package com.clxmhcs.chinaunicom.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.WidgetDualSide
import com.clxmhcs.chinaunicom.data.settings.SharedPreferencesWidgetConfigurationStorage
import com.clxmhcs.chinaunicom.widget.AndroidWidgetSnapshotStore
import com.clxmhcs.chinaunicom.widget.WidgetRefreshActionContract
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * App-side endpoint for a user tap on a desktop Widget refresh control.
 *
 * This receiver never talks to China Unicom directly. It resolves the configured account and calls
 * the process-wide [UnicomRepository] authority, which keeps quota/session, shared-balance and
 * Widget Snapshot side effects on the same production path as the main App.
 */
class WidgetManualRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = WidgetManualRefreshRequest.fromAction(intent.action) ?: return
        val pendingResult = goAsync()
        if (!refreshGate.tryLock()) {
            pendingResult.finish()
            return
        }

        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { refresh(appContext, request) }
            } finally {
                refreshGate.unlock()
                pendingResult.finish()
            }
        }
    }

    private suspend fun refresh(context: Context, request: WidgetManualRefreshRequest) {
        val repository = UnicomRepositoryProvider.create(context)
        repository.reloadAccountsFromPersistence()

        val storage = SharedPreferencesWidgetConfigurationStorage(context)
        val accounts = repository.appState.value.accounts.sortedBy(UnicomAccount::sortOrder)
        val target = when (request) {
            WidgetManualRefreshRequest.SINGLE -> {
                val configuration = storage.loadSingle()
                val snapshotAccountID = AndroidWidgetSnapshotStore(context).loadSingle()?.accountID
                resolveSingleAccountID(
                    selectedAccountID = configuration.selectedAccountID,
                    snapshotAccountID = snapshotAccountID,
                    accounts = accounts,
                )?.let { WidgetRefreshTarget(it, includeBalance = configuration.showsBalance) }
            }
            WidgetManualRefreshRequest.DUAL_LEFT -> storage.loadDual().accountID(WidgetDualSide.LEFT)
                ?.let { WidgetRefreshTarget(it, includeBalance = true) }
            WidgetManualRefreshRequest.DUAL_RIGHT -> storage.loadDual().accountID(WidgetDualSide.RIGHT)
                ?.let { WidgetRefreshTarget(it, includeBalance = true) }
        } ?: return

        if (accounts.none { it.id == target.accountID }) return
        repository.refreshWidgetAccount(target.accountID, target.includeBalance)
    }

    companion object {
        private val refreshGate = Mutex()

        internal fun resolveSingleAccountID(
            selectedAccountID: UUID?,
            snapshotAccountID: UUID?,
            accounts: List<UnicomAccount>,
        ): UUID? {
            val validIDs = accounts.map(UnicomAccount::id).toSet()
            selectedAccountID?.takeIf(validIDs::contains)?.let { return it }
            snapshotAccountID?.takeIf(validIDs::contains)?.let { return it }
            return accounts.sortedBy(UnicomAccount::sortOrder).firstOrNull(UnicomAccount::isEnabled)?.id
                ?: accounts.minByOrNull(UnicomAccount::sortOrder)?.id
        }
    }
}

private data class WidgetRefreshTarget(
    val accountID: UUID,
    val includeBalance: Boolean,
)

private enum class WidgetManualRefreshRequest {
    SINGLE,
    DUAL_LEFT,
    DUAL_RIGHT;

    companion object {
        fun fromAction(action: String?): WidgetManualRefreshRequest? = when (action) {
            WidgetRefreshActionContract.ACTION_REFRESH_SINGLE -> SINGLE
            WidgetRefreshActionContract.ACTION_REFRESH_DUAL_LEFT -> DUAL_LEFT
            WidgetRefreshActionContract.ACTION_REFRESH_DUAL_RIGHT -> DUAL_RIGHT
            else -> null
        }
    }
}
