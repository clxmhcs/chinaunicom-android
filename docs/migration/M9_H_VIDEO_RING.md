# Android-M9-H — 视频彩铃会员

## Status

`M9-H_RESULT = IN_PROGRESS`

- `M9-H1_RESULT = REOPENED_R1` — initial implementation compiled, but real-device H2 exposed 10155 auth rejection: `接口鉴权拦截不通过，请求随机数不存在`.
- `M9-H1-R1_RESULT = IN_PROGRESS` — realign Android to the **currently active iOS `VideoRingInlineMemberService`**.
- `M9-H2_RESULT = FAIL_RETRY_REQUIRED` — repeat real-device validation only after R1 CI passes.

Minimum supported Android remains **Android 11 / API 30**.
Final app-owned visual parity remains deferred to the later page-by-page visual pass.

## Real-device failure that reopened H1

The first H2 candidate reached the Android member center but the 10155 server rejected the request before business data was accepted:

`接口鉴权拦截不通过，请求随机数不存在`

The failure proved that the earlier Android boundary was derived from a non-driving service implementation. The current iOS view actually calls the private `VideoRingInlineMemberService` in `VideoRingMemberView.swift`, so R1 uses that implementation as the authority.

No Cookie, appID, token_online, password, SMS code or identity material from the real device is stored in this document.

## Active iOS source boundary used by R1

The current iOS chain is:

1. selected enabled mobile account only; 11 digits and starts with `1`;
2. read that selected account's credentials from the existing credential authority;
3. native ticket: `https://m.client.10010.com/edop_ng/getTicketByNative` with selected account `ecs_token` and native app id `edop_unicom_c43eac06`;
4. if ticket retrieval fails and appID/token_online are available, reactivate only that selected account, persist renewed credentials, then retry ticket retrieval;
5. create a fresh ephemeral 10155 cookie session for this refresh;
6. login: `https://m.10155.com/woapp/login/ecsAppletLogin`;
7. normalized login `caller` must exactly equal the selected phone number; mismatch is a hard failure;
8. configured member tabs: `/woapp/h5/woMember/getClientMemberInfosByUserId`;
9. actual member-open state: `/woapp/uc/getmemberinfo`;
10. merge by member type against the three source tabs: `87 AI彩铃视听剧场会员`, `15 铂金会员`, `76 AI彩铃升级版`.

The active inline implementation does **not** use `getCrbtFlag` or `getMemberDetail` for this page's live member state.

## 10155 authentication contract

Every 10155 request, including login, carries:

- `appid = 3000013947`;
- persistent 36-character client `uid` (client identity, **not** server-returned userid);
- millisecond `timestamp`;
- nonce formatted like `0.%016llu`;
- uppercase MD5 `sign = MD5(timestamp + "VNEU8G4V" + nonce)`;
- `oswoversion = 1018`;
- `Accept-Language = zh-Hans-CN;q=1.0`;
- iOS-equivalent China Unicom `User-Agent`;
- normalized Bearer `accessToken` only after login.

Android R1 creates one fresh in-memory cookie jar per refresh and only accepts/replays automatic cookies for `m.10155.com`, matching the source's ephemeral 10155 session while preventing cookie reuse across account refreshes.

## Cache / entry refresh parity

The active iOS implementation has a per-account disk cache and a `videoRing` entry policy in the shared refresh-policy schema.

R1 therefore adds:

- per-account `AtomicFile` cache with `fd.sync()`;
- cached phone-number guard before restoring data;
- default entry mode `everyEntry`;
- `refreshWhenExpired` with default 60-minute validity;
- `manualOnly` support;
- manual refresh always forces a real network query;
- the `videoRing` policy is written to the same existing refresh-logic JSON/storage authority, not a second settings store.

## R1 acceptance order

1. R1 source-derived static contract passes.
2. Core network/login/settings/cache/store tests pass.
3. Debug + Release app builds pass.
4. Main APK and permanent M9 regressions remain green.
5. Repeat H2 on a real device. The first proof is that the previous `请求随机数不存在` authentication rejection is gone and the three member tabs show server-derived open state.

`NEXT = Android-M9-H1-R1 — CI then H2 retry`
