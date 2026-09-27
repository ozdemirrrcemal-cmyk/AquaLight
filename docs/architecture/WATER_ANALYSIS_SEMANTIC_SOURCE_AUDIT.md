# Water Analysis source semantics and method-profile audit

Reviewed 27 September 2026 against the production contract §0.5 and §§6.1–6.16.
This records the permitted numeric paths in the current application code. A
numeric conversion is not a water-quality assessment or a verified product.

## S.3 — Current parameter and conversion boundary

`WaterMeasurementSemanticMatrixTest` checks every `WaterParameter` ×
`WaterMeasurementBasis` × `WaterMeasurementUnit` combination against an explicit
reviewed allowlist. It also checks all non-manual source kinds, invalid numbers,
measured zero and positive conversion underflow. No other basis/unit combination
can emit a canonical value. `SOURCE_TYPED` now requires an actual canonical
value; raw-only kit and device records are `SOURCE_NATIVE_UNASSESSED`.

| Current parameter | Permitted explicitly typed manual numeric path | Other current representations |
| --- | --- | --- |
| PH | pH, unitless identity | No inferred source or assessment authority |
| NITRATE | mg/L as NO3 identity | Generic NO3-N remains source-native |
| NITRITE | mg/L as NO2 identity | No unidentified nitrogen basis |
| AMMONIA_AMMONIUM | None | Legacy NH3/NH4 and bare TAN remain `LEGACY_UNASSESSED`; new legacy writes rejected |
| TOTAL_AMMONIA_NITROGEN | mg/L as N identity | Never substitutes for direct NH3 |
| FREE_AMMONIA_NH3 | mg/L as NH3 identity | Never substitutes for TAN or a calculated value |
| GH | mg/L as CaCO3 identity; dGH × 17.86 | Never elemental Ca/Mg or KH |
| KH | None | Historical KH lacks verified carbonate/alkalinity scope |
| TOTAL_ALKALINITY | meq/L identity; dKH × 0.358; mg/L as CaCO3 ÷ 50 | Bare ppm and KH reporting basis rejected |
| PHOSPHATE | mg/L as PO4 identity | Generic phosphorus and total phosphorus are not reinterpreted |
| TDS | None | Device ppm requires its scale/method |
| EC | None | Temperature/reference and device compensation unresolved |
| CO2 | None | No pH/KH derivation or color-to-ppm inference |
| IRON | None | Total/dissolved/ferrous analytical scope unresolved in legacy input |
| POTASSIUM | mg/L as K identity | No K2O or unidentified ppm conversion |
| SALINITY | None | Generic ppt does not establish PSS-78 or mass fraction |
| SPECIFIC_GRAVITY | None | No SG/salinity equivalence or guessed reference temperature |
| CALCIUM | mg/L as elemental Ca identity | No hardness or generic ppm equivalence |
| MAGNESIUM | mg/L as elemental Mg identity | No hardness or generic ppm equivalence |
| COPPER | mg/L as Cu identity | No method-dependent hazard claim |
| DISSOLVED_OXYGEN | mg/L as O2 identity | No saturation conversion without same-sample context |

All `TEST_KIT`, `DIGITAL` and historical `SENSOR` source values remain raw-only
until their exact profile/calibration is resolved. The current Salifert Nitrate
name and generic Other do not bypass this boundary. The only non-identity
numeric factors above are the already accepted GH/total-alkalinity factors in
the production contract. Overflow and positive-to-zero underflow return no
canonical value. Rounding is not performed by this normalization boundary.

This completes the current-option audit in S.3; adding a new option requires
updating this table and the exhaustive allowlist test. It does not close the
profile evidence, historical migration, per-method input or assessment gates.

## S.5 — Versioned method-profile schema

Application-owned types now represent:

- `WaterMethodProfileKey`: stable product ID and positive immutable revision.
- `WaterMethodProduct`: exact brand/model/variant; a brand-only entry is invalid.
- `WaterMethodSourceSemantic`: parameter, reporting basis, source unit and
  explicit analytical scope. Ambiguous legacy ammonia cannot define a method.
- `WaterMethodMode`: matrix-specific mode with independent concurrent output
  channels. Only multiple modes require an exclusive mode choice; TAN and
  direct NH3 can be concurrent outputs of one mode.
- `WaterMethodResultScale`: exact decimal range, decimal/increment/comparator
  precision, detection/quantification limits and supported inequality notation.
- `WaterMethodEvidence`: primary-source kind, HTTPS URL, document revision,
  section and retrieval/publication dates. Declaring metadata does not verify
  the source contents; product acceptance still requires primary-source review.
- `WaterMethodCatalogSnapshot`: unique exact-revision lookup, one selectable
  revision per product, immutable deep copies, matrix-aware selection, retained
  retired profiles and successor validation that rejects rewritten or missing
  historical revisions. A product ID cannot be reused for another model/variant.

`WaterMethodProfileTest` and `WaterMethodCatalogSnapshotTest` cover invalid
identity/evidence, concurrent-versus-exclusive outputs, matrix isolation,
range/limit invariants, duplicate IDs, unknown revisions, retirement,
immutability and revision replacement. Synthetic fixtures live in tests only.
No new named product or scientific conversion is enabled by this schema.

## Verification

The local Kotlin 2.1.0/JUnit run covers the three new test classes plus
`WaterMeasurementCatalogTest`, `WaterMeasurementNormalizerTest` and
`WaterAnalysisPolicyTest`: **40 tests passed**. This compiles the real pure
application sources; it does not replace the complete Android variant build.

The local Detekt CLI is the repository-pinned **1.23.8** release, verified with
its published SHA-512. Both repository configurations and
`tools/verify_detekt_policy.py` run against the existing advisory baseline:
zero blockers and zero new advisory debt. The baseline file is unchanged.
Commit/CI acceptance is recorded in the production checklist after publication.
