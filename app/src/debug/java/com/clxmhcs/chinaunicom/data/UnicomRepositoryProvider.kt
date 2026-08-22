package com.clxmhcs.chinaunicom.data

import android.content.Context

/** Debug wiring only; fake fixtures remain isolated from release/main production wiring. */
object UnicomRepositoryProvider {
    fun create(context: Context): UnicomRepository {
        context.applicationContext // Keep variant API identical to release without retaining the Activity.
        return FakeUnicomRepository()
    }
}
