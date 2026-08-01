# AquaLight WS v1 Migration Tracker

Branch: `integration/aql-ws-v1-commercial`  
Firmware source: `AquaLight-Firmware`  
Target: **41 authenticated commands / 0 public commands**

## Current position

- Current stage: **02 — Correlated request broker**
- Active branch: `feat/ws-02-request-broker`
- Status: **IN PROGRESS**
- Previous stage: **01 PASSED / PR #182 MERGED**
- Next: Inspect the current client/session lifecycle, add request correlation without rewriting transport or crypto, then run the complete automatic gate.

## Fixed rules

- Every implementation stage uses a separate branch and PR.
- A stage must pass its relevant tests before the next stage starts.
- Transport and crypto will not be rewritten.
- BLE + QR provisioning, UDP discovery and online/offline behavior must not regress.
- Physical regression tests are required when the related runtime behavior changes or at release-candidate gate.
- All firmware-supported user settings and operations must exist on Android.
- GPIO, PWM, mappings and factory identity remain read-only.

## Stage list

- [x] **00** `test/ws-00-runtime-baseline` — **MERGED**
  - [x] Existing Android CI, API 27/36 emulator and CodeQL passed
  - [x] Runtime/provisioning code diff: none

- [x] **01** `chore/ws-01-contract-parity` — **MERGED / PR #182**
  - [x] Firmware golden fixture copied byte-identically
  - [x] Android command matrix `38 → 41`
  - [x] Added `device.name.set`
  - [x] Added `light.temperature-protection.status.get`
  - [x] Added `light.temperature-protection.set`
  - [x] Golden test and protocol guard updated to 41
  - [x] Android CI passed
  - [x] Emulator API 27 passed
  - [x] Emulator API 36 passed
  - [x] CodeQL passed
  - [x] Provisioning/runtime/transport/crypto/presence code unchanged

- [ ] **02** `feat/ws-02-request-broker` — **IN PROGRESS**
  - [ ] Request ID correlation
  - [ ] Typed success/error result
  - [ ] Bounded timeout
  - [ ] Pending-request cancellation on disconnect/shutdown
  - [ ] Stale-session response rejection
  - [ ] Wrong module/action rejection
  - [ ] Duplicate response rejection
  - [ ] Multi-device isolation
  - [ ] Existing metadata bootstrap remains functional
  - [ ] Android CI/API 27/API 36/CodeQL pass

- [ ] **03** `feat/ws-03-event-routing`
  - Typed routing for all firmware events
  - Per-device/session isolation
  - Stale-event rejection
  - Module state updates

- [ ] **04** `feat/device-04-common-commands`
  - Device: identity/status/capabilities/name
  - Security: status/pair/unpair/reset
  - Network status
  - Time: status/config/phone/NTP/RTC
  - Firmware status and OTA status read-only
  - Unpair/reset token and session lifecycle

- [ ] **05** `feat/light-05-runtime-alignment`
  - 7/7 light commands
  - Manual level and clear
  - Channel Auto/On/Off
  - Program create/update/delete
  - Temperature protection status/set
  - `light.status.changed`

- [ ] **06** `feat/cooling-06-runtime-alignment`
  - 2/2 cooling commands
  - Auto/On/Off
  - Minimum/maximum temperature
  - Supported fan display names
  - Atomic config apply
  - `cooling.status.changed`

- [ ] **07** `feat/timer-07-runtime-alignment`
  - 3/3 timer commands
  - Channel state/name
  - Schedule create/update/delete
  - `timer.status.changed`
  - Dosing products must not expose timer API

- [ ] **08** `feat/dosing-08-runtime-alignment`
  - 11/11 dosing commands
  - Prime and manual dose
  - Full calibration workflow
  - Reservoir refill/tracking
  - Schedule/config
  - `dosing.status.changed`

- [ ] **09** `feat/ota-09-update-orchestration`
  - Manifest fetch/signature verification
  - Product/hardware/version/SHA/size validation
  - OTA start/progress/completed
  - Restart, UDP rediscovery and WebSocket reconnect
  - New firmware version verification

- [ ] **10** `test/ws-10-firmware-interoperability`
  - 41/41 command parity
  - All firmware-event parity
  - All user-write fields covered
  - No hardware-owned editable fields
  - Golden fixtures byte-identical
  - All product families and nine SKUs checked before release

## Relevant gate after each stage

- [ ] Android build passes
- [ ] Unit/golden/protocol tests pass
- [ ] Changed module tests pass
- [ ] Provisioning/UDP/WebSocket/presence regressions pass when those areas are affected
- [ ] Physical-device smoke passes when runtime behavior is affected
- [ ] No secret/token logging
- [ ] PR evidence recorded

## Progress log

| Date | Stage | Result | Next |
|---|---|---|---|
| 2026-08-01 | Tracker setup | PASS | Start Stage 00 baseline |
| 2026-08-01 | Stage 00 existing CI baseline | PASS / MERGED | Start Stage 01 |
| 2026-08-01 | Stage 01 contract parity | PASS / PR #182 MERGED | Start Stage 02 |
| 2026-08-01 | Stage 02 branch opened | IN PROGRESS | Inspect request/session lifecycle |
