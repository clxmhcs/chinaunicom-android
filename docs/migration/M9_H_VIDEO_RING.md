# Android-M9-H — 视频彩铃会员

## Status

`M9-H_RESULT = IN_PROGRESS`

- `M9-H1_RESULT = IN_PROGRESS` — source-derived native client + selected-account credential lifecycle + member/benefit store + rough functional wiring + CI
- `M9-H2_RESULT = NOT_STARTED` — real-device functional validation

Minimum supported Android remains **Android 11 / API 30**.
Final app-owned visual parity remains deferred to the later page-by-page visual pass.

## Source-derived functional boundary

Current iOS source establishes `其它业务 -> 视频彩铃会员` as a per-mobile-number native member center.

Android preserves these source rules:

- selectable targets are persisted enabled mobile accounts only; independent broadband is not inserted into this account selector;
- credentials are read only for the selected account from the existing M5 credential authority;
- native China Unicom ticket request uses `https://m.client.10010.com/edop_ng/getTicketByNative`, the selected account's `ecs_token`, and app id `edop_unicom_c43eac06`;
- if that ticket request fails and the selected account has appID/token_online, only that same account is reactivated through the existing M4 session path, renewed credentials are immediately persisted through M5, then the ticket request is retried;
- the video-ring service root is `https://m.10155.com` with client header appid `3000013947`;
- login endpoint: `/woapp/login/ecsAppletLogin`;
- video-ring activation endpoint: `/woapp/videoRing/getCrbtFlag`;
- member configuration endpoint: `/woapp/h5/woMember/getClientMemberInfosByUserId`;
- member-benefit endpoint: `/woapp/h5/woMember/getMemberDetail`;
- after 10155 login, normalized `caller` must exactly equal the selected mobile number. A mismatch is a hard failure and no member/benefit data is exposed;
- 10155 networking uses no automatic cookie jar, preventing session/cookie cross-contamination between mobile numbers;
- source member types include `87 AI彩铃视听剧场会员`, `15 铂金会员`, and `76 AI彩铃升级版`;
- the first active member type is used for member detail, falling back to type `15` when none is active;
- current source's monthly-benefit view is read/display only. Android does not invent claim, redeem, purchase or other write APIs;
- current iOS implementation has no VideoRing-specific disk cache or refresh-policy Settings authority; Android therefore keeps this feature's business result in memory and reloads on entry/manual refresh.

## Android implementation boundary

M9-H1 adds:

- `core:model/VideoRingModels.kt`;
- `core:network/UnicomVideoRingClient.kt` with isolated GET/POST transport and `CookieJar.NO_COOKIES`;
- protocol tests covering the ticket -> 10155 login -> flag -> member -> benefit chain, renewed selected-account credentials and caller mismatch hard failure;
- `core:login/VideoRingAccountCredentialLifecycle.kt`, which keeps M5 `CredentialStore` as the only credential authority and strips renewed credentials from ordinary business state;
- `data:videoring/VideoRingStore.kt`, an in-memory selected-account store with stale-request suppression and defense-in-depth phone matching;
- `VideoRingViewModel`, mobile-account selector and member-center rough functional Compose UI;
- active `其它业务 -> 视频彩铃会员` routes `other/video-ring` and `other/video-ring/{accountId}`;
- a dedicated `Android M9 H Video Ring` workflow.

No Cookie, appID, token_online, password, SMS code or identity material is placed in Compose/navigation/business state.

## Acceptance order

1. H1 implementation and CI must pass first.
2. H2 real-device evidence must then verify the selected mobile account reaches its own 10155 member state without cross-account leakage.
3. Visual styling remains deferred and does not block functional closure.

`NEXT = Android-M9-H1 — Video Ring compile / CI`
