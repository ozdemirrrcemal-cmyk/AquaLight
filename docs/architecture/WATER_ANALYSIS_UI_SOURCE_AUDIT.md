# Water Analysis UI source and fixture audit

Baseline: `feat/water-analysis-ui-flow` at `3b8920d`, reviewed against the
production branch on 27 September 2026. This inventories the present views;
it is not a sign-off for accessibility, chemistry, assessment or release.

| View / layout | Displayed data and action | Source / disposition |
| --- | --- | --- |
| Tank Health / `fragment_tank_health.xml`, `item_tank_health_water_quality_header.xml` | Latest analysis date, up to eight profile-aware metric cards, missing values, Add Analysis | `TankHealthFragment` observes owner-scoped tank and analysis flows. `WaterAnalysisLatestMeasurements` takes a single event by observed time, commit time, ID. Card status says **Recorded**, not normal/optimal. Unknown tank profile renders no invented freshwater metrics. |
| Add Analysis / `fragment_tank_health_analysis_add.xml`, `item_tank_health_analysis_water_parameters.xml` | Selected tank-type recommendations, user-added tests, manual time/temperature, method/kit/basis/unit, save | Application `WaterTankMeasurementPolicy` supplies the 12-code visibility matrix. Typed values and selections are fragment saved state, and a saved UUID deduplicates retry. Hidden typed tests require confirmation before exclusion. This is still Proto v2, without durable draft, verified sensor, profile preference or assessment. |
| Test picker / `content_sheet_water_test_picker.xml` | Additional test choices | `WaterTestProfileUiCatalog` projects application visibility. Central `DialogSettingsBottomSheetBinding` hosts the picker. No unverified product names are offered. |
| Method sheet / `content_sheet_water_measurement_method.xml` | Manual, test kit, digital, unavailable sensor; typed basis and unit | `WaterMeasurementUiCatalog` delegates to the domain catalog. Only Salifert Nitrate and Other exist. SENSOR cannot be selected for a new unverified reading. Typed value source changes require explicit confirmation. |
| History / `fragment_tank_health_analysis_history.xml`, `item_tank_health_analysis_history_record.xml` | All owner/tank events; up to three actual measured results in each preview, including temperature when space permits; tap for detail | `WaterAnalysisViewModel.analysesForTank`. The preview no longer implies unmeasured pH/NO3; other results remain accessible in detail. Current full-list Proto flow has no indexed paging or 10,000-record evidence. |
| Detail / `fragment_tank_health_analysis_detail.xml`, `item_tank_health_analysis_detail_measurement.xml` | Observed date/time, optional temperature, every stored raw measurement and method metadata, delete confirmation | Same owner-scoped snapshot. Canonical conversion is displayed where valid; when it changes the result, the original number/unit is additionally labelled. No historical assessment snapshot is stored yet. |
| Maintenance | Static water-change/pruning/filter ages and status labels | Obsolete unused layout and fixture strings removed after Lint exposed them. Build a new projection from real owner-scoped care data when that feature is ready. |
| System summary | Static CO2, lighting, filter and load labels/values | Obsolete unused layout and fixture strings removed after Lint exposed them. A future context/device adapter must distinguish installed hardware from live measurements. |
| Plant/Livestock Health / `fragment_aquarium_health_placeholder.xml` | Placeholder | Explicitly deferred until Water Analysis acceptance; no implied health verdict. Algae Control remains deferred. |

TR and EN strings exist for the current dynamic water controls. The parameter
input now names the specific additional test in its remove action; the unit
chevron is decorative so the parent configuration action is announced once.
Source values use `LocaleFormatter` and input accepts decimal comma/dot without
grouping. The visible state and exact string/semantics still require API 27/36,
TR/EN, dark/light, large-font and TalkBack inspection on device. In particular,
the 28 dp remove button, 30 dp card header, dense four-column dashboard and
fixed-height history preview cards need touch-target and font-scaling review before
the UI/accessibility gate can close. Removed fixture layouts and their static
"normal" values cannot be reused as evidence for a health assessment.
