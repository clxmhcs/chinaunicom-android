package com.clxmhcs.chinaunicom.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.WidgetDualSide
import com.clxmhcs.chinaunicom.data.settings.SharedPreferencesWidgetConfigurationStorage
import com.clxmhcs.chinaunicom.widget.AndroidWidgetSnapshotStore
import com.clxmhcs.chinaunicom.widget.WidgetRefreshActionContract
import java.util.UUID

/**
 * Lightweight App-side endpoint for desktop Widget refresh taps.
 *
 * Broadcast delivery only validates the action and enqueues durable work. Carrier networking never
 * runs inside the receiver lifecycle and remains owned by [UnicomRepository].
 */
class WidgetManualRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = WidgetManualRefreshRequest.fromAction(intent.action) ?: return
        val work = OneTimeWorkRequestBuilder<WidgetManualRefreshWorker>()
            .setInputData(workDataOf(WidgetManualRefreshWorker.ACTION_KEY to request.action))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            request.uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            work,
        )
    }
}

/** One-shot user-initiated Widget refresh routed through the existing production repository. */
class WidgetManualRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val request = WidgetManualRefreshRequest.fromAction(inputData.getString(ACTION_KEY))
            ?: return Result.failure()
        return runCatching {
            refresh(request)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.failure() },
        )
    }

    private suspend fun refresh(request: WidgetManualRefreshRequest) {
        val repository = UnicomRepositoryProvider.create(applicationContext)
        repository.reloadAccountsFromPersistence()

        val storage = SharedPreferencesWidgetConfigurationStorage(applicationContext)
        val accounts = repository.appState.value.accounts.sortedBy(UnicomAccount::sortOrder)
        val target = when (request) {
            WidgetManualRefreshRequest.SINGLE -> {
                val configuration = storage.loadSingle()
                val snapshotAccountID = AndroidWidgetSnapshotStore(applicationContext).loadSingle()?.accountID
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
        internal const val ACTION_KEY = "widget_refresh_action"

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

private enum class WidgetManualRefreshRequest(
    val action: String,
    val uniqueWorkName: String,
) {
    SINGLE(
        WidgetRefreshActionContract.ACTION_REFRESH_SINGLE,
        "chinaunicom-widget-manual-refresh-single",
    ),
    DUAL_LEFT(
        WidgetRefreshActionContract.ACTION_REFRESH_DUAL_LEFT,
        "chinaunicom-widget-manual-refresh-dual-left",
    ),
    DUAL_RIGHT(
        WidgetRefreshActionContract.ACTION_REFRESH_DUAL_RIGHT,
        "chinaunicom-widget-manual-refresh-dual-right",
    );

    companion object {
        fun fromAction(action: String?): WidgetManualRefreshRequest? = entries.firstOrNull { it.action == action }
    }
}
