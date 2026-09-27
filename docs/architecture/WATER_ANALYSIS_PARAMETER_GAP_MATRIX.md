# Water Analysis parameter and tank-type gap matrix

Baseline: `feat/water-analysis-ui-flow` at `3b8920d`, reviewed against the
production branch on 27 September 2026. This is an implementation inventory,
not an approved chemistry rule table. The executable visibility authority is
`WaterTankMeasurementPolicy`; `WaterTankMeasurementPolicyTest` checks all 12
codes, disjoint recommended/additional lists, unknown codes and coverage of
every selectable `WaterParameter`, excluding the retired ambiguous ammonia slot.

## Tank-type visibility

R = recommended card; A = user-addable card; blank = unavailable. The codes in
each row are exact `AquariumTankTaxonomy` values. All rows include separate
manual temperature input outside the test grid.

| Tank code(s) | R | A |
| --- | --- | --- |
| `Freshwater Fish`, `Other Freshwater` | pH, TAN as N, NO2, NO3, GH, KH | direct free NH3, PO4, TDS, EC, DO |
| `Planted` | pH, NO3, PO4, GH, KH | TAN as N, direct free NH3, NO2, CO2, Fe, K, TDS, EC, DO |
| `Shrimp` | pH, TAN as N, NO2, NO3, GH, KH, TDS | direct free NH3, EC, PO4, Cu, DO |
| `Brackish General`, `Other Brackish` | pH, TAN as N, NO2, NO3, salinity, KH | direct free NH3, GH, PO4, SG, DO |
| `Marine Fish`, `Other Marine` | pH, TAN as N, NO2, NO3, salinity, total alkalinity, PO4 | direct free NH3, SG, Ca, Mg, DO |
| `Soft Coral Reef`, `LPS Reef`, `SPS Reef`, `Mixed Reef` | pH, NO3, PO4, salinity, total alkalinity, Ca, Mg | TAN as N, direct free NH3, NO2, SG, DO |

Unknown/empty tank type has no inherited freshwater row and disables the
profile-specific save path. Hidden draft values survive a tank-type change;
confirmation is required before saving an event that excludes them.

## Per-parameter semantic and presentation gap

Every source selection and entered number is retained as raw method, kit ID,
basis and unit in Proto history/detail. The dashboard displays only measured
fields from **one** latest event; it currently says “Recorded”, which is not a
health assessment. `canonicalValue = null` means source-native display, never
zero or normal. No parameter has a committed rule/context assessment yet.

| Current parameter | Current source and canonical handling | Remaining assessment/input work |
| --- | --- | --- |
| pH | Typed pH without unit; identity normalization | Sample calibration, physical bounds, tank/inhabitant context and rule evidence |
| NO3 | Salifert Nitrate or guided Other; typed NO3 mg/L identity; generic NO3-N stays raw | Exact profile revision/matrix/range, method-scoped NO3-N conversion and evidence-backed rules |
| NO2 | Typed NO2 mg/L identity | Source profile/range and freshwater versus marine rules |
| `AMMONIA_AMMONIUM` | Historical `NH3_NH4`/`TAN` choice remains raw and labelled `LEGACY_UNASSESSED`; new writes are rejected | Product/source revision and historical migration evidence; never relabel this value as TAN or direct NH3 |
| `TOTAL_AMMONIA_NITROGEN` | Separate manual typed TAN result, `mg/L as N` | Verified source profiles, precision, limits, thresholds and provenance revision |
| `FREE_AMMONIA_NH3` | Separate manual typed direct free NH3 result, `mg/L as NH3`; can coexist with TAN in one event | Verify direct method and source identity; never derive from TAN without same-sample validated calculation |
| GH | Raw dGH or mg/L as CaCO3; dGH converts at 17.86 to canonical mg/L as CaCO3 | Profile precision, verified scope and range intersection |
| KH | Historical/unspecified freshwater KH slot remains raw; old marine KH is retained in history/detail without becoming a second dashboard alkalinity result | Distinguish verified freshwater carbonate hardness from total alkalinity using a source profile |
| `TOTAL_ALKALINITY` | Marine/reef UI has one typed alkalinity slot. Explicit manual total alkalinity in dKH or mg/L as CaCO3 converts to canonical meq/L (0.358 per dKH, 50 mg/L as CaCO3 per meq/L); raw source stays intact. Unknown kits/digital readings remain raw | Verified product/matrix/method profile, source revision, precision and evidence-backed rules; no old KH relabelling |
| PO4 | Typed PO4 mg/L identity; bare phosphorus remains raw | Reactive method scope, limits and total-P exclusion |
| TDS | Meter-reported ppm stays raw | Device factor, compensation and no generic EC conversion |
| EC | µS/cm stays raw | Reference temperature and verified device correction |
| CO2 | Generic mg/L stays raw | Direct method versus labelled pH/KH derived path |
| Fe | Generic mg/L stays raw | Total/dissolved/ferrous analytical scope |
| K | Typed mg/L as K identity | Product range/precision and plant rule evidence |
| Salinity | Generic ppt stays raw | PSS-78 versus mass/vendor scale and temperature context |
| SG | Raw unitless SG stays unassessed | Reference/sample temperature and method provenance |
| Ca | Typed elemental mg/L identity | Verified source species/matrix and reef evidence |
| Mg | Typed elemental mg/L identity | Verified source species/matrix and reef evidence |
| Cu | Typed mg/L identity | Detection-limit notation, shrimp-specific evidence and source scope |
| DO | Typed mg/L O2 identity | Same-sample compensation, separate saturation result and contextual rules |

Free/total chlorine and saturation percent
are accepted **future typed metrics**, not additional cards silently hidden in
the current picker. A verified product can introduce them only with independent
result identity and migration. The named product catalog currently contains
Salifert Nitrate and generic Other only; JBL/Sera/API/Red Sea/Hanna expansion
remains a separate evidence gate.

## Acceptance links

- S.1–S.3: replace legacy slots with typed metrics and method-qualified source
  profiles before any new canonical assessment.
- E.1/E.5: supply immutable tank/plant/livestock/sensor context and one
  deterministic engine with coverage, severity, conflict and missing reasons.
- M.2–M.7: persist historical raw/canonical/context/assessment revisions in
  indexed storage, migrate without loss and prove archive/delete behavior.
- E.4/E.7: render dated assessment states and test all 12 rows in TR/EN,
  accessibility, unit/instrumentation, lint, release and emulator gates.
