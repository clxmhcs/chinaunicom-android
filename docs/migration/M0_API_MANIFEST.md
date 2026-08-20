# Android-M0 — API / Protocol Manifest

This file freezes the currently identified network surface from the supplied iOS source. It is a migration map, not an instruction to invent new requests. Request fields, headers, cookie mutation and response parsing remain governed by the corresponding iOS implementation.

## Core quota/session

Source: `ChinaUnicom/Services/UnicomAPIClient.swift`, `UnicomNetworking.swift`.

| Host | Endpoint | Purpose |
| --- | --- | --- |
| `m.client.10010.com` | `/servicequerybusiness/operationservice/queryOcsPackageFlowLeftContentRevisedInJune` | quota/flow/voice source response |
| `m.client.10010.com` | `/servicequerybusiness/query/myInformation` | account information |
| `m.client.10010.com` | `/mobileService/onLine.htm` | session reactivation using `appId` + `token_online` |

Frozen behavior includes:

- `application/x-www-form-urlencoded` request support.
- automatic system Cookie storage disabled in the core HTTP client; Cookie behavior is explicit.
- `Set-Cookie` mutation is parsed and applied to the current Cookie set.
- session-expiry markers include `9998`, `999998`, `999999`, `0500` plus source-defined textual invalid-cookie conditions.
- current iOS logic performs controlled retry/reactivation rather than silently inventing a new login flow.

## Balance

Source: `ChinaUnicom/Services/UnicomBalanceClient.swift`, `UnicomNetworking.swift`, `AppStoreBalance.swift`.

| Host | Endpoint | Purpose |
| --- | --- | --- |
| `m.client.10010.com` | `/servicequerybusiness/balancenew/accountBalancenew.htm` | account/group balance |

The protocol cannot be migrated independently from the shared-balance scope/cache/lease rules documented in `M0_STORAGE_MANIFEST.md`.

## Login

### SMS verification login

Source: `Views/AccountCredentialLoginSessions.swift`.

| Host | Endpoint | Purpose |
| --- | --- | --- |
| `loginxx.10010.com` | `/mobileService/sendRadomNum.htm` | send SMS verification code |
| `loginxx.10010.com` | `/mobileService/radomLogin.htm` | SMS-code login |
| `loginxx.10010.com` | `/login-web/v1/switch/getSwitch` | login switch/security-captcha support |

### Password login

Source: `Services/RSAEncryptor.swift`.

| Host | Endpoint | Purpose |
| --- | --- | --- |
| `loginxx.10010.com` | `/mobileService/login.htm` | service-password login |
| `loginxx.10010.com` | `/login-web/v1/switch/getSwitch` | login switch/security-captcha support |

Password login includes source-defined RSA PKCS#1 behavior and device/session identity handling. Android must match semantics, not copy Apple Security APIs.

## Ordered business

Source: `Services/OrderedBusinessClient.swift`.

| Host | Endpoint |
| --- | --- |
| `mxx.client.10010.com` | `/servicebusiness/newOrdered/provincialAlloc` |
| `mxx.client.10010.com` | `/servicebusiness/newOrdered/queryOrderRelationship` |
| `loginxx.10010.com` | `/mobileService/onLine.htm` |

The client also freezes `imgxx.client.10010.com` Origin/Referer behavior.

## Phone bill

Source: `Services/PhoneBillClient.swift`.

| Host | Endpoint |
| --- | --- |
| `m.client.10010.com` | `/serviceimportantbusiness/phoneBillNew/queryMonths` |
| `m.client.10010.com` | `/serviceimportantbusiness/phoneBillNew/queryDetail` |
| `m.client.10010.com` | `/mobileService/onLine.htm` |

## Integral/points

Source: `Services/IntegralClient.swift`.

Host: `activity.10010.com`

- `/welfare-mall-front/mobile/show/bj2205/v2/1`
- `/welfare-mall-front/new/integral/queryMonthlyList/v1`
- `/welfare-mall-front/new/integral/querySummaryList/v1`

## My orders

Source: `Services/MyOrderClient.swift`.

- Host `m.client.10010.com`
- `/mobileservicequery/order/newQueryOrder`

Order detail WebView bridge additionally calls source-page-relative endpoints:

- `/udbh/rest/portal/qryEvaluateOrderInfoByOrderId`
- `/udbh/rest/portal/querySubProducts`
- `/npfwap/NpfMobAppQuery/broadRenewalOrderHandle/broaRenewalInfo?...`

Source: `Views/MyOrderDetailWebBridge.swift`.

## My package

Source: `Services/MyPackageClient.swift`.

Host: `mxx.client.10010.com`

- `/servicequerybusiness/queryPackage/myPackage`
- `/servicequerybusiness/queryPackage/myResourceDetails`
- `/servicequerybusiness/queryPackage/myMemberMobile`
- `/servicequerybusiness/queryPackage/myPrettyNumber`

## Tariff zone

Source: `Services/TariffZoneClient.swift`.

Host: `mxx.client.10010.com`

- `/servicequerybusiness/queryTariffNew/indexData`
- `/servicequerybusiness/queryTariffNew/threeLevelName`
- `/servicequerybusiness/queryTariffNew/operateData/{pathIDs}`

## Rebate / gift

Source: `Views/RebateAndGiftView.swift`.

Host: `hlbasic.10010.com`

- `/servicequerybusiness/grantsAndContractRebates/contractRebate`
- `/servicequerybusiness/grantsAndContractRebates/canOpenAnInterfaceCall`

Session activation still depends on `m.client.10010.com/mobileService/onLine.htm`.

## Video ring membership

Source: `Services/VideoRingAPIClient.swift`, `VideoRingMemberService.swift`.

Host: `m.10155.com`

- `/woapp/login/ecsAppletLogin`
- `/woapp/videoRing/getCrbtFlag`
- `/woapp/h5/woMember/getClientMemberInfosByUserId`
- `/woapp/h5/woMember/getMemberDetail`

The iOS client intentionally disables shared Cookie storage for 10155 sessions and authenticates with its source-defined UID/access-token scheme. This isolation must be preserved.

## Electronic receipt

Source: `Views/ElectronicReceiptCore.swift`, `ElectronicReceiptViews.swift`, `ElectronicReceiptWebView.swift`.

- Entry H5: `https://imgxx.client.10010.com/dianzishoulidan/index.html`
- Main route: `https://imgxx.client.10010.com/dianzishoulidan/index.html#/dianzishoulidan`
- PDF/query route pattern: `/servicequerybusiness/queryNoPaper/noPaperDetailPdfByUser?...`
- API origin is selected by the source based on H5 host (`m.client.10010.com`, `mxx.client.10010.com`, or `hlbasic.10010.com`).

Android WebView migration must preserve credential injection, route observation, JavaScript bridge behavior, PDF recognition/download and local-cache integration; it must not merely open the web page.

## Phone-number attribution helpers

Source: `Services/PhoneAttributionService.swift`.

These are third-party helpers, not China Unicom core protocol:

- `https://zj.v.api.aa1.cn/api/phone/2024/?num=...`
- `https://cx.shouji.360.cn/phonearea.php?number=...`

They must remain isolated from account authentication data.

## Security constraint

This manifest deliberately contains no real account Cookie, `token_online`, password, SMS code or identity data.
