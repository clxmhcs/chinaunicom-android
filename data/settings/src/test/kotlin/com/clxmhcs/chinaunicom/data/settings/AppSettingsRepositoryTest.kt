package com.clxmhcs.chinaunicom.data.settings

import com.clxmhcs.chinaunicom.core.model.AppSettings
import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsRepositoryTest {
    @Test
    fun missingStorageUsesIosParityDefaults() {
        val repository = DefaultAppSettingsRepository(FakeStorage())
        assertEquals(AppSettings(), repository.settings.value)
    }

    @Test
    fun malformedFieldsFallBackIndependently() {
        val storage = FakeStorage(
            """{"schemaVersion":99,"hideMobileMiddleDigits":"bad","hideBroadbandMiddleDigits":true,"displayUnit":"GB"}""",
        )
        val settings = DefaultAppSettingsRepository(storage).settings.value
        assertTrue(settings.hideMobileMiddleDigits)
        assertTrue(settings.hideBroadbandMiddleDigits)
        assertEquals(DisplayUnit.AUTOMATIC, settings.displayUnit)
    }

    @Test
    fun savePublishesAndRoundTrips() {
        val storage = FakeStorage()
        val repository = DefaultAppSettingsRepository(storage)
        val expected = AppSettings(
            autoRefreshOnLaunch = false,
            hideMobileMiddleDigits = false,
            hideBroadbandMiddleDigits = true,
            displayUnit = DisplayUnit.GIGABYTES,
        )
        assertTrue(repository.save(expected))
        assertEquals(expected, repository.settings.value)
        assertEquals(expected, DefaultAppSettingsRepository(storage).settings.value)
    }

    @Test
    fun failedWriteDoesNotPublish() {
        val storage = FakeStorage(writeSucceeds = false)
        val repository = DefaultAppSettingsRepository(storage)
        val before = repository.settings.value
        assertFalse(repository.save(before.copy(hideMobileMiddleDigits = !before.hideMobileMiddleDigits)))
        assertEquals(before, repository.settings.value)
    }

    private class FakeStorage(
        private var raw: String? = null,
        private val writeSucceeds: Boolean = true,
    ) : AppSettingsStorage {
        override fun read(): String? = raw
        override fun write(value: String): Boolean {
            if (!writeSucceeds) return false
            raw = value
            return true
        }
    }
}
