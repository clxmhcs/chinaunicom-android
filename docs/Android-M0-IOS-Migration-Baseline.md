# Android-M0 iOS Migration Baseline

## Status

Stage: Android-M0
Goal: Freeze iOS reference baseline before Android implementation.

## Source of Truth

The iOS project `chinaunicom-ios-main` is the reference baseline for:

- Business behavior
- API request behavior
- Data parsing rules
- Cache semantics
- UI visual reference

Android implementation must reproduce behavior instead of redesigning business rules.

## Migration Rules

1. iOS behavior is the business reference.
2. iOS UI is the visual reference except platform-owned system UI.
3. Android platform implementations may differ, but observable behavior must remain consistent.
4. Do not re-investigate or redesign Unicom API protocols.
5. No real credentials, cookies, tokens, passwords, or private account data may enter Git history.
6. Business/domain code must not depend on Compose UI.
7. Widget code must consume shared repository/snapshot data only.
8. Packet capture/VPN related features are migrated after the main application.

## Baseline Inventory To Complete

- [ ] IOS_SOURCE_MANIFEST
- [ ] API_MANIFEST
- [ ] MODEL_MANIFEST
- [ ] STORAGE_MANIFEST
- [ ] SCREEN_MANIFEST
- [ ] VISUAL_BASELINE

## Current Android Repository Direction

Target stack:

- Kotlin
- Jetpack Compose
- ViewModel + StateFlow
- Coroutines
- OkHttp
- Room/DataStore
- Android Keystore
- AppWidget/Glance

## M0 Exit Criteria

M0 passes when:

- iOS source inventory is recorded.
- API inventory is recorded.
- Model inventory is recorded.
- Storage inventory is recorded.
- Screen inventory is recorded.
- Light/Dark UI reference screenshots are collected.

Only after M0 completion can Android-M1 engineering begin.
