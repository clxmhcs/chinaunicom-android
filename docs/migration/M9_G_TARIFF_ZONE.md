# Android-M9-G — 资费专区

## Status

`M9-G_RESULT = PASS / CLOSED`

- `M9-G1_RESULT = PASS / CLOSED` — source-derived native core + mobile-account credential lifecycle + browse/search store + rough functional wiring + CI
- `M9-G2_RESULT = PASS / CLOSED` — real-device functional validation accepted by the user on 2026-08-27

Minimum supported Android remains **Android 11 / API 30**.
Final visual parity remains intentionally deferred to the later page-by-page visual pass.

## Accepted source-derived boundary

The Android implementation preserves the iOS business boundary:

- only persisted enabled mobile accounts are selectable; independent broadband is not inserted into the target list;
- `全国资费 = 1`, `本地资费 = 2`;
- local mode supports province/city selection;
- first-level / second-level categories and tariff product references come from the carrier;
- tariff detail is fetched in batches of 5 and supports `加载更多`;
- global search traverses the carrier tariff catalog and can open a matching tariff detail;
- carrier `0001` is a legal empty result rather than a session/network failure;
- M4 session activation and M5 `CredentialStore` remain the only session/credential authorities;
- no TariffZone-specific credential authority or credential-bearing business cache was added.

Carrier root/endpoints:

- `https://mxx.client.10010.com`
- `/servicequerybusiness/queryTariffNew/indexData`
- `/servicequerybusiness/queryTariffNew/threeLevelName`
- `/servicequerybusiness/queryTariffNew/operateData`

## Implementation / CI

Primary implementation:

- `c27a9f87d883c46678b1c7cbcc91e920836450a0`

Accepted Compose compile fix:

- `2b91e0eab1d4b065c740d22e1997fe31bfdc9ffc`

Accepted CI at the functional head:

- M9-G run `33075369336` — **SUCCESS**
- Main APK run `33075369429` — **SUCCESS**
- M2 run `33075369406` — **SUCCESS**
- permanent M9 regression `33075369466` — **SUCCESS**
- M5/M6/M7/M8/M9-B4/M9-C/M9-D/M9-E/M9-F historical regressions triggered by the final candidate — **SUCCESS**
- Action Test `33075369339` — **SUCCESS**

Real-device artifact:

- artifact id `9647744051`
- head SHA `2b91e0eab1d4b065c740d22e1997fe31bfdc9ffc`
- GitHub ZIP digest `sha256:6ee335ad55f39f31f53d34958cdd589985483d6eaeb41d4c4682e168c2bde05b`
- extracted APK SHA-256 `f48327aefa75b92fde4bd2003975c4251c827e85852ac5e8b6b8eaf4e87e2fd2`

## Real-device acceptance

The user completed the requested M9-G2 real-device checks and reported the stage **verified successfully** on 2026-08-27. This closes the functional migration boundary for Tariff Zone. Visual polishing is still deferred and is not part of this closure.

`NEXT = Android-M9-H — 视频彩铃会员`
