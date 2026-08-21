package com.clxmhcs.chinaunicom.core.security

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import java.util.UUID

/**
 * Stores only server session credentials. Account profiles, passwords and SMS codes are deliberately
 * outside this M5-A boundary.
 */
interface CredentialVault {
    fun save(accountId: UUID, credentials: AccountCredentials)

    fun read(accountId: UUID): AccountCredentials?

    fun delete(accountId: UUID)

    fun deleteAll()
}

/** A safe-to-display failure; its message never contains credential material or an account identifier. */
class CredentialVaultException internal constructor(cause: Throwable? = null) :
    Exception("Credential vault is unavailable or its data is invalid.", cause)
