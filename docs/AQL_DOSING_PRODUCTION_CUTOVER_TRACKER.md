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

- Current stage: **01 — Architecture and package boundaries**
- Status: **READY FOR CHECK**
- Production wiring: **DISABLED**
- Rule: the next stage cannot start until the current stage is reviewed and checked.

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

- [ ] **01 — Architecture and package boundaries** — **READY FOR CHECK**
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
  - [ ] Pass Android compile/unit CI on the cutover branch.

### Stage 01 evidence

- `python3 tools/dosing_architecture_guard.py`: passed.
- `python3 tools/architecture_guard.py`: passed.
- `python3 tools/device_root_application_boundary_guard.py`: passed.
- `python3 -m unittest discover -s tools/tests -p 'test_*.py'`: 159 tests passed.
- `git diff HEAD --check`: passed.
- Android Gradle/CI: pending branch publication; Stage 01 remains unchecked until this gate passes.

- [ ] **02 — Firmware-compatible display-name semantics**
  - [ ] Move display-name validation to an application-owned Dosing policy.
  - [ ] Enforce the firmware limit of 32 UTF-8 bytes, not 32 UI characters.
  - [ ] Cover Turkish characters, emoji, combining characters, control characters and trimming.
  - [ ] UI renders semantic validation results without knowing the firmware byte limit.

- [ ] **03 — Exact reservoir-capacity semantics**
  - [ ] Replace presentation-owned positive `Double` validation with an application policy.
  - [ ] Enforce exact 0.001 ml quanta and the firmware unsigned 32-bit amount range.
  - [ ] Preserve locale-aware text entry without using floating-point values as persisted intent.
  - [ ] UI renders semantic precision/range errors without knowing firmware quanta.

- [ ] **04 — Reservoir alarm and supply-projection separation**
  - [ ] Keep firmware `lowLevelActive` as the sole low-reservoir alert signal.
  - [ ] Move remaining-day projection and 10/20-day thresholds into application policy.
  - [ ] Publish application-owned `supplySeverity` for UI color mapping.
  - [ ] Prove projected card severity and firmware low-level alert transitions are independent.
  - [ ] Remove firmware low-level fields and product thresholds from UI contracts.

- [ ] **05 — Calibration rejection semantics**
  - [ ] Represent time-required, busy, verification-required, conflict, storage-health,
        unavailable, connection and unknown outcomes in the application boundary.
  - [ ] Map exact firmware errors to semantic application results.
  - [ ] Prevent safety or state rejections from being presented as connection failures.
  - [ ] Keep firmware error codes outside UI.

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
