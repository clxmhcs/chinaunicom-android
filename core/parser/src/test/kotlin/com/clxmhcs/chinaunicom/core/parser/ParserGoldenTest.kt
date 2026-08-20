package com.clxmhcs.chinaunicom.core.parser

import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.RemainingQuerySnapshot
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ParserGoldenTest {
    private val quotaParser = QuotaParser()
    private val remainingParser = RemainingQueryParser()

    @Test
    fun quotaGoldenFixtures_matchFrozenExpectedOutputs() {
        listOf(
            "fixture_01_normal",
            "fixture_02_unlimited",
            "fixture_03_shared",
            "fixture_04_directional",
            "fixture_05_voice",
        ).forEach { fixture ->
            val actual = quotaProjection(quotaParser.parse(resource("golden/quota/$fixture.json")))
            val expected = parserJson.parseToJsonElement(resource("golden/quota/$fixture.expected.json").toString(Charsets.UTF_8))
            assertEquals(fixture, expected, actual)
        }
    }

    @Test
    fun remainingGoldenFixture_matchesFrozenExpectedOutput() {
        val snapshot = remainingParser.parse(
            resource("golden/remaining/fixture_01_full.json"),
            updatedAt = Instant.parse("2026-08-20T00:00:00Z"),
        )
        val actual = remainingProjection(snapshot)
        val expected = parserJson.parseToJsonElement(resource("golden/remaining/fixture_01_full.expected.json").toString(Charsets.UTF_8))
        assertEquals(expected, actual)
    }

    @Test
    fun quotaParser_preservesExpiredSessionSignal() {
        assertThrows(QuotaParserException.SessionExpired::class.java) {
            quotaParser.parse("""{"code":"9998","resources":[]}""".encodeToByteArray())
        }
    }

    @Test
    fun quotaParser_successContainerWithoutPackages_isNotSubscribed() {
        val result = quotaParser.parse("""{"code":"0000","resources":[]}""".encodeToByteArray())
        assertEquals("notSubscribed", result.quotaResourceStatus.rawValue)
        assertEquals(emptyList<FlowPackage>(), result.packages)
    }

    @Test
    fun flowFormatter_matchesIOSRoundingAndUnitRules() {
        assertEquals("1023.5 MB", FlowFormatter(DisplayUnit.AUTOMATIC).string(1023.5))
        assertEquals("1 GB", FlowFormatter(DisplayUnit.AUTOMATIC).string(1024.0))
        assertEquals("1.50 GB", FlowFormatter(DisplayUnit.GIGABYTES).string(1536.0))
        assertEquals("0 MB", FlowFormatter(DisplayUnit.MEGABYTES).string(-5.0))
        assertEquals("--", FlowFormatter(DisplayUnit.AUTOMATIC).string(null))
        assertEquals("138 **** 8000", "13800138000".maskedMobile())
        assertEquals(null, cleanedText("  \n "))
    }

    private fun resource(path: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "missing resource: $path" }.use { it.readBytes() }

    private fun quotaProjection(result: QuotaParseResult): JsonObject = JsonObject(
        linkedMapOf(
            "packageName" to JsonPrimitive(result.packageName),
            "status" to JsonPrimitive(result.quotaResourceStatus.rawValue),
            "flows" to JsonArray(result.packages.map(::flowProjection)),
            "voices" to JsonArray(result.voicePackages.map(::voiceProjection)),
        ),
    )

    private fun flowProjection(value: FlowPackage): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "name" to JsonPrimitive(value.originalName),
            "total" to numberString(value.totalMB),
            "used" to numberString(value.usedMB),
            "remaining" to numberString(value.remainingMB),
            "quota" to JsonPrimitive(value.detectedQuotaType.rawValue),
            "category" to JsonPrimitive(value.detectedCategory.rawValue),
            "shared" to JsonPrimitive(value.isShared),
            "shareScope" to JsonPrimitive(value.resolvedShareScope.rawValue),
            "carry" to JsonPrimitive(value.resolvedCarryForwardScope.rawValue),
            "rawType" to nullableString(value.rawType),
            "rawCode" to nullableString(value.rawCode),
        ),
    )

    private fun voiceProjection(value: VoicePackage): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "name" to JsonPrimitive(value.originalName),
            "total" to numberString(value.totalMinutes),
            "used" to numberString(value.usedMinutes),
            "remaining" to numberString(value.remainingMinutes),
            "unlimited" to JsonPrimitive(value.isUnlimited),
            "shared" to JsonPrimitive(value.isShared),
            "rawType" to nullableString(value.rawType),
            "rawCode" to nullableString(value.rawCode),
        ),
    )

    private fun remainingProjection(snapshot: RemainingQuerySnapshot): JsonObject = JsonObject(
        linkedMapOf(
            "members" to JsonArray(snapshot.members.map { member ->
                JsonObject(linkedMapOf(
                    "number" to JsonPrimitive(member.maskedNumber),
                    "role" to JsonPrimitive(member.role.rawValue),
                    "current" to nullableBoolean(member.isCurrentLogin),
                ))
            }),
            "summaries" to JsonArray(snapshot.flowSummaries.map { summary ->
                JsonObject(linkedMapOf(
                    "category" to JsonPrimitive(summary.category.rawValue),
                    "remaining" to numberString(summary.remainingMB),
                    "used" to numberString(summary.usedMB),
                ))
            }),
            "flows" to JsonArray(snapshot.flowPackages.map { flow ->
                JsonObject(linkedMapOf(
                    "id" to JsonPrimitive(flow.id),
                    "name" to JsonPrimitive(flow.name),
                    "total" to numberString(flow.totalMB),
                    "used" to numberString(flow.usedMB),
                    "remaining" to numberString(flow.remainingMB),
                    "unlimited" to JsonPrimitive(flow.resolvedIsUnlimited),
                    "speedLimit" to numberString(flow.speedLimitMB),
                ))
            }),
            "sharedTotals" to JsonArray(snapshot.sharedFlowMemberTotals.map { usage ->
                JsonObject(linkedMapOf(
                    "number" to JsonPrimitive(usage.maskedNumber),
                    "role" to JsonPrimitive(usage.role.rawValue),
                    "used" to numberString(usage.usedValue),
                    "current" to nullableBoolean(usage.isCurrentLogin),
                ))
            }),
            "voice" to JsonObject(linkedMapOf(
                "remaining" to numberString(snapshot.voice.remainingMinutes),
                "used" to numberString(snapshot.voice.usedMinutes),
                "packages" to JsonArray(snapshot.voice.packages.map { JsonPrimitive(it.id) }),
                "unshared" to JsonArray(snapshot.voice.unsharedPackages.map { JsonPrimitive(it.id) }),
            )),
            "sms" to JsonObject(linkedMapOf(
                "remaining" to numberString(snapshot.sms.remainingCount),
                "used" to numberString(snapshot.sms.usedCount),
                "packages" to JsonArray(snapshot.sms.packages.map { JsonPrimitive(it.id) }),
                "unshared" to JsonArray(snapshot.sms.unsharedPackages.map { JsonPrimitive(it.id) }),
            )),
        ),
    )

    private fun numberString(value: Double?): JsonElement =
        value?.let { JsonPrimitive(String.format(Locale.US, "%.4f", it)) } ?: JsonNull

    private fun nullableString(value: String?): JsonElement = value?.let { JsonPrimitive(it) } ?: JsonNull
    private fun nullableBoolean(value: Boolean?): JsonElement = value?.let { JsonPrimitive(it) } ?: JsonNull
}
