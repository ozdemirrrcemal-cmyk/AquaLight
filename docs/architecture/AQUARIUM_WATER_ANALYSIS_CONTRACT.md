# AquaLight Aquarium Water Analysis Contract

## Status

This document freezes the architectural, data, analysis, persistence, and UI-integration contract for the first production-grade Aquarium Health analysis flow.

The existing Tank Health / Water Quality UI is considered visually complete for this stage. Implementation work governed by this contract must connect that UI to authoritative data and analysis without redesigning the approved screens unless a later explicit UI change is requested.

Accepted clarification on 26 September 2026 (K03.0): the selected tank type determines which measurement fields are shown. Preserve the existing screen structure, cards, field styling, grid, and navigation while populating them with the applicable measurements in section 25.1. Basic, tank-specific, and additional measurements are logical groups, not approval for a new visual layout. K03.1 subsequently freezes nitrate, nitrite, and phosphate recording semantics in section 6.1. K03.2 freezes the test/device selection, source-semantic resolution, and normalization workflow in sections 6.2, 6.5, 7, and 28.1. K03.3 freezes the canonical ammonia reporting bases in section 6.3. K03.4 freezes concurrent multi-result measurement/cardinality behavior in section 6.6. K03.5 freezes marine salinity/specific-gravity semantics and conversion safety in section 6.7. K03.6 freezes alkalinity/KH semantics, canonical units, and duplicate-field prevention in section 6.8. K03.7 freezes dissolved-oxygen concentration/saturation semantics and conversion prerequisites in section 6.9; the remaining K03 decisions stay open.

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

The other original unit proposals remain subject to their own decisions:

- temperature: degrees Celsius;
- pH: unitless;
- GH: degrees dGH;
- KH: degrees dKH;
- ammonia: canonical ammonia semantic defined in section 6.3.

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
- a direct free-NH3 measurement is never substituted for TAN, and a TAN measurement is never presented as a direct free-NH3 measurement. Any future calculated free-NH3 value remains a separately identified derived result.

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
- **Calculated free ammonia remains a separate open decision:** if later accepted, derive it only from a compatible total-ammonia result and pH/temperature belonging to the same measurement event; marine calculations also need compatible salinity. Never substitute today's sensor temperature for an older sample. Allowed time differences, equation, applicability range, concentration basis, and precision must be researched and decided before enabling the calculation. Present any future derived output as **"Hesaplanan değer"** and preserve calculation provenance.

Scientific background is in research references R7–R8. K03.2 accepts the source-selection and resolution workflow; K03.3 freezes TAN as mg/L as N and direct free NH3 as mg/L as NH3. Neither decision freezes a complete commercial product catalog, a derived-ammonia calculation equation, or toxicity thresholds.

### 6.6 Accepted concurrent multi-result measurement policy (K03.4)

Decision accepted with the user on 26 September 2026: if a real test/device can produce multiple semantically distinct measurements from the same sampling event, AquaLight must preserve those measurements independently instead of collapsing them into a single field with a mode switch.

- **Same event, separate metrics:** one `WaterAnalysisRecord` may contain both `TOTAL_AMMONIA_NITROGEN` and `FREE_AMMONIA_NH3` when both were directly measured for the same `observedAt` event. They share the analysis event identity but remain separate measurement entries.
- **Separate UI inputs for concurrent outputs:** when the selected verified source profile supports both results from the same event, the Add Analysis screen renders separate inputs **"Toplam amonyak"** and **"Serbest amonyak (NH3)"**. Each input displays its own source unit/reporting context. The user may enter either available result or both according to what was actually measured; missing one does not fabricate or infer it from the other.
- **Mode selector only for true exclusivity:** a selector/toggle is reserved for products or device configurations where the source can output only one mutually exclusive semantic mode at a time. Concurrent outputs must not be represented as mutually exclusive choices.
- **Independent provenance:** each measured metric retains its own raw value, source unit/reporting basis, result/channel identity when applicable, normalized value, and normalization provenance. Shared product/profile identity may be referenced by both entries without merging their measurements.
- **No overwrite or derivation by implication:** entering direct free NH3 must not overwrite TAN; entering TAN must not clear or synthesize direct free NH3. A future calculated free-NH3 result, if accepted separately, uses a distinct derived provenance/type and never replaces a directly measured free-NH3 value.
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
| `Fish` | Balık | Freshwater | General hardness (Genel sertlik / GH), buffering capacity (Tampon kapasitesi / KH) |
| `Shrimp` | Karides | Freshwater shrimp | General hardness (GH), buffering capacity (KH) |
| `Planted` | Bitkili | Planted freshwater | General hardness (GH), buffering capacity (KH), phosphate (Fosfat / PO4) |
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
| Free chlorine + total chlorine / Serbest klor + Toplam klor | Additional for all types, particularly when municipal source water is used | Separate results; a negative free-chlorine test alone does not rule out chloramine. Record source-water versus tank-water context explicitly before assessment |
| Conductivity / İletkenlik and TDS | Additional for freshwater types; `Other` may record explicitly identified results | Useful tracking scope, not a replacement for GH/KH or an overall health score; conductivity and TDS are not the same measurement |
| Carbon dioxide / CO2 | Additional for freshwater types, especially planted tanks with CO2 use | Equipment presence is not a measured concentration; a pH/KH-derived estimate needs a separately accepted method and prerequisites |
| Iron / Demir (Fe) and potassium / Potasyum (K) | Additional for freshwater nutrient/plant investigation | Test method, analytical scope, interpretation, and exact units remain open; do not generate fertilizer dosing from a bare value |
| Calcium / Kalsiyum (Ca) and magnesium / Magnezyum (Mg) | Additional for `Marine`; already default for coral profiles; recordable for `Other` with explicit semantics | Their presence does not resolve an unknown water profile |

For `Other`/unknown profiles, the supported fields listed above and the profile-specific fields from section 25.1 may be explicitly selected as additional measurements, once their semantics are defined. No such selection acts as an implicit freshwater/marine profile declaration. Support and interpretation must stay separate. Missing context restricts assessment rather than changing the entered result.

### 25.3 Presentation, record continuity, and remaining decisions

- Preserve the existing screen structure, sensor/temperature area, water-parameter grid and input components, card styling, and save/history navigation. Applicable fields reuse those components; row count may change with the tank type. Do not introduce new tabs, sections, or a different visual layout from the logical grouping alone.
- Use readable localized names; chemical formulas are secondary identifiers. The accepted freshwater names are Sıcaklık, pH, Toplam amonyak, Nitrit (NO2), Nitrat (NO3), Genel sertlik (GH), Tampon kapasitesi (KH), and Fosfat (PO4). Labels must remain readable at supported font scales.
- Decide the precise way users reveal/select additional measurements together before UI implementation. The accepted availability table is not approval to add a new accordion, picker, or help layout now.
- Retain the proposal for short **"Nedir / Nasıl ölçülür?"** help for every measurement field, including additional fields. Explain the parameter, the accepted test result, and its unit without requiring the user to know chemical notation. Product-specific instructions must match a verified test profile. The placement/opening interaction remains a K13/UI decision and must preserve the existing design; documenting this proposal does not authorize a new help icon or layout now.
- A visible field is not a promise of a complete assessment. Unmeasured, measured zero, inapplicable, unknown test basis, and missing assessment rules must remain distinct. A critical known result must not be hidden by a partial-data state.
- Preserve entered drafts and persisted measurements when tank type changes; no field hidden by the new profile may silently discard its value or be persisted invisibly. Resolve affected draft values explicitly before saving. Detailed interaction belongs to the later state/UI decision.
- Store the assessment's tank-type/profile context with its provenance. History/detail render the saved measurement set and assessment context; today's tank type must not erase or reinterpret yesterday's fields. The latest result must expose a context mismatch if the tank type has since changed.
- K03.1 fixes nitrate/nitrite/phosphate canonical meanings and units, K03.2 source-resolution/normalization, K03.3 TAN/direct-NH3 bases, K03.4 concurrent multi-result behavior, K03.5 marine salinity/SG safety, K03.6 total-alkalinity/KH semantics, and K03.7 dissolved-oxygen concentration/saturation semantics and conversion prerequisites. Remaining K03 decisions include other fields' chemical reporting bases/units, evidence-backed profile/conversion entries and precision, and derived-measurement prerequisites. K03.0–K03.7 do not accept numerical safety thresholds, complete implementation, or all of K03/K11/K13.

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
- An empty field means **"Ölçülmedi"**; a measured `0` remains a real result. Explain limited coverage as **"Kısmi değerlendirme"** alongside any known critical finding. Missing results must not increase a health score or be described as normal. Final aggregation and score policy remain separate decisions.

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
- PSS-78 practical salinity remains distinct from SG, conductivity, absolute/mass salinity, and ambiguous vendor `ppt`;
- total alkalinity canonicalizes to meq/L only from a source verified as the same alkalinity semantic; dKH / mg/L as CaCO3 conversion round-trips preserve raw source representation;
- `TOTAL_ALKALINITY`, `CARBONATE_HARDNESS`, and `GENERAL_HARDNESS` never alias by label/unit alone;
- marine UI does not double-enter the same alkalinity observation as both KH and alkalinity;
- unknown `KH` source semantics produce unavailable/insufficient-data rather than a guessed metric;
- dissolved oxygen mg/L and % saturation remain distinct typed values; conversion requires same-event temperature, pressure reference, and salinity/specific-conductance inputs required by the method;
- one DO probe observation reporting mg/L + % saturation is not double-counted as two independent measurements;
- historical DO conversion never substitutes today's environmental context;
- missing DO conversion prerequisites produce conversion-unavailable/`INSUFFICIENT_DATA`;
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
9. severity/status model;
10. species-range intersection/conflict policy;
11. intrinsic chemistry rule catalog and evidence revision.

### Phase 2 - Application/domain foundation

12. WaterAnalysis input/record models;
13. structured assessment models;
14. recommendation/reason codes;
15. PlantCareCatalogOperations boundary;
16. AquariumHealthContext model/provider;
17. reuse/integrate LivestockWaterAdvisorOperations;
18. WaterQualityAssessmentEngine;
19. TankWaterTemperatureOperations boundary.

### Phase 3 - Persistence

20. dedicated water-analysis proto/store;
21. schema version;
22. strict store rules;
23. owner/tank-scoped queries;
24. create/delete/latest operations;
25. persisted assessment/provenance snapshot.

### Phase 4 - Integrity

26. integrate Water Analysis into tank deletion transaction;
27. process-death recovery;
28. account deletion cleanup;
29. backup/restore policy and implementation if included;
30. data inventory / export updates where applicable.

### Phase 5 - UI integration

31. WaterAnalysisViewModel;
32. save real analysis;
33. replace mock history;
34. pass analysisId through Safe Args;
35. bind real record detail;
36. implement real delete;
37. bind latest analysis to Tank Health Water Quality metrics;
38. connect fresh cooling sensor temperature.

### Phase 6 - Validation

39. engine tests;
40. persistence tests;
41. owner/process-death tests;
42. ViewModel/UI contract tests;
43. architecture/lint/detekt/CodeQL;
44. all CI green.

Only after this sequence:

45. Algae Control;
46. Plant Health;
47. Livestock Health.

---

## 46. Acceptance criteria for Water Quality completion

Water Quality is complete only when all of the following are true:

- approved UI remains visually intact unless explicitly changed;
- all nine canonical tank types follow the accepted measurement matrix without locale-dependent matching; unknown types never silently become freshwater;
- additional measurements preserve the existing design and have explicit supported semantics before being enabled;
- each parameter can retain its own remembered test/device selection; known profiles drive analyte/unit/result-mode semantics, unknown products use guided typed fallback, and multi-mode results are never inferred;
- total ammonia normalizes to TAN in mg/L as N, while directly measured free ammonia normalizes separately to mg/L as NH3; neither is silently substituted for the other;
- when a source can measure TAN and direct free NH3 in the same event, both can be entered and persisted as separate measured metrics with separate provenance; concurrent outputs are not collapsed into a selector;
- marine salinity assessment never assumes SG, PSS-78, conductivity, absolute/mass salinity, or generic `ppt` are equivalent; cross-representation comparison requires an explicit verified conversion with all required reference conditions, otherwise the result remains source-native/insufficient for that rule;
- marine/coral alkalinity uses one `TOTAL_ALKALINITY` metric with canonical meq/L and verified dKH / mg/L as CaCO3 representations; no duplicate KH+alkalinity field/evidence is created, and freshwater `CARBONATE_HARDNESS`/`GENERAL_HARDNESS` remain semantically distinct;
- dissolved oxygen concentration uses canonical mg/L O2 while `% saturation` remains a separate context-dependent metric/representation; conversions require same-event method inputs and linked device outputs are not double-counted;
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
