# AquaLight Dosing Production Cutover Tracker

Android baseline branch: `agent/dosing-channel-card-fixture-preview`

Android baseline commit: `d11699d6c18284f2b924db2e7b1a2be0d3605267`

Cutover branch: `agent/dosing-production-cutover`

Firmware source: `ozdemirrrcemal-cmyk/AquaLight-Firmware`

Firmware contract commit: `c77d191398b4bca1d24be99699d1a8fe17ac3dfb`

Target: replace the fail-closed Dosing preview boundaries with one central, firmware-aligned,
commercial production data path without introducing a second state owner or bypassing shared
device runtime and notification infrastructure.

## Current position

- Current stage: **05 — Calibration rejection semantics**
- Status: **READY FOR CHECK**
- Production wiring: **DISABLED**
- Rule: stages remain atomic and independently evidenced; the owner authorized uninterrupted
  execution of Stages 02 and 03 on this branch.

## Fixed rules

- Dosing-specific application code lives below `application/devices/dosing/`.
- Dosing-specific data code lives below `data/devices/dosing/`.
- Dosing UI lives below `ui/tabs/devices/detail/dosing/` and is grouped by screen/feature.
- The shared closed device-slot/catalog algebra may contain the Dosing slot identity/shape variant;
  it must not own Dosing screen, runtime, mutation or validation behavior.
- Shared, family-neutral device primitives may remain directly below `application/devices/` or
  `data/devices/`; they must not own Dosing behavior.
- UI never imports the Dosing data layer, runtime modules, wire requests, wire responses, JSON,
  firmware actions, channel keys, revisions or firmware enums.
- Application never imports UI or the Dosing v1 wire package.
- Data maps the firmware contract into application-owned semantic models.
- Firmware-authoritative values are never recomputed in UI.
- Product policies and validation thresholds are never owned by UI.
- `lowLevelActive` is the firmware-authoritative low-reservoir notification signal.
- Supply projection severity is an independent application-owned product projection.
- Device-alert delivery uses the central `NotificationDispatchUseCase`; no feature-specific
  notification store, channel registry or direct Android notification path may be introduced.
- Mutations use firmware `expectedRevision`; stale conflicts are refreshed and surfaced without a
  blind mutation retry.
- Production composition remains fail-closed until every screen and the final cutover gate pass.
- Each stage is implemented, tested, reviewed and checked before the next stage begins.

## Stage list

- [x] **01 — Architecture and package boundaries** — **COMPLETE**
  - [x] Inventory every Dosing-specific application, data and UI production source.
  - [x] Move Dosing-specific sources below their canonical Dosing package roots.
  - [x] Group the Dosing root UI below an explicit `root/` package.
  - [x] Preserve shared family-neutral device catalog/slot primitives outside Dosing packages only
        when they contain no Dosing behavior.
  - [x] Add an executable architecture guard for package placement and dependency direction.
  - [x] Prove UI has no direct data/runtime/wire dependency.
  - [x] Prove application has no UI/data/v1-wire dependency.
  - [x] Keep runtime behavior and production composition unchanged.
  - [x] Pass the focused architecture tests, repository Python guards and `git diff --check`.
  - [x] Pass Android compile/unit CI on the cutover branch.

### Stage 01 evidence

- `python3 tools/dosing_architecture_guard.py`: passed.
- `python3 tools/architecture_guard.py`: passed.
- `python3 tools/device_root_application_boundary_guard.py`: passed.
- `python3 -m unittest discover -s tools/tests -p 'test_*.py'`: 163 tests passed.
- `git diff HEAD --check`: passed.
- Dependency-direction checks reject both imports and fully-qualified UI/application/data layer
  references; focused negative tests cover all three forbidden directions.
- Published Stage 01 tree: `a0a32864e2bc99d73ec03fde94b81009fce30cfe`.
- Android CI, CodeQL, API 27/API 36 emulator integration, installable debug APK and Firebase
  production-config guard passed at remote commit `4e9aabc58ee8993843dfc0b2c8e8a4fc8de14ec3`.

- [x] **02 — Firmware-compatible display-name semantics** — **COMPLETE**
  - [x] Move display-name validation to an application-owned Dosing policy.
  - [x] Enforce the firmware limit of 32 UTF-8 bytes, not 32 UI characters.
  - [x] Cover Turkish characters, emoji, combining characters, control characters and trimming.
  - [x] UI renders semantic validation results without knowing the firmware byte limit.

### Stage 02 evidence

- Firmware pin confirms ASCII-whitespace-trimmed display names are bounded to 32 UTF-8 bytes and
  reject the firmware byte-level C0/DEL controls while permitting valid C1 UTF-8 bytes.
- `DeviceDosingDisplayNamePolicyTest` covers Turkish, emoji, combining sequences, controls,
  trimming and exact byte boundaries.
- Calibration presentation retains the complete user draft and maps application rejection reasons
  to semantic UI errors; no byte count or firmware limit is present in UI.
- Repository architecture guards and 163 Python guard tests pass.
- Android compile/unit execution is included in the final Stage 03 branch CI gate.

- [x] **03 — Exact reservoir-capacity semantics** — **COMPLETE**
  - [x] Replace presentation-owned positive `Double` validation with an application policy.
  - [x] Enforce exact 0.001 ml quanta and the firmware unsigned 32-bit amount range.
  - [x] Preserve locale-aware text entry without using floating-point values as persisted intent.
  - [x] UI renders semantic precision/range errors without knowing firmware quanta.

### Stage 03 evidence

- The pinned firmware contract stores dosing amounts in 0.001 ml `uint32` quanta; the application
  policy accepts only exact values in that range and retains the intent as microlitres.
- `DeviceDosingReservoirCapacityPolicyTest` covers English, Turkish and Arabic numeric input,
  locale grouping ambiguity, bounded raw input, trailing zeros, sub-quantum precision, the exact
  unsigned maximum and semantic rejection cases.
- The shared text-input surface has a platform-safe default bound, while the smaller reservoir
  parser bound remains private to the application policy and is checked before allocation.
- Reservoir draft state and `Bundle` recreation persist `Long` microlitres; no `Double` parsing or
  floating-point persisted intent remains in the reservoir UI path.
- `DosingDraftViewModelBoundaryTest` proves exact locale input and recreation retention.
- `DosingUiLayerBoundaryTest` proves precision/range constants and decimal parsing stay outside UI.
- Repository architecture guards, XML parsing, 163 Python guard tests and `git diff --check` pass.
- Android compile/unit execution is the final branch CI gate before hand-off.

- [x] **04 — Reservoir alarm and supply-projection separation** — **COMPLETE**
  - [x] Keep firmware `lowLevelActive` as the sole low-reservoir alert signal.
  - [x] Move remaining-day projection and 10/20-day thresholds into application policy.
  - [x] Publish application-owned `supplySeverity` for UI color mapping.
  - [x] Prove projected card severity and firmware low-level alert transitions are independent.
  - [x] Remove firmware low-level fields and product thresholds from UI contracts.
  - [x] Pass Android compile/unit CI on the cutover branch.

### Stage 04 evidence

- Pinned firmware `AqlDosingReservoirPolicy.hpp` derives the authoritative low-level signal from
  exact canonical reservoir quanta at the 10% boundary and publishes it as `lowLevelActive`;
  Android does not recompute that alarm.
- `DeviceDosingSupplyProjectionPolicy` owns weekday-aware remaining-day projection and the private
  10/20-day product thresholds, publishing `DeviceDosingSupplySeverity` for presentation mapping.
- Application policy tests prove critical projection with the firmware alarm inactive, normal
  projection with the firmware alarm active, uncertainty handling and exact 10/20-day boundaries.
- Dosing UI contains neither `lowLevelActive` nor supply thresholds; the executable architecture
  guard rejects future firmware low-level signal ownership in UI.
- Production composition and central notification delivery remain unchanged; notification dispatch
  is still reserved for Stage 11.
- Repository guards, 163 Python guard tests and `git diff --check` passed locally.
- Android CI, CodeQL, API 27/API 36 emulator integration, installable debug APK and Firebase
  production-config guard passed at remote commit `9254d1bf20d341e99e5c52ce4ef83cf1846c1c00`.

- [ ] **05 — Calibration rejection semantics** — **READY FOR CHECK**
  - [x] Represent connection, storage, physical hardware, operation-in-progress, trusted-time,
        calibration-state and invalid-measurement outcomes in the application boundary.
  - [x] Map the pinned firmware code, field and message contract to semantic application results.
  - [x] Fold verification preconditions and stale revision recovery into calibration-state mismatch
        without exposing a separate revision or verification error to UI.
  - [x] Keep unsupported, protocol and unrecognized outcomes as an internal fail-closed fallback;
        do not expose an unavailable calibration failure to application or UI.
  - [x] Prevent safety, state, storage and hardware rejections from being presented as connection
        failures.
  - [x] Keep firmware error codes, fields and messages outside UI.
  - [ ] Pass Android compile/unit CI on the cutover branch.

### Stage 05 evidence

- `DeviceDosingCalibrationFailure` is the closed application-owned semantic result set; there is no
  calibration-specific revision-conflict, verification-required or unavailable variant.
- `DeviceDosingCalibrationFailureMapper` pins the firmware `code + field + message` contract. The
  firmware's trusted-time requirement maps to `DEVICE_TIME_NOT_READY`, busy safety gates map to
  `OPERATION_IN_PROGRESS`, verification/no-pending/stale-revision outcomes map to
  `CALIBRATION_STATE_MISMATCH`, and measured-volume rejection maps to `INVALID_MEASUREMENT`.
- Only the exact physical output-start failure maps to `HARDWARE`; firmware values transported as
  `HARDWARE_ERROR` for runtime-not-ready/internal failures remain in the internal fallback.
- Unsupported/new protocol outcomes and the fail-closed production placeholder resolve to the
  internal generic failure; neither application nor UI publishes an unavailable calibration state.
- UI maps application semantics to localized messages and contains no firmware error identity.
- Focused mapper and presentation tests cover every public semantic failure plus the internal
  fallback.
- Dosing/general/device-root architecture guards, 164 Python guard tests, English/Turkish resource
  XML parsing and `git diff --check` pass locally; Android CI is the remaining Stage 05 check gate.

- [ ] **06 — Central Dosing state, revision and invalidation adapter**
  - [ ] Implement one device/channel-scoped Dosing state owner below `data/devices/dosing/`.
  - [ ] Map stable application slot ids to firmware channel keys inside data.
  - [ ] Combine global status, channel status and occurrence progress deterministically.
  - [ ] Map v1 wire models to application channel/calibration snapshots.
  - [ ] Serialize channel mutations and use authoritative `expectedRevision`.
  - [ ] Reject stale responses by connection generation, request generation and revision.
  - [ ] Refresh and surface revision conflicts without blind mutation retry.
  - [ ] Consume `dosing.status.changed` as real state invalidation.
  - [ ] Add adapter, stale-response, conflict and reconnect tests.

- [ ] **07 — Root screen read path and navigation**
  - [ ] Bind root cards, pump state, channel names, usage, progress and reservoir projections.
  - [ ] Resolve channel navigation from current authoritative calibration state.
  - [ ] Keep catalog values as bootstrap-only presentation.
  - [ ] Verify two- and four-channel products and reconnect behavior.

- [ ] **08 — Calibration screen cutover**
  - [ ] Bind naming, prime, calibration run, measurement, verification and confirmation.
  - [ ] Use the firmware 5-second calibration run and 4 ml pending-calibration verification dose.
  - [ ] Preserve prime timeout, host-stop and exit cleanup ordering.
  - [ ] Verify recovery after recreation, reconnect and interrupted verification.

- [ ] **09 — Channel detail cutover**
  - [ ] Bind missed-dose recovery through full-program mutation.
  - [ ] Bind manual dose start/stop and active-run state.
  - [ ] Bind channel reset with revision checked before any destructive consequence.
  - [ ] Render actionable semantic failures and authoritative refreshed state.

- [ ] **10 — Dosing plan cutover**
  - [ ] Bind Single, Hourly 24, Custom Periods and Timer modes.
  - [ ] Preserve Monday-through-Sunday ordering and firmware-published scheduling limits.
  - [ ] Bind enabled state, recurrence and missed-dose recovery in one program intent.
  - [ ] Verify round-trip status/apply/status equality and conflict behavior.

- [ ] **11 — Reservoir and central low-level notification cutover**
  - [ ] Load and save tracking/capacity from authoritative channel state.
  - [ ] Show remaining volume and bind reservoir refill.
  - [ ] Persist channel-level low-level alert intent outside UI.
  - [ ] Detect the authoritative `lowLevelActive` false-to-true transition with durable deduplication.
  - [ ] Gate dispatch by channel intent, owner preference and Android delivery readiness.
  - [ ] Dispatch exclusively through `NotificationDispatchUseCase`.
  - [ ] Verify process recreation, reconnect, alert reset and repeated-low-state behavior.

- [ ] **12 — Production composition and commercial acceptance**
  - [ ] Replace all production `UnavailableDeviceDosing*` bindings with central adapters.
  - [ ] Set the pinned Dosing contract `productionWiring` evidence to true.
  - [ ] Add a guard that forbids fail-closed Dosing bindings in production composition.
  - [ ] Keep debug fixtures isolated from production state ownership.
  - [ ] Pass Android CI, API 27/API 36 emulator, installable APK and CodeQL.
  - [ ] Pass physical-device status, calibration, manual dose, program, reservoir and notification
        acceptance without changing transport, crypto, provisioning or firmware-owned behavior.
  - [ ] Grant merge approval only after all automated and physical gates are recorded.
