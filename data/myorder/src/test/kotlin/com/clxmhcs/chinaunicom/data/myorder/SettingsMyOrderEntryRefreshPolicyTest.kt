package com.clxmhcs.chinaunicom.data.myorder

import com.clxmhcs.chinaunicom.data.settings.DefaultSettingsRepository
import com.clxmhcs.chinaunicom.data.settings.OrderRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.RefreshLogicPolicyStorage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMyOrderEntryRefreshPolicyTest {
    @Test fun readsExistingSingleSettingsRepositoryOrderDomain() {
        val settings = DefaultSettingsRepository(MemoryOrderPolicyStorage())
        val policy = SettingsMyOrderEntryRefreshPolicy(settings)
        assertTrue(policy.refreshOnEntry())
        settings.saveOrderRefreshPolicy(OrderRefreshPolicy(false))
        assertFalse(policy.refreshOnEntry())
    }
}

private class MemoryOrderPolicyStorage : RefreshLogicPolicyStorage {
    private var raw: String? = null
    override fun read(): String? = raw
    override fun write(value: String): Boolean { raw = value; return true }
}
