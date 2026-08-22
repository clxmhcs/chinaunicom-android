package com.clxmhcs.chinaunicom.core.security

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

interface CredentialStore {
    fun save(accountID: UUID, credentials: AccountCredentials)
    fun read(accountID: UUID): AccountCredentials?
    fun delete(accountID: UUID)
    fun deleteAll()
}

interface CredentialCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedCredentialPayload
    fun decrypt(payload: EncryptedCredentialPayload, associatedData: ByteArray): ByteArray
}

interface CredentialBlobStorage {
    fun write(accountID: UUID, encryptedBlob: ByteArray)
    fun read(accountID: UUID): ByteArray?
    fun delete(accountID: UUID)
    fun deleteAll()
}

data class EncryptedCredentialPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

sealed class CredentialStorageException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Corrupted(cause: Throwable? = null) : CredentialStorageException("credentialStorageCorrupted", cause)
    class Crypto(cause: Throwable? = null) : CredentialStorageException("credentialCryptoFailed", cause)
}

class SecureAccountCredentialStore(
    private val cipher: CredentialCipher,
    private val storage: CredentialBlobStorage,
) : CredentialStore {

    override fun save(accountID: UUID, credentials: AccountCredentials) {
        val plaintext = AccountCredentialsCodec.encode(credentials)
        try {
            val payload = cipher.encrypt(plaintext, associatedData(accountID))
            storage.write(accountID, EncryptedCredentialEnvelopeCodec.encode(payload))
        } finally {
            plaintext.fill(0)
        }
    }

    override fun read(accountID: UUID): AccountCredentials? {
        val stored = storage.read(accountID) ?: return null
        val payload = try {
            EncryptedCredentialEnvelopeCodec.decode(stored)
        } catch (error: Exception) {
            throw CredentialStorageException.Corrupted(error)
        }
        val plaintext = try {
            cipher.decrypt(payload, associatedData(accountID))
        } catch (error: CredentialStorageException) {
            throw error
        } catch (error: Exception) {
            throw CredentialStorageException.Crypto(error)
        }
        return try {
            AccountCredentialsCodec.decode(plaintext)
        } catch (error: Exception) {
            throw CredentialStorageException.Corrupted(error)
        } finally {
            plaintext.fill(0)
        }
    }

    override fun delete(accountID: UUID) {
        storage.delete(accountID)
    }

    override fun deleteAll() {
        storage.deleteAll()
    }

    private fun associatedData(accountID: UUID): ByteArray =
        "chinaunicom.account.credentials.v1:$accountID".toByteArray(StandardCharsets.UTF_8)
}

internal object AccountCredentialsCodec {
    private const val VERSION = 1
    private const val MAX_FIELD_BYTES = 1024 * 1024

    fun encode(credentials: AccountCredentials): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeInt(VERSION)
            writeString(stream, credentials.cookie)
            writeNullableString(stream, credentials.appID)
            writeNullableString(stream, credentials.tokenOnline)
        }
        return output.toByteArray()
    }

    fun decode(data: ByteArray): AccountCredentials {
        DataInputStream(ByteArrayInputStream(data)).use { stream ->
            val version = stream.readInt()
            require(version == VERSION) { "Unsupported credential payload version: $version" }
            val credentials = AccountCredentials(
                cookie = readString(stream),
                appID = readNullableString(stream),
                tokenOnline = readNullableString(stream),
            )
            require(stream.available() == 0) { "Trailing credential payload bytes" }
            return credentials
        }
    }

    private fun writeString(stream: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_FIELD_BYTES) { "Credential field is too large" }
        stream.writeInt(bytes.size)
        stream.write(bytes)
    }

    private fun writeNullableString(stream: DataOutputStream, value: String?) {
        if (value == null) {
            stream.writeInt(-1)
            return
        }
        writeString(stream, value)
    }

    private fun readString(stream: DataInputStream): String {
        val length = stream.readInt()
        require(length in 0..MAX_FIELD_BYTES) { "Invalid credential field length" }
        val bytes = ByteArray(length)
        stream.readFully(bytes)
        return bytes.toString(StandardCharsets.UTF_8)
    }

    private fun readNullableString(stream: DataInputStream): String? {
        val length = stream.readInt()
        if (length == -1) return null
        require(length in 0..MAX_FIELD_BYTES) { "Invalid credential field length" }
        val bytes = ByteArray(length)
        stream.readFully(bytes)
        return bytes.toString(StandardCharsets.UTF_8)
    }
}

internal object EncryptedCredentialEnvelopeCodec {
    private const val VERSION = 1
    private const val MAX_IV_BYTES = 64
    private const val MAX_CIPHERTEXT_BYTES = 2 * 1024 * 1024

    fun encode(payload: EncryptedCredentialPayload): ByteArray {
        require(payload.iv.isNotEmpty() && payload.iv.size <= MAX_IV_BYTES)
        require(payload.ciphertext.isNotEmpty() && payload.ciphertext.size <= MAX_CIPHERTEXT_BYTES)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeInt(VERSION)
            stream.writeInt(payload.iv.size)
            stream.write(payload.iv)
            stream.writeInt(payload.ciphertext.size)
            stream.write(payload.ciphertext)
        }
        return output.toByteArray()
    }

    fun decode(data: ByteArray): EncryptedCredentialPayload {
        DataInputStream(ByteArrayInputStream(data)).use { stream ->
            val version = stream.readInt()
            require(version == VERSION) { "Unsupported encrypted credential version: $version" }
            val ivLength = stream.readInt()
            require(ivLength in 1..MAX_IV_BYTES) { "Invalid credential IV length" }
            val iv = ByteArray(ivLength)
            stream.readFully(iv)
            val ciphertextLength = stream.readInt()
            require(ciphertextLength in 1..MAX_CIPHERTEXT_BYTES) { "Invalid credential ciphertext length" }
            val ciphertext = ByteArray(ciphertextLength)
            stream.readFully(ciphertext)
            require(stream.available() == 0) { "Trailing encrypted credential bytes" }
            return EncryptedCredentialPayload(iv, ciphertext)
        }
    }
}
