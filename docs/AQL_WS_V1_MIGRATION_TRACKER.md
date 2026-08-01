# AquaLight WS v1 Migration Tracker

Branch: `integration/aql-ws-v1-commercial`  
Firmware source: `AquaLight-Firmware`  
Target: **41 authenticated commands / 0 public commands**

## Current position

- Current stage: **02 — Correlated request broker**
- Active branch: `feat/ws-02-request-broker`
- Status: **READY FOR TEST**
- Previous stage: **01 PASSED / PR #182 MERGED**
- Next: Run Android CI, API 27/36 emulator integration and CodeQL; fix only failures caused by the request-broker scope.

## Fixed rules

- Every implementation stage uses a separate branch and PR.
- A stage must pass its relevant tests before the next stage starts.
- Transport and crypto will not be rewritten.
- BLE + QR provisioning, UDP discovery and online/offline behavior must not regress.
- Physical regression tests are required when the related production runtime path changes or at release-candidate gate.
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
  - [x] Android CI/API 27/API 36/CodeQL passed
  - [x] Provisioning/runtime/transport/crypto/presence code unchanged

- [ ] **02** `feat/ws-02-request-broker` — **READY FOR TEST**
  - [x] Exact request correlation: device + generation + message ID
  - [x] Typed success, firmware error and protocol error outcomes
  - [x] Bounded timeout: 1–30 seconds, default 8 seconds
  - [x] Pending requests cancelled on disconnect, route replacement, close and shutdown
  - [x] Old-generation response rejection
  - [x] Wrong module/action rejection
  - [x] Duplicate/late response rejection
  - [x] Same message ID isolated across devices/generations
  - [x] Single existing transport event collector preserved
  - [x] Matched broker responses do not leak to legacy event consumers
  - [x] Unmatched events and metadata bootstrap responses remain observable
  - [x] WebSocket transport/codec/crypto unchanged
  - [x] Unit and repository integration tests added
  - [ ] Android CI passes
  - [ ] Emulator API 27 passes
  - [ ] Emulator API 36 passes
  - [ ] CodeQL passes
  - [ ] PR evidence recorded

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
- [ ] Physical-device smoke passes when a production runtime behavior is affected
- [ ] No secret/token logging
- [ ] PR evidence recorded

## Progress log

| Date | Stage | Result | Next |
|---|---|---|---|
| 2026-08-01 | Tracker setup | PASS | Start Stage 00 baseline |
| 2026-08-01 | Stage 00 existing CI baseline | PASS / MERGED | Start Stage 01 |
| 2026-08-01 | Stage 01 contract parity | PASS / PR #182 MERGED | Start Stage 02 |
| 2026-08-01 | Stage 02 branch opened | IN PROGRESS | Inspect request/session lifecycle |
| 2026-08-01 | Stage 02 broker implementation | READY FOR TEST | Run full automatic gate |
