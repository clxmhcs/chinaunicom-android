package com.clxmhcs.chinaunicom.data.phonebill

import android.content.Context
import android.util.AtomicFile
import com.clxmhcs.chinaunicom.core.model.BillItem
import com.clxmhcs.chinaunicom.core.model.BillItemSection
import com.clxmhcs.chinaunicom.core.model.BillMonth
import com.clxmhcs.chinaunicom.core.model.PhoneBillSnapshot
import com.clxmhcs.chinaunicom.core.model.PhoneBillSummary
import com.clxmhcs.chinaunicom.core.model.UserBill
import com.clxmhcs.chinaunicom.data.settings.PhoneBillRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.SettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val PHONE_BILL_DIRECTORY = "phone-bill"
private const val PHONE_BILL_FILE = "phone-bill-snapshots.json"

interface PhoneBillDiskCache {
    fun load(accountID: UUID): Map<String, PhoneBillSnapshot>
    fun loadAll(): Map<UUID, Map<String, PhoneBillSnapshot>>
    @Throws(IOException::class)
    fun upsert(snapshot: PhoneBillSnapshot, accountID: UUID, keepingMonthKeys: Set<String> = emptySet()): Map<String, PhoneBillSnapshot>
    @Throws(IOException::class)
    fun removeSnapshot(accountID: UUID, monthKey: String): Map<String, PhoneBillSnapshot>
    @Throws(IOException::class)
    fun pruneAccounts(keeping: Set<UUID>)
    @Throws(IOException::class)
    fun clear()
}

class AndroidPhoneBillDiskCache(
    context: Context,
    private val codec: PhoneBillSnapshotJsonCodec = PhoneBillSnapshotJsonCodec(),
) : PhoneBillDiskCache {
    private val directory = File(context.applicationContext.filesDir, PHONE_BILL_DIRECTORY)
    private val atomicFile = AtomicFile(File(directory, PHONE_BILL_FILE))
    private val lock = Any()

    override fun load(accountID: UUID): Map<String, PhoneBillSnapshot> = synchronized(lock) {
        readAll()[accountID].orEmpty()
    }

    override fun loadAll(): Map<UUID, Map<String, PhoneBillSnapshot>> = synchronized(lock) { readAll() }

    override fun upsert(
        snapshot: PhoneBillSnapshot,
        accountID: UUID,
        keepingMonthKeys: Set<String>,
    ): Map<String, PhoneBillSnapshot> = synchronized(lock) {
        val all = readAll().toMutableMap()
        var account = all[accountID].orEmpty().toMutableMap()
        account[snapshot.month.key] = snapshot
        if (keepingMonthKeys.isNotEmpty()) account = account.filterKeys(keepingMonthKeys::contains).toMutableMap()
        all[accountID] = account
        writeAll(all)
        account
    }

    override fun removeSnapshot(accountID: UUID, monthKey: String): Map<String, PhoneBillSnapshot> = synchronized(lock) {
        val all = readAll().toMutableMap()
        val account = all[accountID].orEmpty().toMutableMap()
        account.remove(monthKey)
        if (account.isEmpty()) all.remove(accountID) else all[accountID] = account
        writeAll(all)
        account
    }

    override fun pruneAccounts(keeping: Set<UUID>) = synchronized(lock) {
        val all = readAll()
        val filtered = all.filterKeys(keeping::contains)
        if (filtered.size != all.size) writeAll(filtered)
    }

    override fun clear() = synchronized(lock) { writeAll(emptyMap()) }

    private fun readAll(): Map<UUID, Map<String, PhoneBillSnapshot>> = runCatching {
        if (!atomicFile.baseFile.exists()) return@runCatching emptyMap()
        codec.decode(atomicFile.readFully().decodeToString()) ?: emptyMap()
    }.getOrDefault(emptyMap())

    private fun writeAll(snapshots: Map<UUID, Map<String, PhoneBillSnapshot>>) {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create phone-bill cache directory")
        val bytes = codec.encode(snapshots).encodeToByteArray()
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let(atomicFile::failWrite)
            throw IOException("Cannot save phone-bill cache", error)
        }
    }
}

class PhoneBillSnapshotJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(values: Map<UUID, Map<String, PhoneBillSnapshot>>): String = JsonObject(
        values.entries.associate { (accountID, months) ->
            accountID.toString() to JsonObject(months.entries.associate { (key, snapshot) -> key to snapshotElement(snapshot) })
        },
    ).toString()

    fun decode(raw: String): Map<UUID, Map<String, PhoneBillSnapshot>>? {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val result = mutableMapOf<UUID, Map<String, PhoneBillSnapshot>>()
        for ((rawID, monthsElement) in root) {
            val accountID = runCatching { UUID.fromString(rawID) }.getOrNull() ?: continue
            val monthsObject = monthsElement as? JsonObject ?: return null
            val decodedMonths = mutableMapOf<String, PhoneBillSnapshot>()
            for ((key, snapshotElement) in monthsObject) {
                decodedMonths[key] = snapshot(snapshotElement as? JsonObject) ?: return null
            }
            result[accountID] = decodedMonths
        }
        return result
    }

    private fun snapshotElement(value: PhoneBillSnapshot) = JsonObject(
        linkedMapOf(
            "month" to monthElement(value.month),
            "queryTime" to nullable(value.queryTime),
            "summary" to summaryElement(value.summary),
            "userBills" to JsonArray(value.userBills.map(::userBillElement)),
            "accountSections" to JsonArray(value.accountSections.map(::sectionElement)),
            "fetchedAt" to JsonPrimitive(value.fetchedAt.toString()),
            "parserVersion" to (value.parserVersion?.let(::JsonPrimitive) ?: JsonNull),
        ),
    )

    private fun monthElement(value: BillMonth) = JsonObject(
        linkedMapOf(
            "year" to JsonPrimitive(value.year),
            "month" to JsonPrimitive(value.month),
            "key" to JsonPrimitive(value.key),
        ),
    )

    private fun summaryElement(value: PhoneBillSummary) = JsonObject(
        linkedMapOf(
            "amountDue" to JsonPrimitive(value.amountDue),
            "realPayFee" to JsonPrimitive(value.realPayFee),
            "totalPrice" to JsonPrimitive(value.totalPrice),
            "totalDiscount" to JsonPrimitive(value.totalDiscount),
            "totalRealFee" to JsonPrimitive(value.totalRealFee),
            "totalAdjustAfter" to JsonPrimitive(value.totalAdjustAfter),
            "totalAcctDiscnt" to nullable(value.totalAcctDiscnt),
            "totalLateFee" to nullable(value.totalLateFee),
            "allRebates" to nullable(value.allRebates),
            "realPayFeeP" to nullable(value.realPayFeeP),
        ),
    )

    private fun userBillElement(value: UserBill) = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "mobile" to JsonPrimitive(value.mobile),
            "virtualUserTag" to nullable(value.virtualUserTag),
            "payable" to JsonPrimitive(value.payable),
            "sections" to JsonArray(value.sections.map(::sectionElement)),
            "totalPrice" to nullable(value.totalPrice),
            "totalDiscount" to nullable(value.totalDiscount),
            "totalRealFee" to nullable(value.totalRealFee),
            "totalAdjustAfter" to nullable(value.totalAdjustAfter),
            "totalAcctDiscnt" to nullable(value.totalAcctDiscnt),
            "totalLateFee" to nullable(value.totalLateFee),
            "allRebates" to nullable(value.allRebates),
            "realPayFeeP" to nullable(value.realPayFeeP),
        ),
    )

    private fun sectionElement(value: BillItemSection) = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "title" to JsonPrimitive(value.title),
            "items" to JsonArray(value.items.map(::itemElement)),
        ),
    )

    private fun itemElement(value: BillItem) = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "name" to JsonPrimitive(value.name),
            "code" to nullable(value.code),
            "originalFee" to JsonPrimitive(value.originalFee),
            "discount" to JsonPrimitive(value.discount),
            "realFee" to JsonPrimitive(value.realFee),
        ),
    )

    private fun snapshot(value: JsonObject?): PhoneBillSnapshot? {
        value ?: return null
        val month = month(value["month"] as? JsonObject) ?: return null
        val summary = summary(value["summary"] as? JsonObject) ?: return null
        val users = (value["userBills"] as? JsonArray)?.map { userBill(it as? JsonObject) ?: return null } ?: return null
        val sections = (value["accountSections"] as? JsonArray)?.map { section(it as? JsonObject) ?: return null } ?: return null
        val fetchedAt = value.string("fetchedAt")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        return PhoneBillSnapshot(
            month = month,
            queryTime = value.string("queryTime"),
            summary = summary,
            userBills = users,
            accountSections = sections,
            fetchedAt = fetchedAt,
            parserVersion = value.int("parserVersion"),
        )
    }

    private fun month(value: JsonObject?): BillMonth? {
        value ?: return null
        return BillMonth(
            year = value.string("year") ?: return null,
            month = value.string("month") ?: return null,
            key = value.string("key") ?: return null,
        )
    }

    private fun summary(value: JsonObject?): PhoneBillSummary? {
        value ?: return null
        return PhoneBillSummary(
            amountDue = value.string("amountDue") ?: return null,
            realPayFee = value.string("realPayFee") ?: return null,
            totalPrice = value.string("totalPrice") ?: return null,
            totalDiscount = value.string("totalDiscount") ?: return null,
            totalRealFee = value.string("totalRealFee") ?: return null,
            totalAdjustAfter = value.string("totalAdjustAfter") ?: return null,
            totalAcctDiscnt = value.string("totalAcctDiscnt"),
            totalLateFee = value.string("totalLateFee"),
            allRebates = value.string("allRebates"),
            realPayFeeP = value.string("realPayFeeP"),
        )
    }

    private fun userBill(value: JsonObject?): UserBill? {
        value ?: return null
        val sections = (value["sections"] as? JsonArray)?.map { section(it as? JsonObject) ?: return null } ?: return null
        return UserBill(
            id = value.string("id") ?: return null,
            mobile = value.string("mobile") ?: return null,
            virtualUserTag = value.string("virtualUserTag"),
            payable = value.string("payable") ?: return null,
            sections = sections,
            totalPrice = value.string("totalPrice"),
            totalDiscount = value.string("totalDiscount"),
            totalRealFee = value.string("totalRealFee"),
            totalAdjustAfter = value.string("totalAdjustAfter"),
            totalAcctDiscnt = value.string("totalAcctDiscnt"),
            totalLateFee = value.string("totalLateFee"),
            allRebates = value.string("allRebates"),
            realPayFeeP = value.string("realPayFeeP"),
        )
    }

    private fun section(value: JsonObject?): BillItemSection? {
        value ?: return null
        val items = (value["items"] as? JsonArray)?.map { item(it as? JsonObject) ?: return null } ?: return null
        return BillItemSection(
            id = value.string("id") ?: return null,
            title = value.string("title") ?: return null,
            items = items,
        )
    }

    private fun item(value: JsonObject?): BillItem? {
        value ?: return null
        return BillItem(
            id = value.string("id") ?: return null,
            name = value.string("name") ?: return null,
            code = value.string("code"),
            originalFee = value.string("originalFee") ?: return null,
            discount = value.string("discount") ?: return null,
            realFee = value.string("realFee") ?: return null,
        )
    }

    private fun nullable(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

    private fun JsonObject.string(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive === JsonNull) return null
        return primitive.content
    }

    private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()
}

fun interface PhoneBillPolicyProvider {
    fun current(): PhoneBillRefreshPolicy
}

class SettingsPhoneBillPolicyProvider(
    private val settingsRepository: SettingsRepository,
) : PhoneBillPolicyProvider {
    override fun current(): PhoneBillRefreshPolicy = settingsRepository.loadPhoneBillRefreshPolicy()
}

/** Source-equivalent cache timing semantics from iOS PhoneBillCachePolicy. */
class PhoneBillCachePolicy(
    private val policyProvider: PhoneBillPolicyProvider,
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
) {
    companion object {
        const val VISIBLE_MONTH_COUNT = 13
    }

    fun currentMonthKey(at: Instant): String {
        val date = at.atZone(zoneId)
        return "%04d%02d".format(date.year, date.monthValue)
    }

    fun visibleMonths(serverMonths: List<BillMonth>, at: Instant): List<BillMonth> {
        val current = monthStart(currentMonthKey(at)) ?: return deduplicated(serverMonths).take(VISIBLE_MONTH_COUNT)
        val earliest = current.minusMonths(12)
        return deduplicated(serverMonths.filter { month ->
            monthStart(month.key)?.let { !it.isBefore(earliest) && !it.isAfter(current) } ?: false
        }).take(VISIBLE_MONTH_COUNT)
    }

    fun isFresh(snapshot: PhoneBillSnapshot, month: BillMonth, at: Instant): Boolean {
        if (snapshot.month.key != month.key || !isCompatible(snapshot) || snapshot.fetchedAt > at) return false
        val policy = policyProvider.current()
        if (month.key == currentMonthKey(at)) {
            return at < snapshot.fetchedAt.plus(policy.currentMonthCacheMinutes.coerceAtLeast(1).toLong(), ChronoUnit.MINUTES)
        }
        val expiration = historicalExpiration(snapshot.fetchedAt, policy) ?: return false
        return at < expiration
    }

    fun nextQueryTime(snapshot: PhoneBillSnapshot, at: Instant): Instant? {
        if (!isCompatible(snapshot) || snapshot.fetchedAt > at) return null
        val policy = policyProvider.current()
        return if (snapshot.month.key == currentMonthKey(at)) {
            snapshot.fetchedAt.plus(policy.currentMonthCacheMinutes.coerceAtLeast(1).toLong(), ChronoUnit.MINUTES)
        } else {
            historicalExpiration(snapshot.fetchedAt, policy)
        }
    }

    fun isCompatible(snapshot: PhoneBillSnapshot): Boolean = snapshot.parserVersion == PhoneBillSnapshot.CURRENT_PARSER_VERSION

    private fun historicalExpiration(fetchedAt: Instant, policy: PhoneBillRefreshPolicy): Instant? {
        val fetched = fetchedAt.atZone(zoneId)
        val intervalExpiration = fetched.toLocalDate().atStartOfDay(zoneId)
            .plusDays(policy.historicalCacheDays.coerceAtLeast(1).toLong()).toInstant()
        val monthlyReset = nextMonthlyReset(fetched, policy).toInstant()
        return minOf(intervalExpiration, monthlyReset)
    }

    private fun nextMonthlyReset(after: ZonedDateTime, policy: PhoneBillRefreshPolicy): ZonedDateTime {
        val day = policy.monthlyRecheckDay.coerceIn(1, 28)
        val hour = policy.monthlyRecheckHour.coerceIn(0, 23)
        var reset = after.withDayOfMonth(day).withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!reset.isAfter(after)) reset = reset.plusMonths(1)
        return reset
    }

    private fun monthStart(key: String): LocalDate? {
        if (key.length != 6) return null
        val year = key.take(4).toIntOrNull() ?: return null
        val month = key.takeLast(2).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        return runCatching { LocalDate.of(year, month, 1) }.getOrNull()
    }

    private fun deduplicated(months: List<BillMonth>): List<BillMonth> {
        val seen = mutableSetOf<String>()
        return months.sortedByDescending { it.key }.filter { seen.add(it.key) }
    }
}
