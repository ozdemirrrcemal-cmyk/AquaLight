# AquaLight Aquarium Water Analysis Contract

## Status

This document freezes the architectural, data, analysis, persistence, and UI-integration contract for the first production-grade Aquarium Health analysis flow.

The existing Tank Health / Water Quality UI is considered visually complete for this stage. Implementation work governed by this contract must connect that UI to authoritative data and analysis without redesigning the approved screens unless a later explicit UI change is requested.

This contract is intentionally broader than a screen implementation. It defines the foundation that later powers:

- Tank Health / Water Quality;
- Tank Health / Algae Control;
- Plant Health;
- Livestock Health.

The first implementation target is Water Quality end to end. Algae Control, Plant Health, and Livestock Health come only after the Water Quality contract is implemented, persisted, tested, and CI-clean.

---

## 1. Goals

The Water Analysis system must:

1. accept water measurements entered by the user, with temperature optionally coming from an authoritative tank sensor;
2. persist owner-scoped and tank-scoped analysis records across process death;
3. evaluate every supported measurement deterministically;
4. interpret measurements in the context of the actual aquarium rather than against one global "normal" table;
5. use registered livestock requirements and verified plant care requirements;
6. identify incompatible requirements between inhabitants instead of averaging conflicts away;
7. preserve the historical assessment that was produced at record creation time;
8. produce structured reasons, affected entities, missing-data signals, conflicts, and recommendation codes;
9. expose the result through an application boundary so UI code never reads DataStore, JSON catalogs, device repositories, or Firebase ownership directly;
10. clean up analysis records safely when a tank or owner is deleted;
11. remain deterministic and testable without relying on an LLM for the decision itself;
12. provide a reusable authoritative Aquarium Health context for later Algae, Plant, and Livestock Health engines.

---

## 2. Non-goals for the first Water Quality implementation

The following are not part of the first implementation:

- redesigning the approved Water Quality UI;
- implementing Algae Control UI or analysis;
- implementing Plant Health UI or analysis;
- implementing Livestock Health UI or disease diagnosis;
- generating clinical/veterinary diagnoses;
- allowing an LLM to decide whether a value is safe;
- silently inventing target ranges for missing catalog data;
- silently treating missing measurements as normal;
- recomputing old historical records every time catalog data or rules change;
- storing localized UI strings such as "Normal", "Yüksek", or "Low" as authoritative domain state.

---

## 3. Current branch inventory

At the time this contract is introduced, the branch already contains important authoritative inputs.

### 3.1 Tank snapshot

`AquariumTankSnapshot` already provides:

- stable tank id;
- tank name and description;
- setup date;
- dimensions and volume unit;
- tank type;
- tank style;
- registered plants;
- selected materials/components;
- registered livestock;
- Smart Care / reminder settings.

Each registered plant carries a stable `catalogId`.

Each registered livestock item carries a stable `catalogEntryId`.

These stable identities are the bridge between tank state and canonical care requirements.

### 3.2 Plant catalog

The packaged plant catalog currently contains 271 records.

Current branch audit:

- 271 total records;
- 182 records with `healthDataStatus = VERIFIED`;
- 182 records with `healthAnalysisReady = true`;
- 89 records with `healthDataStatus = PARTIAL`;
- 89 records not ready for hard health-analysis decisions;
- 240 records with at least one explicit `verifiedCareFields` entry;
- 31 records with no explicit verified care field;
- 124 records at 100% health-data completeness;
- 185 records at 90% or greater completeness.

The current plant care model already exposes:

- light requirement;
- CO2 requirement;
- difficulty;
- growth rate;
- temperature minimum / maximum;
- pH minimum / maximum;
- KH minimum / maximum;
- GH minimum / maximum;
- nutrient demand;
- substrate requirement;
- root-feeder flag;
- water-column-feeder flag;
- health-data status;
- analysis-ready flag;
- verified care fields.

### 3.3 Livestock catalog

The packaged livestock catalog currently contains 687 records.

Current branch coverage audit:

- temperature: 687 / 687;
- pH: 687 / 687;
- nitrate: 686 / 687;
- GH: 343 / 687;
- KH: 167 / 687;
- phosphate: 147 / 687;
- TDS: 34 / 687;
- specific gravity: 348 / 687;
- alkalinity: 280 / 687;
- calcium: 157 / 687;
- magnesium: 157 / 687;
- PAR: 113 / 687;
- flow: 121 / 687.

The existing `LivestockWaterCompatibilityEvaluator` and `LivestockWaterAdvisorOperations` already provide deterministic livestock-range comparison. This implementation must be reused, not duplicated.

### 3.4 Tank equipment / system context

Tank material selections can identify canonical categories including:

- CO2;
- light;
- filter;
- fertilizer;
- substrate;
- heater;
- cooler;
- dosing.

Existing Smart Care code can already derive contextual traits such as:

- freshwater vs marine;
- planted vs no plants;
- livestock present;
- fish present;
- shrimp present;
- CO2 present;
- fertilizer present;
- active soil present;
- filter present;
- light present;
- high-tech / low-tech;
- startup vs mature tank.

Health analysis may reuse the same canonical facts, but critical analysis decisions should prefer stable category keys and authoritative structured data over keyword heuristics.

### 3.5 Cooling / temperature

The existing Cooling application boundary exposes `DeviceCoolingCardSummary.waterTemperatureC`.

Tank-device assignment infrastructure already identifies which devices are assigned to which tank.

The Water Analysis UI already distinguishes sensor temperature from manual temperature. That distinction must become real provenance, not a cosmetic UI flag.

### 3.6 Current Water Quality UI state

The approved UI currently supports:

- pH;
- NO3 / nitrate;
- NO2 / nitrite;
- NH3/NH4;
- temperature;
- GH;
- KH;
- PO4 / phosphate;
- measurement date;
- measurement time;
- temperature source;
- save analysis;
- history;
- record detail;
- record deletion.

Today those screens still contain mock / fixture data and deferred persistence hooks. This contract defines how those fixtures are replaced.

---

## 4. Architectural boundary

The required dependency direction is:

```text
UI
  -> WaterAnalysisViewModel
      -> WaterAnalysisOperations
          -> WaterQualityAssessmentEngine
          -> AquariumHealthContextProvider
              -> AquariumTankOperations / tank snapshot
              -> LivestockWaterAdvisorOperations
              -> PlantCareCatalogOperations
              -> TankWaterTemperatureOperations
          -> WaterAnalysisStore / repository implementation
```

The UI must not directly depend on:

- Proto DataStore managers;
- JSON asset readers;
- `LivestockCatalog`;
- plant JSON parsing;
- device repositories;
- assignment stores;
- Firebase Auth;
- owner UID resolution;
- corruption recovery internals.

Ownership is resolved below the application boundary.

### 4.1 Accepted package and layer ownership (K01)

Decision accepted with the user on 26 September 2026: preserve AquaLight's existing application, data, UI, and composition architecture for this feature.

Paths below are relative to `app/src/main/java/com/aqua/aqualight/`.

| Location | Responsibility |
| --- | --- |
| `application/aquarium/health/context/` | Shared immutable Aquarium Health context and context-provider contracts used by the four health engines. |
| `application/aquarium/health/water/` | Water Analysis contracts, models, policies, and the pure deterministic assessment engine; model/policy/engine concerns may use focused subpackages here. |
| `data/aquarium/health/` | Implementations for context assembly, sensor adaptation, owner-scoped analysis orchestration, persistence, and queries. |
| `data/aquarium/catalog/plant/` | Plant catalog loading, parsing, and caching behind an application contract; the existing UI reader must move here during its implementation stage. |
| Existing `ui/tabs/aquarium/detail/health/` | Approved screens, ViewModels, and presentation models; focused `presentation/water/` code stays within this UI boundary. |
| Existing `composition/` | Concrete dependency construction and committed owner-scoped dependency wiring. |

The accepted constraints are:

- keep these responsibilities within the existing application module; do not introduce a separate domain root or a new Gradle module for this work;
- keep pure models, policies, and assessment logic in the application layer, independent of Android, data implementations, JSON readers, Firebase, and UI resources;
- keep storage, catalog I/O, and sensor access in data implementations behind application contracts;
- keep approved Fragments and XML layouts in place; do not move them solely to reorganize folders;
- preserve the existing owner/session lifecycle and composition boundaries;
- introduce concrete files when their implementation stage is reached, rather than creating empty scaffolding as part of this decision.

K01 settles package and layer ownership only. Exact model shapes, parameter reuse, unit semantics, scientific rules, and the remaining checklist decisions require their own item-by-item decisions. Recording K01 does not mark those decisions or implementation tasks as complete.

---

## 5. Canonical Water Analysis record

A stored analysis is a historical event, not merely the latest mutable tank state.

The target domain shape is conceptually:

```text
WaterAnalysisRecord
|
+-- identity
|   +-- analysisId
|   +-- ownerUid
|   +-- tankId
|
+-- time
|   +-- observedAtMillis
|   +-- createdAtMillis
|
+-- measurements
|   +-- temperature
|   +-- pH
|   +-- nitrate
|   +-- nitrite
|   +-- ammonia
|   +-- GH
|   +-- KH
|   +-- phosphate
|
+-- assessment snapshot
|   +-- overall severity
|   +-- parameter assessments
|   +-- livestock assessments
|   +-- plant assessments
|   +-- conflicts
|   +-- missing context
|   +-- recommendations
|
+-- provenance
    +-- engine version
    +-- rule revision
    +-- plant catalog revision
    +-- livestock catalog revision
```

### 5.1 Identity

`analysisId`:

- must be positive / valid according to the chosen stable id policy;
- must be unique within the owner scope;
- is required by history-to-detail navigation;
- is required for deletion;
- must not be inferred from list position or time.

`ownerUid`:

- is mandatory in persistence;
- is captured from the immutable active owner scope;
- is never supplied by the UI;
- is never blank;
- ownerless legacy adoption is forbidden.

`tankId`:

- must be positive;
- must reference a tank belonging to the active owner when the record is created;
- is part of every query and mutation boundary.

### 5.2 Time semantics

`observedAtMillis` means when the water measurement was actually taken.

`createdAtMillis` means when AquaLight persisted the analysis.

They must not be conflated.

A user may enter an older measurement today. In that case:

`observedAtMillis < createdAtMillis`

is valid.

History ordering should normally use `observedAtMillis` descending, with a deterministic secondary ordering by creation/id if two records share the same observation time.

---

## 6. Measurement model and units

All domain values are numeric canonical values. Localized strings are presentation only.

The first Water Analysis measurement set is:

- temperature;
- pH;
- NO3 / nitrate;
- NO2 / nitrite;
- NH3/NH4 / ammonia metric;
- GH;
- KH;
- PO4 / phosphate.

### 6.1 Canonical units

The domain must define one canonical unit per parameter.

Recommended canonical contract:

- temperature: degrees Celsius;
- pH: unitless;
- GH: degrees dGH;
- KH: degrees dKH;
- nitrate: mg/L as the exact catalog/rule semantic selected below;
- nitrite: mg/L as the exact rule semantic selected below;
- phosphate: mg/L as the exact catalog/rule semantic selected below;
- ammonia: canonical ammonia semantic defined in section 6.3.

UI formatting and parsing must use `LocaleFormatter` or the central locale policy. The UI must not use ad-hoc `toDoubleOrNull`, comma replacement, or locale-blind number parsing.

### 6.2 ppm vs mg/L normalization

The livestock catalog currently names some fields `nitratePpm` and `phosphatePpm` while the Water Quality UI displays mg/L.

The engine must not silently compare differently named unit semantics.

Before implementing persistence and rules, one canonical concentration policy must be frozen and conversion / equivalence must be explicit in code and tests.

If the source data represents dilute freshwater mass concentration where numeric equivalence is intentionally accepted, that equivalence must be documented by the rule/catalog ingestion layer rather than assumed by UI code.

Marine-specific concentration semantics must likewise be explicit.

### 6.3 NH3/NH4 semantic freeze

The current UI label `NH3/NH4` is not sufficient as a storage semantic.

Different tests may report:

- free ammonia NH3;
- ammonium NH4+;
- total ammonia nitrogen;
- combined total ammonia expressed in another convention.

Therefore the persistence model must not use an ambiguous field such as only `ammonia = 0.2`.

Before implementation, the supported metric must be explicitly named in the domain enum / model. For example, if the product chooses combined total ammonia, the domain representation must say so explicitly.

The UI label may remain visually unchanged, but the internal semantic may not be ambiguous.

---

## 7. Measurement provenance

Every measurement may eventually gain provenance, but temperature requires provenance in the first implementation.

### 7.1 Temperature source

Temperature source must be an enum/domain value, not a localized string.

Minimum supported sources:

- `MANUAL`;
- `COOLING_SENSOR`.

A sensor-backed temperature must also retain:

- device UID;
- sampled-at timestamp;
- freshness / validity evidence as required by the application boundary.

### 7.2 Dedicated temperature application boundary

Water Analysis must not reach into the Cooling repository directly.

Introduce a boundary conceptually equivalent to:

```text
TankWaterTemperatureOperations
  observe/readFreshTemperature(tankId)
```

Possible result states:

- fresh value;
- no assigned compatible sensor;
- assigned sensor unavailable;
- stale sample;
- invalid sample.

A stale or invalid device value must never be persisted as if it were a fresh sensor reading.

If sensor temperature is unavailable, the user may choose manual temperature according to the existing UI flow.

---

## 8. Context-aware analysis

A water value is not globally "normal" merely because it falls in one generic table.

The analysis pipeline is:

```text
Measurements
  -> intrinsic chemistry / safety rules
  -> tank-type context
  -> registered livestock requirements
  -> registered verified plant requirements
  -> equipment / system context
  -> conflict detection
  -> final structured assessment
```

The engine must preserve the reason for each decision.

---

## 9. Aquarium Health context

Introduce one authoritative contextual snapshot used by Water Analysis and later health engines.

Conceptual shape:

```text
AquariumHealthContext
|
+-- tank identity / type / style / age / volume
+-- registered livestock identities and quantities
+-- resolved livestock care profiles
+-- registered plant identities
+-- resolved verified plant care profiles
+-- CO2 presence
+-- light presence
+-- filter presence
+-- fertilizer presence
+-- active substrate / soil signal
+-- heater / cooler presence where relevant
+-- assigned authoritative sensor capabilities
+-- context completeness / unresolved entities
```

This context must be built below the UI boundary.

Later Algae Control, Plant Health, and Livestock Health must consume this same authoritative context rather than independently reconstructing tank facts in three different feature stacks.

---

## 10. Livestock evaluation

### 10.1 Reuse the existing evaluator

The existing `LivestockWaterCompatibilityEvaluator` is authoritative for comparing measurements with one livestock requirement profile.

Do not implement a second parallel livestock-water range evaluator inside Water Analysis.

The Water Analysis orchestrator must delegate species-specific comparisons to the existing livestock application/data boundary.

### 10.2 Custom livestock

Custom livestock without canonical catalog data must not be assigned invented ranges.

They produce an explicit unverified / insufficient-data state.

### 10.3 Missing catalog entries

A persisted catalog identity that can no longer resolve is an explicit catalog-missing condition.

It is not silently ignored.

### 10.4 Quantity

Livestock quantity may affect future bioload/context reasoning, but quantity must not duplicate a species requirement range. Ten animals do not change the species pH range merely because there are ten of them.

---

## 11. Aggregating multiple livestock requirements

Do not average species ranges.

For each comparable parameter, the engine should compute the intersection of all authoritative requirements that apply.

Example:

```text
Species A pH: 6.0 - 7.0
Species B pH: 6.5 - 7.5

Common compatible interval: 6.5 - 7.0
```

If requirements do not overlap:

```text
Species A pH: 5.5 - 6.5
Species B pH: 7.2 - 8.0
```

the engine must emit a structured conflict such as:

`CONFLICTING_REQUIREMENTS`

It must not fabricate an "ideal" average such as 6.8.

A conflict result must preserve:

- parameter;
- affected livestock ids / catalog ids;
- individual expected ranges;
- absence of common intersection.

The UI may present a simplified message, but the underlying conflict must remain structured.

---

## 12. Plant evaluation

Plant health data is not equally trustworthy across all 271 catalog records.

### 12.1 Hard-decision eligibility

A plant may participate in hard Water Quality decisions only when:

`healthAnalysisReady == true`

which currently corresponds to:

`healthDataStatus == VERIFIED`

### 12.2 Partial plant records

A `PARTIAL` plant may contribute informational context only where the specific field is explicitly verified and policy permits it.

It must not create a hard warning from unverified or unknown data.

At minimum, the first production implementation may choose the stricter policy:

- VERIFIED records participate in hard analysis;
- PARTIAL records produce `INSUFFICIENT_DATA` / informational context only.

That strict policy is preferred until field-level analysis policy is explicitly tested.

### 12.3 No invented values

`UNKNOWN`, null, or missing plant fields remain unknown.

The engine must not derive ranges from a plant name, genus, UI category, or visual similarity.

---

## 13. Base water-chemistry rule catalog

Species requirements alone are not enough.

The current livestock requirement model does not cover all first-screen parameters, particularly NO2 and NH3/NH4.

Create a separate evidence-backed rule catalog for intrinsic water chemistry.

Conceptual rule:

```text
WaterChemistryRule
- parameter
- applicable tank profile
- lower bound?
- upper bound?
- severity
- recommendation code(s)
- evidence/source id
- rules revision
```

Applicable profile examples may include:

- freshwater;
- planted freshwater;
- shrimp;
- marine;
- reef.

Exact numeric thresholds must be source-backed and frozen in the rule catalog. They must not be casually embedded in Fragment/ViewModel code.

This rule catalog is responsible for parameters that need a general safety interpretation even if no species-specific requirement exists.

---

## 14. Severity and status model

Domain status is structured and locale-independent.

A suitable initial vocabulary is:

- `OPTIMAL`;
- `LOW`;
- `HIGH`;
- `WARNING`;
- `CRITICAL`;
- `CONFLICT`;
- `INSUFFICIENT_DATA`.

The implementation may refine names before code is frozen, but must preserve these semantics:

- direction where meaningful (low/high);
- severity;
- conflict distinct from simple out-of-range;
- missing evidence distinct from "normal".

The overall status must be derived deterministically from parameter results according to an explicit severity ordering. A missing measurement must not make the overall result better.

Localized UI text is mapped from enums / reason codes at the presentation layer.

---

## 15. Structured assessment output

The engine result must contain enough information to explain itself.

Conceptual output:

```text
WaterQualityAssessment
|
+-- overallSeverity
|
+-- parameterAssessments[]
|   +-- parameter
|   +-- measuredValue
|   +-- status
|   +-- reasons[]
|   +-- affectedEntities[]
|   +-- expected / compatible ranges
|
+-- livestockAssessments[]
|
+-- plantAssessments[]
|
+-- conflicts[]
|
+-- missingData[]
|
+-- recommendations[]
```

A parameter reason must be machine-readable.

Examples:

- intrinsic chemistry threshold exceeded;
- outside livestock requirement;
- outside verified plant requirement;
- conflicting livestock requirements;
- no comparable livestock data;
- plant data partial;
- missing measurement;
- stale sensor value rejected.

---

## 16. Recommendation engine

Recommendations are deterministic rule outputs, not free-form AI decisions.

Use stable codes such as:

- `CHECK_NITRITE`;
- `RECHECK_AMMONIA`;
- `WATER_CHANGE_RECOMMENDED`;
- `PH_OUTSIDE_LIVESTOCK_RANGE`;
- `CONFLICTING_LIVESTOCK_REQUIREMENTS`;
- `VERIFY_TEST_RESULT`;
- `INSUFFICIENT_PLANT_CARE_DATA`.

Exact code names may be refined, but the contract is:

1. a recommendation has a stable code;
2. it references the assessment reason that caused it;
3. localized text is presentation;
4. recommendation generation is deterministic;
5. tests assert the code, not Turkish/English prose.

A future LLM layer may explain structured recommendations in natural language, but it must not be the authority that determines whether water is safe.

---

## 17. Persistence architecture

Do not place growing analysis history inside `aquarium_tanks.pb`.

Create a dedicated versioned store, conceptually:

`water_analyses.pb`

Reasons:

- history grows independently of tank configuration;
- every new analysis should not rewrite the tank record;
- analysis lifecycle and schema can evolve independently;
- owner/tank queries are natural;
- deletion and corruption policies are explicit.

The store must follow commercial store rules:

- explicit schema version;
- strict validation;
- owner UID on every record;
- tank ID on every record;
- no ownerless adoption;
- deterministic corruption handling;
- no UI access to the store.

Add a dedicated schema constant to the commercial store schema when implementation begins.

---

## 18. Persistence operations

The application boundary should support at least:

```text
observeForTank(tankId)
observeRecord(tankId, analysisId)
latestForTank(tankId)
createAnalysis(input)
deleteAnalysis(tankId, analysisId)
```

Implementation requirements:

- owner scope is captured below UI;
- tank existence/ownership is validated on create;
- queries only return records belonging to the active owner;
- deletion cannot delete another owner's or another tank's record;
- history is deterministic and sorted;
- create returns the stable analysis id.

---

## 19. Persist the assessment snapshot

A historical record must preserve the result produced at creation time.

Do not store only raw measurements and recompute the old record every time it is opened.

Why:

- catalogs can change;
- rule thresholds can change;
- engine behavior can change;
- livestock may be removed later;
- plants may be changed later.

A record created on 25 September must still mean:

"Using the tank context, catalogs, and rules available at that analysis, this was the result."

Therefore persist:

- measurement snapshot;
- context references / relevant snapshot data needed for explanation;
- assessment snapshot;
- engine version;
- rules revision;
- plant catalog revision;
- livestock catalog revision.

A future explicit "Re-analyze with current rules" operation may create a new assessment/version, but silent mutation of history is forbidden.

---

## 20. Engine and catalog versioning

At minimum define:

- `engineVersion`;
- `waterChemistryRulesRevision`;
- `plantCatalogRevision`;
- `livestockCatalogRevision`.

If the livestock catalog does not currently expose a revision constant, add one as part of the implementation rather than persisting an implicit unknown version.

Version fields are provenance, not UI labels.

---

## 21. Tank deletion and crash safety

Water analyses are dependent tank data.

Deleting a tank must not leave orphan Water Analysis records.

The existing `OwnerTankDataCleaner` already provides a crash-safe compensating transaction for tank/care-task integrity. Water Analysis must be integrated into the authoritative tank-deletion transaction.

The implementation must not do:

```text
delete tank
then best-effort delete analyses
```

because process death can leave orphan records.

Required behavior:

1. freeze/capture the immutable owner;
2. begin durable integrity transaction;
3. snapshot dependent Water Analysis records for each tank;
4. delete dependent records in a safe order;
5. delete tank;
6. complete cleanup;
7. on failure/cancellation, restore required dependent snapshots;
8. recover unfinished work after process death.

The exact transaction extension may generalize the existing tank-care journal rather than create disconnected cleanup logic.

---

## 22. Account deletion and owner cleanup

Water Analysis data is owner-scoped personal application data and must participate in the same local account-deletion / owner cleanup guarantees as other aquarium records.

Requirements:

- no records from owner A become visible to owner B;
- account deletion clears the owner's Water Analysis records according to the existing deletion contract;
- process-death recovery for account deletion must include the new store;
- backup/restore and data-inventory documentation must be updated if Water Analysis records are included in user backup/export.

---

## 23. Backup, restore, and export policy

Before implementation is considered complete, explicitly decide and test:

### Backup / restore

If aquarium health history is part of AquaLight user data backup:

- archive models must contain the Water Analysis records;
- restored tank ids must remap correctly;
- analysis ids must not collide;
- owner UID must be rebound only through the canonical restore owner contract;
- assessment provenance is preserved.

If excluded from backup, that exclusion must be explicit in the product/data contract rather than accidental.

### PDF / user-facing export

Existing tank PDF export does not automatically need Water Analysis history in phase one, but if added later it must read from the application boundary, not DataStore directly.

---

## 24. Input validation

Validation occurs before persistence and before assessment.

Rules:

- all provided numeric values must be finite;
- concentrations/hardness values that cannot physically be negative are rejected if negative;
- pH must obey the canonical pH input policy;
- required measurements follow the product contract;
- locale-aware parsing occurs at the UI/presentation boundary;
- canonical numeric values cross into the application layer;
- empty text is not converted into zero;
- invalid input cannot be persisted.

Scientific target ranges are not input-validation ranges. A user may legitimately enter a dangerous measurement; the app should accept a physically valid measurement and assess it as dangerous rather than reject it merely for being out of target.

---

## 25. Tank-type context

Tank type changes interpretation.

The context builder must use stable taxonomy, not translated labels.

Examples:

- freshwater;
- planted freshwater;
- shrimp;
- marine;
- reef/coral variants.

The rule engine chooses applicable intrinsic chemistry rules based on canonical tank classification.

Unknown/custom tank classifications produce explicit reduced-confidence / missing-context behavior; they do not default silently to a convenient profile.

---

## 26. Equipment/system context

The engine may consume system context such as:

- CO2 installed;
- light installed;
- filter installed;
- fertilizer configured;
- active substrate;
- heater/cooler;
- tank age.

For critical decisions, use canonical material category keys and structured metadata wherever possible.

Keyword heuristics used by older Smart Care classification may be used only as a clearly lower-confidence fallback when explicitly allowed.

System context is primarily explanatory / recommendation context. It must not override a direct hazardous chemistry measurement.

---

## 27. Sensor freshness and authority

A sensor value has additional failure modes that manual entry does not.

A sensor-backed measurement must define:

- which device produced it;
- whether that device is assigned to this tank;
- whether the device family/capability is valid;
- whether the reading is valid;
- whether the reading is fresh enough;
- sample timestamp.

The threshold for freshness must be a central policy, not Fragment-local logic.

If the reading is stale:

- do not label it as current;
- do not persist it as fresh sensor input;
- allow manual fallback according to UI behavior.

---

## 28. UI integration contract

The existing approved UI is frozen for data-integration work.

### 28.1 Add Analysis

Replace the deferred save click with ViewModel/application behavior.

Flow:

1. collect canonical measurement input;
2. obtain authoritative tank context;
3. obtain fresh temperature if sensor mode is selected;
4. validate;
5. run assessment;
6. atomically persist raw measurement + assessment + provenance;
7. return/navigate according to the existing approved flow.

The Fragment must not build domain ranges or read catalogs itself.

### 28.2 Tank Health main screen

`TankHealthContentAdapter` currently renders hard-coded metric values/statuses.

Replace static metrics with presentation models derived from the latest persisted analysis.

If there is no analysis:

- render the approved empty/no-analysis state according to the existing design contract;
- do not invent "normal" values.

### 28.3 History

Remove all fixture records.

History observes real owner/tank-scoped records.

Each history item carries `analysisId`.

### 28.4 History -> Detail navigation

Detail navigation must pass both the tank identity required by the route and the stable analysis identity.

Use Safe Args / generated Directions and the existing `navigateSafelyFrom` navigation contract.

Do not retrieve a detail record by list index or by a hard-coded fixture.

### 28.5 Detail

Detail observes the persisted record by id and displays its historical snapshot.

If the record no longer exists:

- use the central process-safe feedback / navigation pattern;
- do not crash;
- do not fabricate a fallback record.

### 28.6 Delete

The existing central confirmation dialog remains.

On confirmation:

1. ViewModel invokes application delete;
2. application layer validates owner/tank/analysis identity;
3. persistence delete completes;
4. UI navigates back only after success;
5. failure is surfaced through the project's standard feedback mechanism.

---

## 29. ViewModel contract

Introduce a focused Water Analysis ViewModel rather than expanding Fragment responsibilities.

It should expose immutable UI state and one-shot events according to existing AquaLight conventions.

It must own:

- input state orchestration where appropriate;
- loading/saving/deleting state;
- latest analysis presentation;
- history observation;
- detail observation;
- application-operation calls;
- mapping structured domain output to presentation state.

It must not own:

- Firebase owner lookup;
- DataStore instances;
- JSON catalog parsing;
- device repository access;
- scientific rule constants.

---

## 30. Composition root

Dependencies must be constructed in the existing composition root / owner dependency system.

Do not instantiate production repositories or DataStore managers in Fragments/ViewModels.

New dependencies may include:

- WaterAnalysisOperations;
- WaterAnalysisStore implementation;
- WaterQualityAssessmentEngine;
- AquariumHealthContextProvider;
- PlantCareCatalogOperations;
- TankWaterTemperatureOperations;
- existing LivestockWaterAdvisorOperations.

Owner-scoped runtime dependencies must use the same immutable owner/session-generation guarantees as current owner dependencies.

---

## 31. Water Analysis engine purity

The core assessment engine should be a pure deterministic component as far as practical.

Preferred shape:

```text
evaluate(
    measurements,
    context,
    rules
) -> WaterQualityAssessment
```

It should not:

- perform navigation;
- read Android resources;
- query Firebase;
- read DataStore;
- call network APIs;
- depend on current wall-clock time except through explicit inputs;
- emit localized strings.

This allows exhaustive unit testing.

---

## 32. Explanation hierarchy

When a parameter is assessed, preserve distinct layers of reasoning.

Example:

```text
NO3 = 18 mg/L

Intrinsic chemistry rule:
- acceptable for this tank profile

Livestock:
- Neon Tetra: compatible
- Amano Shrimp: compatible

Plants:
- verified plant profiles: compatible / informational

System context:
- planted tank
- filter present
- fertilizer present

Final:
- status + explicit reasons
```

The UI may collapse this to one status today, but future detail screens can explain why without re-running the engine.

---

## 33. Conflict priority

Conflicts must never be hidden by a generic green status.

If a measured value satisfies one species but violates another, the parameter assessment must identify affected species.

If there is no shared compatible interval for inhabitants, that conflict exists even before a measurement is considered.

A conflict may coexist with a measurement that happens to fit one side.

The overall severity policy must explicitly account for conflicts.

---

## 34. Missing-data policy

Missing information is not success.

Examples:

- custom livestock with no care profile;
- unresolved catalog id;
- partial plant profile;
- missing GH requirement;
- missing PO4 requirement;
- no current temperature sensor;
- user omitted an optional measurement.

Represent these explicitly through `missingData` / `INSUFFICIENT_DATA` structures.

Do not convert unknown ranges to broad infinite ranges and then label the result compatible.

---

## 35. Rule evidence

Every intrinsic chemistry threshold should have a traceable source/evidence identity in the rule catalog or adjacent contract data.

The engine should be able to answer internally:

- which rule was applied;
- which revision;
- which context selected it.

This is necessary because water-chemistry recommendations are product-critical and may change over time.

---

## 36. Safety of recommendations

Recommendations must be proportional to evidence and severity.

The first implementation should prefer conservative, testable guidance codes over aggressive dosing instructions.

A recommendation that implies chemical addition, dosing, or a major environmental change must have explicit rule support and later may require additional context.

The system should be able to recommend re-testing when evidence is uncertain.

---

## 37. Relationship with Smart Care

Water Analysis and Smart Care are related but not the same subsystem.

Water Analysis:

- evaluates an observed measurement event;
- creates a historical assessment;
- explains compatibility and risk.

Smart Care:

- creates maintenance/task guidance over time.

Water Analysis may later emit recommendation codes that are eligible to create Smart Care tasks, but that is a separate explicit integration.

The analysis engine must not directly create care tasks as a side effect in phase one.

---

## 38. Foundation for Algae Control

After Water Quality is complete, Algae Control should reuse:

- AquariumHealthContext;
- latest / recent Water Analysis values;
- tank age;
- CO2/light/filter/fertilizer context;
- plant/livestock context where relevant;
- historical observations.

Algae Control remains tank-level.

It must not be duplicated inside Plant Health.

---

## 39. Foundation for Plant Health

Plant Health should later use:

- selected registered plant identity;
- canonical plant catalog id;
- verified plant care profile;
- AquariumHealthContext;
- Water Analysis history/current values;
- user plant observations;
- structured symptom analysis.

If algae is observed on a plant:

- Plant Health records the observation;
- it may flag algae presence;
- remediation/analysis routes to Tank Health -> Algae Control.

Plant Health must not become a second algae engine.

---

## 40. Foundation for Livestock Health

Livestock Health should later use:

- registered livestock identity;
- canonical catalog entry;
- existing water requirements;
- AquariumHealthContext;
- Water Analysis results;
- behavior/symptom observations.

Water-quality compatibility remains a reusable input rather than duplicated logic.

---

## 41. Store and domain invariants

At minimum:

- owner UID non-blank;
- tank id positive;
- analysis id valid/unique;
- observation timestamp valid;
- creation timestamp valid;
- all numeric values finite;
- enum values recognized;
- record schema version current;
- assessment provenance version fields present;
- record belongs to exactly one owner and one tank;
- no duplicate analysis id within owner scope;
- no cross-owner read/write/delete;
- corrupted/unsupported records fail according to commercial store recovery policy.

---

## 42. Process-death guarantees

Process death must not cause:

- a saved analysis to disappear after a successful commit;
- half-written analysis records;
- owner crossover;
- orphan analysis records after tank deletion;
- loss of the historical assessment snapshot;
- a sensor/manual provenance change;
- navigation reopening a different record.

Persistence is the source of truth. UI memory is not.

---

## 43. Test plan

### 43.1 Pure engine tests

Test every parameter for:

- within range;
- below range;
- above range;
- intrinsic warning;
- critical threshold;
- missing measurement;
- missing rule;
- species issue;
- plant issue;
- conflict;
- insufficient data.

### 43.2 Species intersection tests

Cover:

- overlapping ranges;
- exact-boundary overlap;
- one open-ended range;
- no overlap;
- three or more species;
- custom livestock;
- missing catalog entry.

### 43.3 Plant eligibility tests

Cover:

- VERIFIED / analysis-ready;
- PARTIAL;
- unknown field;
- verified field;
- missing plant catalog id.

### 43.4 Unit/semantic tests

Cover:

- locale parsing;
- canonical unit normalization;
- nitrate/phosphate source-unit conversion policy;
- ammonia semantic mapping;
- no implicit zero for empty input.

### 43.5 Temperature tests

Cover:

- manual;
- fresh sensor;
- stale sensor;
- invalid reading;
- no assignment;
- wrong device family/capability;
- device reassignment;
- process recreation.

### 43.6 Persistence tests

Cover:

- create;
- observe history;
- latest;
- detail lookup;
- delete;
- owner isolation;
- tank isolation;
- duplicate id prevention;
- schema mismatch;
- corruption recovery.

### 43.7 Tank deletion tests

Cover:

- analyses removed with tank;
- failure before tank delete rolls analysis records back;
- cancellation;
- process death / durable recovery;
- device assignment cleanup still works;
- care-task cleanup still works.

### 43.8 UI/ViewModel tests

Cover:

- save success/failure;
- history from store;
- detail by analysis id;
- missing record;
- delete success/failure;
- latest metrics on Tank Health main screen;
- no-analysis state;
- loading state;
- double-click/navigation guard behavior.

### 43.9 Catalog regression tests

Freeze:

- plant catalog identity uniqueness;
- plant analysis-ready semantics;
- livestock catalog identity uniqueness;
- expected parser behavior;
- required coverage assumptions used by the engine.

---

## 44. CI / quality contract

Implementation is not complete until all existing CI remains green.

Forbidden shortcuts:

- no `@Suppress` to hide new architecture/lint/detekt debt;
- no lint baseline additions for Water Analysis;
- no detekt advisory baseline additions for Water Analysis;
- no weakening/removing architecture guards;
- no disabling CodeQL/lint rules;
- no raw navigation actions where generated Directions are required;
- no UI construction of persistence dependencies.

New code should be structured to satisfy the existing zero-new-debt policy naturally.

---

## 45. Implementation order

The implementation order is frozen as follows.

### Phase 1 - Freeze scientific/domain semantics

1. canonical WaterParameter enum;
2. canonical units;
3. nitrate/phosphate unit policy;
4. NH3/NH4 exact semantic;
5. severity/status model;
6. species-range intersection/conflict policy;
7. intrinsic chemistry rule catalog and evidence revision.

### Phase 2 - Application/domain foundation

8. WaterAnalysis input/record models;
9. structured assessment models;
10. recommendation/reason codes;
11. PlantCareCatalogOperations boundary;
12. AquariumHealthContext model/provider;
13. reuse/integrate LivestockWaterAdvisorOperations;
14. WaterQualityAssessmentEngine;
15. TankWaterTemperatureOperations boundary.

### Phase 3 - Persistence

16. dedicated water-analysis proto/store;
17. schema version;
18. strict store rules;
19. owner/tank-scoped queries;
20. create/delete/latest operations;
21. persisted assessment/provenance snapshot.

### Phase 4 - Integrity

22. integrate Water Analysis into tank deletion transaction;
23. process-death recovery;
24. account deletion cleanup;
25. backup/restore policy and implementation if included;
26. data inventory / export updates where applicable.

### Phase 5 - UI integration

27. WaterAnalysisViewModel;
28. save real analysis;
29. replace mock history;
30. pass analysisId through Safe Args;
31. bind real record detail;
32. implement real delete;
33. bind latest analysis to Tank Health Water Quality metrics;
34. connect fresh cooling sensor temperature.

### Phase 6 - Validation

35. engine tests;
36. persistence tests;
37. owner/process-death tests;
38. ViewModel/UI contract tests;
39. architecture/lint/detekt/CodeQL;
40. all CI green.

Only after this sequence:

41. Algae Control;
42. Plant Health;
43. Livestock Health.

---

## 46. Acceptance criteria for Water Quality completion

Water Quality is complete only when all of the following are true:

- approved UI remains visually intact unless explicitly changed;
- a user can enter a physically valid analysis and save it;
- temperature may be manual or a validated fresh tank sensor reading;
- saved analysis survives process death;
- history contains real records only;
- history record opens the correct detail by stable id;
- delete removes the exact record only;
- Tank Health main metrics use the latest real analysis;
- every supported parameter has a structured assessment;
- species requirements influence the result where data exists;
- incompatible inhabitants produce conflict rather than an average;
- verified plant requirements influence the result according to policy;
- partial plant data cannot create unsupported hard warnings;
- NO2 and the chosen NH3/NH4 semantic have evidence-backed intrinsic rules;
- missing information is represented explicitly;
- recommendations are deterministic codes;
- historical assessments remain stable after later catalog/rule changes;
- owner isolation is proven by tests;
- tank deletion cannot leave orphan analyses;
- account deletion handles the new store;
- no UI layer reads persistence/catalog/device repositories directly;
- no new lint/detekt/architecture debt is suppressed or baselined;
- Android CI, CodeQL, installable APK, and emulator integration workflows are green.

---

## 47. Final architecture principle

AquaLight Health is one contextual health platform with separate domain engines, not four disconnected features.

```text
Authoritative Aquarium Health Context
|
+-- Water Quality Engine
+-- Algae Control Engine
+-- Plant Health Engine
+-- Livestock Health Engine
```

The context is shared.

The domain decisions are separate.

Water Quality owns water measurements and their historical assessments.

Algae Control owns tank-level algae observation, cause analysis, intervention planning, and history.

Plant Health owns plant physiology, leaf/growth symptoms, nutrient-deficiency observations, and plant observation history. Algae observed on a plant is a finding that routes to Algae Control for remediation.

Livestock Health owns animal behavior, stress, disease/symptom observations, and livestock health history.

No engine duplicates another engine's responsibility, and no presentation layer becomes an unofficial second source of domain truth.
