# AquaLight Smart / Quick Setup Lighting Automation Engine

Status: Android implementation in progress  
Android base branch: feat/custom-program-device-apply @ 93791a23aca95b3b714628af288f88b31d7f8a08  
Android implementation branch: feat/light-quick-setup-commercial-ui  
Firmware branch: feature/smart-light-automation-plan-v2  
Firmware reviewed commit: 99aca74d3c2ae99e85584893822c0a63fe50bcd8  
Firmware authoritative contract: docs/LIGHT_MANAGED_AUTO_PLAN_V1_CONTRACT.md  
UI implementation contract: docs/LIGHT_QUICK_SETUP_UI_CONTRACT.md  
Scope: planted freshwater Smart / Quick Setup lighting automation  
Production calibration status: NOT READY - physical PAR/PPFD calibration is a release blocker

---

## 1. Purpose

Quick Setup generates a deterministic, aquarium-specific lighting plan from:

- saved aquarium data;
- exact selected plant identities and reviewed light-demand classes;
- tank age;
- tank geometry;
- explicit user measurements that cannot be derived reliably;
- CO2 readiness;
- exact connected Light product identity;
- product-specific fixture calibration.

Android owns aquarium biology, recommendation evidence and plan construction.

Firmware owns:

- durable plan storage;
- local civil-date phase selection;
- daily schedule execution;
- multi-day scene interpolation;
- optimistic concurrency;
- atomic Apply;
- AUTO selection;
- physical-output safety;
- authoritative status and graph projection.

The feature must never treat raw RGB/W percentages as universal plant-light values.

---

## 2. Current repository status

The existing Android Quick Setup vertical slice is intentionally empty.

Existing packages:

~~~text
application/devices/light/quicksetup/
data/devices/light/quicksetup/
ui/tabs/devices/detail/light/presentation/quicksetup/
~~~

Current Quick Setup application/data package-info files explicitly state that the runtime contract is not implemented yet.

Current DeviceLightQuickSetupFragment only validates deviceUid and renders the header.

Therefore the Android recommendation engine, managed-plan client, calibration layer, state machine and apply flow still need implementation.

---

## 3. Important firmware change

The final Smart Setup implementation MUST NOT create a normal user AutoProgram.

Do not use this as the Smart Setup write path:

~~~text
DeviceLightAutomaticOperations.create
then
DeviceLightControlOperations.setMode(AUTOMATIC)
~~~

Firmware branch feature/smart-light-automation-plan adds a dedicated managed AUTO plan.

Smart Setup must use:

~~~text
light.auto.plan.get
light.auto.plan.apply
light.auto.plan.delete
~~~

One successful light.auto.plan.apply:

1. validates the complete plan;
2. validates expected plan revision;
3. validates expected Light storage generation;
4. stages the complete replacement plan;
5. selects AUTO in the same transaction;
6. evaluates current output;
7. applies physical output through the normal safety pipeline;
8. persists the complete Light document atomically;
9. publishes the existing light.status.changed invalidation event.

No additional Android setMode(AUTOMATIC) call is required after successful managed-plan Apply.

---

## 4. Managed plan authority

Firmware supports exactly zero or one installed managed plan.

While:

~~~text
mode == AUTO
and
managed plan installed
~~~

the managed plan is the only AUTO scheduling authority.

Existing user-authored AUTO programs remain stored unchanged but are not executed.

MANUAL and CUSTOM keep their existing behavior.

The managed plan does not delete or rewrite user AUTO programs.

Global firmware Acclimation is bypassed while the managed plan owns AUTO so the output is not scaled twice.

Thermal Protection and the hard Power Limiter remain downstream and always authoritative.

---

## 5. Firmware managed-plan capacity

Firmware currently supports:

~~~text
maximum phases: 8
initialStartPercent: 20..100, step 5
transitionDays: 0..90
weekdaysMask: 1..127
daily ramp values: 0, 30, 60, 90, 120, 150 minutes
final phase: open-ended
phase dates: contiguous local civil epoch-day ranges
managed daily schedules: same-day only
~~~

A finite phase rejects transitionDays greater than or equal to the phase length.

Therefore:

~~~text
finite phase duration = 7 days
maximum non-zero transitionDays = 6

finite phase duration = 21 days
maximum non-zero transitionDays = 20
~~~

This rule must be validated in Android before Apply and remains firmware-authoritative.

---

# 6. Evidence review

The startup policy below distinguishes source guidance from AquaLight product policy.

## 6.1 Tropica - official planted-aquarium method guidance

Tropica recommends:

- 6 hours of lighting per day for the first 2-3 weeks;
- then gradual increase;
- its quick-start guide caps the gradual increase at 8 hours per day;
- CO2 can be supplied from day 1.

Sources:

- https://tropica.com/en/guide/get-the-right-start/growing-in/
- https://tropica.com/en/guide/get-the-right-start/
- https://tropica.com/media/870849/REDUCEDP14-11434-Quickguide_ny-UK.pdf

This is the primary evidence for holding a new planted tank at 6 hours for the first 21 days.

## 6.2 Green Aqua - experienced aquascaping practice

Green Aqua recommends for powerful controllable LED fixtures:

- begin at 6 hours per day;
- increase photoperiod by 0.5 hour per additional week;
- a maximum of about 8 hours is their standard planted-aquarium practice;
- for raw fixture intensity, start conservatively and increase gradually;
- their general Chihiros guidance describes 50% maximum fixture intensity initially and approximately +5 percentage points weekly.

Source:

- https://greenaqua.hu/en/blog/post/chihiros-led-light-settings-color-intensity-and-duration-what-fits-best-for-planted-aquariums

Important: the raw percentage guidance is practitioner guidance for adjustable fixtures. It is NOT a universal PPFD conversion and must not be copied directly into the production AquaLight calibration model.

## 6.3 The 2Hr Aquarist - planted-aquarium practice

Current guidance recommends:

- roughly 5-6 hours for a brand-new tank;
- 7-8 hours for a stable low-tech aquarium;
- 8-9 hours for a stable high-tech aquarium;
- moderate light at startup;
- stable timing rather than frequent manual schedule changes.

Sources:

- https://www.2hraquarist.com/blogs/beginners-planted-tank-101/first-planted-tank-setup-guide
- https://www.2hraquarist.com/en-au/blogs/light-3pillars/planted-tank-light-hours
- https://www.2hraquarist.com/blogs/light-3pillars/planted-tank-lighting-101

This supports the conservative 6-hour startup and an eventual mature range around 8 hours.

## 6.4 ADA - established Nature Aquarium practice

ADA's Nature Aquarium Guide recommends approximately 8 hours as a regular daily lighting period in its current guide.

Source:

- https://www.adana.co.jp/en/contents/support/pdf/pdf/nature_aquarium_guide_60_en.pdf

ADA gallery examples commonly use approximately 8-8.5 hours in established planted aquariums.

ADA is supporting evidence for the mature target, not the source of the startup phase schedule.

## 6.5 Peer-reviewed physiological rationale

Aquatic Botany research has shown that low-light-acclimated submerged freshwater plants can be sensitive to sudden increases in irradiance.

Source:

- https://doi.org/10.1016/j.aquabot.2010.02.003

Research also shows that light response and inorganic carbon availability interact in submerged aquatic plants.

Sources:

- https://doi.org/10.1111/j.1365-3040.1994.tb00324.x
- https://www.frontiersin.org/journals/plant-science/articles/10.3389/fpls.2013.00140/full

These studies support avoiding abrupt excessive irradiance and support considering CO2 availability. They do NOT define an aquarium hobby schedule such as exact weekly percentages.

---

# 7. AquaLight V1 photoperiod policy

The exact V1 schedule is an AquaLight product policy derived from:

- Tropica: 6 hours for the first 3 weeks;
- Green Aqua: then increase by 0.5 hour per week;
- Tropica / Green Aqua: cap standard automatic startup progression at 8 hours;
- 2Hr / ADA: 7-9 hours is a normal established planted-aquarium range.

The default managed plan uses five age phases.

| Phase | Tank age | Daily photoperiod |
|---|---|---:|
| 1 | Day 1-21 | 6 h |
| 2 | Day 22-28 | 6.5 h |
| 3 | Day 29-35 | 7 h |
| 4 | Day 36-42 | 7.5 h |
| 5 | Day 43+ | 8 h |

The phase dates are anchored to aquarium setupDateEpochDay, NOT to the date Quick Setup is run.

Example:

If the tank was created 18 days ago, Apply still sends the complete plan anchored to setup date. Firmware selects the current phase from the trusted local date.

If the tank is already older than 42 days, firmware immediately selects phase 5.

---

# 8. Exact daily time behavior

The user selects one value:

~~~text
first light-on time
~~~

Android calculates each phase end time from its photoperiod.

Example with user start time 10:00:

| Tank age | Daily window |
|---|---|
| Day 1-21 | 10:00-16:00 |
| Day 22-28 | 10:00-16:30 |
| Day 29-35 | 10:00-17:00 |
| Day 36-42 | 10:00-17:30 |
| Day 43+ | 10:00-18:00 |

The user does not separately choose a shutdown time.

---

# 9. Same-day firmware restriction

Managed phases reject overnight schedules.

Therefore every phase must satisfy:

~~~text
endTimeMs > startTimeMs
endTimeMs < 86_400_000
~~~

Because the mature phase is 8 hours:

~~~text
startMinuteOfDay + 480 < 1440
~~~

must be true.

A 16:00 start is NOT valid because 16:00 + 8 h equals 24:00, which is outside the accepted authored-time range.

If the UI uses 5-minute time increments, the latest valid mature-plan start is 15:55.

Quick Setup must validate the complete future plan, not merely the current tank-age phase.

---

# 10. Daily sunrise / sunset ramp

V1 default:

~~~text
60-minute sunrise ramp
60-minute sunset ramp
~~~

The 60-minute value is directly supported by the firmware managed-plan ramp contract.

This is independent of the multi-day biological transition.

Example, Day 1-21 with 10:00 start:

~~~text
10:00-11:00  sunrise ramp
11:00-15:00  plateau
15:00-16:00  sunset ramp
~~~

The daily ramp repeats every enabled day.

weekdaysMask is 127: every day.

---

# 11. Daily ramp and multi-day transition are different

Daily ramp:

~~~text
minutes within one day
dark -> target -> dark
~~~

Multi-day transition:

~~~text
civil days
previous phase scene -> current phase scene
~~~

They must never be conflated.

Firmware evaluates multi-day interpolation first and then evaluates the normal daily sunrise/hold/sunset geometry against the interpolated scene.

---

# 12. Startup intensity policy

There is no robust universal rule saying every planted aquarium should run at one exact percentage of fixture maximum.

Published practitioner guidance varies with fixture power and aquarium geometry.

Examples from Green Aqua and Chihiros support range from very low starting percentages on powerful fixtures to approximately 50% on more general setups.

Therefore production AquaLight MUST NOT implement:

~~~text
new tank = fixed 30% fixture
or
new tank = fixed 50% fixture
~~~

as biological truth.

Production intensity must be calculated in this order:

~~~text
plant light demand
+ CO2 readiness
+ water height
+ fixture height
+ tank footprint
+ exact product calibration
        ->
target PPFD
        ->
product-specific channel scene
~~~

The gradual-output mechanism then uses calibrated phase scenes and firmware transition interpolation.

---

# 13. Development placeholder intensity

Development requires the complete firmware path to be testable before physical production calibration exists.

Therefore a development-only placeholder profile may use:

~~~text
CalibrationStatus = PLACEHOLDER
initialStartPercent = 50
phase 1 transitionDays = 20
phase scenes = deterministic placeholder-calibration result
~~~

This exercises:

- first-phase initial scaling;
- firmware transitionPermille;
- managed-plan graph/status;
- Android live UI;
- Apply concurrency;
- reboot/offline behavior.

This value is NOT a production biological recommendation.

A release build must fail if a product exposing LIGHT_QUICK_SETUP still resolves to PLACEHOLDER calibration.

---

# 14. Production intensity progression

Production physical calibration replaces the placeholder.

The calibrated engine may produce phase-specific target scenes.

When adjacent phase scenes differ:

- firmware transitionDays interpolates from the previous phase target;
- a 7-day finite phase may use at most 6 transition days;
- a 21-day finite phase may use at most 20 transition days.

Exact production startup PPFD and scene progression is a calibration deliverable.

It must not be finalized from raw fixture percentages before physical measurement.

---

# 15. DLI

DLI is derived information and may be shown in the UI.

Formula:

~~~text
DLI mol/m2/day =
PPFD umol/m2/s * photoperiodSeconds / 1,000,000
~~~

DLI must be calculated from the effective authored target PPFD and phase photoperiod.

It must never be hard-coded in the UI.

Example at 42 PPFD:

| Duration | DLI |
|---|---:|
| 6 h | about 0.91 mol/m2/day |
| 6.5 h | about 0.98 mol/m2/day |
| 7 h | about 1.06 mol/m2/day |
| 7.5 h | about 1.13 mol/m2/day |
| 8 h | about 1.21 mol/m2/day |

---

# 16. Aquarium data already available

Quick Setup reuses persisted aquarium data:

| Data | Source |
|---|---|
| tank ID | AquariumTankSnapshot |
| widthCm | AquariumTankSnapshot |
| lengthCm | AquariumTankSnapshot |
| heightCm | AquariumTankSnapshot |
| setupDateEpochDay | AquariumTankSnapshot |
| tank type | AquariumTankSnapshot |
| tank style | AquariumTankSnapshot |
| selected plants | AquariumPlantTag |
| stable plant catalogId | AquariumPlantTag |
| active substrate identity | materials |
| CO2 equipment selection | materials |

---

# 17. Plant identity and light demand

Every selected plant must resolve by exact catalogId:

~~~text
AquariumPlantTag.catalogId
        ->
AquariumPlantLightCatalog.requireRecord
        ->
LOW / MEDIUM / HIGH
~~~

No fallback by:

- display name;
- translated name;
- category;
- genus;
- keyword.

Unknown or missing catalogId is a blocking data defect.

The maximum selected plant demand must not be averaged away.

Example:

~~~text
20 LOW
8 MEDIUM
1 HIGH

highestDemand = HIGH
~~~

---

# 18. Plant density

Plant density / coverage is OUT OF SCOPE for V1.

Marker count is not plant coverage.

Quick Setup does not ask:

~~~text
low density / medium density / high density
~~~

and does not infer density from plant-tag count.

---

# 19. Floating plants

The following are OUT OF SCOPE for V1:

- floating-plant classification;
- floating-plant coverage;
- surface coverage percentage.

The engine does not infer or request them.

---

# 20. Hardscape and water clarity

OUT OF SCOPE for V1:

- hardscape shading;
- tannin level;
- water clarity;
- turbidity user input.

No hidden multiplier is derived from tank style such as Blackwater.

---

# 21. Fertilizer

Fertilizer amount and dosing frequency are OUT OF SCOPE for the V1 light engine.

Existing fertilizer material selections remain valuable for SmartCare but do not directly scale Quick Setup output.

---

# 22. Substrate semantic

Existing substrate semantic remains available:

~~~text
INERT
NUTRIENT_BASE
ACTIVE_SOIL
ADDITIVE
UNKNOWN
~~~

V1 rule:

~~~text
ACTIVE_SOIL does not directly increase light output
~~~

It may be used for:

- explanation;
- startup context;
- future maintenance policies.

It is not an intensity multiplier.

---

# 23. User inputs

Quick Setup asks only for values that cannot be derived reliably.

Required:

1. real water height;
2. fixture height above water;
3. first light-on time.

Conditional:

4. CO2 precharge confirmation switch, only if the saved aquarium has a CO2 material selection.

---

# 24. Real water height

Definition:

~~~text
distance from planting substrate surface to water surface
~~~

User enters waterHeightCm.

Validation:

~~~text
waterHeightCm > 0
waterHeightCm <= tank.heightCm
~~~

Android must not subtract a guessed substrate depth from tank height.

---

# 25. Fixture height

Definition:

~~~text
vertical distance from fixture light-output plane to water surface
~~~

User enters fixtureHeightAboveWaterCm.

Production validation comes from the selected product calibration measurement domain.

Do not create an arbitrary universal maximum independent of calibration data.

---

# 26. Device identity

Quick Setup already starts with deviceUid.

Android resolves exact device identity and capabilities from existing device application boundaries.

The user is never asked to select the lamp model manually.

Required context includes:

- productKey;
- model/display identity;
- channel layout;
- LIGHT_QUICK_SETUP support;
- exact Light V1 runtime policy.

---

# 27. Device -> tank context

Quick Setup is launched from a device screen but recommendation requires the assigned aquarium.

Android must expose an application-safe reverse lookup.

Proposed boundary:

~~~text
DeviceLightQuickSetupContextOperations
~~~

Resolution:

~~~text
deviceUid
-> assigned tank ID
-> AquariumTankSnapshot
-> exact device snapshot
-> substrate semantic
-> CO2 presence
~~~

Presentation code must not import TankDeviceAssignmentRepository or DataStore directly.

Unassigned Light device is a blocking state.

---

# 28. Multiple Light fixtures

V1 does not optimize combined optical output from multiple fixtures.

If one aquarium has more than one assigned Light fixture:

~~~text
MULTIPLE_LIGHT_FIXTURES_UNSUPPORTED
~~~

Quick Setup is blocked.

Non-Light devices assigned to the tank do not count.

A future multi-fixture feature requires aggregate physical calibration.

---

# 29. CO2 presence

CO2 is not re-asked in Quick Setup.

V1 product rule:

~~~text
saved aquarium contains selected material in exact CO2 category
-> CO2 present

no selected CO2 category material
-> CO2 absent
~~~

Do not infer CO2 from display-name keywords.

---

# 30. CO2 readiness UI

If CO2 is absent:

- no CO2 Quick Setup control is rendered;
- engine state is NOT_PRESENT.

If CO2 is present:

the user gets one switch.

Example with first light time 10:00:

~~~text
CO2 en geç 08:00'de çalışmaya başlıyor
[ switch ]
~~~

The switch means:

~~~text
CO2 is already running at least 2 hours before the first light-on time
~~~

Suggested domain:

~~~text
NOT_PRESENT
PRESENT_NOT_PRECHARGED
PRESENT_PRECHARGED
~~~

The switch does not control a CO2 device.

It is a user confirmation used by the recommendation engine.

---

# 31. CO2 evidence

The 2Hr Aquarist recommends starting injected CO2 before lights so adequate CO2 is present at light-on; around 2 hours is a common practical starting point.

Source:

- https://www.2hraquarist.com/blogs/hot-topics/injecting-enough

AquaLight V1 uses 2 hours as its explicit product rule.

This is not a universal physical saturation time for every aquarium.

---

# 32. CO2 safety cap

A CO2-absent or CO2-not-precharged aquarium must not receive the same aggressive target as a verified CO2-supported high-demand setup.

The PPFD policy returns both:

~~~text
requestedTargetPpfd
effectiveTargetPpfd
~~~

and a limiting reason when required.

Exact production cap values belong to the calibrated PPFD policy and must be source/version tracked.

Do not silently boost photoperiod to compensate for missing CO2.

---

# 33. Target PPFD

Raw RGB/W percentages are not the biological target.

Flow:

~~~text
plant demand
-> biological PPFD target
-> CO2 safety policy
-> fixture calibration
-> product channel scene
~~~

The plant catalog already warns that LOW/MEDIUM/HIGH classifications must not be presented directly as PPFD or channel percentages without fixture-specific calibration.

---

# 34. Fixture calibration abstraction

Proposed application boundary:

~~~text
DeviceLightFixtureCalibration
~~~

Minimum request:

~~~text
productKey
tankWidthCm
tankLengthCm
waterHeightCm
fixtureHeightAboveWaterCm
targetPpfd
spectrumRecipe
supportedChannels
~~~

Air path and water path remain separate inputs.

Do not collapse them into one opticalDistanceCm value in the public contract.

---

# 35. Calibration result

Minimum result:

~~~text
status
calibrationRevision
channelScene
estimatedPpfd
coverageStatus
supportedWaterDepthRange
supportedFixtureHeightRange
~~~

Calibration status:

~~~text
PLACEHOLDER
CALIBRATED
~~~

---

# 36. Production release blocker

A product exposing LIGHT_QUICK_SETUP cannot ship with PLACEHOLDER calibration.

Release condition:

~~~text
LIGHT_QUICK_SETUP exposed
AND
calibration.status != CALIBRATED
=> release FAIL
~~~

The release guard must be machine enforced, not documentation only.

Proposed guard:

~~~text
tools/light_quick_setup_production_guard.py
~~~

Physical production calibration must include, at minimum:

- productKey;
- exact channel configuration;
- fixture mounting height;
- water height;
- tank footprint;
- authored drive / scene;
- horizontal measurement location;
- measured PPFD;
- calibration revision.

Center-only PPFD is insufficient for fixture coverage decisions.

---

# 37. Spectrum

Quick Setup does not invent independent RGB/W values from plant names.

Use a versioned balanced planted-aquarium spectrum recipe, then calibration solves absolute output.

The existing PLANTED_AQUARIUM preset may be used as an initial spectral-shape reference, but its percentages are not a universal PAR target.

RGB and WRGB products require separate calibrated solutions.

Do not remove White from a WRGB result and assume the remaining RGB scene preserves PPFD.

---

# 38. Five firmware phases

V1 uses five managed-plan phases.

Let setup = aquarium setupDateEpochDay.

~~~text
Phase 0
validFrom = setup
validUntilExclusive = setup + 21
photoperiod = 360 min

Phase 1
validFrom = setup + 21
validUntilExclusive = setup + 28
photoperiod = 390 min

Phase 2
validFrom = setup + 28
validUntilExclusive = setup + 35
photoperiod = 420 min

Phase 3
validFrom = setup + 35
validUntilExclusive = setup + 42
photoperiod = 450 min

Phase 4
validFrom = setup + 42
validUntilExclusive = null
photoperiod = 480 min
~~~

All phases:

~~~text
weekdaysMask = 127
rampDurationMs = 3_600_000
~~~

unless a future versioned policy explicitly changes them.

---

# 39. Scene transitions across phases

Photoperiod and scene target are independent phase properties.

If calibrated production phase scenes differ:

- phase 0 starts from initialStartPercent of phase-0 target;
- every later phase interpolates from the previous phase target;
- transitionDays controls only scene interpolation;
- start/end/ramp schedule is the current phase schedule immediately at the date boundary.

This is exactly how firmware evaluates managed plans.

---

# 40. First-phase intensity transition

Firmware first-phase transition source:

~~~text
phase0.scene * initialStartPercent
~~~

Transition progresses by trusted local civil days.

For development placeholder:

~~~text
initialStartPercent = 50
transitionDays = 20
~~~

For production, both values are supplied by the calibrated startup-output policy.

---

# 41. Recommendation algorithm revision

Every recommendation should carry Android-owned metadata:

~~~text
algorithmRevision
plantCatalogRevision
calibrationRevision
evidenceIds
profileFingerprint
generatedAt
reevaluationDate
~~~

These values are Android metadata.

They are NOT added to the firmware managed-plan DTO.

Firmware receives executable phases only.

---

# 42. Reevaluation

Firmware can autonomously execute the installed phase plan offline.

Android does not need to remain connected for phase progression.

Android may reevaluate recommendation when:

- the tank profile changes;
- selected plants change;
- CO2 material selection changes;
- fixture assignment changes;
- user reruns Quick Setup;
- future algae/stress/maintenance signals are implemented;
- a versioned recommendation policy changes.

Firmware must never invent new biological policy.

---

# 43. Managed plan application contract

Android needs a dedicated boundary, for example:

~~~text
DeviceLightManagedAutoPlanOperations
~~~

Minimum operations:

~~~text
observe(deviceUid)
current(deviceUid)
read(deviceUid)
apply(deviceUid, expectedRevision, expectedStorageGeneration, plan)
delete(deviceUid, expectedRevision, expectedStorageGeneration, planId)
~~~

Do not reuse DeviceLightAutomaticOperations for managed-plan DTOs.

---

# 44. Apply concurrency

Apply requires both:

~~~text
expectedRevision
expectedStorageGeneration
~~~

On stale revision or stale storage generation:

1. do not blind retry;
2. re-read authoritative plan/status;
3. re-read aquarium Quick Setup context;
4. rebuild recommendation;
5. require a deliberate new Apply from the user if recommendation meaningfully changed.

---

# 45. Plan identity

Create:

~~~text
planId = null
~~~

Firmware allocates the stable plan ID.

Replace:

~~~text
planId = currently installed planId
~~~

Android does not invent plan IDs.

---

# 46. Delete behavior

Firmware rejects managed-plan deletion while AUTO is selected.

If Android later offers delete:

1. deliberately switch to MANUAL or CUSTOM;
2. confirm authoritative mode state;
3. call managed-plan delete.

Quick Setup creation/replacement does not need a separate mode change because Apply selects AUTO atomically.

---

# 47. Authoritative live status

After Apply, UI must not claim success solely because the request returned success.

Re-read authoritative firmware state.

Required status signals include:

~~~text
mode
auto.scheduleSource
auto.planRevision
auto.planInstalled
auto.planId
auto.activePlanPhaseIndex
auto.planRuntimeState
auto.planTransitionPermille
auto.nextPlanTransitionEpochDay
~~~

Expected active state:

~~~text
mode = AUTO
scheduleSource = MANAGED_PLAN
planInstalled = true
planId != null
~~~

---

# 48. Authoritative graph

For the live device screen, use light.graph.get.

When managed plan owns AUTO:

~~~text
basis = MANAGED_PLAN
autoSpans = []
planSpans = firmware current-day managed-plan spans
~~~

Android renders firmware graph points.

Android does not reconstruct today's graph from the original recommendation.

This guarantees that the displayed running program is the device's actual authoritative schedule.

---

# 49. Live UI after successful Apply

The user should clearly see that the plan is physically installed and selected on the device.

Example:

~~~text
Canlı

WRGB Pro Elite 120
Otomatik mod

Akıllı plan
Cihazda aktif

Faz
1 / 5 - Yeni tank

Bugün
10:00 - 16:00

Günlük süre
6 saat

Geçiş
%43

Sonraki faz
22. gün - 6,5 saat

Sonraki faz tarihi
<firmware nextPlanTransitionEpochDay>
~~~

The phase index, transition progress and next phase date come from firmware readback.

---

# 50. RTC behavior

Firmware uses trusted local civil date.

If RTC is not trusted:

~~~text
planRuntimeState = RTC_BLOCKED
output = dark
~~~

Android must display that state.

It must not locally continue the schedule using phone time as a hidden fallback.

---

# 51. Before-plan behavior

If trusted current date is before phase 0 validFrom:

~~~text
planRuntimeState = BEFORE_PLAN
output = dark
~~~

The normal AquaLight plan anchored to aquarium setup date should rarely produce this unless the aquarium setup date is in the future or data is invalid.

---

# 52. Device safety

Managed-plan output still passes through:

- Thermal Protection;
- hard Power Limiter;
- normal physical-output verification.

Android-authored graph/scene is requested intent.

Current effective output may be lower because of safety scaling.

UI must keep requested plan and effective live output conceptually separate.

---

# 53. SmartCare relationship

SmartCareLightingAdvisor is not the Quick Setup engine.

SmartCare remains maintenance/recommendation infrastructure.

Quick Setup is fixture-specific device automation.

They may share small evidence-backed domain policies but the Quick Setup engine must not depend on SmartCare worker execution.

---

# 54. Global Acclimation

Existing firmware Acclimation remains available for user AUTO/CUSTOM schedules.

Managed Smart Setup phases already model multi-day transition.

Therefore global Acclimation is bypassed by firmware while managed plan owns AUTO.

Android must not start an additional Acclimation cycle after managed-plan Apply.

---

# 55. Android recommendation engine

Proposed pure application engine:

~~~text
DeviceLightQuickSetupRecommendationEngine
~~~

Inputs:

~~~text
tank/device context
waterHeightCm
fixtureHeightAboveWaterCm
firstLightOnMinuteOfDay
CO2 readiness
managed-plan firmware policy
calibration profile
current local date only for validation/display
~~~

Output:

~~~text
plant profile
requested/effective PPFD
calibration result
five executable managed phases
warnings
evidence IDs
algorithm revision
profile fingerprint
~~~

No Fragment, DataStore or firmware-runtime types inside the pure decision engine.

---

# 56. Plant profile resolver

Suggested result:

~~~text
selectedPlantCount
uniqueSpeciesCount
lowDemandCount
mediumDemandCount
highDemandCount
highestDemand
plantCatalogRevision
~~~

The highest demand remains visible even when only one selected species is HIGH.

---

# 57. Quick Setup UI flow

Step 1 - profile summary

Show:

- aquarium name;
- tank age;
- dimensions;
- selected plant count;
- highest plant demand;
- substrate semantic;
- CO2 present/absent;
- exact Light product.

Step 2 - real water height

Step 3 - fixture height

Step 4 - first light-on time

Step 5 - CO2 confirmation, only if CO2 is present

Step 6 - calculate

Step 7 - recommendation review

Step 8 - managed plan Apply

Step 9 - authoritative readback success

Step 10 - live managed-plan status

---

# 58. Recommendation review

Before Apply show at least:

- current tank phase;
- first light time;
- current phase shutdown time;
- current phase photoperiod;
- mature photoperiod;
- daily ramp;
- five-phase progression;
- highest plant light demand;
- requested PPFD;
- effective PPFD;
- channel scene;
- calibration status;
- CO2 limit warning when applicable;
- why-this-plan explanation.

Development builds may show:

~~~text
Calibration: PLACEHOLDER
~~~

Release builds must never reach this state.

---

# 59. Fail-closed rules

Block recommendation or Apply when:

| Condition | Behavior |
|---|---|
| invalid deviceUid | block |
| device not registered | block |
| device not assigned to aquarium | block |
| multiple assigned Light fixtures | block V1 |
| unsupported product | block |
| no plants | block V1 plant automation |
| missing plant catalogId | block |
| unknown plant catalogId | block |
| missing setup date | block |
| invalid water height | input error |
| invalid fixture height | input error |
| start time cannot support future 8-hour same-day phase | input error |
| missing calibration | block |
| calibration geometry outside measured domain | block |
| insufficient fixture coverage | block |
| firmware RTC unavailable at Apply/readback | explain/block active claim |
| stale revision | reread/rebuild |
| stale storage generation | reread/rebuild |
| malformed plan | block |
| connection lost | retryable failure |
| output/storage failure | failure; no success claim |

---

# 60. Production calibration release gate

Production build must verify every product that advertises LIGHT_QUICK_SETUP.

Minimum checks:

~~~text
calibration exists
status == CALIBRATED
calibrationRevision > 0
exact channel layout supported
water-depth domain defined
fixture-height domain defined
tank-footprint / coverage model defined
no placeholder records reachable
~~~

This check belongs in CI/release tooling.

---

# 61. Tests - evidence policy

Unit tests must lock the V1 photoperiod policy:

~~~text
day 1  -> 360 min
day 21 -> 360 min
day 22 -> 390 min
day 28 -> 390 min
day 29 -> 420 min
day 35 -> 420 min
day 36 -> 450 min
day 42 -> 450 min
day 43 -> 480 min
day 365 -> 480 min
~~~

---

# 62. Tests - date anchoring

Verify phase boundaries are derived from setupDateEpochDay.

Test Quick Setup applied on:

- tank day 1;
- tank day 18;
- tank day 21;
- tank day 22;
- tank day 42;
- tank day 43;
- mature tank.

Firmware should select the correct phase immediately after Apply.

---

# 63. Tests - time validation

Test:

- 10:00 start: valid;
- 15:55 start with 5-minute UI step: valid for 8-hour mature phase;
- 16:00 start: invalid;
- future phases validated even when current phase is only 6 hours.

---

# 64. Tests - CO2

Test:

- no CO2 material -> no switch -> NOT_PRESENT;
- CO2 material + switch OFF -> PRESENT_NOT_PRECHARGED;
- CO2 material + switch ON -> PRESENT_PRECHARGED;
- displayed required precharge time wraps to previous day when needed.

---

# 65. Tests - managed plan contract

Test:

- exactly 5 contiguous phases;
- final phase open-ended;
- every weekdaysMask = 127;
- every daily schedule same-day;
- 60-minute ramp fits all phases;
- transitionDays valid relative to phase length;
- scene keys exactly match product channel layout;
- RGB and WRGB exact DTOs.

---

# 66. Tests - concurrency

Test:

- stale revision;
- stale storage generation;
- reconnect;
- event gap;
- no blind retry;
- context changed between recommendation and Apply;
- plant list changed after recommendation;
- device assignment changed after recommendation.

---

# 67. Tests - authoritative live state

After Apply verify:

~~~text
mode AUTO
scheduleSource MANAGED_PLAN
planInstalled true
planId non-null
active phase index expected
transitionPermille firmware value used
next transition epoch day firmware value used
graph basis MANAGED_PLAN
~~~

UI must render firmware readback rather than optimistic local state.

---

# 68. Implementation order

## Phase 0 - baseline health

Before feature coding, restore Android branch CI to a known green baseline.

The catalogId persistence commit introduced constructor changes and all fixtures/tests must compile with mandatory catalogId.

Do not start the Quick Setup vertical slice on an unknown failing baseline.

## Phase 1 - sync managed-plan firmware contract

Android protocol/client must add exact firmware managed-plan DTOs, commands, errors, status fields and graph fields from:

~~~text
AquaLight-Firmware
feature/smart-light-automation-plan
455298833668537fedc16b851067558815d2cc7b
~~~

No alternate Android contract.

## Phase 2 - managed-plan application boundary

Create dedicated DeviceLightManagedAutoPlanOperations.

## Phase 3 - Quick Setup context boundary

Resolve device -> assigned aquarium -> plants/materials/device capabilities.

## Phase 4 - evidence-backed pure policies

Implement:

- plant profile resolver;
- photoperiod phase policy;
- CO2 readiness policy;
- target PPFD policy;
- spectrum policy.

## Phase 5 - fixture calibration abstraction

Implement PLACEHOLDER development calibration plus release-block structure.

## Phase 6 - recommendation engine

Generate exactly five managed phases.

## Phase 7 - Quick Setup UI

Implement measurement/time/conditional CO2 input flow and recommendation review.

## Phase 8 - atomic managed-plan Apply

Use expectedRevision + expectedStorageGeneration and one light.auto.plan.apply.

## Phase 9 - authoritative live state

Implement status and graph rendering from firmware Managed Plan readback.

## Phase 10 - hardening

Lifecycle, SavedStateHandle, reconnect, stale state, double-submit, error mapping, accessibility, localization.

## Phase 11 - production physical calibration

Replace placeholder calibration with measured profiles.

Release guard must become green before production.

---

# 69. Definition of Done

Quick Setup is not complete until all are true:

- exact plant catalogId resolution;
- five-phase evidence-backed photoperiod schedule;
- setup-date anchored phases;
- real water height input;
- fixture height input;
- user-selected first light time;
- conditional CO2 precharge switch;
- same-day future-plan validation;
- product-specific calibration boundary;
- managed-plan client;
- exact managed-plan firmware Apply;
- no separate setMode after Apply;
- no DeviceLightAutomaticOperations.create write path for Smart Setup;
- global Acclimation not started by Android;
- authoritative managed-plan status readback;
- authoritative graph basis MANAGED_PLAN;
- current phase displayed;
- transitionPermille displayed from firmware;
- nextPlanTransitionEpochDay displayed from firmware;
- stale revision/generation handled without blind retry;
- RTC_BLOCKED displayed correctly;
- multiple Light fixtures blocked in V1;
- placeholder calibration blocked from release;
- unit tests green;
- integration tests green;
- Detekt green;
- Android Lint green;
- Android CI green;
- emulator integration green;
- CodeQL green;
- physical production calibration completed.

---

# 70. Final V1 product policy summary

User chooses:

~~~text
real water height
fixture height above water
first light-on time
CO2 precharge confirmation only when CO2 is installed
~~~

Android derives:

~~~text
exact selected plants
maximum plant light demand
tank age
tank dimensions
substrate semantic
device product/channel layout
target PPFD
fixture-calibrated scene
five date phases
~~~

Default photoperiod lifecycle:

~~~text
Day 1-21   6:00
Day 22-28  6:30
Day 29-35  7:00
Day 36-42  7:30
Day 43+    8:00
~~~

Daily ramp:

~~~text
60 minutes sunrise
60 minutes sunset
~~~

Firmware receives one complete managed plan and autonomously advances phases while offline.

Android remains the biological recommendation authority.

Firmware remains the execution, persistence, date, concurrency and physical-safety authority.
