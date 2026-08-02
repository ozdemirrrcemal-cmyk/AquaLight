# AquaLight WS v1 Migration Tracker

Branch: `integration/aql-ws-v1-commercial`  
Firmware source: `AquaLight-Firmware`  
Target: **41 authenticated commands / 0 public commands**

## Current position

- Current stage: **10 — COMPLETE / firmware interoperability and final matrix**
- Active branch: `integration/aql-ws-v1-commercial`
- Status: **STAGE 10 AUTOMATED PASS / PR #191 MERGED — PHYSICAL SIGNED-OTA FOLLOW-UP PENDING**
- Completed sequence: **Stages 00–10 merged into integration**
- Next: Promote the completed integration branch to `main`, then start `feat/ota-update-ui` from the exact promoted SHA.

## Fixed rules

- Every implementation stage uses a separate branch and PR.
- A stage must pass its relevant tests before the next stage starts.
- Transport and crypto will not be rewritten.
- BLE + QR provisioning, UDP discovery and online/offline behavior must not regress.
- Physical regression tests are required when the related production runtime path changes or at release-candidate gate.
- All firmware-supported user settings and operations must exist on Android.
- GPIO, PWM, mappings and factory identity remain read-only.
- Runtime alignment stages prepare application/data contracts first; screen binding is performed only in the dedicated UI stage.

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

- [x] **06** `feat/cooling-06-runtime-alignment` — **MERGED / PR #187**
  - [x] Confirm both Cooling commands: `cooling.status.get`, `cooling.config.apply`
  - [x] Align with firmware PR #26 / commit `38e8812c1bcecf948ebab85979bff21a24f4b79c`
  - [x] Freeze exact four-field live temperature snapshot/event shape
  - [x] Copy the live temperature golden fixture into Android protocol evidence
  - [x] `cooling.status.get` exact typed status parser
  - [x] `cooling.config.apply` exact atomic mutation outcome
  - [x] Auto/On/Off, min/max temperature and supported fan display-name writes
  - [x] Expose correlated Cooling repository and module provider in the data/runtime layer
  - [x] Integrate `cooling.status.changed` and `temperature.changed` into device-scoped runtime state
  - [x] Clear Cooling runtime state on reconnect/generation changes
  - [x] Reject unsupported Cooling operations using exact capabilities/features
  - [x] Reject stale/duplicate events and older status responses
  - [x] Clear stale valid temperature on completed invalid CRC/OneWire samples
  - [x] Handle ESP32 32-bit `millis()` wraparound in freshness ordering
  - [x] Keep GPIO, PWM, fan mapping, sensor mapping and calibration read-only
  - [x] Add unit, parser, repository, capability and reducer tests
  - [x] Keep Cooling layout, ViewModel, UI state and presentation strings unchanged
  - [x] Android CI — run #2908
  - [x] Installable Debug APK — run #200
  - [x] Emulator API 27 / 36 — run #1032
  - [x] CodeQL Security Scan — run #4693
  - [x] PR evidence recorded on PR #187 for tested head `355b00b6ba9dc4e5e721dc8c30aa4b757a928092`
  - [x] Physical Cooling screen regression N/A: no production UI consumer is changed in this Stage

- [x] **07** `feat/timer-07-runtime-alignment` — **MERGED / PR #188**
  - [x] Confirm and mirror all 3 Timer commands: `timer.status.get`, `timer.config.apply`, `timer.channel.set`
  - [x] Align with firmware PR #26 / commit `38e8812c1bcecf948ebab85979bff21a24f4b79c`
  - [x] Add fail-closed status, config-mutation and channel-mutation parsers with exact response keys and literals
  - [x] Support exact Auto/On/Off channel state and clearable channel display-name overrides
  - [x] Model schedule create/update/delete as firmware-compatible full-list replacement, including an empty delete-all payload
  - [x] Reject invalid weekday, interval, repeat, channel-binding, derived-time and standalone `amountMl` values
  - [x] Move production Timer commands from send-success to the correlated request broker
  - [x] Expose device-scoped Timer status/config state through the runtime module provider
  - [x] Reduce `timer.status.changed` snapshot and command-result events deterministically
  - [x] Reject stale/duplicate status snapshots and handle ESP32 32-bit `millis()` wraparound
  - [x] Clear Timer runtime state on reconnect/generation changes
  - [x] Gate Timer access using exact family, capability, limit, feature, screen and module metadata
  - [x] Require Timer status/config channel counts to match authenticated product metadata
  - [x] Prevent dosing products with the internal timer engine from exposing or reducing the standalone Timer API
  - [x] Keep GPIO, LEDC/PWM, mapping and hardware calibration fields read-only
  - [x] Add parser, mutation, capability, repository and typed-event reducer tests (25 local Timer contract tests passed)
  - [x] WebSocket protocol, commercial catalog runtime and device catalog parity guards passed locally
  - [x] Primary Detekt and zero-new-advisory-debt checks passed locally for the Stage 07 change set
  - [x] Keep Timer layout, ViewModel, UI state and presentation resources unchanged
  - [x] Android CI — run #2910
  - [x] Installable Debug APK — run #202
  - [x] Emulator API 27 / 36 — run #1034
  - [x] CodeQL Security Scan — run #4695
  - [x] PR evidence recorded on PR #188 for tested head `e6cfd0acb327f17a4b18912ce77a11a411e213a4`
  - [x] Physical Timer screen regression N/A: no production UI consumer is changed in this Stage
  - [x] Squash-merged to integration as `b01c4af07f9edbbbae85bf9e134d1509d10a7730`

- [x] **08** `feat/dosing-08-runtime-alignment` — **MERGED / PR #189**
  - [x] Confirm and mirror all 11 authenticated Dosing commands from current firmware
  - [x] Audit exact firmware files `AqlDosingCommands.hpp` (`1d84bc0eaadb77f9041978c2ce46c7042c158009`) and `AqlTimerService.hpp` (`ca37e6722e4e9d214e5efd6fc089d5e64db2490a`)
  - [x] Add fail-closed parsers for the exact status and all 10 mutation result schemas
  - [x] Move production Dosing commands from send-success to the correlated request broker
  - [x] Support prime start/stop and calibrated manual dose start/stop
  - [x] Support calibration start, finish, confirm and cancel with pending/persisted semantics
  - [x] Support reservoir refill/tracking with exact before/after/capacity/persistence echoes
  - [x] Support channel display name, regime, dosing calibration and reservoir config writes
  - [x] Model schedule create/update/delete as full-list replacement, including empty delete-all
  - [x] Expose device-scoped Dosing status/config/mutation state through the runtime provider
  - [x] Reduce `dosing.status.changed` snapshot and command-result events deterministically
  - [x] Reject stale/duplicate status snapshots and handle ESP32 32-bit `millis()` wraparound
  - [x] Clear Dosing runtime state on reconnect/generation changes
  - [x] Gate each operation using exact family, capabilities, limits, features, screens and modules
  - [x] Prevent standalone Timer products from exposing or reducing the Dosing API
  - [x] Keep GPIO, LEDC/PWM, channel mapping and hardware calibration fields read-only
  - [x] Add parser, mutation, capability, repository and typed-event reducer tests (29 local Dosing contract tests passed)
  - [x] Repository Python suite passed (138 tests) and all architecture/protocol/catalog guards passed locally
  - [x] Primary Detekt and zero-new-advisory-debt policy passed locally
  - [x] Keep Dosing screens, ViewModels, UI state and presentation resources unchanged
  - [x] Android CI — run #2912
  - [x] Installable Debug APK — run #204
  - [x] Emulator API 27 / 36 — run #1036
  - [x] CodeQL Security Scan — run #4697
  - [x] PR evidence recorded on PR #189 for tested head `63e0cfd3d30566cf6f6c9eedc0ff73f7365e6ef2`
  - [x] Physical Dosing screen regression N/A: no production UI consumer is changed in this Stage
  - [x] Squash-merged to integration as `fe2e7da20b7fb83dc6f4e5ea6e694979fe8f3da3`

- [x] **09** `feat/ota-09-update-orchestration` — **MERGED / PR #190**
  - [x] Audit current firmware commit `38e8812c1bcecf948ebab85979bff21a24f4b79c`
  - [x] Freeze exact `AqlFirmwareCommands.hpp` (`8b1107d`) start/status/clear responses
  - [x] Freeze exact `AqlOtaService.hpp` (`7150082`) lifecycle and restart semantics
  - [x] Freeze exact `AqlRealtimeServer.cpp` (`a0caaeb`) progress/completed event payloads
  - [x] Preserve fail-closed manifest fetch, ECDSA signature and signed payload-hash verification
  - [x] Select exactly one artifact by channel, environment, product, family, line, model and hardware revision
  - [x] Validate target version, HTTPS release URL, SHA-256 and binary size before dispatch
  - [x] Move OTA start/status/clear from send-success to the correlated request broker
  - [x] Parse exact start acceptance, status snapshot, compact clear-previous and cleared snapshot schemas
  - [x] Consume exact typed `firmware.ota.progress`, `firmware.ota.completed` and `system.restarting` events
  - [x] Reject stale-generation, wrong-device, unsolicited and malformed OTA events
  - [x] Serialize concurrent starts per device and reject duplicate active operations
  - [x] Validate firmware request echo and every active/terminal snapshot against the selected plan
  - [x] Trigger UDP foreground rediscovery and device-scoped WebSocket runtime replacement after restart
  - [x] Preserve restart recovery when the terminal event is missed and recover through correlated status
  - [x] Require new authenticated metadata generation and exact product/hardware identity after restart
  - [x] Verify the reconnected firmware version exactly equals the selected target before success
  - [x] Keep Settings screens, ViewModels, UI state and presentation resources unchanged
  - [x] OTA parser/planner/coordinator/application tests passed locally (26 tests)
  - [x] Runtime module provider and application adapter wiring compiled independently
  - [x] Repository Python suite passed (138 tests) and all architecture/protocol/catalog guards passed locally
  - [x] Primary Detekt and zero-new-advisory-debt policy passed locally
  - [x] Android CI — run #2914
  - [x] Installable Debug APK — run #206
  - [x] Emulator API 27 / 36 — run #1038
  - [x] CodeQL Security Scan — run #4699
  - [ ] Physical signed-OTA smoke: download, verify, restart, rediscover, reconnect and version proof — carried into the Stage 10 final physical gate
  - [x] PR evidence recorded on PR #190 for tested head `c880207839fac8a119acb08bd82cd420128156cd`
  - [x] Squash-merged to integration as `4347adac8dfc5ebf07976bd7570c3de823eae22f`

- [x] **10** `test/ws-10-firmware-interoperability` — **MERGED / PR #191 / AUTOMATED GATES PASSED**
  - [x] Audit firmware commit `38e8812c1bcecf948ebab85979bff21a24f4b79c`
  - [x] Pin command names, event contract, all ten command handlers and the nested Timer/Dosing and pairing request parsers by firmware blob SHA
  - [x] Verify exact 41 authenticated / 0 public command parity
  - [x] Classify all 41 commands as 18 payloadless and 23 payload-bearing commands
  - [x] Cover every payload-bearing command with its exact Android request serializer
  - [x] Verify exact 11/11 firmware-event parity against the typed Android event enum
  - [x] Reject whitespace- and case-normalized command/event identities fail-closed
  - [x] Verify all runtime serializer key sets against the pinned request-field matrix
  - [x] Keep GPIO, PWM/LEDC, mappings, polarity, grouping, factory calibration and profile-managed fields non-editable
  - [x] Keep immutable product identity non-editable except the exact OTA compatibility echo allowlist
  - [x] Verify WebSocket and Cooling telemetry golden fixtures byte-identically against firmware blobs
  - [x] Verify the firmware-exported product catalog checksum, all four families and all nine commercial SKUs
  - [x] Add the final interoperability guard to Android CI, CodeQL and the protected release-quality gate
  - [x] Repository Python suite passed locally (139 tests)
  - [x] Stage policy plus all Android CI architecture/protocol/privacy/design/localization guards passed locally
  - [x] Android unit/golden/interoperability tests — Android CI run #2917
  - [x] Android CI — run #2917
  - [x] Installable Debug APK — run #209
  - [x] Emulator API 27 / 36 — run #1041
  - [x] CodeQL Security Scan — run #4702 with zero new Detekt debt
  - [ ] Physical signed-OTA release-candidate smoke: download, verify, restart, rediscover, reconnect and exact version proof — post-merge commercial-device follow-up
  - [x] PR evidence recorded on PR #191 for tested head `cd7a0500022f1cd201dd6936b2158c6ff9b11563`
  - [x] Squash-merged to integration as `8b0fbfc7fe85063f4e57a69b5e5b879768521f9a`

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
| 2026-08-02 | Stage 06 UI scope correction | PASS | Keep layout, ViewModel and presentation resources unchanged; validate runtime layer only |
| 2026-08-02 | Stage 06 Cooling runtime alignment | PASS / PR #187 MERGED | Start Stage 07 |
| 2026-08-02 | Stage 07 Timer runtime alignment | PASS / PR #188 MERGED | Start Stage 08 |
| 2026-08-02 | Stage 08 Dosing runtime branch opened | IN PROGRESS | Freeze all 11 firmware contracts |
| 2026-08-02 | Stage 08 Dosing runtime implementation | LOCAL GATES PASSED | Publish branch and complete PR CI evidence |
| 2026-08-02 | Stage 08 Dosing pull-request gates | PASS / PR #189 READY | Request explicit merge approval |
| 2026-08-02 | Stage 08 Dosing runtime alignment | PASS / PR #189 MERGED | Start Stage 09 |
| 2026-08-02 | Stage 09 OTA orchestration branch opened | IN PROGRESS | Freeze firmware OTA and restart contracts |
| 2026-08-02 | Stage 09 OTA orchestration implementation | LOCAL GATES PASSED | Publish PR and complete remote evidence |
| 2026-08-02 | Stage 09 OTA orchestration pull-request gates | PASS / PR #190 READY | Squash-merge now; run physical signed-OTA smoke afterwards |
| 2026-08-02 | Stage 09 OTA update orchestration | PASS / PR #190 MERGED | Start Stage 10 final interoperability matrix |
| 2026-08-02 | Stage 10 firmware interoperability branch opened | IN PROGRESS | Pin firmware request sources and build the final 41/41 matrix |
| 2026-08-02 | Stage 10 final interoperability implementation | LOCAL CONTRACT GATES PASSED | Publish draft PR and complete remote plus physical evidence |
| 2026-08-02 | Stage 10 final interoperability pull-request gates | AUTOMATED PASS / PR #191 READY | Squash-merge now; run physical signed-OTA smoke afterwards |
| 2026-08-02 | Stage 10 firmware interoperability | AUTOMATED PASS / PR #191 MERGED | Promote completed integration branch to `main`; retain physical signed-OTA follow-up |
