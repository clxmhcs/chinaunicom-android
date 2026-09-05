package com.clxmhcs.chinaunicom.core.network

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal val testRenewalContext = UnicomSessionRenewalDeviceContext(
    deviceCode = "550E8400-E29B-41D4-A716-446655440000",
    deviceID = "b".repeat(64),
    uniqueIdentifier = "iosa" + "a".repeat(32),
    deviceModel = "Pixel-Test",
    deviceOS = "13",
    userAgentSystemVersion = "13",
    localIPv4Address = "192.0.2.8",
)

internal val testRenewalProvider = UnicomSessionRenewalDeviceContextProvider { testRenewalContext }

internal val testRenewalClock: Clock = Clock.fixed(
    Instant.parse("2026-09-05T06:30:00Z"),
    ZoneId.of("Asia/Shanghai"),
)

internal fun testUnicomAPIClient(http: UnicomHTTPClient): UnicomAPIClient = UnicomAPIClient(
    http = http,
    renewalDeviceContextProvider = testRenewalProvider,
    clock = testRenewalClock,
)
