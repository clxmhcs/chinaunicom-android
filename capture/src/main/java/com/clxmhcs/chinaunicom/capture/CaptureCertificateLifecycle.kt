package com.clxmhcs.chinaunicom.capture

import android.content.Context
import java.io.File

/**
 * M14-D certificate lifecycle mirrors the current iOS CaptureTool architecture without pretending
 * that a real CA/signing engine already exists. Root certificate bytes are process-local; only an
 * explicitly requested installable copy is written to the app cache for manual Android 11+ install.
 */
object CaptureCertificateManager {
    private const val ROOT_CERTIFICATE_KEY = "__capture_root_ca__"
    private const val CERTIFICATE_DIRECTORY = "CaptureToolCertificates"
    private const val CERTIFICATE_FILE_NAME = "CaptureTool-RootCA.cer"

    private var state: CaptureCertificateState = CaptureCertificateState.MISSING

    @Synchronized
    fun snapshot(): CaptureCertificateSnapshot = CaptureCertificateSnapshot(
        state = state,
        hasRootCertificate = rootCertificateData() != null,
        userConfirmedTrusted = isTrustConfirmed(),
    )

    @Synchronized
    fun rootCertificateData(): ByteArray? = CaptureCertificateStore.certificate(ROOT_CERTIFICATE_KEY)

    @Synchronized
    fun generateRootCertificate(): ByteArray {
        val data = CaptureMitmCertificateGenerator.certificate(ROOT_CERTIFICATE_KEY)
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("根证书生成器尚未返回有效证书")
        registerRootCertificate(data)
        return data.copyOf()
    }

    @Synchronized
    fun registerRootCertificate(data: ByteArray) {
        require(data.isNotEmpty()) { "根证书数据不能为空" }
        CaptureCertificateStore.save(data, ROOT_CERTIFICATE_KEY)
        CaptureCertificateAuthority.userConfirmedTrusted = false
        state = CaptureCertificateState.GENERATED
    }

    @Synchronized
    fun makeInstallableCertificateFile(context: Context): File {
        val data = rootCertificateData()
            ?: throw IllegalStateException("尚未生成或导入抓包根证书")
        val directory = File(context.applicationContext.cacheDir, CERTIFICATE_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "无法创建证书导出目录" }
        val output = File(directory, CERTIFICATE_FILE_NAME)
        output.writeBytes(data)
        state = CaptureCertificateState.INSTALLATION_READY
        return output
    }

    /**
     * Android 11+ does not let an ordinary app silently install a CA certificate. The user must
     * install the exported certificate in system Settings and then explicitly confirm that action.
     * This flag records that user confirmation; it is not a privileged trust-store probe.
     */
    @Synchronized
    fun confirmTrustEnabledByUser() {
        if (rootCertificateData() == null) return
        CaptureCertificateAuthority.userConfirmedTrusted = true
        state = CaptureCertificateState.USER_CONFIRMED_TRUSTED
    }

    @Synchronized
    fun revokeUserConfirmation() {
        CaptureCertificateAuthority.userConfirmedTrusted = false
        refreshState()
    }

    @Synchronized
    fun isTrustConfirmed(): Boolean =
        rootCertificateData() != null && CaptureCertificateAuthority.checkInstallation()

    @Synchronized
    fun isReadyForInterception(): Boolean =
        isTrustConfirmed() &&
            CaptureTlsCapabilities.HOST_CERTIFICATE_GENERATION_AVAILABLE &&
            CaptureTlsCapabilities.ACTIVE_TLS_DECRYPTION_AVAILABLE

    @Synchronized
    fun reset() {
        CaptureCertificateAuthority.userConfirmedTrusted = false
        CaptureCertificateStore.removeAll()
        state = CaptureCertificateState.MISSING
    }

    @Synchronized
    fun reset(context: Context) {
        reset()
        File(context.applicationContext.cacheDir, CERTIFICATE_DIRECTORY).deleteRecursively()
    }

    @Synchronized
    fun refreshState() {
        state = when {
            rootCertificateData() == null -> CaptureCertificateState.MISSING
            CaptureCertificateAuthority.checkInstallation() -> CaptureCertificateState.USER_CONFIRMED_TRUSTED
            else -> CaptureCertificateState.GENERATED
        }
    }

    fun installationInstructions(): List<String> = CaptureCertificateInstaller.installationInstructions()
}

internal object CaptureCertificateAuthority {
    var userConfirmedTrusted: Boolean = false

    fun checkInstallation(): Boolean = userConfirmedTrusted
}

internal object CaptureCertificateStore {
    private val certificates = LinkedHashMap<String, ByteArray>()

    @Synchronized
    fun save(data: ByteArray, host: String) {
        certificates[host] = data.copyOf()
    }

    @Synchronized
    fun certificate(host: String): ByteArray? = certificates[host]?.copyOf()

    @Synchronized
    fun removeAll() {
        certificates.clear()
    }
}

/** Current source-parity seam: no fake certificate material is generated. */
internal object CaptureMitmCertificateGenerator {
    fun certificate(host: String): ByteArray? {
        if (host.isEmpty()) return null
        return null
    }
}

object CaptureCertificateInstaller {
    fun installationInstructions(): List<String> = listOf(
        "生成或导入抓包根证书",
        "导出 CaptureTool-RootCA.cer 证书文件",
        "Android 11 及以上由用户在系统设置中手动安装 CA 证书",
        "安装后返回 App，由用户明确确认已完成证书信任配置",
        "部分 App 默认不信任用户 CA 或启用证书固定，因此不保证可被 HTTPS 解密",
    )
}
