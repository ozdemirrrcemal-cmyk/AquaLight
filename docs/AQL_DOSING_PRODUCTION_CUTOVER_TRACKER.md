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

- Current stage: **12 — Production composition and commercial acceptance**
- Status: **IMPLEMENTED — AUTOMATED AND PHYSICAL ACCEPTANCE PENDING**
- Production wiring: **ENABLED**
- Rule: merge approval remains blocked until Stage 12 automated gates and real-device acceptance are
  recorded. Dosing has no debug, smoke or fixture runtime path after this cutover.

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
- Production composition resolves Dosing only through the central v1 adapter/runtime; fail-closed
  Dosing bindings and Dosing runtime fixtures are forbidden after Stage 12.
- Each stage is implemented, tested, reviewed and checked before merge approval is granted.

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

- [x] **05 — Calibration rejection semantics** — **COMPLETE**
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
  - [x] Pass Android compile/unit CI on the cutover branch.

### Stage 05 evidence

- `DeviceDosingCalibrationFailure` is the closed application-owned semantic result set; there is no
  calibration-specific revision-conflict, verification-required or unavailable variant.
- `DeviceDosingCalibrationFailureMapper` pins the firmware `code + field + message` contract. The
  firmware's trusted-time requirement maps to `DEVICE_TIME_NOT_READY`, busy safety gates map to
  `OPERATION_IN_PROGRESS`, verification/no-pending/stale-revision outcomes map to
  `CALIBRATION_STATE_MISMATCH`, and measured-volume rejection maps to `INVALID_MEASUREMENT`.
- Only the exact physical output-start failure maps to `HARDWARE`; firmware values transported as
  `HARDWARE_ERROR` for runtime-not-ready/internal failures remain in the internal fallback.
- Unsupported/new protocol outcomes resolve to the internal generic failure; neither application nor
  UI publishes an unavailable calibration state.
- UI maps application semantics to localized messages and contains no firmware error identity.
- Focused mapper and presentation tests cover every public semantic failure plus the internal
  fallback.
- Dosing/general/device-root architecture guards, 164 Python guard tests, English/Turkish resource
  XML parsing and `git diff --check` passed locally.
- Android CI compile/unit/lint/coverage, API 27/API 36 emulator integration, installable debug APK
  and Firebase production-config guard passed at remote commit `df828740ee1f9f3a669862edc9bc0305d4ccdf92`.

- [ ] **06 — Central Dosing state, revision and invalidation adapter** — **READY FOR CHECK**
  - [x] Implement one device/channel-scoped Dosing state owner below `data/devices/dosing/`.
  - [x] Map stable application slot ids to firmware channel keys inside data.
  - [x] Combine global status, channel status and occurrence progress deterministically.
  - [x] Map v1 wire models to application channel/calibration snapshots.
  - [x] Serialize channel mutations and use authoritative `expectedRevision`.
  - [x] Reject stale responses by connection generation, request generation and revision.
  - [x] Refresh and surface revision conflicts without blind mutation retry.
  - [x] Consume `dosing.status.changed` as real state invalidation.
  - [x] Add adapter, stale-response, conflict and reconnect tests.
  - [ ] Pass Android compile/unit CI on the cutover branch.

### Stage 06 evidence

- `DeviceDosingV1StateOwner` is the sole device/channel-scoped source of truth. The architecture
  guard rejects a missing, renamed or parallel Dosing state owner.
- Stable application ids remain `dosing:channelN`; only `DeviceDosingV1SlotKeyMapper` translates
  them to the pinned firmware `channelN` identity.
- A snapshot is published only when global status, channel status and occurrence progress share one
  connection generation and revision. Unknown/malformed wire state fails closed.
- Persisted and runtime mutations share one device/channel mutex. Persisted requests read
  `expectedRevision` only from the last complete authoritative snapshot.
- Connection generation, adapter request generation and firmware revision each reject stale
  responses independently. A newer connection clears every snapshot from the previous session.
- Exact revision conflict invalidates and refreshes state, surfaces `CONFLICT`, and never retries the
  mutation. `dosing.status.changed` is likewise treated only as invalidation followed by full reads.
- Focused tests cover mapping, stale requests, lower revisions, old connections, reconnect,
  cross-revision documents, event invalidation, lifecycle clearing, conflict refresh/no-retry and
  same-channel mutation serialization.
- Updated architecture guard tests and `git diff --check` pass locally; Android CI is the remaining
  Stage 06 check gate.

- [ ] **07 — Root screen read path and navigation** — **READY FOR CHECK**
  - [x] Bind root cards, pump state, channel names, usage, progress and reservoir projections.
  - [x] Resolve channel navigation from current authoritative calibration state.
  - [x] Keep catalog values as bootstrap-only presentation.
  - [x] Verify two- and four-channel products and reconnect behavior.

### Stage 07 evidence

- The root switches atomically from catalog bootstrap to central state only after receiving one
  complete, identity-consistent two- or four-channel snapshot set for the bound device.
- Authoritative cards are built directly from channel snapshots, including effective names, pump
  state, calibrated/program state, scheduled and manual usage, occurrence progress and reservoir
  projection. No catalog display value survives the authoritative card mapping.
- Partial, duplicate, foreign-device, wrong-slot and wrong-channel-number snapshot sets fail closed
  to the catalog bootstrap. Clearing the central flow at reconnect immediately removes prior-session
  runtime presentation until a new complete set arrives.
- `DefaultDeviceDosingChannelNavigationOperations` refreshes the selected channel before resolving
  calibrated detail versus calibration, then rechecks the current commercial root, slot identity,
  supported pump count and allowed routes immediately before publishing a navigation target.
- Focused tests cover two- and four-channel products, physical ordering, card projections, reconnect,
  malformed/partial sets, refreshed calibration state, route revocation and snapshot identity drift.
- Production composition remained fail-closed through Stage 11. Dosing/general/device-root
  architecture guards, focused Python guard tests and `git diff --check` covered the pre-cutover
  boundary.

- [x] **08 — Calibration screen cutover** — **IMPLEMENTED**
  - [x] Bind naming, prime, calibration run, measurement, verification and confirmation.
  - [x] Use the firmware 5-second calibration run and 4 ml pending-calibration verification dose.
  - [x] Preserve prime timeout, host-stop and exit cleanup ordering.
  - [x] Verify recovery after recreation, reconnect and interrupted verification.

- [x] **09 — Channel detail cutover** — **IMPLEMENTED**
  - [x] Bind missed-dose recovery through full-program mutation.
  - [x] Bind manual dose start/stop and active-run state.
  - [x] Bind channel reset with revision checked before any destructive consequence.
  - [x] Render actionable semantic failures and authoritative refreshed state.

- [x] **10 — Dosing plan cutover** — **IMPLEMENTED**
  - [x] Bind Single, Hourly 24, Custom Periods and Timer modes.
  - [x] Preserve Monday-through-Sunday ordering and firmware-published scheduling limits.
  - [x] Bind enabled state, recurrence and missed-dose recovery in one program intent.
  - [x] Verify round-trip status/apply/status equality and conflict behavior.

- [x] **11 — Reservoir and central low-level notification cutover** — **IMPLEMENTED**
  - [x] Load and save tracking/capacity from authoritative channel state.
  - [x] Show remaining volume and bind reservoir refill.
  - [x] Persist channel-level low-level alert intent outside UI.
  - [x] Detect the authoritative `lowLevelActive` false-to-true transition with durable deduplication.
  - [x] Gate dispatch by channel intent, owner preference and Android delivery readiness.
  - [x] Dispatch exclusively through `NotificationDispatchUseCase`.
  - [x] Verify process recreation, reconnect, alert reset and repeated-low-state behavior.

- [ ] **12 — Production composition and commercial acceptance** — **ACCEPTANCE PENDING**
  - [x] Replace all production `UnavailableDeviceDosing*` bindings with central adapters and remove
        the obsolete fail-closed implementations.
  - [x] Set the pinned Dosing contract `productionWiring` evidence to true.
  - [x] Add a guard that forbids fail-closed Dosing bindings in production composition.
  - [x] Remove all Dosing debug/smoke fixture runtime, state, calibration, navigation and mutation
        paths; Dosing uses the production path in every build type.
  - [ ] Pass Android CI, API 27/API 36 emulator, installable APK and CodeQL.
  - [ ] Pass physical-device status, calibration, manual dose, program, reservoir and notification
        acceptance without changing transport, crypto, provisioning or firmware-owned behavior.
  - [ ] Grant merge approval only after all automated and physical gates are recorded.

### Stage 12 implementation evidence

- `OwnerDependencyGraph` eagerly owns exactly one owner-scoped Dosing production boundary. All Dosing
  ViewModels resolve channel, calibration and navigation operations from that same boundary, and the
  Dosing event/notification monitor is alive without requiring a Dosing screen to be opened.
- `DeviceDosingV1ProductionRuntime` reuses the existing correlated `DeviceRuntimeCommandGateway`,
  typed runtime events and lifecycle events. It creates no socket, transport, crypto or provisioning
  path and owns no state outside the canonical `DeviceDosingV1StateOwner`.
- Authenticated real Dosing devices are refreshed through the central adapter at the runtime
  lifecycle boundary, so authoritative state and low-level transition monitoring do not depend on
  UI observation.
- The durable channel alert ledger remains an application-intent/deduplication ledger only; alert
  delivery continues exclusively through the central `NotificationDispatchUseCase`.
- The debug decorator shares the exact production `OwnerDependencyGraph` instead of creating a
  second owner graph. Dosing products are excluded from the debug fixture catalog and every Dosing
  ViewModel delegates to production composition.
- Release-smoke composition no longer carries a Dosing root or fail-closed Dosing implementation;
  Dosing acceptance is reserved for the real production graph and physical devices.
- All `UnavailableDeviceDosing*` source/test files and `DebugFixtureDosing*` source/test files are
  removed, so there is no second concrete Dosing operations implementation or fixture state owner.
- `tools/dosing_architecture_guard.py` now fails if production composition regresses to fail-closed
  Dosing bindings, if `productionWiring` becomes false, if a Dosing debug/smoke path returns, if a
  debug decorator creates its own owner graph, or if a second Dosing state owner appears.
- Automated and physical-device acceptance remain intentionally unchecked until their evidence is
  produced; Stage 12 implementation alone does not grant merge approval.
