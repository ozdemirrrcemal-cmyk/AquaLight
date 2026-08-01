# AquaLight WS v1 Migration Tracker

Branch: `integration/aql-ws-v1-commercial`  
Firmware source: `AquaLight-Firmware`  
Target: **41 authenticated commands / 0 public commands**

## Current position

- Current stage: **00 — Existing-system baseline**
- Active branch: `test/ws-00-runtime-baseline`
- Pull request: `#181`
- Status: **IN PROGRESS**
- Evidence: `docs/AQL_WS_V1_STAGE_00_BASELINE.md`
- Next: Run the repository's existing CI/emulator jobs, investigate only real build/test failures, then record physical-device provisioning and online/offline results.

## Fixed rules

- Every stage uses a separate branch and PR.
- A stage must pass tests before the next stage starts.
- Transport and crypto will not be rewritten.
- BLE + QR provisioning, UDP discovery and online/offline behavior must not regress.
- Stage 00 does not add a new architecture guard or alter runtime behavior.
- All firmware-supported user settings and operations must exist on Android.
- GPIO, PWM, mappings and factory identity remain read-only.

## Stage list

- [ ] **00** `test/ws-00-runtime-baseline` — **IN PROGRESS**
  - [x] Baseline evidence/checklist file added
  - [x] Accidental custom runtime guard removed
  - [x] Accidental custom guard test removed
  - [ ] Existing Android CI passes
  - [ ] Existing unit/golden/protocol tests pass
  - [ ] Emulator API 27 passes
  - [ ] Emulator API 36 passes
  - [ ] Physical provisioning smoke recorded
  - [ ] UDP discovery smoke recorded
  - [ ] WebSocket auth/metadata smoke recorded
  - [ ] Online → offline → online recorded
  - [ ] Router/phone network recovery recorded
  - [ ] App background/foreground recorded
  - [ ] Device removal/session shutdown recorded
  - [ ] Secret leakage check recorded

- [ ] **01** `chore/ws-01-contract-parity`
  - Firmware golden fixture copied byte-identically
  - Android command matrix `38 → 41`
  - Add `device.name.set`
  - Add `light.temperature-protection.status.get`
  - Add `light.temperature-protection.set`
  - Golden tests and protocol guard updated to 41

- [ ] **02** `feat/ws-02-request-broker`
  - Request ID correlation
  - Typed success/error result
  - Timeout
  - Pending-request cancellation on disconnect
  - Stale-session response rejection
  - Multi-device isolation

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

## Gate required after every stage

- [ ] Android build passes
- [ ] Unit/golden/protocol tests pass
- [ ] Provisioning regression passes
- [ ] UDP discovery passes
- [ ] WebSocket authentication passes
- [ ] Online/offline regression passes
- [ ] Reconnect regression passes
- [ ] Background/foreground passes
- [ ] Session shutdown passes
- [ ] No secret/token logging
- [ ] Physical-device smoke test recorded
- [ ] PR evidence recorded

## Progress log

| Date | Stage | Result | Next |
|---|---|---|---|
| 2026-08-01 | Tracker setup | PASS | Start Stage 00 baseline |
| 2026-08-01 | Stage 00 first CI run | INVALID | Custom guard stopped CI before the real build |
| 2026-08-01 | Stage 00 correction | IN PROGRESS | Guard/test removed; rerun existing CI only |
