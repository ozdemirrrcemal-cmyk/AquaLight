# Water Analysis legacy format inventory and strict reader

Reviewed 27 September 2026. Source history, rather than an assumed installation
population, defines the known formats below. Release distribution to individual
devices is not established by this inventory.

## Known persistent and archive formats

| Format | Source evidence | Water-analysis content and treatment |
| --- | --- | --- |
| `water_analyses.pb` v1 | Introduced in `86ca6b3409829838d31ae6cf5de567a53cb71d04` | Owner/tank/event IDs, observation/creation times, optional temperature and repeated raw measurements. No request ID. Read additively; retain blank request identity and legacy ammonia meaning. |
| `water_analyses.pb` v2 | `18ed54a2c2916a555a20c57b8940cb923a041aa7` | Adds request ID at tag 8. Identical retry uses the stored request identity; legacy rows may remain blank. No raw measurement field changes. |
| `water_analyses.pb` v3 | `33c75adfa3940cbb96b98034cc7a31c77fb80e83`, plus `3dccd32` | Introduces distinct TAN-as-N/direct-NH3 vocabulary; later adds typed total alkalinity. Proto wire fields remain additive. Old ammonia and KH retain their original keys/bases/units. |
| User backup ZIP v1 / portable JSON v1 | `9c27591b4996499c23f262a204fa60b95aaea553^:UserDataArchiveModels.kt` | Contains aquarium, care-task and device-assignment data; no analysis list, method preference, assessment or migration staging. It cannot supply historic analyses. |
| User backup ZIP v2 / portable JSON v2 | `9c27591b4996499c23f262a204fa60b95aaea553` through current models | Moves livestock to strict catalog identity; later media additions do not add analysis history. The current decoder rejects v1. Old-archive compatibility and new history round-trip remain M.7. |
| Android system backup/data extraction | Current manifest and backup/extraction XML | Backup is disabled and datastore data excluded. This is not a water-analysis export/restore mechanism. |

The archive paths above are under
`app/src/main/java/com/aqua/aqualight/data/user/archive/`.
`UserDataArchiveSnapshotCollector`, `UserDataBackupManifest` and
`PortableAquariumData` confirm that current archives do not collect analysis
history. Empty historical coverage must be stated explicitly when M.7 extends
restore; no synthetic old assessment can be created.

## Complete Proto field mapping

| Message | Tags and exact read behavior |
| --- | --- |
| `WaterAnalysesStore` | `1 analyses`: preserve order and all entries; `100 schema_version`: accept exactly 1/2/3, then upgrade only the envelope version in memory. |
| `StoredWaterAnalysis` | `1 id`, `2 tank_id`, `3 measured_at_millis`, `7 created_at_millis`, `100 owner_uid`: retain exact values and validate identity/owner/date invariants. `8 request_id`: retain exact canonical UUID or the supported legacy blank. `20 measurements`: preserve every measurement without reordering, deduplication or filtering. |
| Temperature fields | `4 has_temperature` controls presence; `5 temperature_celsius` and `6 temperature_source` must be canonical empty values when absent. Present MANUAL and historical SENSOR values are retained, without fabricating sensor provenance. |
| `StoredWaterMeasurement` | `1 parameter`, `3 method`, `5 basis`, `6 unit`: exact case-sensitive enum-name mapping. `2 value`: retain the original finite nonnegative Double. `4 test_kit_id`: retain exact supported identity; no display label or live-default substitution. |
| Unknown Proto wire fields | Retained by the generated Proto messages and `toBuilder()` upgrade. The reader does not project through a lossy reconstruction before returning the stored records. |

## Complete enum and source vocabulary

All known names map to the same named application enum, never to a default:

- Parameters: `PH`, `NITRATE`, `NITRITE`, `AMMONIA_AMMONIUM`,
  `TOTAL_AMMONIA_NITROGEN`, `FREE_AMMONIA_NH3`, `GH`, `KH`,
  `TOTAL_ALKALINITY`, `PHOSPHATE`, `TDS`, `EC`, `CO2`, `IRON`, `POTASSIUM`,
  `SALINITY`, `SPECIFIC_GRAVITY`, `CALCIUM`, `MAGNESIUM`, `COPPER`,
  `DISSOLVED_OXYGEN`. The original v1/v2 vocabulary lacked the two typed
  ammonia keys and total alkalinity; their introduction does not relabel old keys.
- Methods: `MANUAL`, `TEST_KIT`, `DIGITAL`, `SENSOR`.
- Bases: `PH`, `NO3`, `NO3_N`, `NO2`, `NH3_NH4`, `TAN`, `TAN_N`,
  `FREE_NH3`, `GH`, `KH`, `TOTAL_ALKALINITY`, `PO4`, `P`, `TDS`, `EC`,
  `CO2`, `FE`, `K`, `SALINITY`, `SG`, `CA`, `MG`, `CU`, `O2`.
- Units: `NONE`, `MG_L`, `DGH`, `DKH`, `PPM`, `US_CM`, `PPT`, `MEQ_L`,
  `PPM_CACO3`. These are source representations; reading a unit never grants
  conversion or assessment authority.
- Temperature sources: `MANUAL`, `SENSOR`.
- Existing kit IDs: `other`, and nitrate-only `salifert_nitrate`. An unknown
  future kit ID is an unsupported source value, not an instruction to use Other.

`WaterAnalysisLegacyReaderTest.everyCurrentPersistedEnumAndSupportedSelectionRoundTripsWithoutRelabelling`
enumerates every supported parameter/basis/unit/method combination.
`knownTemperatureSourcesNamedKitAndRequestIdentityRemainIntact` covers both
temperature sources, the named kit and v2 request identity.

## Failure and preservation rules

`WaterAnalysisLegacyReader` is the production serializer's single read path:

- Unsupported/missing schema → `WaterAnalysisReadFailure.UnsupportedSchema`.
- Unknown parameter, method, basis, unit, temperature source or kit →
  `WaterAnalysisReadFailure.UnsupportedValue`, retaining field and raw value.
- Malformed wire data, blank required fields or violated stored-record
  invariants → `CorruptionException`.
- Input-stream I/O failure → `IOException`, retaining the underlying cause.
- Valid empty store → a valid empty store; it is never substituted for failure.

The reader accepts an `InputStream` only and performs no writes or deletion.
The DataStore has no empty replacement corruption handler. Unknown data stays
available for recovery/newer software. This is not a Room cutover, quarantine UI,
backup, checksum migration or replay journal; those remain M.2–M.7.

Named preservation/failure tests also include
`nonemptyLegacyVersionsPreserveOwnerIdentityRawValuesAndSourceFields`,
`futureValuesAreTypedUnsupportedFailuresAndNeverSilentlyFiltered`,
`unknownTemperatureSourceAndSchemaAreNotReportedAsCorruption`,
`streamIoFailureRemainsDistinctFromMalformedProtoAndInvalidStoredValues`, and
`unknownProtoFieldsSurviveAdditiveVersionUpgrade`.

## Local verification

On 27 September 2026, Kotlin 2.1.0 compiled the actual reader/serializer and
generated Protobuf 3.25.3 classes: all 10 reader/serializer JUnit tests passed.
The separate application suite passed 40 tests. The architecture guards and
308 Python tool tests passed. Detekt 1.23.8 passed both configured rule sets and
the existing advisory baseline policy: zero blockers and zero new debt.
These are local checks; the full Android Gradle and device gates remain separate.
