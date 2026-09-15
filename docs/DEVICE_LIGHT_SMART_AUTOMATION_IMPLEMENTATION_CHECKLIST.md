# Device Light Smart Automation implementation checklist

Branch: `feature/device-light-smart-automation`  
Base: `feature/device-light-hero` at `e8f448fe3fdaeceded7cf521258b0f4888753635`  
Firmware authority: `ozdemirrrcemal-cmyk/AquaLight-Firmware`  
Firmware branch: `feature/smart-light-automation-plan`  
Firmware commit: `455298833668537fedc16b851067558815d2cc7b`  
Only Light schema: `aqualight.light.v1`, `storageVersion=1`

## Non-negotiable cutover policy

- [x] Keep one exact Light V1 wire and persistence shape.
- [x] Do not add a V2 schema, legacy reader, compatibility adapter, migration,
  dual read/write path, fallback parser, or fallback execution path.
- [x] Reject a status, plan, graph, fixture, product, or calibration shape that
  does not exactly match the pinned contract.
- [x] Never infer a missing aquarium fact from a product name, note, localized
  text, or UI label.
- [x] Never generate or apply a plan from incomplete or unsupported input.
- [x] Keep firmware as the sole execution/date authority after an atomic Apply;
  Android owns aquarium meaning, evidence, and plan authorship only.

## 1. Semantic aquarium data and classification

- [x] Add an explicit, owner-scoped Smart Light profile to the aquarium record.
- [x] Represent plant density as `LOW`, `MEDIUM`, or `HIGH` when planted.
- [x] Represent the highest plant light demand as `LOW`, `MEDIUM`, or `HIGH`.
- [x] Represent CO2 as `NONE`, `INSTALLED`, or `ACTIVE`.
- [x] Represent active soil as a semantic boolean with presence; absence is not
  equivalent to `false`.
- [x] Store water depth and fixture mounting height as bounded centimetres.
- [x] Store a same-day preferred viewing window with minute precision.
- [x] Store explicit algae and plant-stress observations, including observation
  date; absence is not interpreted as a healthy observation.
- [x] Calculate aquarium day/stage from `LocalDate` epoch days, using the active
  device/local calendar boundary and rejecting future setup dates.
- [x] Build maintenance signals only from typed care-task fields and timestamps;
  do not classify free-form notes.
- [x] Remove CO2 and active-soil keyword classification from the Smart Setup
  decision path.
- [x] Validate persistence at the store boundary and preserve owner/record
  identity during profile updates.

## 2. Decision engine and commercial test matrix

- [x] Define `SmartSetupInput` as a semantic, platform-independent value.
- [x] Define exactly three terminal decisions: `Ready`, `MissingData`, and
  `Unsupported`.
- [x] Make `MissingData` return stable field keys; do not substitute defaults.
- [x] Make `Unsupported` return a stable reason and affected facts/product.
- [x] Make `Ready` carry the complete phase draft, confidence, factor records,
  evidence/source IDs, calibration ID, canonical profile fingerprint, generated
  date, and reevaluation date.
- [x] Use deterministic canonical serialization and SHA-256 for the profile
  fingerprint; locale and map iteration order must not affect it.
- [x] Support setup, establishing, and mature freshwater-planted classifications.
- [x] Make unplanted freshwater behavior explicit and tested.
- [x] Fail closed for marine/reef input until a separately reviewed evidence and
  calibration policy exists.
- [x] Fail closed for an unknown device model, missing calibration profile,
  mismatched calibration revision, unsupported channel set, or invalid calendar.
- [x] Bound every generated value to firmware policy: 1..8 contiguous phases,
  2000-01-01..2099-12-31, final phase open-ended, 0..90 transition days,
  weekdays 1..127, same-day schedule, minute-aligned times, allowed ramps, and
  exact product scene fields.
- [x] Cover the cross-product matrix for lifecycle stage, density, maximum light
  demand, CO2 state, active soil, depth, mounting height, algae, plant stress,
  maintenance freshness, viewing window, product, and calibration.
- [x] Add invariant/property cases for determinism, monotonic bounds, phase
  contiguity, fingerprint stability, and prohibited defaulting.

## 3. Exact firmware plan client

- [x] Pin Android fixtures and source blobs to firmware commit
  `455298833668537fedc16b851067558815d2cc7b`.
- [x] Add exact actions `light.auto.plan.get`, `light.auto.plan.apply`, and
  `light.auto.plan.delete` to the single Light V1 runtime.
- [x] Parse root `storageGeneration`, expanded AUTO summary/policy, managed-plan
  runtime, graph `planSpans`, `MANAGED_PLAN` basis, and managed-plan graph reason.
- [x] Use exact product scene width: WRGB four fields, RGB three fields.
- [x] Enforce both Apply preconditions: plan `expectedRevision` and Light
  `expectedStorageGeneration`.
- [x] Use `planId=null` only for create and the current firmware ID only for
  replacement.
- [x] Read authoritative status and plan immediately before every Apply.
- [x] Never blind-retry `STALE_REVISION` or `STALE_STORAGE_GENERATION`; re-read,
  recompute, and require a new deliberate Apply.
- [x] Re-read status, plan, and graph after Apply and verify committed ID,
  revision, generation, AUTO ownership, phase content, and product.
- [x] Treat an unconfirmed readback as unconfirmed, never as success.
- [x] Refresh status, plan, and graph after reconnect and every
  `light.status.changed` invalidation.
- [x] Keep managed-plan deletion unavailable while AUTO owns it; mode change is
  explicit and no user-program fallback is triggered by Smart Setup.
- [x] Add exact request/response/error/parser tests and decoded data-size tests
  against the shared 4096-byte WebSocket data ceiling.

## 4. Quick Setup presentation

- [x] Bind the existing `deviceUid` route to one owner-scoped application
  operation; presentation must not import runtime/data DTOs.
- [x] Resolve the aquarium through the authoritative device assignment.
- [x] Render explicit loading, missing assignment, missing data, unsupported,
  ready preview, applying, applied, stale authority, unconfirmed, and error
  states.
- [x] Let the user enter/update only missing or deliberately edited semantic
  facts; persist them before recomputing.
- [x] Show aquarium day/stage, selected product/calibration, viewing window,
  observations, generated phases, channel targets, confidence, factors,
  sources, fingerprint, and reevaluation date before Apply.
- [x] Explain that dated phase transitions replace the separate Acclimation
  multiplier while firmware thermal and hard-power safety remain active.
- [x] Use a fixed, single atomic Apply CTA; disable it during mutation and when
  the preview fingerprint is stale.
- [x] Localize all visible copy in English and Turkish.
- [x] Meet minimum touch targets, content descriptions, large-font behavior,
  contrast, TalkBack order, and process recreation requirements.
- [x] Refresh the Light root after successful navigation back through the shared,
  authoritatively re-read status/plan/graph state.

## 5. Release evidence

- [x] Android architecture, localization/accessibility, navigation, WebSocket,
  and firmware-interoperability guards pass.
- [ ] Debug and staging unit tests pass, including the decision matrix and exact
  Light V1 contract tests.
- [ ] Detekt and Android Lint pass without suppression of new findings.
- [ ] Debug APK assembles successfully.
- [ ] Record maximum compact Apply request and Get/status/graph response sizes;
  each must remain at or below 4096 decoded bytes.
- [x] Firmware CI run for the pinned commit is green across all seven products.
- [ ] Physical WRGB smoke verifies create, reboot persistence, local-date phase
  transition, RTC loss/recovery, MANUAL exit, AUTO resume, replace, stale-write
  rejection, and explicit safe delete.
- [ ] Do not mark commercial release complete until the physical-device gate is
  recorded; automated CI cannot substitute for that evidence.
