# Water Analysis context and frozen catalog assessment

Implementation date: 27 September 2026. This is an implementation evidence record,
not Water Quality release acceptance. The live history store is still Proto;
Room cutover, explicit archive history and certified sensor integration remain open.

## Implemented boundary

- `PlantCareCatalogOperations` supplies one immutable identity/care snapshot. The
  only plant asset/parser/cache is in data. Picker and context use the same
  composition-owned operations. Loading is off the caller thread, concurrent
  requests share the snapshot, and failures are typed and retryable. The content
  revision is `plant-care-2026-09-27.1`, independent of the lighting catalog.
- Livestock keeps its existing catalog and evaluator. Whole-expression parsing
  retains raw text, strict/inclusive endpoints, nominal/approximate values,
  confidence, warning mode and `livestock-care-2026-09-27.1`. Unknown warning modes
  are informational. Zero comparisons cannot return compatible.
- The context reads the bound owner's tank once, retains local and catalog IDs,
  quantities, dimensions, setup date, selected materials and catalog resolution
  states. Volume is explicitly geometric, never net dosing volume. Capture time
  and content revision are separate. Backdated records use `CURRENT_AT_ENTRY`;
  the detail screen discloses the actual context capture time.
- The pure catalog engine accepts immutable input/context and reuses the existing
  comparator. It has no Android, clock, network, store, task, hardware or dosing
  dependency. Comparable catalog semantics currently cover temperature, pH and
  GH. Unknown nitrate/phosphate ppm basis, TDS scale, KH scope and other unresolved
  source semantics cannot become comparable because a column has a familiar name.
- Verified plant fields participate only for ready profiles. Custom livestock,
  missing catalog identity, partial profiles, unavailable catalogs and unknown or
  incompatible habitats remain separate reasons. Quantity does not multiply a
  species' requirement. Pairwise interval witnesses retain conflicting entities,
  including when no measurement exists. SOFT catalog guidance never exceeds
  ADVISORY; absent chemistry rules prevent COMPLETE overall coverage or a safety
  verdict. No score or numerical hazard threshold is enabled.

## Frozen event and UI

The existing session/tank write gate now captures context and computes the result
before one durable Proto update. Raw readings, canonical value presence and
conversion revision, context document/checksum, catalog/engine/rule revisions,
entity names, findings, conflicts and recommendations are stored together.
An acknowledged request retry returns its existing event before trying to capture
current context. Opening history decodes its saved output; it does not run today's
engine or re-resolve today's catalog. Legacy records remain unassessed.

Read failures remain typed through operations and UI. Loading, unavailable,
empty and missing detail are distinct states. The dashboard and entry card show
the saved partial assessment; detail exposes frozen entity names, bounds,
missing-data reasons and conflicts. All saved extra measurements remain visible
without an eight-card cap. The existing card dimensions, grids and sheets remain.

Each fragment now owns its Water Analysis ViewModel. Safe Args enter its immutable
SavedStateHandle route; request identity and draft data are retained there.
Observation draft time retains its timezone. Save/delete run in the ViewModel
scope with a single pending command, durable completion and one-time route
consumption. Cancellation remains cancellation. The selected record's actual
localized date/time appears in its delete confirmation.

New input checks enforce the accepted pH 0–14 product envelope, finite/nonnegative
concentrations, bounded input text, and the existing temperature input envelope.
Measured zero and missing input differ. Unsupported bounded text such as `<0.1`
is rejected. The invalid field is indicated and the draft is retained. These are
input envelopes, not biological safe ranges or product-specific precision rules.

## Named automated evidence

| Concern | Tests |
| --- | --- |
| Plant asset parity, immutable cache, concurrent off-thread reads, revision and failures | `PlantCareCatalogTest` |
| Whole grammar, reversed/junk expressions, strict/inclusive/approximate bounds | `LivestockWaterRequirementParserTest`, `LivestockCatalogItemTest` |
| One tank read, missing versus unavailable, owner generation cancellation, geometric volume, stable content revision | `AquariumHealthContextProviderTest` |
| Real plant readiness, local/catalog identity, custom/missing and quantity resolution | `HealthContextEntityResolverTest` |
| Coverage, zero comparisons, advisory maximum, quantity/order invariance, strict intersections, habitat mismatch, verified plant fields, unresolved ppm basis, exact GH endpoint | `WaterQualityAssessmentEngineTest` |
| Proto context/result round trip, canonical zero versus absence, frozen catalog result, corrupt/unknown data, inconsistent findings and legacy absence | `WaterEvaluationCodecTest` |
| Read errors versus empty content and cancellation | `WaterAnalysisLoadStateTest` |
| Duplicate commands, delayed acknowledgement, retry identity and cancellation | `WaterAnalysisMutationControllerTest` |
| Input envelope and field failure without draft loss | `WaterMeasurementInputPolicyTest`, `WaterAnalysisInputBuilderTest`, `WaterAnalysisValueParserTest` |
| Existing card/grid and complete saved-metric presentation | `WaterAnalysisUiContractTest`, `TankHealthMetricPresentationContractTest`, `TankHealthWaterMetricUiCatalogTest` |
| Engine purity and application/data/UI import boundaries | `water_analysis_architecture_guard.py` and its three unit tests |

`WaterAnalysisSessionInstrumentedTest` additionally exercises atomic evaluation
commit, capture failure leaving no raw-only record and retry bypassing changed
context. These Android tests require execution on device/emulator; source
compilation alone does not close that gate.

## Remaining acceptance

- Room is staging only. Live indexed/paged operations, cross-store deletion and
  rollback staging, owner-wide cleanup and archive/remap/dedup are not closed.
- Verified product profiles, retained source preferences, chlorine sample context,
  per-method precision and calibrated sensor provenance remain open.
- Calculated ammonia and evidence-backed chemistry rules are not implemented by
  this catalog assessment. No chemical safety verdict is shown.
- Full parameter migration to one canonical application representation remains
  open; the adapter only consumes the narrow verified subset of legacy ranges.
- Physical Cooling, API 27/API 36, process recreation, TalkBack/large-font and TR/EN
  dark/light acceptance need actual run evidence on the release commit.
- E/W9 remains open. A/P/L are not marked complete or presented as diagnoses.

## Local verification — 27 September 2026

The full debug JUnit suite passed: 1,906 tests, zero failures/errors/skips.
Debug Android test sources and releaseSmoke Kotlin compiled successfully.
The Python guard suite passed all 316 tests. Commit-time Detekt 1.23.8 passed
against the unchanged baseline: zero blockers, zero new debt, 776 existing
advisories. No suppression, baseline expansion or disabled guard was added.
Lint identified three quantity-wording issues and two obsolete resources; those
were corrected without suppressions. The follow-up lint run is recorded separately
when it completes. Device/instrumentation execution remains a distinct gate.

Follow-up: debug lint completed successfully with no new errors. The later Room
backend verification passed 1,911 debug tests and compiled the Android tests;
see `WATER_ANALYSIS_ROOM_TRANSACTIONS.md` for exact scope and outstanding gates.
