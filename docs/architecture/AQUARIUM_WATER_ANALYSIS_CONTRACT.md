# AquaLight Aquarium Water Analysis Contract

## Status

This document freezes the architectural, data, analysis, persistence, and UI-integration contract for the first production-grade Aquarium Health analysis flow.

The existing Tank Health / Water Quality UI is considered visually complete for this stage. Implementation work governed by this contract must connect that UI to authoritative data and analysis without redesigning the approved screens unless a later explicit UI change is requested.

Accepted clarification on 26 September 2026 (K03.0): the selected tank type determines which measurement fields are shown. Preserve the existing screen structure, cards, field styling, grid, and navigation while populating them with the applicable measurements in section 25.1. Basic, tank-specific, and additional measurements are logical groups, not approval for a new visual layout. K03.1 subsequently freezes nitrate, nitrite, and phosphate recording semantics in section 6.1. K03.2 freezes the test/device selection, source-semantic resolution, and normalization workflow in sections 6.2, 6.5, 7, and 28.1. K03.3 freezes the canonical ammonia reporting bases in section 6.3. K03.4 freezes concurrent multi-result measurement/cardinality behavior in section 6.6. K03.5 freezes marine salinity/specific-gravity semantics and conversion safety in section 6.7. K03.6 freezes alkalinity/KH semantics, canonical units, and duplicate-field prevention in section 6.8. K03.7 freezes dissolved-oxygen concentration/saturation semantics and conversion prerequisites in section 6.9. K03.8 freezes chlorine/chloramine semantics, sample-context requirements, and multi-result handling in section 6.10. K03.9 freezes marine calcium/magnesium elemental semantics and hardness separation in section 6.11. K03.10 freezes conductivity/TDS semantics, temperature-basis provenance, and safe EC↔TDS behavior in section 6.12. K03.11 freezes direct-vs-calculated CO2 semantics in section 6.13. K03.12 freezes iron/potassium canonical semantics, iron analytical-scope handling, and the no-auto-dosing boundary in section 6.14. K03.13 freezes general-hardness semantics, canonical basis, and dGH conversion behavior in section 6.15. K03.14 freezes the first-release freshwater calculated-free-ammonia policy and marine exclusion in section 6.16. K04 freezes the structured severity, direction, coverage and conflict model in section 14. K05 freezes livestock range-bound and approximate/SOFT interpretation in section 10.5. Evidence-backed source profiles, numerical thresholds, and remaining K06–K18 decisions are still open.

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

The original Water Analysis UI contains the following measurement set:

- temperature;
- pH;
- NO3 / nitrate;
- NO2 / nitrite;
- NH3/NH4 / ammonia metric;
- GH;
- KH;
- PO4 / phosphate.

This original set is an inventory, not a fixed eight-field limit. The accepted tank-type field matrix in section 25.1 now defines the default measurement scope; section 25.2 records additional measurement support. The shared product-level core is temperature, pH, total ammonia, nitrite, and nitrate. Under accepted K03.3, the canonical total-ammonia metric is Total Ammonia Nitrogen (TAN), expressed as mg/L as N; direct free ammonia is a separate metric expressed as mg/L as NH3.

### 6.1 Canonical units

The domain must define one canonical unit per parameter.

Accepted with the user on 26 September 2026 (K03.1):

| UI label | Canonical recording and assessment meaning | Unit / reporting basis |
| --- | --- | --- |
| Nitrat (NO3) | Nitrate concentration | mg/L as NO3 |
| Nitrit (NO2) | Nitrite concentration | mg/L as NO2 |
| Fosfat (PO4) | Orthophosphate / reactive phosphate test result | mg/L as PO4 |

These meanings must stay consistent between input interpretation, normalized records, history, and the analysis engine. "As PO4" specifies the reported mass basis, not a claim that all phosphate exists in one ionic form. Total phosphorus is a different analytical scope and must not be treated as an orthophosphate measurement merely by multiplying a value.

For a compatible source already reporting the same basis and unit, normalization is an identity operation: a test result of 18 mg/L as NO3 remains 18 mg/L nitrate. This example is not a safe-range threshold. K03.1 does not authorize guessing the basis of an unidentified test result or relabelling existing `nitratePpm` / `phosphatePpm` catalog values. Under accepted K03.2, source semantics must be resolved from a verified test/device profile or an explicit guided typed selection before canonicalization; unidentified semantics are never guessed.

Remaining original scalar conventions are:

- temperature: degrees Celsius;
- pH: unitless;
- GH: accepted K03.13 canonical `mg/L as CaCO3`, with verified `dGH` source/display conversion;
- KH/alkalinity: governed by K03.6 rather than a generic dKH field semantic;
- ammonia: canonical ammonia semantics defined in section 6.3.

UI formatting and parsing must use `LocaleFormatter` or the central locale policy. The UI must not use ad-hoc `toDoubleOrNull`, comma replacement, or locale-blind number parsing.

### 6.2 ppm vs mg/L normalization

The livestock catalog currently names some fields `nitratePpm` and `phosphatePpm` while the Water Quality UI displays mg/L.

The engine must not silently compare differently named unit semantics.

K03.1 fixes the three canonical concentration meanings in section 6.1. Accepted K03.2 requires typed, source-aware normalization: a verified test/device profile supplies the measured analyte, chemical reporting basis, source unit, supported result modes, and conversion metadata; when a product is not in the catalog, the user may resolve the same semantics through a guided measurement-type/reporting-basis/unit selection. UI labels and free text are not semantic authority. Supported conversions, equivalence conditions, precision, and profile revisions must be explicit in application policy and tests.

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

Accepted with the user on 26 September 2026 (K03.3):

- canonical total ammonia is **`TOTAL_AMMONIA_NITROGEN` (TAN)**, normalized and assessed as **mg/L as N**;
- a directly measured free-ammonia result is **`FREE_AMMONIA_NH3`**, normalized and assessed as **mg/L as NH3**;
- ammonium-only (`NH4+`) and other reporting conventions remain distinct source semantics and must not be relabelled as either canonical metric without an explicitly verified conversion policy;
- the raw test/device result, its source unit/reporting basis, source profile/revision, and normalized canonical value are preserved separately under K03.2 provenance rules;
- conversion to TAN or free-NH3 canonical form occurs only when the selected verified profile or guided typed source semantics establish a supported conversion; no result is converted merely because a label contains `ammonia`, `NH3`, `NH4`, or `ppm`;
- a direct free-NH3 measurement is never substituted for TAN, and a TAN measurement is never presented as a direct free-NH3 measurement. K03.14 permits a separately identified calculated free-NH3 result only under the conditions in section 6.16.

UI contract for K03.3: keep the approved field/card design. The primary user-facing label remains **"Toplam amonyak"**, with the selected test/device's source unit/reporting context visible at entry. Users should not be forced to understand or manually convert to `mg/L as N`. When the selected profile reports direct free ammonia, present it explicitly as **"Serbest amonyak (NH3)"**. Technical canonical/provenance detail may be shown in record detail where useful, but the entry screen must not replace the familiar label with internal enum terminology.

The explanatory text **"Amonyak ve amonyum toplamı"** remains appropriate for the total-ammonia field; its exact help-control placement follows the pending section 25.3 interaction decision. K03.4 defines how a single measurement event stores and renders multiple independently measured ammonia results.

### 6.4 Accepted reuse of existing measurement models (K02)

Decision accepted with the user on 26 September 2026:

- extend the existing `AquariumWaterParameter` and `AquariumWaterSnapshot` models in a controlled way rather than introducing a parallel independent measurement model and a second parameter vocabulary;
- add the missing nitrite and ammonia support to these application-level measurement contracts during implementation, after K03 has established the exact chemical reporting basis and canonical units;
- continue using `LivestockWaterCompatibilityEvaluator` as the single authority for livestock range comparison through the existing application boundary;
- introduce separate models for the analysis record, structured assessment, and provenance; these event/result/source responsibilities must not be collapsed into the measurement snapshot;
- preserve existing callers through targeted mapping and regression checks; this decision does not approve a broad refactor.

The approved UI already contains NO2 and NH3/NH4 input controls. The missing support refers to the underlying measurement contracts and the future persistence/assessment integration, not missing UI fields. K02 alone did not authorize additional controls; the later K03.0 decision in section 25 defines the tank-specific field additions while preserving the existing UI design.

K03 is partially decided: K03.0 defines measurement scope, K03.1 defines nitrate/nitrite/phosphate canonical meanings, K03.2 defines test/device selection plus source-aware normalization behavior, K03.3 defines TAN as mg/L as N plus direct free NH3 as mg/L as NH3, and K03.4 defines concurrent multi-result storage/UI behavior. Existing `nitratePpm` and `phosphatePpm` names do not, by themselves, establish the source data's chemical reporting basis or authorize treating those values as mg/L. Evidence-backed profile/conversion entries, remaining parameter semantics, and rule thresholds still require their own decisions before implementation. Adding parameters must not invent livestock requirements where the catalog has none.

### 6.5 Accepted test/device selection and source-normalization policy (K03.2)

Decision accepted with the user on 26 September 2026: measurement meaning must be resolved at entry time wherever possible, using the user's actual test/device rather than guessing from a numeric value or a generic field label.

- **Parameter-scoped source selection:** test/device preference is stored per measurement parameter and isolated by owner. A single global product selection must not be applied to every parameter. Tank-bound hardware must also respect the tank/device assignment boundary. The same user may therefore use one source for nitrate and another for ammonia.
- **Verified product/method profile:** when the selected product is known, its verified profile supplies the measured analyte, chemical reporting basis, source unit, supported result modes, and any conversion metadata. Brand alone is insufficient; model, method, or result mode may change semantics.
- **Remembered low-friction entry:** after the source is selected, later measurements should normally require only the numeric result. The input control shows the source unit/reporting context and the application performs any supported normalization; the user is not asked to perform a conversion manually.
- **Multi-result capability:** a verified profile declares whether outputs are concurrent independent results or mutually exclusive device/test modes. If multiple results can be produced from the same measurement event, each result gets its own typed input/metric under K03.4; do not force the user to choose one and discard the other. Use an explicit mode selector only where the source itself is genuinely mutually exclusive. The application must never infer a mode from the number, previous value, or field name.
- **Guided unknown-product fallback:** if the product is not in the catalog, the flow must still allow the user to select the measurement type, chemical reporting basis, and source unit from a controlled typed list. This keeps the system extensible beyond the built-in brand catalog without making arbitrary free text a chemical-semantic source of truth.
- **Raw + normalized provenance:** preserve the entered raw result, resolved source semantics, product/profile id and revision when applicable, selected result mode, and the normalized canonical result separately. Conversion rules/revisions and required preconditions are traceable; rounding is presentation-only and occurs after assessment.
- **No silent reinterpretation:** changing the selected product, method, unit, or result mode after a value has been entered must not silently reinterpret the existing number. The flow must require explicit reconfirmation/re-entry or otherwise keep the old raw value bound to its original source semantics.
- **Unresolved input stays out of committed analysis:** if measurement meaning/unit still cannot be resolved, that field is not committed as a normal `WaterAnalysisRecord` measurement and does not enter engine comparisons or derived calculations. The unresolved value may remain in draft/form state while other resolved measurements are validated, assessed, and saved. This does not define a durable draft-store implementation by itself. Unknown is not zero, measured, normal, or safe.
- **Direct free-ammonia measurement:** when a verified test/profile exposes direct free-NH3, store it as a distinct measured metric, never as total ammonia and never as a calculated value. If the same source also produces TAN in the same event, K03.4 allows both measured metrics to coexist.
- **Calculated free ammonia:** K03.14 and section 6.16 define the freshwater calculation and explicitly exclude automatic marine/reef calculation in the first release. The source/event, applicability, presentation, and provenance requirements there are mandatory; no timestamp proximity alone proves that samples match.

Scientific background is in research references R7–R8 and R44–R46. K03.2 accepts the source-selection and resolution workflow; K03.3 freezes TAN as mg/L as N and direct free NH3 as mg/L as NH3. K03.14 governs the derived result. These decisions do not approve a complete commercial product catalog or toxicity thresholds.

### 6.6 Accepted concurrent multi-result measurement policy (K03.4)

Decision accepted with the user on 26 September 2026: if a real test/device can produce multiple semantically distinct measurements from the same sampling event, AquaLight must preserve those measurements independently instead of collapsing them into a single field with a mode switch.

- **Same event, separate metrics:** one `WaterAnalysisRecord` may contain both `TOTAL_AMMONIA_NITROGEN` and `FREE_AMMONIA_NH3` when both were directly measured for the same `observedAt` event. They share the analysis event identity but remain separate measurement entries.
- **Separate UI inputs for concurrent outputs:** when the selected verified source profile supports both results from the same event, the Add Analysis screen renders separate inputs **"Toplam amonyak"** and **"Serbest amonyak (NH3)"**. Each input displays its own source unit/reporting context. The user may enter either available result or both according to what was actually measured; missing one does not fabricate or infer it from the other.
- **Mode selector only for true exclusivity:** a selector/toggle is reserved for products or device configurations where the source can output only one mutually exclusive semantic mode at a time. Concurrent outputs must not be represented as mutually exclusive choices.
- **Independent provenance:** each measured metric retains its own raw value, source unit/reporting basis, result/channel identity when applicable, normalized value, and normalization provenance. Shared product/profile identity may be referenced by both entries without merging their measurements.
- **No overwrite or derivation by implication:** entering direct free NH3 must not overwrite TAN; entering TAN must not clear or automatically synthesize a directly measured free NH3 value. A calculated result allowed by K03.14 uses distinct derived provenance/type and never replaces a direct measurement.
- **Presentation:** record detail shows both measured metrics separately when present. The Water Quality input/metric presentation may expose direct free NH3 as an additional conditional metric while retaining **Toplam amonyak** as the common core metric; history summary may stay compact but must not mislabel one result as the other.
- **Validation/cardinality:** within one analysis event, TAN and direct free NH3 are different canonical parameter keys and therefore are not duplicates of each other. Validation must operate per canonical metric and preserve partial-analysis behavior.

K03.4 generalizes beyond Seachem: the rule is based on source capability metadata, not a brand-specific exception.
### 6.7 Accepted marine salinity / specific-gravity safety policy (K03.5)

Decision accepted on 26 September 2026 for commercial-safety behavior: AquaLight must not collapse salinity, specific gravity, conductivity, or vendor-labeled `ppt` into one generic numeric field. The exact physical/reporting basis is part of the measurement identity.

- **Practical Salinity:** use a distinct canonical metric such as `PRACTICAL_SALINITY_PSS78`. PSS-78 Practical Salinity is dimensionless in the domain model. A device may display `PSU`, but `PSU` is presentation/source metadata rather than a dimensional SI unit.
- **Specific Gravity:** use a separate canonical metric such as `SPECIFIC_GRAVITY`. SG is a dimensionless density ratio and is not numerically interchangeable with Practical Salinity. Persist the source's calibration/reference-temperature semantics and the sample/measurement temperature when the method requires them.
- **Absolute/mass salinity:** `ABSOLUTE_SALINITY_G_PER_KG` or another mass-fraction basis is a separate metric and may be used only when the source/method explicitly establishes that basis. Do not infer TEOS-10 Absolute Salinity from a generic aquarium label such as `ppt`.
- **Conductivity:** conductivity remains its own measured metric. A conductivity value becomes PSS-78 Practical Salinity only through a versioned, standard algorithm with the required inputs (including temperature and pressure/reference conditions) and declared applicability bounds. Do not use a hobby approximation formula.
- **Ambiguous `ppt`:** `ppt` alone is not a canonical semantic. The verified source profile must state what the manufacturer means by that display (for example a defined seawater/refractometer scale or another mass/concentration basis). Unknown `ppt` remains source-native/unresolved for normalization.
- **No blind SG ↔ salinity conversion:** do not convert `1.026` SG to `35` salinity, or the reverse, from the number alone. Conversion is permitted only when source type, scale/calibration, required temperature/reference metadata, and an evidence-backed algorithm are all available. The conversion algorithm/revision and inputs are persisted in provenance.
- **No location-based ocean correction for closed aquaria:** do not derive TEOS-10 Absolute Salinity from Practical Salinity by applying open-ocean longitude/latitude anomaly corrections to an aquarium. Synthetic/closed aquarium composition is not established by geographic location.
- **Typed rule matching:** chemistry/risk rules must carry the same metric/basis metadata as measurements. The engine compares directly only on matching semantics, or after an approved conversion. Missing conversion prerequisites produce `INSUFFICIENT_DATA`/conversion-unavailable behavior, never a guessed normal/critical result.
- **Do not double-count representations:** if one electronic instrument reports salinity and SG derived from the same underlying conductivity/temperature observation, preserve both source representations if useful but link them to the same source observation/provenance and do not count them as independent evidence.
- **UI:** preserve the approved card/grid layout but render the selected source's real representation. A PSS-78 source can show `Tuzluluk` with its device display convention; an SG source shows `Specific Gravity (SG)` rather than relabelling the number as salinity. Required reference-temperature/calibration context belongs in the source/profile/help/detail presentation, not as an invisible assumption.

K03.5 prioritizes a correct partial assessment over a precise-looking but unsupported conversion. A source-native SG or salinity result may still be stored historically when valid; if no matching rule or verified normalization path exists, that measurement is retained but not used to fabricate an assessment.
### 6.8 Accepted alkalinity / KH semantic and unit policy (K03.6)

Decision accepted with the user on 26 September 2026: AquaLight must treat alkalinity, carbonate hardness, general hardness, and their display units as distinct concepts. A familiar aquarium label such as `KH` or a unit such as `dKH` must never decide the domain semantic by itself.

- **Canonical total alkalinity:** use `TOTAL_ALKALINITY` as the canonical acid-neutralizing-capacity metric. Its canonical unit is **meq/L**. A verified source may report the same total-alkalinity semantic as `dKH`, `meq/L`, or `mg/L as CaCO3`; supported conversions are performed below the UI boundary and the raw source result is preserved.
- **Verified conversion only:** for a source explicitly verified as total alkalinity, the conversion policy may use the documented relationships `1 dKH = 17.86 mg/L as CaCO3 = 0.358 meq/L` and `1 meq/L = 50 mg/L as CaCO3`, with conversion constants/revision covered by tests. Bare `ppm` is not sufficient unless the source profile establishes `ppm as CaCO3`/equivalent semantics.
- **Marine/reef primary semantic:** the marine/coral profile uses `TOTAL_ALKALINITY`; do not create a second independent `KH` measurement field for the same titration/result. In the UI the field is **"Alkalinite"**; reef-friendly `dKH` may be the preferred display unit when the resolved source supports that representation, while the engine/storage canonical value remains meq/L.
- **Freshwater semantic separation:** `TOTAL_ALKALINITY` and `CARBONATE_HARDNESS` are distinct domain concepts. Existing user-facing **"Tampon kapasitesi (KH)"** may remain as a familiar label, but its selected verified test profile must state whether the result is total alkalinity or a true carbonate-hardness value/derivation. The label must not silently alias the two semantics.
- **Carbonate hardness is not total alkalinity:** `CARBONATE_HARDNESS` is related to hardness chemistry, while alkalinity is acid-neutralizing capacity. A carbonate-hardness value may be derived only from the required hardness/alkalinity information using an explicitly accepted method; it must not be synthesized from alkalinity alone or from a field name.
- **GH remains separate:** `GENERAL_HARDNESS` / total hardness is not alkalinity and is never substituted for `TOTAL_ALKALINITY` or `CARBONATE_HARDNESS`, even though multiple results may be reported as CaCO3 equivalents.
- **No duplicate evidence:** the same total-alkalinity observation represented in `dKH`, `meq/L`, and `mg/L as CaCO3` is one measurement with alternate representations, not three independent measurements or three independent pieces of evidence.
- **Typed rule matching:** rule/catalog thresholds carry metric and reporting-basis metadata. A total-alkalinity rule is compared to `TOTAL_ALKALINITY`; a true carbonate-hardness rule is compared to `CARBONATE_HARDNESS`. Conversion changes representation, not semantic identity.
- **Unknown KH tests fail closed:** if a product says only `KH` and its verified method/semantic cannot be resolved, preserve the source-native value in draft/history as allowed by K03.2 but do not feed it to a total-alkalinity or carbonate-hardness assessment. Return conversion/semantic unavailable or `INSUFFICIENT_DATA` rather than guessing.
- **UI:** marine/resif shows one alkalinity input, not separate `KH` and `Alkalinite` inputs for the same result. Freshwater retains the approved familiar label where appropriate, with source-profile/help/detail text making the resolved semantic explicit without forcing users to understand internal enum names.

K03.6 prioritizes semantic correctness over aquarium-industry shorthand. The UI may use familiar terminology, but storage, normalization, rules, history, and assessment use the resolved typed metric.
### 6.9 Accepted dissolved-oxygen concentration / saturation policy (K03.7)

Decision accepted with the user on 26 September 2026: AquaLight must distinguish measured dissolved-oxygen concentration from percent saturation and must not infer one from the other without the same-event environmental inputs and a versioned standards-based calculation.

- **Canonical concentration metric:** use `DISSOLVED_OXYGEN_CONCENTRATION` with canonical unit **mg/L O2**. This is the primary canonical value used by concentration-based water-quality rules.
- **Saturation metric/representation:** use `DISSOLVED_OXYGEN_SATURATION_PERCENT` for **% air saturation**. Percent saturation is related to concentration but is not the same physical/reporting value and must not be stored in the concentration field.
- **Same observation, not duplicate evidence:** when one verified probe/device reports both mg/L and % saturation from the same underlying oxygen observation, preserve both source outputs if useful, link them through the same source-observation/provenance identity, and do not count them as two independent measurements/evidence signals.
- **Context-dependent conversion:** conversion between mg/L and % saturation is permitted only when the calculation has the required same-event inputs for the selected method: water temperature, barometric/pressure reference, and salinity or specific conductance where applicable. The exact algorithm, applicability range, constants, and revision must be versioned and tested.
- **No present-context substitution for historical samples:** never use today's temperature, salinity/conductivity, pressure, or sensor state to convert a historical/backdated oxygen result. Required inputs must belong to the same measurement event or be explicitly proven to represent that event.
- **Verified device compensation:** if a device internally applies temperature, salinity, altitude/barometric-pressure, or other compensation, the source profile must declare that behavior and the stored provenance must retain the compensation inputs/settings available from the device. AquaLight must not silently compensate a value a second time.
- **Source-native-only is valid:** a valid source may report only mg/L or only % saturation. Store the verified source-native result. If prerequisites for a safe cross-representation conversion are missing, conversion remains unavailable; do not fabricate the missing representation.
- **Fail closed for assessment:** a concentration rule compares to `DISSOLVED_OXYGEN_CONCENTRATION`; a saturation rule compares to `DISSOLVED_OXYGEN_SATURATION_PERCENT`. If a required representation is unavailable and no verified conversion path exists, return `INSUFFICIENT_DATA` / conversion unavailable rather than a guessed normal, warning, or critical status.
- **100% is not a health verdict:** `% saturation = 100` represents equilibrium with the relevant atmospheric conditions, not an automatic aquarium-health score or universal safe state. Supersaturation above 100% is possible and requires its own evidence-backed interpretation.
- **UI:** preserve the approved card/grid design. The field label remains **"Çözünmüş oksijen"** and the selected verified source's actual unit is shown (`mg/L O2` or `% doygunluk`). If a device provides both values concurrently, the UI may show the second representation as a linked secondary value/detail without creating a second independent oxygen health signal.

K03.7 prioritizes the directly measured/source-native oxygen result and same-event provenance over convenience conversion. A precise-looking converted value is forbidden when its temperature, salinity/conductivity, pressure, or method context is missing.
### 6.10 Accepted chlorine / chloramine semantic and sample-context policy (K03.8)

Decision accepted on 26 September 2026 for commercial-safety behavior: AquaLight must represent free chlorine, total chlorine, combined chlorine, and directly measured monochloramine as distinct semantics. A negative free-chlorine result alone must never be interpreted as proof that chloramine is absent.

- **Free chlorine:** use `FREE_CHLORINE_AS_CL2` with canonical unit **mg/L as Cl2** for methods verified to measure free available chlorine (primarily HOCl/OCl-).
- **Total chlorine:** use `TOTAL_CHLORINE_AS_CL2` with canonical unit **mg/L as Cl2** for methods verified to measure total available chlorine. Total chlorine is not a direct monochloramine measurement.
- **Combined chlorine is derived, not monochloramine:** when free and total chlorine are measured from the same sample/event with method-compatible semantics, AquaLight may derive `COMBINED_CHLORINE_AS_CL2 = TOTAL - FREE`, preserving calculation provenance and uncertainty/precision rules. This derived value represents combined chlorine residual; it must not be labelled `MONOCHLORAMINE` or treated as a directly measured chloramine species.
- **Direct monochloramine:** use `MONOCHLORAMINE_AS_CL2` only for a verified method/profile that directly measures monochloramine. A direct monochloramine result is a separate measured metric and may coexist with free/total chlorine under the K03.4 multi-result policy.
- **Other chloramine species:** dichloramine/organic chloramines are not inferred from free/total chlorine alone. If later supported, each requires its own verified method/metric. `TOTAL - FREE` does not speciate chloramines.
- **Canonical basis:** `ppm` or a generic `chlorine` label is not sufficient semantic authority. The source profile must establish the analyte and reporting basis (for example mg/L as Cl2) before normalization or rule comparison.
- **Required sample context:** every chlorine-family measurement carries a typed sample context. At minimum support `RAW_SOURCE_WATER`, `CONDITIONED_SOURCE_WATER`, and `TANK_WATER`. A measurement taken from tap/source water before conditioner is not interchangeable with a post-conditioner bucket/reservoir sample or the aquarium itself.
- **Before/after conditioning are different events:** chlorine results before treatment and after dechlorination/conditioning must not overwrite one another. They are separate samples/observations even if taken minutes apart during the same water-change workflow.
- **Matrix/method applicability:** the verified profile declares whether the method is valid for the sample matrix (for example municipal source water, freshwater tank water, marine/seawater). DPD and related methods can have oxidant/interference behavior; a method that is not verified for the matrix must not produce a hard chlorine/chloramine assessment.
- **Same sample for derived combined chlorine:** derive combined chlorine only when free and total measurements belong to the same sample context, compatible sample time/window, and method family/profile required by the derivation. Never subtract a tank free-chlorine result from a tap-water total-chlorine result.
- **Inconsistent free/total pair:** if measured total chlorine is lower than measured free chlorine, do not clamp the difference to zero and do not emit a negative combined-chlorine value. Treat the pair as inconsistent for derivation (considering method precision/uncertainty policy), preserve both raw results, and require re-test/verification before a combined-chlorine assessment.
- **No false chloramine conclusion:** `FREE = 0` with `TOTAL > 0` may establish combined chlorine under a compatible method pair, but does not prove that all combined chlorine is monochloramine. UI/reasons must say combined chlorine detected/indicated unless a direct monochloramine method was used.
- **Transient-sample provenance:** chlorine/chloramine results should retain sample timestamp and source context because disinfectant residual can change after sampling. Product/method instructions that require prompt/on-site reading are profile metadata and must be respected in validation/quality state.
- **UI:** when a source/test supports both free and total chlorine from the same sample, show separate **"Serbest klor"** and **"Toplam klor"** inputs; do not collapse them into one selector. If a direct monochloramine method is selected, show **"Monokloramin"** as its own field. Sample context must be explicit enough that the user knows whether they are testing raw source water, conditioned/prepared water, or tank water.
- **Assessment fail-closed:** rule evaluation compares matching typed metrics and sample contexts. Unknown analyte/basis, unsupported matrix, mismatched sample contexts, or invalid derivation prerequisites produce `INSUFFICIENT_DATA` / semantic-or-method unavailable rather than a guessed safe/unsafe result.

K03.8 deliberately separates measurement semantics from later safety thresholds. It freezes what was measured and where the sample came from; numerical hazard/action thresholds remain evidence-backed rule-catalog decisions.
### 6.11 Accepted marine calcium / magnesium elemental-basis policy (K03.9)

Decision accepted on 26 September 2026 for production-grade reef chemistry semantics: marine/reef calcium and magnesium are stored and assessed as elemental ion concentrations, not as generic hardness or CaCO3-equivalent values.

- **Calcium canonical metric:** use `CALCIUM_CONCENTRATION` with canonical unit **mg/L as Ca2+**.
- **Magnesium canonical metric:** use `MAGNESIUM_CONCENTRATION` with canonical unit **mg/L as Mg2+**.
- **Generic `ppm` is not enough:** a product/display value labelled only `ppm` is normalized only when the verified source profile establishes that the manufacturer means elemental calcium or elemental magnesium on an mg/L-equivalent basis for that method. The UI may preserve the product's familiar `ppm` display, but domain semantics come from the profile.
- **Hardness is separate:** `CALCIUM_HARDNESS_AS_CACO3`, `MAGNESIUM_HARDNESS_AS_CACO3`, `GENERAL_HARDNESS`, and related CaCO3-equivalent hardness results are not the same metrics as elemental Ca2+/Mg2+ concentration. They must never be inserted directly into reef calcium/magnesium rules by label similarity.
- **Derived hardness conversions require explicit method support:** a hardness result may be converted to elemental Ca or Mg only when the source method, reporting basis, stoichiometric conversion, sample matrix, interference policy, and precision are all explicitly supported and versioned. A generic GH result must never be split into calcium and magnesium by assumption.
- **Difference methods remain derived:** if a method obtains magnesium hardness by subtracting calcium hardness from total hardness, preserve that derivation and its source/uncertainty. Do not present the result as a directly measured elemental magnesium value unless the complete verified conversion path establishes that semantic.
- **Marine matrix applicability:** verified profiles declare marine/seawater applicability, range, temperature constraints, dilution requirements, and known interferences. A freshwater hardness method is not automatically valid for reef Ca/Mg assessment.
- **Concurrent results:** a multi-test product that independently measures Ca and Mg exposes separate **"Kalsiyum"** and **"Magnezyum"** inputs/metrics under K03.4. Neither value is inferred from the other, alkalinity, salinity, or GH.
- **No duplicate evidence:** alternate representations of the same Ca or Mg observation are one measurement/provenance chain, not multiple independent evidence signals.
- **UI:** marine/coral profiles show separate `Kalsiyum` and `Magnezyum` fields. The selected verified product's actual source unit/display may be shown, while storage and rules use mg/L as Ca2+ and mg/L as Mg2+ respectively.
- **Fail closed:** unresolved elemental basis, unsupported matrix, invalid dilution/method prerequisites, or a hardness-only result without an approved conversion path produces source-native/`INSUFFICIENT_DATA` behavior rather than a fabricated reef calcium/magnesium assessment.

K03.9 does not freeze target reef ranges or dosing advice. Those remain evidence-backed rule/recommendation decisions.
### 6.12 Accepted conductivity / TDS product and normalization policy (K03.10)

Decision accepted on 26 September 2026 for aquarium-product usability and data safety: users enter the TDS value exactly as shown by their meter in ppm. AquaLight must not expose EC→TDS conversion-factor choices in normal UI, but it must preserve enough source metadata to avoid inventing conversions or treating different source representations as independent evidence.

- **Conductivity metric:** use `ELECTRICAL_CONDUCTIVITY` with canonical unit **µS/cm**. Source values reported in mS/cm may be safely scale-normalized to µS/cm because this is a unit scaling of the same quantity.
- **Temperature basis is part of conductivity provenance:** retain whether the displayed conductivity is in-situ/raw, temperature-compensated/reference (for example 25 °C), or device-compensated with a known profile. If the source profile says ATC/reference compensation is already applied, AquaLight must not compensate it a second time.
- **TDS product metric:** use `TDS_REPORTED_PPM` for the user/device-reported TDS value. In the UI the field remains simply **"TDS"** with unit **ppm**; the user enters the meter reading as-is and is never asked to choose 0.5/0.7/etc. merely to save a TDS result.
- **Reported TDS is preserved even when the factor is unknown:** if a valid meter reports `180 ppm`, AquaLight stores `180 ppm` as that source's TDS reading. Lack of a known conversion factor does not invalidate the user-entered TDS result itself.
- **EC-derived TDS metadata:** many electronic TDS meters derive their ppm display from temperature-referenced conductivity using a device/profile conversion factor or scale. When known from the verified source profile, retain the factor/scale and reference-temperature behavior in provenance. This metadata is backend/source-profile detail, not a normal user prompt.
- **No blind EC↔TDS conversion:** AquaLight may derive TDS from EC, or EC from TDS, only when the source/profile conversion relationship and temperature basis are explicitly known and supported. Do not assume a universal factor such as 0.5, 0.64, or 0.7.
- **Same device observation is one evidence chain:** if one meter shows EC and TDS from the same probe reading, preserve both representations if useful but link them to one source observation; do not count EC and factor-derived TDS as independent water-quality evidence.
- **Rule compatibility:** TDS rules/ranges must declare the TDS reporting scale/basis or otherwise be curated as compatible with the source reading. Existing catalog `TDS` ranges with unknown scale metadata must not automatically create hard safe/unsafe decisions against arbitrary TDS-meter ppm values.
- **Role in AquaLight:** TDS is supported as an additional tracking/water-preparation metric, especially for freshwater, shrimp, remineralization, and RO/DI workflows. It does not replace GH, KH/alkalinity, salinity, nitrate, or ion-specific measurements and is not an overall health score.
- **Marine/reef:** marine/reef primary ionic-strength interpretation remains the K03.5 salinity/SG/conductivity model. A generic TDS ppm value is not used as a substitute for salinity.
- **Fail closed for cross-representation assessment:** if a rule requires EC but only TDS is available (or the reverse) and the source conversion profile is unknown, keep the source-native value and return conversion-unavailable/`INSUFFICIENT_DATA` for that rule rather than fabricating a converted value.

K03.10 deliberately keeps the user experience simple while keeping conversion assumptions out of the analysis engine. The device reading is accepted as reported; conversion metadata matters only when AquaLight tries to translate or compare across representations.
### 6.13 Accepted direct / calculated CO2 policy (K03.11)

Decision accepted with the user on 26 September 2026: AquaLight supports both a directly measured dissolved-CO2 result and a CO2 value calculated from compatible pH + KH measurements. These are different provenance types and must never be presented as the same kind of result.

- **Direct CO2:** use `DISSOLVED_CO2_CONCENTRATION` with canonical unit **mg/L as CO2** when the selected verified test/device directly reports dissolved CO2. The user enters the value shown by the test/device.
- **Calculated CO2:** use a distinct derived result such as `CALCULATED_DISSOLVED_CO2_CONCENTRATION`, also expressed as **mg/L as CO2**, when AquaLight calculates CO2 from a supported pH + KH method. It is displayed explicitly as **"Hesaplanan CO2"** and never as a directly measured result.
- **Same-event inputs:** pH and KH used for the calculation must belong to the same analysis/sample event (or an explicitly accepted time window for the same tank/sample). Historical CO2 must not be recalculated from today's pH/KH values.
- **Typed KH requirement:** the KH input must have a resolved semantic that the accepted calculation method supports. AquaLight must not feed an unresolved `KH` label, GH, marine alkalinity, or another hardness value into the formula merely because the unit/label looks similar.
- **Versioned calculation:** the pH+KH calculation formula, supported input basis/units, applicability limits, and revision are application/domain policy and must be versioned and covered by golden-vector tests. The inputs and calculation revision are persisted in provenance.
- **Direct result has precedence:** when a verified direct CO2 result and a calculated CO2 value both exist for the same event, preserve both but use the direct measured result as the primary CO2 measurement. The calculated value remains secondary/derived evidence and must not overwrite the direct value.
- **pH meter is not a CO2 meter:** a pH meter result by itself is only pH. AquaLight may calculate CO2 only when the required compatible KH result is also available; pH alone must never create a CO2 value.
- **Drop checker:** drop-checker color is not converted into a fixed ppm CO2 value in v1. If retained later, it is an optional qualitative indicator/observation only and never a substitute for direct or pH+KH-calculated CO2.
- **Equipment presence is not a concentration:** a CO2 cylinder, solenoid, diffuser, or configured CO2 system does not create a measured/calculated concentration by itself.
- **UI:** keep the existing Water Quality design. A direct test/device shows `CO2` with its reported mg/L value. If no direct result exists but a valid pH+KH calculation is available, show **"Hesaplanan CO2"** with mg/L. Do not ask the user to manually perform the formula.
- **Fail closed:** missing/ambiguous pH or KH semantics, incompatible input basis, invalid numbers, or unavailable calculation prerequisites produce no calculated CO2 value. Preserve the measurements that are valid and report calculation unavailable/`INSUFFICIENT_DATA` for CO2 rather than guessing.

K03.11 does not approve automatic fertilizer/CO2 dosing. It freezes measurement-vs-calculation semantics only.
### 6.14 Accepted iron / potassium semantic and dosing-boundary policy (K03.12)

Decision accepted with the user on 26 September 2026: AquaLight supports iron and potassium as simple user-facing aquarium measurements while preserving the test method's actual analytical meaning in the source profile. The UI must not turn this into a laboratory workflow.

- **Potassium canonical metric:** use `POTASSIUM_CONCENTRATION` with canonical unit **mg/L as K**. A verified aquarium test that displays potassium as `ppm K` may map that source display to the same elemental-K canonical value according to its profile; the user enters the number shown by the test and performs no manual conversion.
- **Iron canonical basis:** use `IRON_CONCENTRATION` with canonical unit **mg/L as Fe**, but require an attached typed analytical scope. Minimum scopes are `TOTAL_IRON`, `DISSOLVED_IRON`, `FERROUS_IRON_FE2`, and `METHOD_DEFINED_IRON` for a verified aquarium method whose manufacturer-defined Fe result is known but is not safely equivalent to one of the first three.
- **Iron scopes are not interchangeable:** total, dissolved, ferrous, and method-defined Fe results are separate analytical meanings. A rule or catalog range may compare only to the same scope or to an explicitly documented compatible mapping. AquaLight must never relabel ferrous iron as total iron, infer ferric iron from a single Fe result, or treat every `Fe` test as chemically identical.
- **Consumer UI remains simple:** the normal field label is **"Demir (Fe)"** and the source unit is shown beside it. For a known test/profile, AquaLight resolves the analytical scope automatically. The user is not asked to choose total/dissolved/ferrous terminology unless the product is unknown and the K03.2 guided fallback genuinely requires semantic resolution.
- **Multi-result iron methods:** if a verified device/test independently reports more than one iron fraction in the same event, K03.4 applies: store/render separate typed results rather than collapsing them into one selector or overwriting one with another.
- **Method range and matrix:** the verified source profile carries the supported measurement range, sample matrix, dilution instructions where applicable, known interferences, and method revision. Out-of-range/qualified results must never be silently clamped to zero or to the nearest scale value; exact bounded-result handling follows the shared input/limit policy.
- **Potassium compound labels are not elemental K by default:** a result expressed as `K2O`, another potassium compound basis, or an unidentified `ppm` basis is not silently treated as mg/L K. Conversion is allowed only when the verified source profile explicitly defines the reporting basis and supported stoichiometric conversion.
- **No nutrient inference:** iron must not be inferred from fertilizer presence, plant symptoms, substrate type, or another micronutrient; potassium must not be inferred from conductivity/TDS, GH, fertilizer presence, or another macroelement.
- **No automatic fertilizer dosing from a bare Fe/K result:** K03.12 authorizes measurement storage and evidence-backed assessment only. A future dosing feature requires its own contract covering product composition/concentration, tank volume, target change, maximum dose, recent dosing/water-change history, interaction with other nutrients, and safety constraints. Until then AquaLight may explain that a value is low/high only when an evidence-backed matching rule exists, but must not output an amount of fertilizer to add from the measurement alone.
- **UI:** preserve the approved Water Quality design with separate **"Demir (Fe)"** and **"Potasyum (K)"** fields when those additional measurements are enabled. Internal analytical-scope/provenance details belong to the source profile and record detail, not to extra mandatory laboratory controls on the entry screen.
- **Fail closed:** unresolved iron scope, unsupported sample matrix/method, unknown compound basis, or incompatible rule semantics produce source-native/draft or `INSUFFICIENT_DATA` behavior according to K03.2 rather than a fabricated nutrient assessment.

K03.12 does not freeze plant nutrient target ranges, fertilizer brands, or dosing quantities. Those require evidence-backed rule/recommendation decisions.
### 6.15 Accepted general-hardness / GH policy (K03.13)

Decision accepted on 26 September 2026 for aquarium usability and consistent rule matching: AquaLight treats aquarium GH as a total/general-hardness measurement, keeps the familiar `dGH` user experience, and normalizes the same hardness quantity to a CaCO3-equivalent canonical basis below the UI boundary.

- **Canonical metric:** use `GENERAL_HARDNESS` for aquarium general/total hardness with canonical unit **mg/L as CaCO3**.
- **Aquarium display/input:** the normal user-facing label remains **"Genel sertlik (GH)"**. A verified aquarium drop test that reports degrees German hardness may show/input **dGH** directly; the user enters the number produced by the test and performs no manual conversion.
- **Verified unit conversion:** for the same general-hardness semantic, use the accepted hardness conversion `1 mg/L as CaCO3 = 0.056 °dH`, equivalently about `1 dGH = 17.9 mg/L as CaCO3`. Preserve the raw source value/unit and normalize separately. Conversion constants/revision are covered by unit tests.
- **GH is not KH/alkalinity:** `GENERAL_HARDNESS` must never alias `CARBONATE_HARDNESS` or `TOTAL_ALKALINITY`, even when the UI uses familiar GH/KH degree labels. K03.6 remains authoritative for alkalinity/KH behavior.
- **GH is not elemental Ca or Mg:** general hardness in aquarium practice is driven primarily by calcium and magnesium hardness equivalents, but one GH value does not reveal the individual elemental Ca2+ and Mg2+ concentrations. AquaLight must not split GH into calcium and magnesium by assumption or feed GH directly into marine elemental Ca/Mg rules.
- **No naive Ca+Mg sum:** elemental calcium and magnesium values in mg/L must not simply be added and called GH. A calculation to CaCO3-equivalent hardness requires the approved equivalent-mass method and complete required inputs; if supported later, it is a derived result with calculation provenance.
- **Source profile semantics:** known GH test/device profiles declare whether they report dGH, mg/L as CaCO3, or another supported hardness representation. An unidentified `ppm` or generic `hardness` result is not silently assumed to be general hardness as CaCO3.
- **Concurrent hardness outputs:** if a verified device independently reports general/total hardness and calcium hardness or another hardness fraction, preserve them as separate typed metrics under K03.4; do not overwrite one with another.
- **UI simplicity:** no extra laboratory controls are added for ordinary GH entry. Source profile selection resolves the representation automatically; guided fallback is used only for an unknown product as required by K03.2.
- **Fail closed:** unresolved hardness type/basis or an unsupported source representation remains source-native/draft or `INSUFFICIENT_DATA` according to K03.2 rather than being guessed into GH.

K03.13 freezes representation and conversion semantics only. Species/tank GH target ranges remain evidence-backed rule-catalog data and are not approved by this unit decision.
---

### 6.16 Calculated free-ammonia policy (K03.14)

Decision accepted at the user's request on 26 September 2026: support a clearly labelled **estimated** free-ammonia result in the first freshwater release when all validated inputs belong to the same water sample. Direct free-NH3 measurements remain primary. The calculation is an optional assessment output, never an additional user input or a substitute for the recorded TAN result.

- **Freshwater equation and mass basis:** for verified `TOTAL_AMMONIA_NITROGEN` in `mg/L as N`, water temperature `T` in °C and measured sample pH, use the Emerson equilibrium relationship quoted by EPA: `pKa = 0.09018 + 2729.92 / (273.2 + T)` and `f_NH3 = 1 / (1 + 10^(pKa - pH))`. First calculate `NH3-N = TAN × f_NH3` in `mg/L as N`, then convert to the existing `FREE_AMMONIA_NH3` basis: `NH3 = NH3-N × (M(NH3) / M(N))` in `mg/L as NH3`. Fix the atomic/molecular masses and equation revision in the versioned calculation policy. Never compare `NH3-N` directly with a rule expressed as `NH3`.
- **Initial supported envelope:** only freshwater samples within **pH 7.0–10.2** and **6–32 °C** are eligible in v1. These are conservative product bounds matching the published UF/IFAS fraction table, not claims that chemistry stops outside them. No extrapolation or clamping; outside this envelope the output is `CALCULATION_UNAVAILABLE` while TAN and other valid measurements remain saved. Broader support needs separately verified reference vectors and a policy revision.
- **Same sample, not merely a nearby clock time:** TAN, pH, and temperature must be explicitly associated with one sample/measurement-event identity and tank-water context. A form may group values the user actually measured for the same sample; a nearby timestamp, an old pH reading, a current tank sensor value attached to a historical TAN result, or a source-water measurement does not prove that relationship. Timestamp, origin, source profile, and validity of each input are retained. Sensor temperature may participate only when K08/K09 freshness and tank assignment establish that it represents this sample at its observed time; otherwise require a same-sample temperature measurement. No arbitrary minute window silently joins independent observations.
- **Input quality:** resolve TAN's reporting basis under K03.2, respect test detection/range/uncertainty metadata and preserve `0`, below-detection, and missing as different states under K11. pH and temperature must be finite, valid measured values; do not use defaults, inferred pH, or a rounded presentation value in the equation. Calculate from unrounded normalized inputs; apply rounding only to display. If a prerequisite is invalid, unresolved, out of bounds, or absent, do not create a numeric NH3 result or a false safe verdict.
- **Marine/reef first-release boundary:** do **not** run this freshwater equation on marine/brackish water or silently treat a salinity reading as a freshwater correction. In v1, marine/reef free NH3 requires a verified direct free-NH3 measurement; TAN may still be retained and evaluated only by independently approved, matching TAN rules. A later marine calculation requires a versioned seawater equilibrium method, verified salinity and pH-scale semantics, same-sample inputs, published reference vectors, and explicit applicability/precision tests before it can be enabled. EPA's saltwater model and pH-scale discussion are references for that separate gate, not permission to use the freshwater equation.
- **Direct measurement priority and display:** if a verified direct free-NH3 result exists for the event, use it as the primary free-ammonia result and do not produce a second calculated value in v1. Otherwise, an eligible freshwater estimate appears as **"Hesaplanan serbest amonyak (NH3)"**, with `mg/L as NH3`, an estimated/derived marker, and a short explanation that it uses the same-sample TAN, pH, and temperature. The record retains input identities and raw/normalized values, formula and constants revision, computed fraction, computed result before display rounding, and calculation status/reason. Reopening history uses the persisted snapshot; later measurements or formula revisions do not rewrite it.
- **Assessment boundary:** a derived value is one interpretation of TAN, not independent evidence. Do not double-count TAN and calculated NH3, automatically declare the tank safe from a low estimate, or use this calculation to approve dosing. Any numeric hazard threshold and treatment advice require their own versioned, species/context-appropriate evidence under W0.4; K04 defines how supported findings are combined. Missing calculation is reported as unavailable/partial, not `0` or `Normal`.
- **Verification gate:** unit and golden-vector tests cover the fraction, `as N` to `as NH3` conversion, zero vs detection-limit input, envelope edges, monotonicity with pH/temperature, direct-result priority, cross-sample/time/tank rejection, marine exclusion, rounding, and historical snapshot stability. For example, with `TAN = 1 mg/L as N`, `pH = 8.0`, `T = 25 °C`, the reference equation yields `f_NH3 ≈ 0.053842` and `NH3 ≈ 0.06547 mg/L as NH3`; this is a calculation test vector, **not** a safety threshold.

Sources: EPA 2013 freshwater ammonia criteria (Emerson equation and TAN/NH3 mass-basis distinction), UF/IFAS *Ammonia in Aquatic Systems* (published fraction table), and EPA 1989 saltwater ammonia criteria (seawater equilibrium and pH-scale dependence); see research note R44–R46.

---

## 7. Measurement provenance

Under accepted K03.2, every committed non-temperature measurement whose semantics or normalization depends on a selected test/device must retain source-resolution provenance sufficient to explain what the user entered and how it became a canonical value. At minimum this includes source kind, profile id/revision when catalog-backed, result mode when applicable, analyte/reporting basis, source unit, raw value, and normalization rule/revision when conversion occurred. A guided non-catalog selection stores the resolved typed semantics rather than inventing a product profile id. Temperature additionally requires the sensor provenance below.

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

### 10.5 Accepted range-bound and approximate/SOFT policy (K05)

Accepted with the user on 26 September 2026. Preserve the existing `LivestockWaterRequirementParser` and `LivestockWaterCompatibilityEvaluator` as the single parse/comparison path under K02/section 10.1; extend their typed range and result semantics instead of creating another evaluator. Current `LivestockParameterRange.contains()` includes both endpoints, while the parser maps `<` and `≤` to the same maximum and `>` and `≥` to the same minimum. The catalog currently has 687 `SOFT` records, including 582 strict `<` nitrate strings and two `~0.05` phosphate strings; those labels are guidance, not verified toxicity thresholds.

| Source text | Typed meaning | Comparison at the stated boundary |
| --- | --- | --- |
| `<20` | Upper bound 20, **exclusive** | 20 is outside; 19.9 is inside this catalog interval. |
| `≤20` | Upper bound 20, **inclusive** | 20 is inside. |
| `>20` / `≥20` | Lower bound 20, exclusive / inclusive respectively | 20 is outside / inside respectively. |
| `20–26` | Explicit two-sided interval; endpoints inclusive unless the source marks them otherwise | 20 and 26 are inside; no invented tolerance beyond either endpoint. |
| `~20`, `≈20`, `about 20`, or an unqualified single value | Nominal/approximate target with **no verified tolerance** | It is not converted into `[20,20]`, `19–21`, or any other pass/fail interval. Display as informational context only until a supported tolerance/source is verified. |
| `~20–26` or another explicitly approximate interval | Approximate guidance whose endpoints are not validated exact limits | Keep the stated values and approximate marker for display; do not issue an exact-bound violation or hard conflict without separately verified endpoint semantics. |

- **Typed bounds and provenance:** each successfully parsed interval retains lower/upper value, inclusive/exclusive flag, raw source string, catalog record/revision and approximate marker. A missing bound is genuinely open-ended, not an invented zero/infinity. The existing two-sided constructor/defaults can remain inclusive for callers already creating explicit `20–26` intervals; source `<`/`>` parsing must set exclusivity explicitly. Comparison uses unrounded, unit-compatible canonical values; display rounding cannot change an endpoint decision.
- **Strict supported grammar:** consume the entire recognized value/range expression, not the first one or two numbers found anywhere in a string. Reject ambiguous, malformed, reversed or unsupported expressions as `UNPARSEABLE_REQUIREMENT` with the original text and record identity retained. Do not swap reversed endpoints, infer a `±` tolerance, mistake a unit/version number for a bound, or silently map an unknown `warningMode` string to an authoritative one. K03 source-unit/basis verification remains a prerequisite to comparison; a numeric match of incompatible units is not compatibility.
- **Evidence strength:** an out-of-interval value from a comparable `SOFT` profile may generate an `ADVISORY` with its affected animal, direction and source; it is not by itself a `WARNING`, `CRITICAL`, hard toxicity rule, or a treatment/dosing instruction. A value inside a SOFT interval means only "within this catalog guidance," not proof of tank safety. An `INFORMATIONAL` profile and nominal/approximate-only value remain context without directional pass/fail. Even a catalog label `HARD` requires separately verified rule authority, applicable species/context and evidence under W0.4 before it can produce a hard hazard; the string alone is not authority. K18 will decide which requirements may create hard habitat conflicts.
- **Coverage and zero comparisons:** missing/unparseable/approximate-only requirements and absent measurements are reported distinctly under K04 coverage. `issues.isEmpty()` with `checkedParameterCount = 0` is **not** `COMPATIBLE`; the adapter must emit no-comparable-evidence/insufficient coverage. An exclusive-bound violation remains visible as an issue even when severity is advisory. A catalog requirement conflict can be recorded independently of measurements, but SOFT ranges must not silently become a hard incompatibility; K18 owns cross-entity intersection eligibility.
- **Regression gate:** tests cover every `<`, `≤`, `>`, `≥` exact endpoint and nearby value; inclusive two-sided endpoints; nominal/approximate-only input; malformed/reversed/multiple-number strings; compatible units, missing values and zero comparisons; `SOFT` outside guidance staying advisory; unknown warning-mode fail-closed behavior. Existing inclusive two-sided behavior remains intact. These are behavioral tests for the shared parser/evaluator and its Water Analysis mapping, not a second implementation.

This decision does not approve the catalog's numerical ranges as universal safe values. Catalog source quality, chemical reporting bases and applicability still require the K03/W0.4 checks before a rule can use them.

---

## 11. Aggregating multiple livestock requirements

Do not average species ranges.

For each comparable parameter, the engine should compute the intersection of all authoritative requirements that apply.

K05's endpoint inclusion/exclusion must be preserved during any later intersection: touching an exclusive endpoint is not an overlap. SOFT or approximate-only ranges are not automatically authoritative hard requirements; K18 freezes the cross-entity evidence/eligibility and conflict policy before implementation.

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

Accepted with the user on 26 September 2026 (K04): the engine returns **independent, locale-independent dimensions**, not one mutually exclusive `OPTIMAL / LOW / HIGH / WARNING / CRITICAL / CONFLICT / INSUFFICIENT_DATA` enum. The existing `LivestockWaterAssessmentStatus` is an input/adapter concern; its values must not be cast directly to the overall Water Quality result.

| Dimension | Contract | Meaning |
| --- | --- | --- |
| `hazardSeverity` | Nullable `NONE`, `ADVISORY`, `WARNING`, `CRITICAL` | Highest **supported** adverse finding from the evaluated rules; `NONE` means no adverse finding **among evaluated evidence**, not that the aquarium is safe. `null` means no eligible rule was evaluated. |
| `direction` | Per evaluated rule/parameter: `BELOW`, `WITHIN`, `ABOVE`; absent with a typed non-evaluation reason when no comparison was possible | Direction relative to that rule's typed interval. It is not an overall hazard level; different applicable rules may produce different directions for one measurement. |
| `coverage` | `NONE`, `PARTIAL`, `COMPLETE`, plus evaluated/required/missing/unusable rule and measurement identities | How much of a **declared assessment scope** was actually evaluated. `COMPLETE` requires at least one evaluated rule and all evidence required for that scope; `PARTIAL` has at least one evaluated rule but a missing/unusable requirement; `NONE` has no eligible evaluation. Optional visible fields are not automatically required. Required evidence is defined by the applicable rule/profile policy; K11 resolves minimum save input, not scientific rule requirements. |
| `conflicts` | Structured list with affected entities/parameters, incompatible requirements, evidence and confidence; separate `NONE/PARTIAL/COMPLETE` conflict-coverage state | An incompatibility can be present before any value is measured and can coexist with low/high results, a hazard, or missing data. An empty list means "no known conflict in assessed requirements," never "all requirements were checked." |

Each rule evaluation records its rule/revision, source measurement/provenance, typed metric and unit, applicable tank/species context, direction, severity and reason. A rule with unresolved unit, unsupported method, absent threshold, missing input or unverified applicability yields a typed `NOT_EVALUATED` reason and affects coverage; it cannot emit `NONE`, `WITHIN`, or a hard hazard by assumption. Non-comparability carries the precise cause in this reason, not a fabricated direction. The rule catalog and K05/K18 decisions determine which evidence is authoritative; K04 does **not** promote SOFT catalog ranges to `CRITICAL` or approve any numeric threshold.

**Deterministic aggregation:** among eligible, actually evaluated findings, take the maximum `CRITICAL > WARNING > ADVISORY > NONE` for `hazardSeverity`. `NONE` is the aggregate only if at least one eligible rule was evaluated without an adverse finding; when none was evaluated, `hazardSeverity = null`, `coverage = NONE`, and no green/healthy claim is allowed. Missing evidence never downgrades an observed hazard. Preserve *all* findings, directions, conflicting entities and missing/unusable reasons even if one headline is chosen. Stable rule ID, parameter ID and entity ID order breaks ties for explanation/recommendation ordering; list/input order does not change results.

**Presentation priority:** the primary headline presents the highest supported hazard first. With no supported hazard, a known incompatibility is shown before a partial/no-data headline. `coverage = PARTIAL` or `NONE` is always disclosed alongside any hazard or conflict. Only a `COMPLETE` assessment for its explicitly named scope, with no supported adverse finding or known conflict, may show that scope as normal; it cannot claim universal tank safety. Unknown conflict coverage prevents a blanket compatibility claim. Thus `CRITICAL + PARTIAL + conflict` keeps the critical headline **and** visible missing-data/conflict indicators. A conflict alone does not become a chemistry `CRITICAL` without a separately evidenced rule. UI resource strings/colors are mapped from these dimensions and reason codes, not persisted as domain status.

| Evaluated outcome | Primary presentation | Always retained alongside it |
| --- | --- | --- |
| `CRITICAL + PARTIAL + known conflict` | Critical finding | Partial coverage and conflict with affected entities |
| `WARNING + COMPLETE + known conflict` | Warning | Conflict and its evidence |
| `NONE + PARTIAL + no known conflict` | Partial assessment | Evaluated normal findings and specific gaps; no green overall claim |
| `null + NONE + known conflict` | Incompatible requirements | No assessable water-chemistry result |
| `NONE + COMPLETE + no known conflict` | Normal **for the explicitly evaluated scope** | Scope and evidence coverage; a compatibility claim additionally requires complete conflict coverage |

`Loading`, `NoAnalysis`, `Error`, `NotFound` and `SensorUnavailable` are screen/data-source states under K13. An engine or store failure is a typed failure, never an assessment with `hazardSeverity = NONE`. An unavailable sensor may contribute a missing-temperature reason to a valid assessment if the user saved other measurements; it is not silently replaced by a normal value.

---

## 15. Structured assessment output

The engine result must contain enough information to explain itself.

Conceptual output:

```text
WaterQualityAssessment
|
+-- hazardSeverity + coverage(scope, evaluated/required/missing/unusable)
|
+-- parameterAssessments[]
|   +-- parameter
|   +-- measuredValue
|   +-- ruleFindings[] (direction, severity, rule/revision, evidence)
|   +-- reasons[]
|   +-- affectedEntities[]
|   +-- expected / compatible ranges
|
+-- livestockAssessments[]
|
+-- plantAssessments[]
|
+-- conflicts[] + conflictCoverage
|
+-- missingData[] + unusableEvidence[]
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
- default-visible or scientifically important does not mean mandatory in every saved record; partial measurements must remain distinguishable from a complete assessment, and the fully empty-record policy remains an explicit K11 decision;
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

### 25.1 Accepted tank-type measurement scope (K03.0)

Decision accepted with the user on 26 September 2026: use **basic measurements + tank-specific fields + additional measurements**, driven by the tank type already selected when creating the aquarium. Keep the existing UI design. The user requested that the per-type mapping be written into this contract.

Source of truth: `AquariumTankSnapshot.tankType`, using `AquariumTankTaxonomy` constants. The creation UI already offers all nine types through `AquariumTankTaxonomyText`. Do not ask the user to choose a second tank type in the analysis form. This is a field-visibility policy, not a universal safe-range table.

**Common core in every row:** temperature (Sıcaklık), pH, total ammonia (Toplam amonyak), nitrite (Nitrit / NO2), and nitrate (Nitrat / NO3).

| Canonical tank code | Existing Turkish picker label | Analysis profile | Default fields in addition to the common core |
| --- | --- | --- | --- |
| `Fish` | Balık | Freshwater | General hardness (Genel sertlik / GH; K03.13), buffering capacity (Tampon kapasitesi / KH) |
| `Shrimp` | Karides | Freshwater shrimp | General hardness (GH; K03.13), buffering capacity (KH) |
| `Planted` | Bitkili | Planted freshwater | General hardness (GH; K03.13), buffering capacity (KH), phosphate (Fosfat / PO4) |
| `Marine` | Deniz | Marine | Salinity (Tuzluluk), alkalinity (Alkalinite / KH), phosphate (PO4) |
| `Softies` | Yumuşak Mercan | Marine / soft coral | Salinity, alkalinity, phosphate, calcium (Kalsiyum / Ca), magnesium (Magnezyum / Mg) |
| `Mixed Reef` | Karma Resif | Marine / mixed reef | Salinity, alkalinity, phosphate, calcium, magnesium |
| `SPS` | SPS | Marine / SPS | Salinity, alkalinity, phosphate, calcium, magnesium |
| `Coral` | Mercan | Marine / coral | Salinity, alkalinity, phosphate, calcium, magnesium |
| `Other` | Diğer | Unresolved water profile | No automatic freshwater/marine additions; common-core measurements remain recordable with explicit missing profile context |

Product interpretation and limits:

- `Fish`, `Shrimp`, and `Planted` follow the existing canonical freshwater grouping; the five marine/coral codes follow the existing marine grouping. This does not claim all fish or shrimp species live in freshwater. Conflicting registered livestock `waterGroup` must be reported, not used to silently relabel the tank.
- Nitrite remains available in marine profiles for cycle/setup monitoring; this does not imply identical freshwater/marine toxicity rules or testing frequency.
- The same default fields for the four coral profiles do not imply identical target values, consumption, or dosing. These depend on the evidence-backed rules and inhabitants.
- Under accepted K03.6, marine/coral profiles use `TOTAL_ALKALINITY` as the single alkalinity metric; do not expose duplicate `KH` and `Alkalinite` fields for the same test result. The canonical unit is meq/L, while verified source/display representations may be dKH or mg/L as CaCO3. `GENERAL_HARDNESS` and true `CARBONATE_HARDNESS` remain separate semantics.
- Under accepted K03.5, salinity and specific gravity are distinct typed metrics. PSS-78, SG, conductivity, mass/absolute salinity, and vendor `ppt` displays are not silently interchangeable. The selected source profile determines the representation; only evidence-backed conversions with required temperature/reference metadata may normalize across representations.
- `Other`, blank, and unsupported legacy codes do not inherit freshwater or marine thresholds. Additional measurements with known semantics can be recorded without fabricating a profile-dependent assessment. How a user explicitly supplies missing profile context is a later interaction decision.
- Field selection must be an application-layer policy under the accepted `application/aquarium/health/water/` ownership, using the existing parameter vocabulary. Presentation renders this policy; Fragments/XML must not own a second classification table or chemistry rules.

### 25.2 Additional measurement availability

These measurements are supported scope beyond the default fields above. "Additional" describes form visibility, not lesser biological importance. No exact unit, threshold, test-kit catalog, or new disclosure control is approved by this table.

| Measurement / Turkish name | Availability / context | Important boundary |
| --- | --- | --- |
| Phosphate / Fosfat (PO4) | Additional for `Fish` and `Shrimp`; already default for `Planted` and marine/coral types; recordable for `Other` | Supports nutrient, plant, and algae investigation; no diagnosis from this value alone |
| Dissolved oxygen / Çözünmüş oksijen | Additional for all nine types | K03.7: canonical concentration is `DISSOLVED_OXYGEN_CONCENTRATION` in mg/L O2; `% saturation` is a separate context-dependent metric/representation. Temperature, aeration, or saturation alone must not fabricate a concentration without the required same-event conversion context |
| Free chlorine + total chlorine / Serbest klor + Toplam klor | Additional for all types, particularly when municipal source water is used | K03.8: free and total chlorine are separate mg/L as Cl2 metrics; combined chlorine may be derived only from compatible same-sample free+total results and is not synonymous with monochloramine. Sample context distinguishes raw source water, conditioned source water, and tank water |
| Conductivity / İletkenlik and TDS | Additional for freshwater types; `Other` may record explicitly identified results | K03.10: conductivity canonical µS/cm with temperature-basis provenance; TDS UI is source-reported ppm entered as shown by the meter. Unknown TDS factor does not block storing the ppm value, but EC↔TDS conversion and hard cross-scale assessment require a verified source/profile relationship |
| Carbon dioxide / CO2 | Additional for freshwater types, especially planted tanks with CO2 use | K03.11: direct verified CO2 is mg/L as CO2; when direct CO2 is not measured, AquaLight may calculate and label `Hesaplanan CO2` from compatible same-event pH + KH inputs using a versioned method. Drop-checker color is not converted to ppm |
| Iron / Demir (Fe) and potassium / Potasyum (K) | Additional for freshwater nutrient/plant investigation | K03.12: potassium canonical `mg/L as K`; iron canonical `mg/L as Fe` plus verified analytical scope (total/dissolved/ferrous/method-defined). UI remains simple `Demir (Fe)` / `Potasyum (K)`. A bare Fe/K result never authorizes automatic fertilizer dosing |
| Calcium / Kalsiyum (Ca) and magnesium / Magnezyum (Mg) | Additional for `Marine`; already default for coral profiles; recordable for `Other` with explicit semantics | K03.9: canonical reef metrics are elemental `CALCIUM_CONCENTRATION` in mg/L as Ca2+ and `MAGNESIUM_CONCENTRATION` in mg/L as Mg2+. Hardness-as-CaCO3/GH results are separate semantics and require an explicit verified conversion path |

For `Other`/unknown profiles, the supported fields listed above and the profile-specific fields from section 25.1 may be explicitly selected as additional measurements, once their semantics are defined. No such selection acts as an implicit freshwater/marine profile declaration. Support and interpretation must stay separate. Missing context restricts assessment rather than changing the entered result.

### 25.3 Presentation, record continuity, and remaining decisions

- Preserve the existing screen structure, sensor/temperature area, water-parameter grid and input components, card styling, and save/history navigation. Applicable fields reuse those components; row count may change with the tank type. Do not introduce new tabs, sections, or a different visual layout from the logical grouping alone.
- Use readable localized names; chemical formulas are secondary identifiers. The accepted freshwater names are Sıcaklık, pH, Toplam amonyak, Nitrit (NO2), Nitrat (NO3), Genel sertlik (GH), Tampon kapasitesi (KH), and Fosfat (PO4). Labels must remain readable at supported font scales.
- Decide the precise way users reveal/select additional measurements together before UI implementation. The accepted availability table is not approval to add a new accordion, picker, or help layout now.
- Retain the proposal for short **"Nedir / Nasıl ölçülür?"** help for every measurement field, including additional fields. Explain the parameter, the accepted test result, and its unit without requiring the user to know chemical notation. Product-specific instructions must match a verified test profile. The placement/opening interaction remains a K13/UI decision and must preserve the existing design; documenting this proposal does not authorize a new help icon or layout now.
- A visible field is not a promise of a complete assessment. Unmeasured, measured zero, inapplicable, unknown test basis, and missing assessment rules must remain distinct. A critical known result must not be hidden by a partial-data state.
- Preserve entered drafts and persisted measurements when tank type changes; no field hidden by the new profile may silently discard its value or be persisted invisibly. Resolve affected draft values explicitly before saving. Detailed interaction belongs to the later state/UI decision.
- Store the assessment's tank-type/profile context with its provenance. History/detail render the saved measurement set and assessment context; today's tank type must not erase or reinterpret yesterday's fields. The latest result must expose a context mismatch if the tank type has since changed.
- K03.1 fixes nitrate/nitrite/phosphate meanings, K03.2 source-resolution/normalization, K03.3 TAN/direct-NH3 bases, K03.4 concurrent multi-result behavior, K03.5 marine salinity/SG safety, K03.6 alkalinity/KH semantics, K03.7 dissolved oxygen, K03.8 chlorine/chloramine/sample-context, K03.9 marine elemental Ca/Mg, K03.10 conductivity/TDS, K03.11 direct-vs-calculated CO2, K03.12 iron/potassium, K03.13 general-hardness semantics, and K03.14 freshwater calculated free NH3 with marine first-release exclusion. Remaining K03 work is evidence-backed profile/conversion/precision data. K03.0–K03.14 do not accept numerical safety thresholds, complete implementation, or all of K03/K11/K13.

Evidence and repository references for this scope are recorded in [the K03 research note](research/WATER_ANALYSIS_CONCENTRATION_UNITS_K03.md). The per-type visibility matrix is a product decision informed by those sources, not a scientific claim that all listed tests must be performed at every save.

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

1. collect raw measurement input plus the parameter-scoped source/test selection;
2. resolve source semantics from a verified profile or guided typed measurement/basis/unit selection;
3. normalize only supported, resolved inputs to canonical values; keep unresolved fields in draft state and exclude them from commit;
4. obtain authoritative tank context;
5. obtain fresh temperature if sensor mode is selected;
6. validate;
7. run assessment on resolved canonical measurements;
8. atomically persist resolved raw measurement + canonical value + assessment + provenance;
9. return/navigate according to the existing approved flow.

The Fragment must not build domain ranges or read catalogs itself.

Form behavior to preserve from the agreed data-quality direction:

- A new form starts with empty manual measurement fields. Do not preload example numbers or silently copy previous analysis values. Restoring an existing user draft is a different state and must preserve the user's input.
- Auto-populate temperature only from a valid, fresh, assigned sensor sample appropriate for the measurement event. If unavailable, retain explicit unavailable/manual-source behavior; do not invent a reading.
- Let users record the measurements they actually took rather than requiring every displayed test. Exact minimum-input and fully empty-record behavior remain K11 decisions. A missing prerequisite for one calculation does not turn an otherwise valid partial measurement into a fabricated complete analysis.
- Source/test selection is parameter-specific and remembered. A known profile auto-selects the valid analyte/unit/result-mode options; an unknown product opens the guided typed fallback. The unit/reporting context remains visible beside the result input while conversion happens below the UI boundary.
- If a selected product exposes multiple **concurrent** independent results, render separate typed fields for each supported result and allow them to coexist in the same analysis event. Request a mode selector only for genuinely mutually exclusive source modes. For marine salinity sources, render the verified source representation (`PSS-78`/device salinity scale, `SG`, conductivity, or explicitly defined mass salinity) rather than silently relabelling or converting it.
- If a field's source semantics cannot be resolved, keep that field out of the committed analysis and retain it only as draft/form state; other resolved measurements may still be saved. Changing source semantics after entering a number must not silently reinterpret the number.
- An empty field means **"Ölçülmedi"**; a measured `0` remains a real result. Explain limited coverage as **"Kısmi değerlendirme"** alongside any known critical finding under K04. Missing results must not increase a health score or be described as normal. The separate numerical score policy remains an open W0.13/K12 decision.

### 28.2 Tank Health main screen

`TankHealthContentAdapter` currently renders hard-coded metric values/statuses.

Replace static metrics with presentation models derived from the latest persisted analysis.

Use the accepted profile/measurement scope in section 25 instead of a fixed eight-card assumption. Keep the existing metric-card design, and preserve the saved analysis context when presenting historical measurements.

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

K04/section 14 keeps conflict identity and conflict-coverage independently of hazard severity. A known conflict is highlighted when there is no higher supported hazard, and remains visible alongside a higher hazard. It cannot be silently averaged away or promoted to a chemistry `CRITICAL` without an evidenced rule. K18 still decides the detailed eligibility and intersection policy for plant/livestock combinations.

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

Represent these explicitly through `missingData`, unusable-evidence reasons, and the scoped K04 `coverage` dimension. `INSUFFICIENT_DATA` is a presentation/reason concept, not a replacement for the independently observed hazard and conflict dimensions. Absence of a purely optional visible test does not by itself make a scoped assessment incomplete; its applicability and required evidence come from the rule/profile policy.

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

Combination and ordering tests cover:

- `CRITICAL + PARTIAL + conflict` retains all three dimensions and a critical headline;
- `WARNING + missing required evidence` retains warning with partial coverage;
- all measurements/rules absent yields `hazardSeverity = null`, `coverage = NONE`, never normal;
- all evidence required for the declared scope evaluated with no adverse finding may present scope-limited normal; if that claim includes livestock/plant compatibility, `conflictCoverage = COMPLETE` with no conflict is also required;
- conflicting requirements with no measurement remain visible, with hazard unassessed and the conflict-coverage state retained;
- unsupported unit, missing rule and SOFT-only evidence never create a hard hazard or `WITHIN` result;
- permutation of rule/entity input order leaves aggregation and ordered reasons stable.

### 43.2 Species intersection tests

Cover:

- overlapping ranges;
- exact-boundary overlap;
- one open-ended range;
- no overlap;
- three or more species;
- custom livestock;
- missing catalog entry.
- `<20` rejects exactly 20 while `≤20` accepts it; corresponding strict/inclusive lower-bound cases and nearby values;
- inclusive two-sided endpoints remain valid; exclusive touching endpoints do not create a shared interval;
- `~20` and an unqualified single value remain nominal context without fabricated tolerance/pass-fail;
- malformed or reversed strings and unknown warning-mode values retain typed unavailable evidence;
- zero checked comparable parameters never yield compatibility; a SOFT out-of-interval finding is advisory only.

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
- parameter-scoped remembered test/device selection;
- verified profile analyte/reporting-basis/unit resolution;
- guided unknown-product measurement/basis/unit selection;
- concurrent multi-result source renders separate inputs and commits both independent metrics;
- mutually exclusive source modes use explicit selection with no inference;
- TAN + direct free NH3 same-event round-trip without overwrite or accidental derivation;
- source change after value entry without silent reinterpretation;
- unresolved source semantics staying draft-only while other resolved fields can commit;
- raw/source-profile/revision/normalized provenance round-trip;
- TAN `mg/L as N` canonical mapping;
- direct free-NH3 `mg/L as NH3` canonical mapping;
- no substitution between TAN, direct free NH3, NH4-only, and calculated free NH3;
- calculated freshwater free NH3 uses same-sample verified TAN-as-N/pH/temperature, the K03.14 fraction and NH3/N mass conversion; EPA/UF reference vectors, supported-envelope edges, direct-result priority, unavailable cases, marine exclusion, and historical provenance are tested;
- PSS-78 practical salinity remains distinct from SG, conductivity, absolute/mass salinity, and ambiguous vendor `ppt`;
- total alkalinity canonicalizes to meq/L only from a source verified as the same alkalinity semantic; dKH / mg/L as CaCO3 conversion round-trips preserve raw source representation;
- `TOTAL_ALKALINITY`, `CARBONATE_HARDNESS`, and `GENERAL_HARDNESS` never alias by label/unit alone;
- marine UI does not double-enter the same alkalinity observation as both KH and alkalinity;
- unknown `KH` source semantics produce unavailable/insufficient-data rather than a guessed metric;
- dissolved oxygen mg/L and % saturation remain distinct typed values; conversion requires same-event temperature, pressure reference, and salinity/specific-conductance inputs required by the method;
- one DO probe observation reporting mg/L + % saturation is not double-counted as two independent measurements;
- historical DO conversion never substitutes today's environmental context;
- missing DO conversion prerequisites produce conversion-unavailable/`INSUFFICIENT_DATA`;
- free chlorine, total chlorine, derived combined chlorine, and direct monochloramine remain distinct semantics;
- combined chlorine derives only from compatible same-sample free+total measurements and is never labelled monochloramine;
- raw-source / conditioned-source / tank-water chlorine contexts do not cross-subtract or overwrite one another;
- direct monochloramine requires a method/profile that measures monochloramine specifically;
- unsupported chlorine sample matrix/interference or unknown basis fails closed;
- marine calcium canonicalizes only to mg/L as Ca2+ and marine magnesium only to mg/L as Mg2+ from verified elemental source semantics;
- calcium/magnesium hardness as CaCO3 and GH remain distinct from elemental Ca/Mg and never enter reef rules without an approved conversion path;
- difference-derived magnesium preserves derivation provenance and is not silently treated as a direct elemental measurement;
- TDS reported ppm is stored exactly as reported; no user-facing factor prompt is required to save it;
- conductivity and TDS remain distinct, and EC↔TDS conversion occurs only with a verified source factor/scale and temperature basis;
- same-meter EC+TDS outputs are linked and not double-counted; unknown-scale catalog TDS ranges do not produce hard assessment;
- direct CO2 mg/L remains distinct from `CALCULATED_DISSOLVED_CO2_CONCENTRATION`;
- pH-only input never creates CO2; pH+KH calculation requires compatible same-event typed inputs and a versioned method;
- direct CO2 and calculated CO2 same-event round-trip preserves both without overwrite, with direct result primary;
- drop-checker color never maps to a fixed ppm CO2 value;
- potassium canonicalizes to mg/L as K only from a resolved elemental-K source basis;
- iron canonicalizes to mg/L as Fe with analytical scope preserved; total/dissolved/ferrous/method-defined scopes never silently alias;
- known Fe test profiles resolve scope without extra user-facing laboratory choices; unknown scope fails closed;
- K2O/compound-basis potassium does not silently enter elemental-K rules;
- bare Fe/K measurements never generate fertilizer dosing quantities;
- `GENERAL_HARDNESS` canonicalizes to mg/L as CaCO3; verified dGH conversion round-trips preserve the raw source value;
- GH never aliases KH/alkalinity or elemental Ca/Mg, and elemental Ca+Mg are not naively summed into GH;
- unknown hardness/ppm semantics fail closed instead of being guessed into GH;
- SG conversion requires declared reference/calibration temperature and all algorithm prerequisites;
- conductivity→PSS-78 uses a versioned standards-based algorithm with golden-vector tests and applicability checks;
- unknown/ambiguous `ppt` never normalizes by label alone;
- two representations derived by one device observation are not double-counted as independent evidence;
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

1. controlled extension of the existing AquariumWaterParameter enum and AquariumWaterSnapshot measurement model (accepted K02; exact chemical semantics follow K03);
2. canonical units;
3. K03.2 source/test profile selection, guided source resolution, and normalization policy (accepted; exact evidence-backed profile/conversion entries remain implementation data);
4. K03.3 ammonia canonical semantics: TAN as mg/L as N; direct free NH3 as mg/L as NH3 (accepted);
5. K03.4 concurrent multi-result measurement policy: separate canonical metrics/inputs for simultaneous outputs; selector only for mutually exclusive modes (accepted);
6. K03.5 marine salinity/SG/conductivity typed semantics and conversion-safety policy (accepted);
7. K03.6 total-alkalinity/KH semantic separation and canonical meq/L policy (accepted);
8. K03.7 dissolved-oxygen mg/L / % saturation semantic separation and same-event conversion policy (accepted);
9. K03.8 free/total/combined chlorine and direct-monochloramine semantics plus sample-context policy (accepted);
10. K03.9 marine calcium/magnesium elemental mg/L semantics and hardness separation (accepted);
11. K03.10 conductivity canonical µS/cm + source-reported TDS ppm and safe conversion policy (accepted);
12. K03.11 direct measured CO2 vs same-event pH+KH calculated CO2 policy (accepted);
13. K03.12 iron mg/L-as-Fe analytical-scope + potassium mg/L-as-K and no-auto-dosing policy (accepted);
14. K03.13 general-hardness canonical mg/L-as-CaCO3 + dGH display/conversion policy, and K03.14 freshwater calculated free-NH3 equation/same-sample gate with marine first-release exclusion (accepted);
15. severity/status model;
16. species-range intersection/conflict policy;
17. intrinsic chemistry rule catalog and evidence revision.

### Phase 2 - Application/domain foundation

18. WaterAnalysis input/record models;
19. structured assessment models;
20. recommendation/reason codes;
21. PlantCareCatalogOperations boundary;
22. AquariumHealthContext model/provider;
23. reuse/integrate LivestockWaterAdvisorOperations;
24. WaterQualityAssessmentEngine;
25. TankWaterTemperatureOperations boundary.

### Phase 3 - Persistence

26. dedicated water-analysis proto/store;
27. schema version;
28. strict store rules;
29. owner/tank-scoped queries;
30. create/delete/latest operations;
31. persisted assessment/provenance snapshot.

### Phase 4 - Integrity

32. integrate Water Analysis into tank deletion transaction;
33. process-death recovery;
34. account deletion cleanup;
35. backup/restore policy and implementation if included;
36. data inventory / export updates where applicable.

### Phase 5 - UI integration

37. WaterAnalysisViewModel;
38. save real analysis;
39. replace mock history;
40. pass analysisId through Safe Args;
41. bind real record detail;
42. implement real delete;
43. bind latest analysis to Tank Health Water Quality metrics;
44. connect fresh cooling sensor temperature.

### Phase 6 - Validation

45. engine tests;
46. persistence tests;
47. owner/process-death tests;
48. ViewModel/UI contract tests;
49. architecture/lint/detekt/CodeQL;
50. all CI green.

Only after this sequence:

51. Algae Control;
52. Plant Health;
53. Livestock Health.

---

## 46. Acceptance criteria for Water Quality completion

Water Quality is complete only when all of the following are true:

- approved UI remains visually intact unless explicitly changed;
- all nine canonical tank types follow the accepted measurement matrix without locale-dependent matching; unknown types never silently become freshwater;
- additional measurements preserve the existing design and have explicit supported semantics before being enabled;
- each parameter can retain its own remembered test/device selection; known profiles drive analyte/unit/result-mode semantics, unknown products use guided typed fallback, and multi-mode results are never inferred;
- total ammonia normalizes to TAN in mg/L as N, while directly measured free ammonia normalizes separately to mg/L as NH3; neither is silently substituted for the other;
- when a source can measure TAN and direct free NH3 in the same event, both can be entered and persisted as separate measured metrics with separate provenance; concurrent outputs are not collapsed into a selector;
- freshwater calculated free NH3 follows K03.14 only for one verified sample and within the supported pH/temperature envelope, is labelled as estimated and keeps calculation provenance; direct NH3 is primary, marine/reef v1 never runs the freshwater equation, and no missing calculation becomes a safe/normal value;
- marine salinity assessment never assumes SG, PSS-78, conductivity, absolute/mass salinity, or generic `ppt` are equivalent; cross-representation comparison requires an explicit verified conversion with all required reference conditions, otherwise the result remains source-native/insufficient for that rule;
- marine/coral alkalinity uses one `TOTAL_ALKALINITY` metric with canonical meq/L and verified dKH / mg/L as CaCO3 representations; no duplicate KH+alkalinity field/evidence is created, and freshwater `CARBONATE_HARDNESS`/`GENERAL_HARDNESS` remain semantically distinct;
- dissolved oxygen concentration uses canonical mg/L O2 while `% saturation` remains a separate context-dependent metric/representation; conversions require same-event method inputs and linked device outputs are not double-counted;
- free chlorine and total chlorine use separate mg/L as Cl2 metrics; combined chlorine is only a compatible same-sample derived value and never a synonym for monochloramine; direct monochloramine requires a specific verified method and chlorine assessments retain raw/conditioned/tank sample context;
- marine/coral calcium and magnesium use elemental mg/L as Ca2+ and mg/L as Mg2+ canonical metrics; hardness-as-CaCO3/GH representations never silently substitute for them;
- TDS accepts the meter's ppm value as reported without asking the user for a conversion factor; conductivity remains separate, and any EC↔TDS translation requires a verified source profile/temperature basis and is never double-counted;
- CO2 may be either directly measured in mg/L as CO2 or explicitly calculated from compatible same-event pH+KH; calculated CO2 is labelled/provenanced separately, pH alone and drop-checker color never fabricate ppm, and a direct result is never overwritten by the calculation;
- potassium uses elemental mg/L as K, while iron uses mg/L as Fe with its verified analytical scope preserved; the entry UI remains simple and no bare Fe/K measurement produces an automatic fertilizer dose;
- freshwater GH uses `GENERAL_HARDNESS` canonical mg/L as CaCO3 with familiar verified dGH source/display support; GH is never substituted for KH/alkalinity or elemental Ca/Mg;
- K04 hazard, per-rule direction, declared-scope coverage and conflicts persist as independent fields; `CRITICAL + PARTIAL + conflict` is rendered without loss, no evaluated rule never appears green, and any normal label names its verified scope rather than implying universal tank safety;
- unresolved source semantics are excluded from committed analysis and may remain in draft while other resolved measurements are saved; changing a source never silently reinterprets an entered number;
- tank-type changes do not silently lose draft values or remove historical measurements;
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
- livestock range parsing preserves strict versus inclusive endpoints; approximate-only values do not become point intervals, and SOFT catalog guidance cannot by itself produce a critical toxicity verdict;
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
