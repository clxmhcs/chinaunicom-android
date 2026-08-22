package com.clxmhcs.chinaunicom.core.security

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SecureAccountCredentialStoreTest {

    @Test
    fun roundTripPreservesCredentialsWithoutPlaintextBlob() {
        val storage = RecordingBlobStorage()
        val store = SecureAccountCredentialStore(JvmAesGcmCipher(), storage)
        val accountID = UUID.randomUUID()
        val credentials = AccountCredentials(
            cookie = "M5_PLAINTEXT_SENTINEL_COOKIE=secret-value",
            appID = "0123456789abcdef",
            tokenOnline = "M5_PLAINTEXT_SENTINEL_TOKEN",
        )

        store.save(accountID, credentials)

        assertEquals(credentials, store.read(accountID))
        val blob = requireNotNull(storage.values[accountID])
        assertFalse(blob.containsSubsequence(credentials.cookie.toByteArray()))
        assertFalse(blob.containsSubsequence(credentials.tokenOnline!!.toByteArray()))
    }

    @Test
    fun multipleAccountsRemainIsolatedAndOverwriteOnlyTarget() {
        val storage = RecordingBlobStorage()
        val store = SecureAccountCredentialStore(JvmAesGcmCipher(), storage)
        val firstID = UUID.randomUUID()
        val secondID = UUID.randomUUID()
        val first = AccountCredentials("first=1", "app-first", "token-first")
        val second = AccountCredentials("second=2", "app-second", "token-second")
        val renewedFirst = AccountCredentials("first=renewed", "app-first", "token-renewed")

        store.save(firstID, first)
        store.save(secondID, second)
        store.save(firstID, renewedFirst)

        assertEquals(renewedFirst, store.read(firstID))
        assertEquals(second, store.read(secondID))
    }

    @Test
    fun nullableAppIdAndTokenRoundTripExactly() {
        val store = SecureAccountCredentialStore(JvmAesGcmCipher(), RecordingBlobStorage())
        val accountID = UUID.randomUUID()
        val credentials = AccountCredentials("cookie=only", null, null)

        store.save(accountID, credentials)

        assertEquals(credentials, store.read(accountID))
    }

    @Test
    fun accountIdIsAuthenticatedAndBlobCannotBeSwapped() {
        val storage = RecordingBlobStorage()
        val store = SecureAccountCredentialStore(JvmAesGcmCipher(), storage)
        val sourceID = UUID.randomUUID()
        val destinationID = UUID.randomUUID()
        store.save(sourceID, AccountCredentials("cookie=source", "app", "token"))
        storage.values[destinationID] = requireNotNull(storage.values[sourceID]).copyOf()

        assertThrows(CredentialStorageException.Crypto::class.java) {
            store.read(destinationID)
        }
    }

    @Test
    fun corruptEnvelopeFailsClosedInsteadOfReturningPartialCredentials() {
        val storage = RecordingBlobStorage()
        val store = SecureAccountCredentialStore(JvmAesGcmCipher(), storage)
        val accountID = UUID.randomUUID()
        storage.values[accountID] = byteArrayOf(0, 1, 2, 3)

        assertThrows(CredentialStorageException.Corrupted::class.java) {
            store.read(accountID)
        }
    }

    @Test
    fun deleteAndDeleteAllRemoveCredentialBlobs() {
        val storage = RecordingBlobStorage()
        val store = SecureAccountCredentialStore(JvmAesGcmCipher(), storage)
        val firstID = UUID.randomUUID()
        val secondID = UUID.randomUUID()
        store.save(firstID, AccountCredentials("a=1", null, null))
        store.save(secondID, AccountCredentials("b=2", null, null))

        store.delete(firstID)
        assertNull(store.read(firstID))
        assertEquals("b=2", store.read(secondID)?.cookie)

        store.deleteAll()
        assertNull(store.read(secondID))
    }
}

private class RecordingBlobStorage : CredentialBlobStorage {
    val values = mutableMapOf<UUID, ByteArray>()

    override fun write(accountID: UUID, encryptedBlob: ByteArray) {
        values[accountID] = encryptedBlob.copyOf()
    }

    override fun read(accountID: UUID): ByteArray? = values[accountID]?.copyOf()

    override fun delete(accountID: UUID) {
        values.remove(accountID)
    }

    override fun deleteAll() {
        values.clear()
    }
}

private class JvmAesGcmCipher : CredentialCipher {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedCredentialPayload {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        return EncryptedCredentialPayload(cipher.iv.copyOf(), cipher.doFinal(plaintext))
    }

    override fun decrypt(payload: EncryptedCredentialPayload, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(payload.ciphertext)
    }
}

private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean {
    if (needle.isEmpty() || needle.size > size) return false
    return indices.take(size - needle.size + 1).any { start ->
        needle.indices.all { offset -> this[start + offset] == needle[offset] }
    }
}
