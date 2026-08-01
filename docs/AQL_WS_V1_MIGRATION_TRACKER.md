# AquaLight WS v1 Migration Tracker

Branch: `integration/aql-ws-v1-commercial`  
Firmware source: `AquaLight-Firmware`  
Target: **41 authenticated commands / 0 public commands**

## Current position

- Current stage: **04 — Common device commands**
- Active branch: `feat/device-04-common-commands`
- Status: **IN PROGRESS**
- Previous stage: **03 PASSED / PR #184 MERGED**
- Next: Align common device, security, network, time and firmware read paths with the correlated request broker and typed event pipeline.

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

- [x] **02** `feat/ws-02-request-broker` — **MERGED / PR #183**
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
  - [x] Android CI passed
  - [x] Emulator API 27 passed
  - [x] Emulator API 36 passed
  - [x] CodeQL passed
  - [x] Physical device test N/A: no production module command path, provisioning, UDP, transport or crypto behavior changed
  - [x] PR evidence recorded

- [x] **03** `feat/ws-03-event-routing` — **MERGED / PR #184**
  - [x] Inventoried all 11 firmware-emitted authenticated events
  - [x] Added exact typed contracts for every supported firmware event
  - [x] Routed by exact device + connection generation + module + event identity
  - [x] Rejected stale-generation events
  - [x] Preserved per-device/session isolation
  - [x] Added deterministic per-device latest-state reductions
  - [x] Preserved unmatched/legacy raw event visibility
  - [x] Added unit and repository pipeline integration tests
  - [x] Android CI, installable APK, Emulator API 27/36 and CodeQL passed
  - [x] Zero critical/high CodeQL findings
  - [x] Physical device test N/A: production module consumers, provisioning, UDP, transport and crypto unchanged
  - [x] PR evidence recorded

- [ ] **04** `feat/device-04-common-commands` — **IN PROGRESS**
  - [ ] Device: identity/status/capabilities/name
  - [ ] Security: status/pair/unpair/reset
  - [ ] Network status
  - [ ] Time: status/config/phone/NTP/RTC
  - [ ] Firmware status and OTA status read-only
  - [ ] Unpair/reset token and session lifecycle
  - [ ] Integrate production common-command paths with correlated request broker
  - [ ] Consume relevant typed events without regressing raw compatibility
  - [ ] Add unit and repository integration tests
  - [ ] Run relevant automatic and physical gates based on production behavior impact

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
| 2026-08-01 | Stage 02 correlated request broker | PASS / PR #183 MERGED | Start Stage 03 |
| 2026-08-01 | Stage 03 event routing branch opened | IN PROGRESS | Inventory firmware event contracts |
| 2026-08-01 | Stage 03 typed event routing | PASS / PR #184 MERGED | Start Stage 04 |
| 2026-08-01 | Stage 04 common command branch opened | IN PROGRESS | Inventory firmware common-command payloads and current Android consumers |
