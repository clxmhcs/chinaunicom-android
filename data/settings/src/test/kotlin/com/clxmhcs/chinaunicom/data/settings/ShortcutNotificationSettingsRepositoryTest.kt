package com.clxmhcs.chinaunicom.data.settings

import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationProfile
import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationSlot
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutNotificationSettingsRepositoryTest {
    @Test
    fun assigningSameSlotMovesPreviousAccountBackToNone() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val storage = InMemoryShortcutStorage()
        val repository = DefaultShortcutNotificationSettingsRepository(storage)

        assertTrue(repository.save(ShortcutNotificationProfile(accountID = first, slot = ShortcutNotificationSlot.A)))
        assertTrue(repository.save(ShortcutNotificationProfile(accountID = second, slot = ShortcutNotificationSlot.A)))

        val values = storage.values
        assertEquals(ShortcutNotificationSlot.NONE, values[first]?.slot)
        assertEquals(ShortcutNotificationSlot.A, values[second]?.slot)
    }

    @Test
    fun reconcileRemovesProfilesForDeletedAccounts() {
        val kept = UUID.randomUUID()
        val deleted = UUID.randomUUID()
        val storage = InMemoryShortcutStorage(
            mutableMapOf(
                kept to ShortcutNotificationProfile(accountID = kept, slot = ShortcutNotificationSlot.B),
                deleted to ShortcutNotificationProfile(accountID = deleted, slot = ShortcutNotificationSlot.C),
            ),
        )
        val repository = DefaultShortcutNotificationSettingsRepository(storage)

        repository.reconcileAccounts(setOf(kept))

        assertTrue(storage.values.containsKey(kept))
        assertFalse(storage.values.containsKey(deleted))
    }

    private class InMemoryShortcutStorage(
        initial: MutableMap<UUID, ShortcutNotificationProfile> = linkedMapOf(),
    ) : ShortcutNotificationSettingsStorage {
        var values: MutableMap<UUID, ShortcutNotificationProfile> = initial

        override fun load(): Map<UUID, ShortcutNotificationProfile> = values.toMap()

        override fun save(value: Map<UUID, ShortcutNotificationProfile>): Boolean {
            values = value.toMutableMap()
            return true
        }
    }
}
