package com.clxmhcs.chinaunicom.core.storage

import android.content.Context
import android.util.AtomicFile
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

interface AccountMetadataStore {
    fun loadAccounts(): List<UnicomAccount>
    fun saveAccounts(accounts: List<UnicomAccount>)
    fun clear()
}

/**
 * Android counterpart of iOS PersistenceStore.accounts.json.
 *
 * The file is app-private, excluded from backup by the app manifest, and written through
 * AtomicFile so interrupted writes can recover the previous complete generation.
 */
class AndroidFileAccountMetadataStore(
    context: Context,
    private val codec: AccountMetadataJsonCodec = AccountMetadataJsonCodec(),
    relativePath: String = DEFAULT_RELATIVE_PATH,
) : AccountMetadataStore {
    private val atomicFile: AtomicFile

    init {
        val file = File(context.applicationContext.filesDir, relativePath)
        file.parentFile?.mkdirs()
        atomicFile = AtomicFile(file)
    }

    override fun loadAccounts(): List<UnicomAccount> = try {
        atomicFile.openRead().use { stream ->
            codec.decode(stream.readBytes())
        }
    } catch (_: FileNotFoundException) {
        emptyList()
    } catch (_: Exception) {
        // Matches iOS PersistenceStore.loadAccounts(): malformed/unreadable metadata restores as empty.
        emptyList()
    }

    override fun saveAccounts(accounts: List<UnicomAccount>) {
        val payload = codec.encode(accounts.sortedBy { it.sortOrder })
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(payload)
            stream.flush()
            stream.fd.sync()
            atomicFile.finishWrite(stream)
            stream = null
        } catch (error: Exception) {
            stream?.let(atomicFile::failWrite)
            throw AccountMetadataStorageException("accounts.json write failed", error)
        } finally {
            payload.fill(0)
        }
    }

    override fun clear() {
        atomicFile.delete()
    }

    companion object {
        const val DEFAULT_RELATIVE_PATH = "persistence/accounts.json"
    }
}

class AccountMetadataStorageException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object AndroidAccountMetadataStores {
    fun accounts(context: Context): AccountMetadataStore = AndroidFileAccountMetadataStore(context)
}
