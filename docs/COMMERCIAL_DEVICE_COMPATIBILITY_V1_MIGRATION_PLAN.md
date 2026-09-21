# AquaLight Commercial Device Compatibility V1 — Migration and Release Plan

Status: **normative implementation plan for the first commercial release**

Android branch: `feat/commercial-device-compatibility-v1`  
Android base: `feat/light-quick-setup-commercial-ui`

Firmware branch: `feature/commercial-device-compatibility-v1`  
Firmware base: `feature/smart-light-automation-plan-v2`

## 1. Decision record

AquaLight has not shipped a public Android application or field firmware baseline yet. There is therefore no commercial legacy contract to preserve during this migration.

The first public contract remains **V1**. This work does **not** introduce Light V2, Timer V2, Dosing V2, Cooling V2, OTA manifest V2, or a legacy adapter.

Before the first commercial release, the current V1 contracts may be finalized in place. After the first commercial release, every V1 wire contract listed by this document is frozen. A future breaking wire change requires a new major contract; additive feature availability must not mutate unrelated base surfaces.

This plan applies to every commercial family:

- Light — WRGB Pro Elite and RGB Pro Slim
- Timer — Relay Pro 2 and Relay Pro 4
- Dosing — Dose Pro 2 and Dose Pro 4
- Cooling — Cool Pro 1F

There is one compatibility architecture. Family-specific screens must not invent their own connection, compatibility, update, or error policies.

## 2. Problem statement

A device can be physically reachable and authenticated while one domain payload is unsupported, malformed, older, or newer than the Android code expects.

Those states are not equivalent.

The commercial architecture must never convert:

```text
domain parser/contract failure
        ->
device offline
```

A device card may remain Online while a control surface is blocked. The UI must explain the real reason.

The current Light development mismatch exposed this defect: Android expects the finalized Managed AUTO Plan additions while a device running the older development `main` Light V1 payload does not provide them. The Light refresh fails, and existing menu preparation collapses that failure into `CURRENT_LIVENESS_NOT_PROVEN`, which is rendered by an Offline dialog.

This migration fixes the category error for all families, not only Light.

## 3. Non-negotiable invariants

### 3.1 Presence is transport/liveness only

Only the central device-presence/runtime owner may decide whether a device is Online or Offline.

Domain parsers, Light/Timer/Dosing/Cooling feature adapters, route policies, OTA policy, or UI screens must not write physical presence state merely because a domain contract cannot be consumed.

### 3.2 Compatibility is independent from presence

A reachable device may be:

- compatible,
- missing an optional feature,
- using a firmware contract that requires an Android update,
- returning malformed domain state,
- or eligible for a firmware update.

All of those remain distinct from Offline.

### 3.3 Product identity is not optional-feature identity

The immutable commercial product identity remains based on authenticated identity, frozen high-level capabilities, frozen physical limits, and family/module composition.

`supportedFeatures[]` and `supportedScreens[]` are feature availability evidence. A missing optional feature must disable only the affected surface.

Unknown future additive feature/screen tokens are ignored by an older Android build unless they are required by an explicitly requested surface. Unknown additive tokens do not invalidate the physical product.

### 3.4 One central access policy

All user-initiated entry points use the same application-level policy:

```text
canonical presence
      +
authenticated runtime metadata
      +
commercial product compatibility
      +
requested surface requirements
      +
release/update policy when relevant
      ->
typed access decision
```

Devices, Tank Devices, provisioning handoff, restored device roots, and deep-link/navigation revalidation must use the same typed decision model.

### 3.5 Domain ownership remains intact

Central compatibility does not move domain topology or policy into `device.*`.

Runtime authority remains:

- Light -> `light.status.get`
- WRGB Light Thermal -> `light.thermal.status.get`
- Timer -> `timer.status.get`
- Dosing -> `dosing.status.get`
- Cooling -> `cooling.status.get`

There is no generic runtime `device.compatibility.get` and no runtime `contracts[]` registry in Shared Device Core.

Centralization occurs in Android policy and release metadata, not by making Shared Device Core depend on every domain.

### 3.6 OTA is a rescue plane

A domain incompatibility must not make firmware update/recovery unavailable.

The shared authenticated firmware/core path must remain independent from Light/Timer/Dosing/Cooling presentation parsing so a user can recover from an incompatible domain release.

### 3.7 Updates are not blindly mandatory

The release policy levels are:

- `OPTIONAL` — available in settings; no blocking prompt.
- `RECOMMENDED` — visible recommendation/badge; user may postpone.
- `FEATURE_REQUIRED` — only the listed feature surfaces require the target firmware.
- `COMPATIBILITY_REQUIRED` — affected control surfaces are blocked because continuing would be unsafe; OTA/recovery/device information remain available.

A normal feature or bug-fix release must not automatically become a mandatory update.

## 4. First commercial V1 baseline

The first release freezes:

- `aql.ws.v1`
- Shared Device Core V1
- `aqualight.light.v1`
- `aql.light-thermal.v1` where applicable
- `aqualight.timer.v1`
- `aqualight.dosing.v1`
- `aql.cooling.v1`
- `aql.ota.product-manifest.v1`

The current Smart Light Managed AUTO Plan work is part of the final unreleased `aqualight.light.v1`; it is not a V2 migration.

Firmware storage version and wire contract version remain separate concepts.

## 5. Target Android architecture

### 5.1 Application boundary

Add one owner-scoped compatibility boundary:

```text
DeviceCompatibilityOperations
```

It exposes immutable compatibility facts and never talks directly to UI resources.

Add one central policy:

```text
DeviceAccessPolicy
```

It evaluates a requested commercial surface against compatibility facts.

The existing `DeviceMenuOpenUseCase` remains the single root-menu entry point and consumes the central policy rather than duplicating family decisions.

### 5.2 Compatibility facts

The compatibility projection must distinguish at least:

```text
COMPATIBLE
FEATURE_UNAVAILABLE
FIRMWARE_UPDATE_REQUIRED
APPLICATION_UPDATE_REQUIRED
CONTRACT_INCOMPATIBLE
MALFORMED_DEVICE_STATE
```

Transport/access failures remain distinct:

```text
LOCAL_NETWORK_UNAVAILABLE
AUTHENTICATION_REQUIRED
DEVICE_UNRESPONSIVE
VERIFICATION_TIMED_OUT
```

No domain `INVALID_DATA` result may be rewritten as Offline.

### 5.3 Feature/surface requirements

Base family root access and optional surfaces are separate.

Examples:

```text
LIGHT_ROOT
  requires commercial Light base contract

LIGHT_QUICK_SETUP
  requires LIGHT_QUICK_SETUP feature/screen
  and finalized Managed AUTO Plan firmware support

TIMER_ROOT
  requires Timer base control contract

DOSING_ROOT
  requires Dosing base control contract

COOLING_ROOT
  requires Cooling base control contract
```

A missing optional feature does not block the family root.

### 5.4 Runtime feature projection

`DeviceRootSnapshot.menuFeatures` and `allowedRoutes` are projected from the device's authenticated runtime feature/screen advertisement, constrained by the recognized product's physical capabilities and limits.

They are not projected solely from the Android static product catalog.

### 5.5 Forward-compatible feature tokens

The fixed high-level capability object and physical limit object remain exact.

Feature and screen token arrays are extensibility points:

- known tokens are parsed into typed Android values,
- duplicates remain invalid,
- malformed strings remain invalid,
- unknown additive tokens are retained/ignored safely for older clients,
- unknown tokens do not make the product Offline or invalidate base product identity.

### 5.6 Central presentation mapping

UI receives typed access reasons.

One shared UI resolver maps a reason to commercial copy, tone, and action.

Required copy categories include:

- Device Offline
- Local Network Unavailable
- Device Could Not Be Reached
- Device Authentication Required
- Firmware Update Required
- App Update Required
- Device Software Incompatible
- Device State Could Not Be Verified
- Feature Not Available
- Unsupported Device

A generic `showDeviceOfflineDialog()` must not render non-offline failures.

## 6. Target firmware architecture

### 6.1 Shared Device Core remains frozen

No family-specific contract metadata is added to:

- `device.identity.get`
- `device.status.get`
- `device.capabilities.get.capabilities`
- `device.capabilities.get.limits`
- `device.status.get.modules`

The existing Shared Device Core boundaries remain the stable bootstrap/rescue plane.

### 6.2 Feature advertisement must be executable truth

A product may advertise a feature token only when the corresponding firmware surface is implemented and release-gated.

For example, `LIGHT_QUICK_SETUP` means the finalized Managed AUTO Plan command/status contract required by Android is present.

Feature tokens must not be aspirational UI labels.

### 6.3 Domain contracts remain strict

Each domain parser/status contract remains fail-closed for its own normative V1 shape.

Strict domain parsing is compatible with this architecture because parser failure is classified as domain incompatibility/malformed state, not physical Offline.

### 6.4 ESP-IDF OTA safety

Existing rollback safeguards remain mandatory:

- dual OTA application slots plus `otadata`,
- `CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE`,
- pending-verify first boot,
- application health validation,
- `esp_ota_mark_app_valid_cancel_rollback()` only after health passes,
- `esp_ota_mark_app_invalid_rollback_and_reboot()` on critical failure,
- authenticated release metadata,
- HTTPS certificate/hostname validation,
- product/hardware identity validation,
- SHA-256 integrity verification,
- Secure Boot v2 / signed image and Flash Encryption production gates.

Anti-rollback security version is a security epoch, not a normal semantic firmware version counter.

## 7. OTA manifest V1 finalization

Because no public V1 manifest has shipped, `aql.ota.product-manifest.v1` is finalized in place.

Each artifact adds three generic commercial sections:

```json
{
  "contracts": {
    "wsSchema": "aql.ws.v1",
    "wsProtocolVersion": 1,
    "deviceApiVersion": 1,
    "requiredDomains": [
      "<family-base-contract-v1>"
    ],
    "optionalDomains": [
      "<optional-domain-contract-v1>"
    ]
  },
  "features": [
    "<exact supportedFeatures token>"
  ],
  "updatePolicy": {
    "level": "RECOMMENDED",
    "requiredFeatures": []
  }
}
```

Rules:

1. `contracts.wsSchema`, `wsProtocolVersion`, and `deviceApiVersion` mirror the existing Shared Device Core/transport V1 values; no invented second Device Core schema identifier is introduced.
2. `contracts.requiredDomains` contains only domain contracts required for the base product control surface.
3. Unknown/newer required transport, device API, or domain contracts mean the Android build must not install that firmware; the user receives App Update Required.
4. `contracts.optionalDomains` may describe optional domain surfaces and never forces base-root rejection.
5. `features` is generated from the authoritative firmware product profile; it is not handwritten in Android.
6. `FEATURE_REQUIRED` must list one or more feature tokens from `features`.
7. `OPTIONAL`, `RECOMMENDED`, and `COMPATIBILITY_REQUIRED` use an empty `requiredFeatures` list.
8. Manifest signing covers these fields.
9. The Android OTA planner validates target contracts before exposing the update action.
10. Android never installs firmware that would make the currently running Android build unable to operate the product's required base contract.

Family base contracts:

- Light -> `aqualight.light.v1`
- Timer -> `aqualight.timer.v1`
- Dosing -> `aqualight.dosing.v1`
- Cooling -> `aql.cooling.v1`

WRGB Light Thermal remains an optional Light-owned contract: `aql.light-thermal.v1`.

## 8. Update-policy behavior

### OPTIONAL

No blocking UX. Settings may show an update.

### RECOMMENDED

Non-blocking badge/banner and release notes. The user may postpone.

### FEATURE_REQUIRED

Only a feature whose exact token is listed in `requiredFeatures` is blocked.

Example:

```text
device online
Light base V1 compatible

Manual      -> available
Auto        -> available
Custom      -> available
Quick Setup -> Firmware Update Required
```

### COMPATIBILITY_REQUIRED

Use only when the currently installed firmware cannot safely serve the affected base control contract.

The device remains Online if presence is proven.

Allowed rescue surfaces remain available:

- device identity/info,
- firmware status,
- OTA availability/update/recovery,
- safe account/pairing recovery where applicable.

## 9. Migration steps

### Phase A — documentation and branch isolation

- [x] Create Android migration branch from `feat/light-quick-setup-commercial-ui`.
- [x] Create firmware migration branch from `feature/smart-light-automation-plan-v2`.
- [x] Add this normative plan.

### Phase B — Android central compatibility

- [ ] Add owner-scoped `DeviceCompatibilityOperations`.
- [ ] Add central `DeviceAccessPolicy`.
- [ ] Extend typed access reasons without family-specific copies.
- [ ] Wire the compatibility instance through the owner dependency graph.
- [ ] Make every menu entry path use the same policy.
- [ ] Keep UI/ViewModels independent from transport/parser implementation types.

### Phase C — product/feature separation

- [ ] Remove optional feature/screen exact equality from product identity validation.
- [ ] Keep identity, frozen high-level capabilities, frozen physical limits, and module composition fail-closed.
- [ ] Parse known feature/screen tokens without rejecting safe unknown additive tokens.
- [ ] Project menu features/routes from authenticated runtime advertisements.
- [ ] Verify Light, Timer, Dosing, and Cooling base roots are not blocked by unrelated optional features.

### Phase D — family preparation failure taxonomy

- [ ] Light preparation maps not-connected, unsupported, invalid-data, and rejected states separately.
- [ ] Timer preparation uses the same central mapping rules.
- [ ] Cooling preparation uses the same central mapping rules.
- [ ] Dosing preparation cannot silently turn a domain refresh failure into Offline.
- [ ] Root restore/revalidation uses the same typed reasons.

### Phase E — commercial UI feedback

- [ ] Replace generic Offline rendering for non-offline reasons.
- [ ] Centralize title/message/action mapping.
- [ ] Add Turkish and English copy.
- [ ] Devices, Tank Devices, provisioning handoff, and restored roots render the same reason consistently.
- [ ] Accessibility labels remain truthful.

### Phase F — firmware release compatibility metadata

- [ ] Export authoritative `supportedFeatures[]` from the firmware product catalog for release generation.
- [ ] Add generic artifact `contracts`, `features`, and `updatePolicy` to manifest V1.
- [ ] Add release workflow input/validation for update policy.
- [ ] Keep domain runtime metadata out of Shared Device Core.
- [ ] Sign the final manifest including compatibility/update-policy fields.
- [ ] Add fail-closed release checks for all seven product environments.

### Phase G — Android OTA policy

- [ ] Parse and signature-verify the finalized manifest V1 shape.
- [ ] Validate target required contracts against the Android contract registry.
- [ ] Carry update policy into the prepared update plan.
- [ ] Do not install an app-incompatible target firmware.
- [ ] Keep normal updates non-blocking.
- [ ] Expose FEATURE_REQUIRED only to the affected feature.
- [ ] Preserve existing OTA transaction/recovery/rollback coordinator behavior.

### Phase H — regression and release gates

- [ ] Current Android + current firmware.
- [ ] New Android + current compatible firmware.
- [ ] Current Android + newer firmware with only optional/additive features.
- [ ] Current Android + firmware requiring a newer app.
- [ ] New Android + older firmware missing one optional feature.
- [ ] Domain malformed while canonical presence remains Online.
- [ ] Authentication failure.
- [ ] Local network unavailable.
- [ ] Device physically unresponsive.
- [ ] OTA rollback after failed health window.
- [ ] OTA target contract incompatible with app -> installation blocked before start.
- [ ] All seven commercial product builds pass.
- [ ] Cross-family fixture/isolation guards pass.

## 10. Required acceptance matrix

| Presence | Base contract | Optional feature | Expected root | Expected feature | User message |
|---|---|---|---|---|---|
| Online | compatible | supported | open | open | none |
| Online | compatible | missing | open | blocked | Firmware Update Required / Feature Not Available |
| Online | incompatible/newer | n/a | safe control blocked | blocked | App Update Required / Device Software Incompatible |
| Online | malformed domain | n/a | safe control blocked | blocked | Device State Could Not Be Verified |
| Offline | unknown | unknown | blocked | blocked | Device Offline |
| Local network unavailable | unknown | unknown | blocked | blocked | Local Network Unavailable |
| Auth required | unknown | unknown | blocked | blocked | Device Authentication Required |

The first row to fail this table is a release blocker.

## 11. Regression constraints

The migration must not:

- create a second DevicesRepository,
- create family-specific compatibility repositories,
- create family-specific OTA policy evaluators,
- move scheduling/topology/calibration ownership out of its domain,
- change working hardware behavior,
- change existing storage ownership,
- bypass owner/session generation barriers,
- weaken WebSocket authentication or MAC/sequence validation,
- weaken OTA signature/hash/product/hardware checks,
- reinterpret an acknowledged firmware mutation as failed because readback is delayed,
- make a missing optional feature invalidate unrelated routes.

## 12. Architecture conformance

Android implementation follows Android's recommended layered architecture:

- UI renders typed application state.
- ViewModels do not access transport/data sources directly.
- repositories/data-layer owners centralize mutable data and conflict resolution.
- shared business policy is reusable outside individual ViewModels.
- immutable state and unidirectional data flow remain the default.

Reference:
https://developer.android.com/topic/architecture
https://developer.android.com/topic/architecture/data-layer
https://developer.android.com/topic/architecture/recommendations

Firmware OTA behavior follows ESP-IDF's documented rollback model: a new OTA image is pending verification, application diagnostics decide valid/rollback, and a reset before validation rolls back when rollback is enabled.

Reference:
https://docs.espressif.com/projects/esp-idf/en/stable/esp32/api-reference/system/ota.html

## 13. Final commercial freeze checklist

The first public release may freeze V1 only when every item below is true:

- [ ] Android and firmware V1 fixtures are pinned to the exact release commits.
- [ ] All seven products pass runtime contract parity.
- [ ] Presence and compatibility are observably independent.
- [ ] No contract/parser failure is presented as Offline.
- [ ] Missing optional features do not block base family roots.
- [ ] Manifest V1 compatibility/update policy is signed and parsed by Android.
- [ ] Android refuses firmware whose required base contracts it cannot support.
- [ ] OTA rollback health test passes on physical target hardware.
- [ ] Turkish and English access/update copy has product review approval.
- [ ] Release CI and physical regression matrix are attached to the release candidate.

After this checklist is signed off, V1 is immutable. Breaking changes then require a new contract major and an explicit migration strategy.
