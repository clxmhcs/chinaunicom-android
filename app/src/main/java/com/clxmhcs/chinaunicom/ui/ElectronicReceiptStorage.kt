package com.clxmhcs.chinaunicom.ui

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal class ElectronicReceiptStorage(context: Context) {
    private val root = File(context.filesDir, "electronic_receipts").apply { mkdirs() }
    private val indexFile = AtomicFile(File(root, "index.json"))

    @Synchronized
    fun load(): List<SavedElectronicReceipt> {
        val file = indexFile.baseFile
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val id = row.optString("id").trim()
                val accountID = runCatching { UUID.fromString(row.optString("accountID")) }.getOrNull() ?: continue
                val fileName = row.optString("fileName").trim()
                if (id.isEmpty() || fileName.isEmpty()) continue
                val pdf = File(root, fileName)
                if (!pdf.exists() || pdf.length() < 4L) continue
                add(
                    SavedElectronicReceipt(
                        id = id,
                        accountID = accountID,
                        maskedNumber = row.optString("maskedNumber"),
                        orderID = row.optString("orderID"),
                        acceptDate = row.optString("acceptDate"),
                        queryMonth = row.optString("queryMonth"),
                        fileName = fileName,
                        savedAtEpochMillis = row.optLong("savedAtEpochMillis", pdf.lastModified()),
                        exportedDocumentUri = row.optString("exportedDocumentUri").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }.sortedByDescending { it.savedAtEpochMillis }
    }

    @Synchronized
    fun save(
        target: ElectronicReceiptTarget,
        candidate: ElectronicReceiptPdfCandidate,
        pdfBytes: ByteArray,
    ): SavedElectronicReceipt {
        require(pdfBytes.size >= 4 && pdfBytes.copyOfRange(0, 4).contentEquals("%PDF".toByteArray())) {
            "联通服务器没有返回 PDF 文件"
        }
        val stableID = UUID.nameUUIDFromBytes("${target.id}|${candidate.orderID}|${candidate.acceptDate}".toByteArray()).toString()
        val fileName = "receipt_${stableID}.pdf"
        val atomicPdf = AtomicFile(File(root, fileName))
        var stream: FileOutputStream? = null
        try {
            stream = atomicPdf.startWrite()
            stream.write(pdfBytes)
            stream.fd.sync()
            atomicPdf.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let { runCatching { atomicPdf.failWrite(it) } }
            throw error
        }

        val previous = load().filterNot { it.id == stableID }
        val record = SavedElectronicReceipt(
            id = stableID,
            accountID = target.id,
            maskedNumber = target.maskedNumber,
            orderID = candidate.orderID,
            acceptDate = candidate.acceptDate,
            queryMonth = candidate.queryMonth,
            fileName = fileName,
            savedAtEpochMillis = System.currentTimeMillis(),
        )
        writeIndex(listOf(record) + previous)
        return record
    }

    @Synchronized
    fun markExported(id: String, documentUri: String): SavedElectronicReceipt? {
        val current = load()
        val updated = current.map { if (it.id == id) it.copy(exportedDocumentUri = documentUri) else it }
        writeIndex(updated)
        return updated.firstOrNull { it.id == id }
    }

    @Synchronized
    fun delete(id: String) {
        val current = load()
        current.firstOrNull { it.id == id }?.let { File(root, it.fileName).delete() }
        writeIndex(current.filterNot { it.id == id })
    }

    fun fileFor(item: SavedElectronicReceipt): File = File(root, item.fileName)

    private fun writeIndex(items: List<SavedElectronicReceipt>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("accountID", item.accountID.toString())
                    .put("maskedNumber", item.maskedNumber)
                    .put("orderID", item.orderID)
                    .put("acceptDate", item.acceptDate)
                    .put("queryMonth", item.queryMonth)
                    .put("fileName", item.fileName)
                    .put("savedAtEpochMillis", item.savedAtEpochMillis)
                    .put("exportedDocumentUri", item.exportedDocumentUri ?: JSONObject.NULL),
            )
        }
        var stream: FileOutputStream? = null
        try {
            stream = indexFile.startWrite()
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            indexFile.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let { runCatching { indexFile.failWrite(it) } }
            throw error
        }
    }
}
