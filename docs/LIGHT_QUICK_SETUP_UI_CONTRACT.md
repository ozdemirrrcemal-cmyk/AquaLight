# AquaLight Light Quick Setup UI Contract

Status: implementation contract  
Android base: `feat/custom-program-device-apply` @ `93791a23aca95b3b714628af288f88b31d7f8a08`  
Implementation branch: `feat/light-quick-setup-commercial-ui`  
Firmware authority: `feature/smart-light-automation-plan-v2` @ `99aca74d3c2ae99e85584893822c0a63fe50bcd8`

This document fixes the presentation, data ownership and state-authority rules for the first commercial
Quick Setup implementation. It supplements `LIGHT_QUICK_SETUP_AUTOMATION_ENGINE.md`; it does not
replace the firmware managed-plan contract.

## 1. Data ownership

Quick Setup must not ask the user to re-enter data already persisted by aquarium creation or device
registration.

| Value | Authority | UI behavior |
|---|---|---|
| aquarium id/name | saved aquarium | read-only |
| tank width/length/height | saved aquarium | read-only |
| setup date / tank age | saved aquarium | read-only |
| tank type/style | saved aquarium | read-only |
| selected plants | saved aquarium | read-only |
| stable plant catalogId | saved aquarium | hidden identity, never name-matched |
| plant light demand | reviewed plant catalog | derived/read-only |
| substrate identity/semantic | saved materials + reviewed metadata | derived/read-only |
| CO2 equipment presence | saved materials exact CO2 category | derived/read-only |
| Light product/model | validated device metadata | read-only |
| Light channel layout | validated product contract | read-only |
| real water height | user measurement | required input |
| fixture height above water | user measurement | required input |
| first light-on time | user preference | required input |
| CO2 precharge readiness | user confirmation | shown only when saved aquarium contains CO2 |

CO2 presence and CO2 readiness are different facts. Quick Setup never asks “Do you use CO2?”. If CO2
is absent from the saved aquarium, the readiness control is not rendered.

## 2. Screen/state sequence

The commercial flow has nine authoritative states:

1. PROFILE
2. WATER_HEIGHT
3. FIXTURE_HEIGHT
4. LIGHT_TIME
5. CO2_CONFIRMATION — conditionally skipped when CO2 is absent
6. CALCULATING
7. REVIEW
8. APPLYING
9. LIVE

The progress surface may skip directly from LIGHT_TIME to CALCULATING when CO2 is absent. Skipping a
conditional screen must not fabricate a CO2 state; the engine receives `NOT_PRESENT`.

## 3. PROFILE

Purpose: confirm the saved context that will drive the recommendation. This screen is not an edit form.

Required read-only cards:

- Aquarium: name, dimensions, age, type and style.
- Plants: selected plant count and highest reviewed light demand.
- Setup: substrate semantic and CO2 present/absent.
- Lighting: exact product display name and exact channel layout.

Any correction is performed in the original aquarium/device surface. Quick Setup does not create a
second copy of these fields.

## 4. WATER_HEIGHT

Definition: distance from the planting substrate surface to the water surface.

Requirements:

- starts empty;
- numeric input only;
- must be greater than zero;
- must not exceed the saved physical tank height;
- no guessed substrate-depth subtraction;
- explanatory measurement illustration uses the central guided-flow color system.

## 5. FIXTURE_HEIGHT

Definition: vertical distance from the fixture light-output plane to the water surface.

Requirements:

- starts empty;
- numeric input only;
- zero is syntactically valid;
- production acceptance is ultimately restricted by measured fixture calibration domain;
- no arbitrary universal maximum is invented in presentation.

## 6. LIGHT_TIME

Requirements:

- five-minute increment;
- all five phases must remain same-day schedules;
- the mature eight-hour phase makes 15:55 the latest valid start;
- 16:00 and later are blocked;
- presentation never silently wraps an end time into the next civil day.

## 7. CO2_CONFIRMATION

Rendered only when exact saved material category indicates CO2 presence.

The switch means only:

> CO2 is already running at least two hours before the first Light-on time.

It does not switch a CO2 device. The UI shows the selected light-on time and the corresponding two-hour
precharge time so the statement is explicit.

## 8. CALCULATING

This is a deterministic local calculation state, not fake server progress.

The UI may state that these facts are being evaluated:

- saved tank geometry and setup date;
- exact plant identities and reviewed demands;
- CO2 readiness;
- exact Light product/channel layout;
- recommendation policy revision;
- fixture calibration boundary.

It must not show fabricated percentages or claim firmware writes have begun.

## 9. REVIEW

Review displays authored recommendation metadata before mutation:

- current date-anchored tank phase;
- current phase light window and photoperiod;
- mature photoperiod;
- requested PPFD;
- effective target PPFD;
- any CO2 cap;
- five-phase progression;
- channel scene;
- calibration status;
- evidence-driven explanation/warnings.

There is no development or debug calibration mode. Review is reachable only when a measured,
versioned AquaLight fixture calibration profile resolves for the exact product and geometry.

## 10. APPLYING

No optimistic success is allowed.

Before mutation Android must:

1. re-read aquarium/device context;
2. compare profile fingerprint;
3. rebuild and require another review if the context changed;
4. read current managed plan;
5. use current `revision` and `storageGeneration`;
6. send one `light.auto.plan.apply`;
7. wait for firmware response/readback;
8. refresh authoritative Light status/graph.

Stale revision/storage generation never triggers a blind retry.

## 11. LIVE

LIVE is rendered only from authoritative firmware state after successful Apply/readback.

Required managed-plan fields include:

- installed/selected state;
- planId;
- active phase index;
- transitionPermille;
- nextPlanTransitionEpochDay.

The daily graph must use firmware-projected graph points. Android must not reconstruct a “better looking”
curve from the original recommendation.

## 12. Central visual system

Quick Setup must reuse existing AquaLight UI primitives:

- `AquaHeader` for top navigation;
- `AquaGuidedFlowSurface`, `AquaGuidedFlowButton`, `AquaGuidedFlowColors`,
  `AquaGuidedFlowTypography` and `AquaGuidedFlowGeometry` for wizard surfaces and actions;
- `AquaDeviceCardSurface` plus device-card typography/colors for device/profile/plan cards;
- existing Light channel colors from `aquaLightManualColors()`.

Feature code must not define an independent brand palette or hard-code production colors.

## 13. Architecture boundaries

Presentation may depend on application contracts only. It must not import DataStore repositories or
firmware runtime DTOs.

Application contracts:

- `DeviceLightQuickSetupContextOperations`
- `DeviceLightManagedAutoPlanOperations`
- `DeviceLightFixtureCalibration`
- `DeviceLightQuickSetupRecommendationEngine`

Data adapters:

- `DefaultDeviceLightQuickSetupContextOperations`
- `DefaultDeviceLightManagedAutoPlanOperations`
- `DefaultDeviceLightFixtureCalibration`

The managed-plan adapter delegates to the existing central Light runtime. There is no Quick Setup-owned
runtime state store.

## 14. Evidence and calibration

Biological policy evidence is versioned separately from fixture physics.

Reviewed general policy sources currently include:

- Tropica Aquarium Plants — plant/light-demand guidance:
  https://tropica.com/en/guide/make-your-aquarium-a-success/light/
- Green Aqua — planted-aquarium photoperiod practice:
  https://greenaqua.hu/en/technika/vilagitas.html
- The 2Hr Aquarist — planted-tank PAR bands:
  https://www.2hraquarist.com/blogs/light-3pillars/planted-tank-lighting-101
- The 2Hr Aquarist — CO2 precharge practice:
  https://www.2hraquarist.com/blogs/hot-topics/injecting-enough

Existing `AquariumPlantLightCatalog` remains the exact per-species source and resolves stable catalog IDs
without display-name fallback.

These sources do not constitute AquaLight fixture calibration. Production requires measured
product-specific optical calibration for the supported geometry domain.

## 15. Calibration runtime policy

Quick Setup has one runtime policy across all build variants:

- no build-type feature flag;
- no debug-only optical profile;
- no synthetic or percentage-based fixture fallback;
- measured AquaLight calibration is mandatory;
- missing calibration fails closed before recommendation/apply.

Build variants may differ for normal application packaging and CI, but they must not alter Quick Setup
recommendation or calibration behavior.

## 16. Failure behavior

The flow fails closed for:

- invalid/unregistered device;
- device metadata not ready;
- missing aquarium assignment;
- multiple Light fixtures assigned to one tank;
- unsupported product;
- zero plants;
- missing/unknown plant catalogId;
- missing setup date;
- invalid user measurement/time;
- missing/out-of-domain calibration;
- insufficient coverage;
- connection/authentication failure;
- RTC not ready;
- stale context/revision/storage generation;
- malformed firmware state;
- device write/commit failure.

A failed Apply never transitions to LIVE.

## 17. Commercial completion gate

Quick Setup is not release-ready until all are true:

- debug APK builds;
- Android CI green;
- emulator integration green;
- CodeQL green;
- zero-new-debt Detekt green;
- baseline-free Android Lint green;
- recommendation policy tests green;
- managed-plan contract tests green;
- exact data ownership preserved;
- EN/TR localization complete;
- accessibility semantics complete;
- measured AquaLight fixture calibration complete.
