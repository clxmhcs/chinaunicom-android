# Android M10-CLOUD — Electronic Receipt Cross-Platform Cloud Decision

Status: **DECISION CLOSED / IMPLEMENTATION DEFERRED TO MILESTONE 3**

Date: 2026-08-28

## Prerequisite closure

The following real-device stages are accepted and closed before this decision:

- M9-H 视频彩铃会员 — **PASS / CLOSED**
- M9-I 附近营业厅 / 原生预约取号 — **PASS / CLOSED**
- M10 LOCAL 电子受理单 / WebView / 认证 / PDF / 本地保存 / 查看 / SAF 导出 — **PASS / CLOSED**

The final JVM regression debt in `UnicomAppointmentTicketClientTest` was caused by Android `org.json` being used from plain JVM tests. Production appointment behavior was already validated on a real device. Test scope now provides a real JVM JSON implementation; production appointment code is unchanged.

CI closure on `e102a86adf9687072355c972280df5e97f16314d`:

- Android M9 I Service Hall run `33135197129` — SUCCESS
- Android M10 Electronic Receipt run `33135197194` — SUCCESS
- Android M9 Other Business permanent run `33135197160` — SUCCESS
- Android Main APK Build run `33135197154` — SUCCESS
- Android M2 Models run `33135197118` — SUCCESS
- Action Test run `33135197106` — SUCCESS

## Current iOS authority

Current iOS production code uses `CKContainer(identifier: "iCloud.com.clxmhcs.chinaunicom").privateCloudDatabase`.

### Receipt cloud record

Each electronic receipt is an independent `ElectronicReceiptIndex` record keyed by receipt UUID. Current CloudKit fields are:

- `receiptID`
- `orderID`
- `mobile`
- `month`
- `acceptDate`
- `createdAt`
- `title`
- `fileName`
- `fileHash`
- `updatedAt`
- `note`

The PDF bytes themselves are **not** stored in CloudKit.

### iOS merge contract

During restore, iOS:

1. loads the local index;
2. fetches CloudKit indexes;
3. merges by receipt UUID;
4. when the same UUID exists on both sides, the record with the newer `updatedAt` wins;
5. persists the merged result locally;
6. after a successful cloud fetch, uploads local records that are missing in cloud or newer than the cloud copy.

This UUID + `updatedAt` last-write-wins rule is the compatibility baseline for the future cross-platform provider.

### Platform-specific directory metadata

Current iOS also stores `ElectronicReceiptBookmarkSetting` in CloudKit. `bookmarkData` is an Apple security-scoped bookmark and is not portable to Android. Android SAF tree URI permissions are likewise device/platform local.

Therefore directory authorization is explicitly excluded from cross-platform cloud sync.

## Android M10-CLOUD decision

### Provider

The future cross-platform provider is **Google Drive `appDataFolder`**.

Reasons for this migration decision:

- usable by both Android and iOS clients;
- per-user private app storage rather than a public Drive document surface;
- no project-owned database/server is required for the receipt index;
- appropriate for small metadata records;
- independent of Android local SAF storage and iOS security-scoped bookmarks.

Actual Google OAuth client configuration and production provider implementation are deferred to **Milestone 3 — Full Feature Parity / Cloud replacement**. M10 local receipt functionality does not wait for that work.

### Portable cloud payload

Only portable receipt metadata is eligible for cloud sync:

- receipt UUID
- order ID
- mobile/account identifier required by the receipt index
- month
- accept date
- created timestamp
- title
- file name
- file hash
- updated timestamp
- user note

The provider must never upload:

- PDF bytes;
- Cookie;
- `appId`;
- `token_online`;
- SMS verification code;
- identity-card suffix;
- Android Keystore data;
- iOS Keychain data;
- iOS security-scoped bookmark bytes;
- Android SAF URI / persisted URI permission.

### Merge contract

Future Android/iOS Drive implementations must use the following provider-neutral contract:

1. receipt identity = UUID;
2. same UUID conflict = record with greater `updatedAt` wins;
3. cloud missing + local existing = local record is eligible for upload after a successful cloud listing;
4. cloud newer + local older = cloud metadata replaces local metadata;
5. equal `updatedAt` must be deterministic and must not create duplicate records;
6. PDF presence is resolved only from local/device storage and `fileHash`; cloud metadata must not pretend a missing local PDF exists;
7. cloud failure must never block local query, local PDF viewing, saving, deletion, or SAF export.

A future implementation may introduce versioned deletion tombstones to prevent stale-device resurrection, but that is an explicit schema upgrade and must not be silently mixed into the current iOS compatibility contract.

## M10 result

- M10 LOCAL = **PASS / CLOSED**
- M10-CLOUD decision = **CLOSED**
- M10-CLOUD provider implementation = **DEFERRED TO MILESTONE 3**
- Android minimum remains **Android 11 / API 30**

## NEXT

**M11 — Settings complete functional migration.**

M11 starts from the current iOS `SettingsView.swift` feature inventory and the existing Android repositories. Visual parity remains deferred until the functional settings authority and navigation are complete.
