# AquaLight WS v1 Migration Tracker

Branch: `integration/aql-ws-v1-commercial`  
Firmware source: `AquaLight-Firmware`  
Target: **41 authenticated commands / 0 public commands**

## Current position

- Current stage: **06 — Cooling runtime alignment**
- Active branch: `feat/cooling-06-runtime-alignment`
- Status: **IN PROGRESS — LIVE TEMPERATURE CONTRACT VERIFICATION**
- Previous stage: **05 PASSED / PR #186 MERGED**
- Next: Close the live temperature telemetry contract gap before implementing the two Cooling commands and production consumers.

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
  - [x] Inventoried all 11 firmware-declared authenticated events
  - [x] Added exact typed contracts for every declared firmware event
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

- [x] **04** `feat/device-04-common-commands` — **MERGED / PR #185**
  - [x] Device identity/status/capabilities/name aligned
  - [x] Security status/pair/unpair/reset aligned
  - [x] Network status aligned
  - [x] Time status/config/phone/NTP/RTC aligned
  - [x] Firmware status and OTA status read-only aligned
  - [x] Unpair/reset token and session lifecycle enforced
  - [x] Production common-command paths moved to correlated request broker
  - [x] Relevant typed events consumed without raw compatibility regression
  - [x] Unit, golden and repository integration tests added
  - [x] Android CI, installable APK, Emulator API 27/36 and CodeQL passed
  - [x] Current custom-name firmware interoperability and physical menu liveness passed
  - [x] PR evidence recorded

- [x] **05** `feat/light-05-runtime-alignment` — **MERGED / PR #186**
  - [x] Inventoried exact firmware request/response shapes for all 7 Light commands
  - [x] `light.status.get` exact typed status parser
  - [x] `light.manual.set` typed manual-level and clear outcomes
  - [x] `light.channel.regime.set` exact Auto/On/Off persistence outcome
  - [x] `light.program.apply` exact create/update outcome
  - [x] `light.program.delete` exact delete outcome
  - [x] `light.temperature-protection.status.get` exact typed status
  - [x] `light.temperature-protection.set` exact typed mutation outcome
  - [x] Production Light consumers moved from send-success to correlated firmware success
  - [x] `light.status.changed` integrated into deterministic current-state projection
  - [x] Unsupported Light operations rejected using exact firmware capabilities/features
  - [x] Unit, parser, repository and production-consumer tests added
  - [x] Android CI, installable APK, Emulator API 27/36 and CodeQL passed
  - [x] WRGB Pro Elite physical Light regression passed
  - [x] PR evidence recorded

- [ ] **06** `feat/cooling-06-runtime-alignment` — **IN PROGRESS**
  - [x] Confirm both Cooling commands: `cooling.status.get`, `cooling.config.apply`
  - [x] Confirm Cooling engine internally uses fixed temperature sensor #0
  - [x] Confirm all Cooling-capable products expose one temperature sensor
  - [x] Confirm current Cooling WS response has configuration thresholds but no live measured temperature
  - [x] Confirm `temperature.changed` is declared/routed but not currently published by firmware
  - [ ] Freeze minimal live temperature snapshot/event shape before Android implementation
  - [ ] Add live fixed-sensor temperature to firmware WS contract and golden fixtures
  - [ ] Publish validated `temperature.changed` telemetry without sentinel values
  - [ ] `cooling.status.get` exact typed status parser
  - [ ] `cooling.config.apply` exact atomic mutation outcome
  - [ ] Auto/On/Off, min/max temperature and supported fan display-name writes
  - [ ] Move production Cooling consumers from send-success to correlated firmware success
  - [ ] Integrate `cooling.status.changed` and `temperature.changed` into device-scoped current state
  - [ ] Reject unsupported Cooling operations using exact capabilities/features
  - [ ] Add unit, parser, repository and production-consumer tests
  - [ ] Run automatic gates and targeted physical Cooling-device regression

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
| 2026-08-01 | Stage 04 common runtime commands | PASS / PR #185 MERGED | Start Stage 05 |
| 2026-08-01 | Stage 05 Light runtime branch opened | IN PROGRESS | Inventory all seven firmware Light contracts and Android consumers |
| 2026-08-01 | Stage 05 Light runtime alignment | PASS / PR #186 MERGED | Start Stage 06 |
| 2026-08-01 | Stage 06 Cooling runtime branch opened | IN PROGRESS | Verify live temperature telemetry before implementation |
| 2026-08-01 | Stage 06 live temperature audit | CONTRACT GAP FOUND | Freeze and implement firmware telemetry contract first |
