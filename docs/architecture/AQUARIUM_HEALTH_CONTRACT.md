# AquaLight Aquarium Health Architecture Contract

**Status:** Normative architecture contract; Stage 1 persistence implementation is in progress.

**Working branch:** `feat/aquarium-health-contract`

**Base branch:** `feat/cooler-hardware-catalog`

**Implementation scope:** This contract governs the Stage 1 persistence work on this branch. Health UI/navigation remains deferred until Stage 1 is green.

---

## 1. Purpose

Aquarium Health is a new product domain that turns existing aquarium state, maintenance history, livestock/plant catalog context, user-entered water measurements, livestock observations, and plant/algae observations into explainable tank-health, livestock-health, and plant-health assessments.

The domain has three product surfaces inside one health destination:

1. **Tank Health**
   - water-quality measurements;
   - maintenance recency;
   - tank configuration;
   - installed/selected equipment;
   - livestock context;
   - later, eligible device telemetry.

2. **Livestock Health**
   - livestock inventory;
   - catalog-backed water requirements;
   - user observations/symptoms;
   - evidence-backed possible factors;
   - recommendations derived from available evidence.

3. **Plant Health**
   - saved plant identities from the tank;
   - user plant symptoms and algae observations;
   - water-test and maintenance evidence;
   - selected substrate/soil and fertilizer context;
   - assigned-light evidence through application boundaries;
   - later, source-backed plant requirements and automation recommendations.

Health is not a settings domain and is not a replacement for Maintenance, Tank Life, device control, or catalog ownership. It reads those authoritative domains and owns only data that is intrinsically health-specific.

The first commercial implementation must remain deterministic, local-first, explainable, owner-scoped, and fail-closed.

---

## 2. Product placement and navigation contract

### 2.1 Existing Tank Detail tabs remain unchanged

The Tank Detail top-level tabs remain:

- Devices;
- Activity;
- Tank;
- Plants;
- Tank Life.

Health must **not** become a sixth top-level Tank Detail tab. The current five-tab information architecture remains authoritative.

### 2.2 Entry point

The existing **Tank** tab receives one Health summary card near the top of the Tank content.

The card is a read-only summary surface. It may show:

- current health status;
- number of findings requiring attention;
- latest water-test age when available;
- an explicit insufficient-data state.

The card must never show a positive state merely because health data is absent.

Selecting the card opens the dedicated Health destination for that tank.

### 2.3 Health destination

The product has one Health destination:

`TankHealthFragment(tankId)`

The Health destination contains three local sections:

- **Tank Health** — selected by default;
- **Livestock Health**;
- **Plant Health**.

These are tabs/segments within the Health destination. They are not separate top-level navigation destinations.

Opening Health therefore follows:

`TankDetailFragment -> TankHealthFragment`

and not:

`TankDetailFragment -> HealthOverview -> TankHealth/LivestockHealth`.

There is no intermediate Health overview screen.

### 2.4 Central navigation ownership

Health must use the existing central navigation architecture:

- the existing application `NavController`;
- the existing aquarium navigation graph;
- Safe Args for destination arguments;
- existing guarded navigation conventions.

No Health screen may create a parallel `NavController`, private navigation graph, Activity-owned side route, or ad-hoc Fragment transaction path.

Extracted presentation coordinators may calculate a semantic target but may not call `navigate()` themselves unless they are an established navigation owner under the central navigation contract.

### 2.5 Return behavior

Back from Health returns to the Tank Detail destination with the Tank tab selected.

A future entry point from Tank Life may open the same Health destination with Livestock Health selected and, optionally, a livestock target. A future Plants entry point may open the same destination with Plant Health selected and, optionally, a plant target. Neither flow may create a parallel Health destination.

---

## 3. Existing authoritative domains

Health must reuse the current application boundaries rather than duplicating their state.

### 3.1 Aquarium tank state

`AquariumTankOperations` remains authoritative for:

- tank identity;
- name and description;
- setup date;
- dimensions;
- tank type and style;
- plants;
- material/equipment selections;
- livestock records;
- Smart Care and care-reminder settings.

Health may consume `AquariumTankSnapshot`. It must not persist copies of tank dimensions, tank type, materials, plants, or livestock quantities as Health-owned authoritative state.

### 3.2 Maintenance state

`MaintenanceOperations` remains authoritative for:

- completed water changes;
- completed filter maintenance/change;
- plant trimming;
- water-test care tasks;
- temperature checks;
- other care-task history;
- manual and automatic care tasks.

Health may derive recency facts from completed care history.

Health must not copy “last water change”, “last filter maintenance”, or similar timestamps into the Health store.

### 3.3 Livestock catalog and water requirements

The existing application contracts remain authoritative:

- `LivestockCatalogOperations`;
- `LivestockWaterRequirements`;
- `LivestockWaterCompatibilityEvaluator`;
- `LivestockWaterAdvisorOperations`;
- `AquariumWaterSnapshot`.

Catalog-backed livestock water requirements remain canonical in the livestock catalog and are resolved through stable `catalogEntryId`.

Health must not duplicate per-species requirement ranges into tank records or Health records.

### 3.4 Device data

Existing device/application boundaries remain authoritative for device state and telemetry.

Health must never read a device repository, BLE client, WebSocket client, device UI ViewModel, or presentation state directly.

When device evidence is integrated, it must enter Health through a read-only application boundary with:

- the tank identity;
- measurement identity;
- canonical value/unit;
- source device identity when needed internally;
- capture timestamp;
- freshness state.

Device presence is not equivalent to a measured condition.

Examples:

- CO2 equipment present != CO2 concentration is high;
- cooler present != water temperature is normal;
- filter selected != filtration is sufficient.

---

## 4. Health-owned authoritative data

Health owns exactly three new categories of user-created records in the first implementation:

1. water-test records;
2. livestock-health observations;
3. plant-health/algae observations.

Derived assessments, findings, statuses, explanations, and recommendations are not authoritative persisted user data in v1. They are calculated from authoritative inputs.

### 4.1 Proposed application boundary

The persistence-facing application contract is:

`AquariumHealthRecordOperations`

Conceptual responsibilities:

```kotlin
interface AquariumHealthRecordOperations {
    fun waterTests(tankId: Long): Flow<List<AquariumWaterTestRecord>>
    fun livestockObservations(
        tankId: Long
    ): Flow<List<LivestockHealthObservation>>
    fun plantObservations(
        tankId: Long
    ): Flow<List<PlantHealthObservation>>

    suspend fun addWaterTest(input: AquariumWaterTestInput): Long
    suspend fun updateWaterTest(
        testId: Long,
        input: AquariumWaterTestInput
    )
    suspend fun deleteWaterTest(testId: Long)

    suspend fun addLivestockObservation(
        input: LivestockHealthObservationInput
    ): Long
    suspend fun updateLivestockObservation(
        observationId: Long,
        input: LivestockHealthObservationInput
    )
    suspend fun deleteLivestockObservation(observationId: Long)

    suspend fun addPlantObservation(
        input: PlantHealthObservationInput
    ): Long
    suspend fun updatePlantObservation(
        observationId: Long,
        input: PlantHealthObservationInput
    )
    suspend fun deletePlantObservation(observationId: Long)
}
```

The final implementation may adjust names, but it must preserve this boundary and ownership semantics.

UI must depend on application contracts only. UI must not construct or consume the concrete Health store/manager.

---

## 5. Water-test record contract

### 5.1 Event model

A water test is a point-in-time user record, not a mutable field on the tank.

Conceptual model:

```kotlin
data class AquariumWaterTestRecord(
    val id: Long,
    val tankId: Long,
    val measuredAtMillis: Long,
    val readings: List<AquariumWaterReading>,
    val note: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

data class AquariumWaterReading(
    val parameter: HealthWaterParameter,
    val value: Double
)
```

A test may contain any valid subset of supported parameters but must contain at least one reading.

Missing values stay missing. They are never replaced with zero.

### 5.2 Canonical parameter set

Health needs a superset of the existing livestock compatibility parameters.

The first contract reserves these stable parameter identities:

- `TEMPERATURE_C`;
- `PH`;
- `GH_DGH`;
- `KH_DKH`;
- `TDS_PPM`;
- `TOTAL_AMMONIA_PPM`;
- `NITRITE_PPM`;
- `NITRATE_PPM`;
- `PHOSPHATE_PPM`;
- `DISSOLVED_OXYGEN_MG_L`;
- `CO2_MG_L`;
- `SPECIFIC_GRAVITY`;
- `ALKALINITY_DKH`;
- `CALCIUM_PPM`;
- `MAGNESIUM_PPM`;
- `PAR_UMOL_M2_S`.

Exact naming may be refined before implementation only if the same chemical meaning and stable persistence identity are preserved.

User-visible labels are localized resources and are never used as persisted parameter identities.

### 5.3 Canonical storage units

Persistent values use one canonical unit per parameter:

- temperature: degrees Celsius;
- pH: dimensionless pH value;
- GH: dGH;
- KH/alkalinity where defined: dKH;
- TDS: ppm;
- ammonia, nitrite, nitrate, phosphate, calcium, magnesium, CO2: the parameter's defined ppm or mg/L canonical contract;
- dissolved oxygen: mg/L;
- specific gravity: dimensionless;
- PAR: micromoles per square metre per second.

UI may display supported alternate units later, but conversion must occur at the application boundary and persisted values remain canonical.

A parameter must not silently change chemical meaning during conversion. In particular, ammonia-related labels and storage semantics must be scientifically explicit before implementation; “NH3”, “NH4”, total ammonia, and total ammonia nitrogen must not be treated as interchangeable aliases.

### 5.4 Validation

A shared application-level `AquariumHealthMeasurementPolicy` must validate water input.

Required rules:

- record ID and tank ID are positive;
- at least one reading exists;
- one test contains at most one reading for each parameter;
- every numeric value is finite;
- parameter-specific physical/product bounds are enforced;
- invalid, ambiguous, non-finite, negative-when-impossible, or unsupported values are rejected;
- values are never silently clamped;
- locale parsing follows the existing AquaLight measurement contract;
- stored numeric values are locale-independent;
- `measuredAtMillis` cannot be a future observation time;
- `createdAtMillis` and `updatedAtMillis` are system-owned;
- note text follows the product's canonical text-length/whitespace policy.

Exact aquarium-health warning thresholds are not persistence validation limits. Persistence rejects impossible/malformed values; the assessment rule catalog determines health significance.

### 5.5 Partial tests and stale-data prevention

A partial test must remain partial.

If a user records only pH today and nitrate seven days ago, Health must not silently merge those two records into a synthetic “today” test without retaining the age and provenance of each value.

For v1:

- one `AquariumWaterSnapshot` passed to the existing compatibility evaluator must be constructed from one coherent test record or one coherent evidence set;
- absent parameters remain null;
- no older measurement is silently promoted to the timestamp of a newer test.

A future multi-source snapshot may combine measurements only if every parameter retains its own source timestamp and freshness metadata.

### 5.6 Ordering

Water-test history is ordered by:

1. `measuredAtMillis` descending;
2. a deterministic secondary identity such as record ID.

Inserting a backdated test does not make it the current test unless its measurement timestamp is actually the newest.

---

## 6. Water parameters shown by tank type

The editor must be driven by a parameter catalog/policy rather than hardcoded Fragment branches.

Examples:

### Freshwater/planted baseline

Commonly relevant inputs may include:

- temperature;
- pH;
- GH;
- KH;
- TDS;
- total ammonia;
- nitrite;
- nitrate;
- phosphate;
- dissolved oxygen;
- CO2 where actually measured.

### Marine/reef baseline

Commonly relevant inputs may additionally include:

- specific gravity;
- alkalinity;
- calcium;
- magnesium;
- PAR.

The exact visible set is a product/domain policy and must be testable.

Users may still record a valid supported parameter when it is not part of the default quick set. Hidden-by-default must not mean unsupported.

---

## 7. Relationship between water tests and Maintenance WATER_TEST

These are different facts.

`CareTaskType.WATER_TEST` means:

> a water-test activity/task exists or was completed.

`AquariumWaterTestRecord` means:

> these concrete measurements were recorded at this time.

The first Health persistence stage must not silently:

- create a Maintenance task when a measurement is saved;
- complete a pending Maintenance task;
- duplicate measurement values into a care-task note;
- derive a measurement record merely because a WATER_TEST task was completed.

A later explicit orchestration policy may connect the two domains, but it must be idempotent and user-visible.

Maintenance remains authoritative for care-task history. Health remains authoritative for measurement values.

---

## 8. Livestock-health observation contract

### 8.1 Observations, not diagnoses

Users record observable conditions/symptoms.

The product must not require the user to diagnose a disease before entering an observation.

Examples of stable symptom concepts include:

- fish staying at the surface;
- rapid breathing;
- loss of appetite;
- abnormal hiding;
- color loss;
- abnormal swimming;
- visible spots;
- fin damage.

User-visible copy is localized separately.

### 8.2 Stable symptom identity

Symptom records use stable locale-independent keys, for example:

- `fish_surface_gasping`;
- `fish_rapid_breathing`;
- `fish_loss_of_appetite`.

The exact catalog is implemented later in a dedicated `LivestockSymptomCatalog`.

A localized title such as “Balıklar su yüzeyinde geziyor” must never be the persistent identity.

### 8.3 Observation model

Conceptual model:

```kotlin
data class LivestockHealthObservation(
    val id: Long,
    val tankId: Long,
    val livestockId: Long?,
    val categoryKey: String,
    val symptomKey: String,
    val intensity: ObservationIntensity,
    val observedAtMillis: Long,
    val note: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
```

`livestockId == null` represents a category/group-level observation.

A non-null livestock ID targets one saved `AquariumLivestock` record.

### 8.4 Observation integrity

Required rules:

- IDs are positive;
- tank exists in the active owner scope;
- a non-null livestock ID belongs to that tank;
- category key is canonical;
- symptom key exists in the symptom catalog;
- symptom applicability includes the selected category;
- observation time is not in the future;
- intensity is a canonical enum;
- free-text note is optional and bounded;
- no localized symptom string is persisted as identity.

### 8.5 Livestock deletion

The first commercial Health implementation must not leave dangling specific-livestock observation references.

Livestock deletion is a cross-store integrity operation because the authoritative livestock
record belongs to the aquarium tank store while specific-livestock Health observations
belong to the Health store. A sequential "delete livestock, then try Health cleanup" flow
is not sufficient.

The deletion workflow must be owner-pinned and compensating:

1. validate the tank/livestock target under the captured owner;
2. capture the specific-livestock Health observation snapshot required for rollback;
3. remove the dependent Health observations;
4. delete the authoritative livestock record;
5. if the authoritative livestock deletion fails, restore the captured Health observations
   before returning the failure;
6. if process death interrupts the operation, recovery must converge to either:
   - livestock present + its pre-delete specific observations restored; or
   - livestock absent + no specific observations targeting that livestock.

The inverse partial state is forbidden:

> livestock absent while specific Health observations for that deleted `livestockId`
> remain reachable.

When a saved livestock record is successfully deleted:

- observations targeting that specific `livestockId` are deleted;
- category-level observations remain;
- no name-based reassignment occurs;
- no observation is reassigned to another livestock record merely because display names
  or catalog names match.

If historical preservation of deleted-livestock observations is desired later, that requires a separate explicit archival contract; it must not be improvised with orphan IDs or copied localized names.

---

## 9. Persistence architecture

### 9.1 Separate Health store

Health-owned records belong in a dedicated owner-scoped Health store instead of expanding the authoritative aquarium tank record with unrelated histories.

The planned store is versioned independently, conceptually:

`aquarium_health.proto`

with initial schema version `1`.

The exact filename is implementation detail; the independent-store boundary is normative.

### 9.2 No legacy inference

Health is a new domain.

Its first persistent schema must not:

- infer historical water measurements from maintenance notes;
- infer symptoms from livestock notes;
- parse old display strings into structured health data;
- copy current tank state into synthetic historical Health records.

No legacy Health migration is required because there is no previous Health store.

### 9.3 Owner isolation

All Health records are owner-scoped.

The concrete adapter must pin each multi-step operation to the immutable owner active when the operation starts, following the existing aquarium/care commercial boundary.

An owner switch must not:

- leak another owner's water tests;
- leak observations;
- continue writes into a newly active owner's store;
- reuse another owner's assessment cache.

### 9.4 Tank deletion

Before Health persistence is commercially enabled, tank deletion must include Health cleanup in the authoritative tank-deletion workflow.

Deleting a tank must remove:

- its water-test records;
- its livestock-health observations;
- its plant-health/algae observations;
- any Health-owned derived cache if one is ever introduced.

Health cleanup must participate in the same fail-closed/compensating integrity design as other dependent tank data. A tank may not be stably deleted while owner-visible Health records for that tank remain reachable.

### 9.5 Account deletion and retention

Health follows the existing local aquarium-content retention contract:

- records remain local/private;
- they persist until the user deletes them, deletes the owning tank/account data, clears app data, or uninstalls, according to existing product retention rules;
- logout alone must follow the existing owner-scoped local-data policy;
- v1 must not silently auto-prune user-created Health history merely for convenience.

If retention limits are added later, they require an explicit product/data-retention decision.

### 9.6 Corruption behavior

Unsupported/missing schema versions and invalid records fail closed under the existing local-data integrity philosophy.

Corruption must not be repaired by:

- substituting zeros;
- dropping invalid measurements silently;
- guessing a tank or livestock identity;
- guessing a parameter;
- coercing an unsupported enum.

Recovery reporting must integrate with the existing local recovery mechanism before commercial release.

---

## 10. Assessment architecture

Persistence and assessment are separate responsibilities.

### 10.1 Read-only assessment boundary

The derived read contract is conceptually:

`AquariumHealthAssessmentOperations`

It produces a Health read model from authoritative sources and Health-owned records.

It does not own the input records.

### 10.2 Assessment input

A complete assessment may consume:

- `AquariumTankSnapshot`;
- completed Maintenance history;
- latest/relevant Health water tests;
- Health livestock observations;
- livestock catalog entries;
- `LivestockWaterRequirements`;
- existing `LivestockWaterAdvisorOperations`;
- future read-only device evidence.

The assessment engine must not consume Fragment bindings, Android Views, localized strings, or data-store implementation models.

### 10.3 Pure deterministic engine

Core evaluation logic must be deterministic and testable without Android.

Conceptually:

```kotlin
interface AquariumHealthEvaluator {
    fun evaluate(
        input: AquariumHealthAssessmentInput
    ): AquariumHealthAssessment
}
```

The evaluator:

- reads immutable input;
- produces immutable output;
- performs no persistence;
- creates no Maintenance task;
- sends no notification;
- issues no device command;
- does not access time implicitly when a clock/reference time can be provided as input.

### 10.4 Derived data is not source of truth

In v1, the current assessment is recomputed from source records.

A derived finding must not become authoritative merely because it was displayed once.

If an assessment cache is introduced later:

- it is explicitly non-authoritative;
- it includes engine/rule version;
- it can be discarded and rebuilt;
- source records remain authoritative.

---

## 11. Assessment output contract

### 11.1 Status

Initial Health UX uses categorical status, not a synthetic 0–100 score.

Required status concepts:

- `INSUFFICIENT_DATA`;
- `GOOD`;
- `ATTENTION`;
- `CRITICAL`.

“GOOD” requires affirmative evaluated evidence. No-data is never GOOD.

A numeric score may be added only after a versioned, documented scoring policy defines weighting, confidence, stale evidence, missing evidence, and category aggregation.

### 11.2 Findings

Conceptual finding:

```kotlin
data class HealthFinding(
    val key: String,
    val scope: HealthFindingScope,
    val severity: HealthSeverity,
    val confidence: HealthConfidence,
    val targetLivestockId: Long?,
    val evidence: List<HealthEvidence>,
    val recommendationKeys: List<String>
)
```

Finding keys are stable and locale-independent.

Examples:

- `water_nitrite_out_of_policy`;
- `livestock_temperature_out_of_range`;
- `maintenance_water_change_overdue_context`.

User-visible text resolves from finding/recommendation keys in the presentation/text boundary.

### 11.3 Evidence

Every non-informational finding must be explainable through structured evidence.

Evidence concepts include:

- water measurement;
- measurement age/freshness;
- completed maintenance history;
- livestock catalog requirement;
- tank dimensions/volume;
- livestock quantity;
- equipment presence;
- device measurement;
- user observation.

Evidence includes enough identity and timestamp information to explain why the result exists.

The UI must be able to answer:

> “Why is this being shown?”

without reconstructing logic from localized text.

### 11.4 Severity and confidence are different

Severity describes the importance of the finding.

Confidence describes how strongly current evidence supports it.

A severe possible factor with weak evidence must not be worded as a confirmed cause.

Directly measured facts and causal claims are not interchangeable.

Example:

- “Nitrite measured above the configured safe policy” can be measurement-backed.
- “Nitrite caused the fish to stay at the surface” is a causal interpretation and must not be presented as certain merely because both facts exist.

### 11.5 Unknown and missing evidence

Missing evidence is first-class state.

The engine must prefer:

- “No recent nitrite measurement”;
- “CO2 level cannot be confirmed”;
- “No comparable catalog water requirement”;

over fabricated normality.

---

## 12. Freshness and provenance

Every evidence source capable of becoming stale must carry a timestamp/freshness policy.

A rule catalog defines the maximum usable age for each evidence type where relevant.

The engine must distinguish:

- current;
- stale but historically relevant;
- unavailable.

A stale measurement may be displayed in history but must not silently support a current-state conclusion beyond its allowed freshness window.

Maintenance recency is inherently historical and is evaluated as recency, not mislabeled as a current sensor measurement.

Device telemetry must carry capture time and freshness. Cached device state with unknown age cannot be treated as live evidence.

---

## 13. Livestock water compatibility reuse

The existing `LivestockWaterCompatibilityEvaluator` remains the authoritative primitive for comparing supported measured parameters to catalog-backed livestock ranges.

Health integration must adapt Health water readings into the existing `AquariumWaterSnapshot` only for parameters whose semantics are identical.

Health must not fork a second pH/temperature/GH/KH/nitrate compatibility algorithm.

Existing outcomes such as:

- compatible;
- out of range;
- no comparable measurements;
- custom/unverified;
- missing catalog entry;

remain meaningful inputs into Livestock Health.

Health may add higher-level findings around these outputs but must preserve the evaluator's stable identity semantics.

---

## 14. Critical inference rules

### 14.1 CO2

The presence of CO2 equipment is evidence only that a CO2 system is configured/selected.

It is **not** evidence that dissolved CO2 is high.

Health must not state “CO2 is high” unless supported by an accepted measurement or an explicitly implemented and labeled estimation method with adequate inputs and provenance.

If a future pH/KH-based CO2 estimate is added:

- it is labeled as an estimate;
- its assumptions are documented;
- it is not presented as equivalent to a direct sensor result;
- it has a distinct evidence type.

### 14.2 Stocking/bioload

Livestock quantity divided by tank litres is not, by itself, a commercially acceptable overstocking rule.

Current livestock records provide quantity, and the catalog currently provides water requirements, but a reliable stocking assessment may require species-specific information such as:

- adult size;
- minimum tank volume;
- territorial/social requirements;
- bioload class;
- filtration assumptions;
- mixed-species constraints.

Until a reviewed stocking-requirement contract exists, Health may show factual context such as total recorded quantity and tank volume, but must not claim:

> “There are too many livestock for this tank”

from count/volume alone.

### 14.3 Equipment capability

Selected hardware does not prove capability/performance.

Examples:

- a filter selection does not prove sufficient turnover;
- a heater selection does not prove water temperature;
- a light selection does not prove actual PAR;
- a CO2 selection does not prove concentration.

Capability rules require explicit structured hardware metadata or device evidence.

### 14.4 Correlation versus diagnosis

Livestock Health presents **possible contributing factors**, not veterinary diagnoses.

A symptom may have multiple causes. The engine must rank/show only factors supported by available rule evidence and use language appropriate to confidence.

The first implementation must not claim a disease diagnosis solely from a symptom checklist.

Where observations indicate potentially serious animal-welfare risk and the app lacks sufficient evidence, the UI should advise checking measured water conditions promptly and seeking qualified aquarium/veterinary guidance where appropriate rather than inventing certainty.

---

## 15. Example: fish staying near the water surface

User observation:

`fish_surface_gasping`

The symptom itself triggers no single diagnosis.

Potential evidence checks may include:

- recent dissolved-oxygen measurement when available;
- recent water temperature and catalog temperature ranges;
- recent ammonia/nitrite measurements;
- other relevant water-quality measurements;
- CO2 measurement if actually available;
- water-change recency as context;
- filtration/aeration capability only when structured capability evidence exists.

Possible output:

- measured nitrite is abnormal -> strong water-quality finding;
- measured temperature is above one or more catalog ranges -> supported temperature finding;
- last water change is old -> contextual maintenance finding;
- CO2 equipment exists but no concentration measurement -> CO2 high is **not confirmed**;
- no dissolved-oxygen reading -> oxygen level remains unknown.

The recommendation layer may advise measurement/check actions before stronger claims.

---

## 16. Symptom catalog contract

`LivestockSymptomCatalog` is a static application/product catalog.

Each definition conceptually contains:

- stable `key`;
- applicable livestock categories;
- localized title resource;
- localized description resource if needed;
- candidate factor keys;
- required/optional evidence types;
- emergency/escalation metadata if domain-reviewed.

The catalog must not embed free-form user-visible Turkish/English strings in Kotlin logic.

English source strings live in the authoritative default resources and Turkish strings in `values-tr`, following the existing localization contract.

Adding a symptom requires tests for:

- stable unique key;
- category applicability;
- resource presence;
- recommendation/factor references;
- duplicate prevention.

---

## 17. Recommendation contract

Recommendations are structured products of findings, not arbitrary prose generated inside a Fragment.

Conceptual recommendation:

```kotlin
data class HealthRecommendation(
    val key: String,
    val priority: HealthRecommendationPriority,
    val findingKeys: List<String>,
    val action: HealthRecommendationAction?
)
```

Examples of actions:

- open water-test editor;
- open Maintenance history;
- open Tank settings/details;
- open Tank Life;
- later, create a proposed care task.

A recommendation must not directly:

- execute a device command;
- delete data;
- complete Maintenance;
- change dosing/lighting/cooling;
- create a care task without the automation/user-confirmation policy.

Recommendations must retain rationale linkage to findings/evidence.

---

## 18. Tank Health UI contract

Tank Health is the default section when Health opens.

Initial information hierarchy:

1. status summary;
2. Water Quality;
3. Maintenance Status;
4. System Summary.

### 18.1 Status summary

Shows:

- categorical current status;
- number of attention/critical findings;
- insufficient-data state when appropriate;
- latest assessment time if useful.

No numeric 0–100 score in v1.

### 18.2 Water Quality

Shows supported recorded parameters and their latest coherent values with timestamps/freshness.

Required action:

**Add Water Analysis**

This opens a dedicated editor destination or approved editor surface suitable for multi-field validated input.

The editor must not be implemented as an unstructured note.

### 18.3 Maintenance Status

Read-only data derived from Maintenance.

Examples:

- last water change;
- last filter maintenance/change;
- last plant trim where relevant.

Editing/completing those activities remains owned by Maintenance/Activity.

### 18.4 System Summary

Read-only context may include:

- CO2 equipment presence;
- filter selection;
- lighting selection;
- relevant cooling/heating presence;
- recorded livestock quantity.

This section is context, not proof of water quality.

---

## 19. Livestock Health UI contract

Livestock Health is the second section of the same `TankHealthFragment`.

Initial information hierarchy:

1. livestock status summary;
2. Add Observation action;
3. recent observations;
4. evidence-backed possible factors and recommendations.

### 19.1 Target selection

An observation can target:

- a saved livestock record; or
- a supported category/group when no specific record is appropriate.

The UI must not invent a catalog-backed identity for custom livestock.

### 19.2 Observation entry

Observation flow:

1. select target;
2. select symptom from the applicable symptom catalog;
3. optional intensity;
4. observation date/time;
5. optional note;
6. save.

The UI never persists localized symptom text as identity.

### 19.3 Possible factors

Possible factors are produced by the assessment engine.

They must visibly distinguish:

- measured/confirmed facts;
- supported associations;
- unavailable evidence.

The UI must not reorder or relabel weak evidence as certainty.

---

## 20. Read-model composition

The Health UI should consume one lifecycle-safe application/presentation state rather than independently joining repositories in the Fragment.

Conceptually:

`TankHealthViewModel -> AquariumHealthAssessmentOperations + AquariumHealthRecordOperations`

The ViewModel may expose:

```kotlin
StateFlow<TankHealthUiState>
```

The Fragment owns rendering and user events only.

It must not:

- query tank storage;
- query care storage;
- query device repositories;
- perform compatibility calculations;
- calculate status/severity;
- build diagnostic explanations.

---

## 21. Automation separation

Health assessment and automation are separate domains.

### 21.1 Direction of dependency

The allowed direction is:

`Authoritative data -> Health Assessment -> Automation candidate`

not:

`Automation task -> Health evidence -> same Automation task`.

### 21.2 Health does not create tasks

`AquariumHealthEvaluator` never writes a `CareTask`.

A later Health automation policy consumes findings.

Conceptually:

`HealthAutomationPolicy.evaluate(assessment, existingCareState)`

and produces proposed actions.

### 21.3 Smart Care gate

Automatic Health task generation must respect the tank's existing Smart Care setting.

Notifications must continue to respect the existing reminder/delivery policy.

Health may not bypass:

- `smartCareEnabled`;
- care reminder settings;
- delivery-time revalidation;
- owner scope.

### 21.4 Idempotency and cooldown

A future generated Health task requires a stable identity derived from at least:

- tank ID;
- finding/rule key;
- target identity where applicable;
- rule/engine version where necessary.

The automation layer must prevent repeated equivalent pending tasks.

A rule-specific cooldown/rearm policy must exist before repeated notifications/tasks are enabled.

### 21.5 Resolution

When evidence changes and a finding resolves:

- the Health assessment resolves immediately on recomputation;
- user-created history remains;
- automatic-task reconciliation follows a separate explicit policy;
- Health must not silently mark a care task completed.

### 21.6 Feedback-loop prevention

Health may consume completed maintenance history.

Health must not treat the existence of its own pending generated recommendation/task as evidence that tank conditions improved or worsened.

---

## 22. Engine/rule versioning

Rules that generate findings must have stable keys.

A versioned rule policy is required when behavior changes in a way that affects:

- thresholds;
- evidence requirements;
- severity;
- recommendation mapping;
- automation idempotency.

Current assessments may be recomputed under the current engine.

Historical user-entered water tests and observations must never be rewritten because rules changed.

If derived assessment history is persisted later, every snapshot must carry the engine/rule version that produced it.

---

## 23. Threshold governance

Exact water-quality thresholds must not be scattered through UI code.

They belong in a reviewed Health rule/policy catalog.

Threshold definitions must state:

- parameter;
- applicable tank/water type;
- expected/attention/critical range;
- required evidence freshness;
- scientific/product source used during domain review;
- whether the rule is absolute or context/species dependent.

Livestock-specific ranges continue to come from the livestock catalog where applicable.

A global tank-water policy and a species compatibility policy are separate concepts and must not overwrite one another.

---

## 24. Status aggregation

Overall Tank Health status is derived from findings, not persisted manually.

Minimum aggregation semantics:

- any active critical finding can escalate overall status to CRITICAL;
- active attention findings can produce ATTENTION;
- GOOD requires sufficient evaluated evidence and no active attention/critical finding;
- missing critical evidence can produce INSUFFICIENT_DATA rather than GOOD.

Livestock Health can expose per-livestock/per-category status and an aggregate livestock status.

A future numeric score requires its own versioned scoring contract and must not be reverse-engineered from arbitrary severity weights.

---

## 25. Custom livestock

Custom livestock has a stable custom `catalogEntryId` under the existing aquarium contract but has no verified catalog water requirements unless explicitly added later.

Health behavior:

- preserve observations;
- show water measurements;
- show tank-level findings;
- do not invent species-specific safe ranges;
- use the existing custom/unverified assessment semantics;
- clearly label species-specific compatibility as unavailable.

Name matching must never be used to guess a catalog entry.

---

## 26. Missing catalog entries

If a saved catalog identity cannot be resolved:

- do not infer by display name;
- retain the saved livestock record;
- report the catalog-entry-missing assessment state;
- allow generic tank-level Health evaluation;
- suppress unsupported species-specific conclusions.

This is fail-closed behavior.

---

## 27. Device telemetry integration stage

Device telemetry is deliberately not required for initial Health persistence/UI.

When introduced:

1. define a read-only application Health-evidence boundary;
2. normalize units before Health evaluation;
3. include capture timestamp;
4. enforce freshness;
5. preserve device source/provenance;
6. never let Health issue device commands;
7. never let presentation ViewModels become Health data sources.

User-entered water tests and device measurements remain distinguishable evidence sources.

A device reading must not overwrite a user-entered test record.

---

## 28. Concurrency and lifecycle

Health writes must follow the existing commercial owner/data integrity posture.

Required behavior:

- concurrent water-test writes allocate unique IDs atomically;
- concurrent observation writes allocate unique IDs atomically;
- update/delete targets are revalidated inside the authoritative write transaction;
- owner changes cannot split a multi-step operation;
- stale Fragment instances cannot write after their owner/tank scope is invalid;
- process recreation does not duplicate a submitted record;
- UI disables/rejects duplicate save actions while a write is active;
- cancellation is handled without leaving partial records.

---

## 29. Localization and measurement entry

Health follows the existing Stage 11 localization contract.

Required behavior:

- English authoritative strings in `values`;
- complete Turkish strings in `values-tr`;
- stable IDs/enum keys are never translated;
- decimal parsing uses the supported app locale policy;
- persisted values remain locale-independent;
- no grouping-like ambiguous input is silently reinterpreted;
- formatted units are localized at presentation boundaries;
- accessibility labels include parameter name, value, unit, and status where applicable.

Health must not introduce direct `Locale.getDefault()` measurement parsing or raw string concatenation for user-visible values.

---

## 30. Accessibility

Every Health action and status must be operable and understandable through accessibility services.

Minimum requirements:

- Health summary card has one coherent accessible description;
- Tank Health/Livestock Health selection exposes selected state;
- water parameter cards expose name, value, unit, freshness, and status;
- color is never the only representation of GOOD/ATTENTION/CRITICAL;
- symptom rows expose selected state;
- finding severity icons have text equivalents;
- dynamic assessment changes are announced where appropriate without excessive interruption;
- touch targets follow existing AquaLight minimum-target ownership;
- 200% font and RTL evidence must not clip Health content.

---

## 31. Privacy and local-data policy

Initial Health records are local aquarium content.

They must not be uploaded to Firebase/remote services as part of Health implementation.

A future remote inference/AI feature would require a separate explicit privacy, product, and data-flow contract before any Health record leaves the device.

Health notes must not be repurposed for analytics.

Existing account/backup/export policy must be reviewed and explicitly extended before Health data is added to those product surfaces.

---

## 32. Export/backup contract

Health persistence must not silently become omitted user data once the feature is commercially active.

Before release, product owners must make an explicit decision for:

- user-readable export;
- backup;
- restore;
- account deletion;
- tank duplication.

Default contract:

- account deletion removes Health data;
- tank deletion removes Health data;
- tank duplication does **not** duplicate historical water tests or livestock symptoms unless a separate product decision explicitly authorizes it;
- backup/restore and export support must be implemented or intentionally excluded with documented product behavior before release.

Historical health events should not be duplicated into a newly duplicated tank because they describe the original physical tank history.

---

## 33. Tank duplication

A duplicated tank may copy configuration according to existing aquarium duplication behavior.

Health history is not configuration.

Therefore v1 must not copy:

- water-test history;
- livestock-health observations;
- derived Health findings.

The duplicate starts with no Health history and is assessed from its own future evidence.

---

## 34. Deletion semantics

### Water test

Deleting a water test:

- removes that authoritative record;
- causes assessment recomputation;
- may change latest values/status;
- does not delete Maintenance history.

### Livestock observation

Deleting an observation:

- removes that authoritative observation;
- causes assessment recomputation;
- does not delete livestock.

### Livestock

Deleting a livestock record is not equivalent to deleting an observation.

The operation must remove specific-livestock Health observations and the authoritative
livestock record as one compensating integrity workflow. If either side fails, the system
must restore/converge to a consistent state rather than leave an orphan reference.

Category-level observations are not deleted solely because one livestock record is removed.

### Plant observation

Deleting a plant observation:

- removes that authoritative Health observation;
- causes assessment recomputation;
- does not delete the saved plant.

When the authoritative saved plant is removed, specific-plant Health observations are
removed through the plant-target cleanup contract while tank-wide plant/algae observations
remain.

### Tank

Deleting a tank removes all Health records under that tank as part of dependent cleanup.

### Account/local owner data

Follows existing account-data deletion guarantees.

---

## 35. Error and insufficient-data behavior

Health must fail closed and remain usable when some inputs are unavailable.

Examples:

- livestock catalog unavailable -> tank-level health still renders; species comparison unavailable;
- no water tests -> show explicit no-analysis/insufficient-data state;
- Maintenance unavailable temporarily -> do not invent last-care dates;
- device offline -> last valid device evidence may be shown as stale, not live;
- one malformed persistent Health record -> corruption policy applies; it is not silently converted.

A partial source failure must not turn into a false GOOD state.

---

## 36. Performance contract

Health evaluation may combine several local sources but must remain bounded.

Initial requirements:

- no network request is required to open Health;
- no full catalog scan on every frame/render;
- catalog identities are indexed/resolved through application operations;
- expensive evaluation happens outside rendering;
- UI collects lifecycle-aware state;
- history lists are bounded/paged in presentation when necessary;
- recomputation is triggered by meaningful source changes, not arbitrary recomposition;
- duplicate equal source snapshots must not generate repeated work.

The first implementation should measure evaluation latency with realistic populated tank fixtures before introducing background caching.

---

## 37. Testing contract

### 37.1 Water persistence tests

Must cover:

- one-parameter partial test;
- multi-parameter test;
- duplicate parameter rejection;
- NaN/infinite rejection;
- invalid physical/domain input rejection;
- locale parsing;
- backdated test;
- deterministic latest ordering;
- update preserving identity;
- deletion;
- concurrent ID allocation;
- owner isolation;
- corruption/schema rejection.

### 37.2 Observation persistence tests

Must cover:

- category observation;
- specific livestock observation;
- invalid tank;
- livestock belonging to another tank;
- unsupported symptom/category pair;
- future timestamp rejection;
- update/delete;
- livestock deletion cleanup;
- livestock deletion cleanup failure rollback/compensation;
- process-death convergence for livestock deletion;
- no orphan specific-livestock observation after successful deletion;
- category-level observation preservation after livestock deletion;
- tank deletion cleanup;
- owner isolation.

### 37.3 Assessment tests

Must cover:

- no evidence -> INSUFFICIENT_DATA;
- partial water evidence;
- stale water evidence;
- catalog-backed compatible livestock;
- catalog-backed out-of-range livestock;
- custom/unverified livestock;
- missing catalog entry;
- symptom without supporting measurements;
- symptom with supporting measurements;
- multiple possible factors;
- CO2 equipment with no CO2 measurement does not produce “high CO2” fact;
- count/volume alone does not produce overstocking finding;
- resolved evidence removes current finding;
- deterministic output for identical input.

### 37.4 Navigation tests

Must prove:

- Tank Detail keeps five top-level tabs;
- Health entry is from Tank;
- Health opens Tank Health by default;
- switching Health section does not create a second root destination;
- back returns to Tank Detail/Tank;
- no parallel `NavController`;
- Safe Args keeps `tankId` intact across process recreation.

### 37.5 UI tests

Must cover:

- no-data state;
- populated water quality;
- stale measurement marker;
- add/edit/delete water test;
- add/delete observation;
- custom livestock;
- critical/attention text independent of color;
- 200% font;
- dark/light;
- RTL;
- API 27 and target API 36.

---

## 38. CI/architecture guards

Before Health ships, deterministic guards must reject:

- Health UI importing concrete data stores/managers;
- direct tank/care/device repository access from Health presentation;
- duplicated livestock water-range logic;
- name-based livestock catalog inference;
- localized strings persisted as symptom/parameter identities;
- direct `NavController.navigate()` from non-navigation helper layers;
- Health evaluator writing Maintenance or device state;
- Health automation bypassing Smart Care/reminder policy;
- unsupported Health schema versions;
- ownerless Health records;
- removal of required Health tests.

Health work must pass the existing:

- architecture guards;
- Detekt zero-new-debt policy;
- baseline-free blocker Lint;
- unit tests;
- configured coverage;
- CodeQL;
- API 27/API 36 integration/release-smoke gates.

---

## 39. Implementation stages

### Stage 0 — Architecture contract

This document only.

Exit criteria:

- ownership boundaries are explicit;
- Health/non-Health data is separated;
- assessment and automation are separated;
- navigation location is fixed;
- no code/schema changes.

### Stage 1 — Health records persistence

Implement:

- Health application record models;
- `AquariumHealthRecordOperations`;
- version-1 owner-scoped Health store;
- water tests;
- livestock observations;
- plant/algae observations;
- validation;
- corruption policy;
- crash-safe tank cleanup integration;
- compensating livestock-observation cleanup integration;
- plant-target orphan cleanup integration;
- owner-isolation/concurrency tests.

No Health UI is required to complete the persistence core.

### Stage 2 — Health navigation and read skeleton

Implement:

- Health card in Tank tab;
- `TankHealthFragment`;
- Tank Health default section;
- Livestock Health section;
- central Safe Args route;
- lifecycle-safe ViewModel/read state;
- no diagnostic automation yet.

### Stage 3 — Water Analysis product slice

Implement:

- water-test editor;
- history;
- latest/freshness presentation;
- `AquariumWaterSnapshot` adapter for compatible parameters;
- initial deterministic Tank Health findings.

This stage is complete only when UI input persists and the same saved data drives assessment.

### Stage 4 — Livestock observation product slice

Implement:

- `LivestockSymptomCatalog`;
- observation editor;
- observation history;
- target selection;
- initial Livestock Health findings;
- existing livestock water compatibility reuse.

### Stage 5 — Integrated evidence engine

Implement:

- reviewed rule catalog;
- structured evidence;
- confidence;
- recommendations;
- complete Tank/Livestock Health status aggregation;
- explicit stale/unknown behavior.

No automatic care tasks yet.

### Stage 6 — Smart Care automation integration

Implement separately:

- Health finding -> automation candidate policy;
- Smart Care gate;
- idempotency;
- cooldown;
- pending-task deduplication;
- reminder policy integration;
- feedback-loop prevention.

### Stage 7 — Device evidence

Only after application-level telemetry contracts exist:

- temperature;
- other supported measurements;
- freshness/provenance;
- Health integration without control authority.

---

## 40. Explicit non-goals for the initial Health implementation

The initial implementation does not:

- add a sixth Tank Detail tab;
- add a Health page to Tank Settings;
- replace Maintenance;
- replace Tank Life;
- create a second livestock catalog;
- duplicate livestock water requirements;
- diagnose disease from symptom selections;
- claim high CO2 from equipment presence;
- infer overstocking from simple count/litre division;
- assign a numeric Health score;
- issue device commands;
- automatically create Smart Care tasks before Stage 6;
- upload Health records to remote services;
- create a parallel navigation stack;
- migrate nonexistent legacy Health records.

---

## 41. Commercial completion criteria

Aquarium Health is commercially complete only when all of the following are true:

1. Health-owned records have one versioned owner-scoped source of truth.
2. Tank, Maintenance, livestock catalog, and device ownership remain unchanged.
3. Water-test and observation identity/validation are deterministic.
4. Tank deletion cannot leave reachable Health records.
5. Livestock deletion cannot leave reachable specific-livestock Health observations and
   cannot permanently delete those observations if the authoritative livestock deletion
   does not commit.
7. Owner switching cannot leak or cross-write Health state.
6. Health assessment is pure, deterministic, explainable, and independently tested.
8. Missing/stale data cannot produce false GOOD status.
9. Species-specific conclusions use stable catalog identity and reviewed requirements.
10. CO2 and stocking conclusions obey the evidence restrictions in this contract.
11. Recommendations retain evidence linkage.
12. Health does not directly write care tasks or device state.
13. Automation, when introduced, is idempotent and respects Smart Care/reminder policy.
14. Navigation stays inside the existing Tank Detail graph and central navigation contract.
15. Turkish/English localization, accessibility, API 27/API 36, process recreation, and owner isolation are validated.
16. Detekt, Lint, unit/integration tests, CodeQL, and architecture guards pass with zero new debt.

---

## 42. Architectural invariant

The permanent invariant is:

```text
AquariumTankOperations ───────────────┐
MaintenanceOperations ────────────────┤
LivestockCatalogOperations ───────────┤
LivestockWaterAdvisorOperations ──────┤
Health-owned Water Tests ─────────────┤
Health-owned Observations ────────────┤
Future read-only Device Evidence ─────┤
                                      ▼
                         Aquarium Health Assessment
                                      │
                     ┌────────────────┴────────────────┐
                     ▼                                 ▼
               Health UI                       Future Automation
                                                       │
                                                       ▼
                                                Smart Care policy
```

No arrow is allowed from Health UI directly to a data store, device command path, or Maintenance persistence.

No derived Health result becomes a substitute source of truth for the authoritative domain that produced its evidence.

That separation is the basis for adding future automation without destabilizing Tank Detail, Tank Settings, Maintenance, Tank Life, device control, or the current commercial data contract.


---

## 43. Plant Health extension

Plant Health is the third local section of the single `TankHealthFragment`. It is not a
sixth Tank Detail tab and it is not a separate navigation subsystem.

The first Plant Health persistence release owns user observations only. It does not copy
the authoritative plant inventory, substrate, fertilizer, maintenance history, light
assignment, or device state into the Health store.

### 43.1 Authoritative evidence sources

Plant Health consumes, through application boundaries:

- saved `AquariumPlantTag` records and their stable `catalogId`;
- completed water changes and plant-trim/fertilizer care history from Maintenance;
- Health-owned water-test records;
- selected substrate/soil and fertilizer materials from the tank snapshot;
- CO2 equipment presence as context only;
- assigned Light device identity from the tank-device assignment boundary;
- authoritative Light state/program/intensity evidence through Light application contracts;
- future source-backed plant requirement metadata.

A selected fertilizer product is not proof of correct dosing. An assigned Light is not
proof of suitable PAR or photoperiod. CO2 equipment presence is not proof of CO2
concentration or stability.

### 43.2 Plant observation model

```kotlin
data class PlantHealthObservation(
    val id: Long,
    val tankId: Long,
    val plantId: Long?,
    val symptomKey: String,
    val algaeTypeKey: String?,
    val intensity: ObservationIntensity,
    val observedAtMillis: Long,
    val note: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
```

`plantId == null` represents a tank-wide plant/algae observation. A non-null plant ID
must belong to the selected tank.

Stable symptom identities include general decline, yellowing, blackening, melting, leaf
holes, stunted growth, pale new growth, twisted growth, leaf loss, root damage, and algae
presence. Localized labels are never persistence identities.

### 43.3 Algae observation identity

An algae observation stores:

- `symptomKey = plant_algae_presence`;
- one stable `algaeTypeKey`.

Non-algae symptoms must not persist an algae type.

The product algae catalog is an observable aquarium-problem taxonomy, not a biological
taxonomy. Cyanobacteria may be included because users encounter it through the same
diagnostic workflow, while presentation and source-backed guidance must describe it
accurately.

The catalog is extensible through stable IDs without changing the Health Proto schema.
Adding an algae type requires catalog uniqueness tests and, before evaluator use, reviewed
source-backed factor/recommendation rules.

### 43.4 Plant deletion integrity

A specific-plant observation must never be reassigned by plant name.

When a saved plant disappears from the authoritative tank plant list:

- observations targeting that `plantId` are removed through the owner-scoped cleanup path;
- tank-wide plant/algae observations remain;
- owner-session orphan repair removes any stale reference left by an interrupted operation;
- no localized name or `catalogId` guessing is used to reconnect a deleted plant.

Tank deletion treats all Health record categories as dependent data and therefore must
snapshot/delete/restore water tests, livestock observations, and plant observations
together.

---

## 44. Plant requirement catalog boundary

The current presentation Plant catalog is not sufficient evidence for Health rules because
it primarily owns display identity/category data. Plant Health must not import or execute a
UI catalog from an evaluator.

Before species-specific Plant Health findings ship, plant requirement metadata must be
available behind an application/data boundary with stable `catalogId` lookup.

The requirement model may include, where supported by reviewed evidence:

- light demand and usable PAR guidance;
- photoperiod context;
- CO2 demand;
- temperature range;
- pH/GH/KH context;
- nutrient demand;
- substrate/root-feeding preference;
- growth rate and maintenance context.

Missing requirement metadata is represented as unknown. It is never inferred from the
localized plant name.

---

## 45. Plant and algae evidence engine

A plant or algae observation is not a diagnosis.

The Plant Health evaluator may correlate an observation with:

- coherent water measurements and freshness;
- water-change recency;
- fertilizer product and actual care history when available;
- substrate/soil selection;
- plant catalog requirement evidence;
- assigned Light program/state;
- measured PAR when available;
- CO2 measurements/estimates only under the existing CO2 evidence restrictions;
- tank age and other reviewed environmental evidence.

A rule must distinguish measured fact, configuration context, association, and unknown
evidence. Black-beard-algae observation plus CO2 equipment presence, for example, does not
permit the statement that CO2 is low or that CO2 instability caused the observation.

---

## 46. Verified-source rule governance

Plant/algae evaluator and automation rules are curated product knowledge, not free-form
presentation logic.

Every rule that can produce a factor, recommendation, severity, or automatic-task candidate
must have:

- a stable rule key;
- a rule version;
- applicable symptom/algae keys;
- required and optional evidence;
- explicit threshold/decision logic;
- confidence semantics;
- recommendation keys;
- one or more reviewed source references;
- a review date.

Source metadata must retain enough information to audit the rule, including organization or
author, document/title, publication/revision date when available, canonical URL/DOI or
equivalent identifier, and access/review date.

Primary/authoritative technical documentation, peer-reviewed literature, recognized
academic/extension resources, and established specialist references are preferred. A forum
post, anonymous article, SEO content, or single hobbyist claim must not be the sole basis
for a health/automation rule.

Runtime Health evaluation remains deterministic and local. It consumes the reviewed,
versioned rule catalog; it does not browse the web or generate unsupported causal claims.

---

## 47. Updated Stage 1 completion

Stage 1 is not complete until schema version 1 supports and validates all three Health-owned
record categories:

```text
AquariumHealthStore v1
├── Water Tests
├── Livestock Health Observations
└── Plant Health / Algae Observations
```

Required Stage 1 persistence/integrity coverage includes:

- water-test add/update/delete and deterministic ordering;
- livestock observation add/update/delete;
- plant observation add/update/delete;
- owner isolation for all three record categories;
- specific-livestock target validation;
- category-level livestock observation support;
- specific-plant target validation;
- tank-wide plant/algae observation support;
- symptom/algae relationship validation;
- orphan repair after plant removal;
- crash-safe tank-deletion snapshot/delete/rollback participation for all Health records;
- compensating livestock deletion so Health cleanup and authoritative livestock deletion
  cannot leave a stable partial state;
- livestock-deletion failure rollback;
- livestock-deletion process-death convergence;
- category-level observations preserved when one livestock record is deleted;
- corruption/schema validation;
- concurrent unique ID allocation across all Health record categories;
- API 27/API 36 integration evidence covering the new Health persistent state.

Stage 1 is not green merely because normal CRUD passes. The deletion-integrity edge cases
above are release blockers.

No Tank/Livestock/Plant Health assessment, recommendation, automation task, or Health UI is
required to complete Stage 1.

---

## 48. Updated post-Stage-1 order

After Stage 1 is green:

1. Stage 2 creates the Health card and one `TankHealthFragment` with Tank Health,
   Livestock Health, and Plant Health local sections.
2. Water Analysis becomes the first end-to-end input/read slice.
3. Livestock observations and existing livestock water compatibility are connected.
4. Plant observations/algae selection are connected.
5. A source-backed plant-requirement boundary and evidence catalog are completed.
6. The deterministic Tank/Livestock/Plant Health evaluators are integrated.
7. Only then may Health findings feed the separate Smart Care automation policy with
   idempotency, cooldown, owner scope, and notification gates.
