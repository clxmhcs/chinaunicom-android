package com.clxmhcs.chinaunicom.data

/** Debug wiring only. */
object UnicomRepositoryProvider {
    fun create(): UnicomRepository = FakeUnicomRepository()
}
