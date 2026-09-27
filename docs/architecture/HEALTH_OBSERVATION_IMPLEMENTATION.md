# Algae, plant and livestock observation implementation

27 September 2026 continuation. These concrete application models and engines
are implemented and locally tested. Separate Room storage, owner-bound operations,
deletion recovery, media ownership, composition, archive and UI integration are
implemented. Device acceptance for the latest continuation remains in progress. This document does not close all A/P/L
acceptance gates or authorize replacing the approved Water Quality UI.

## Observation and engine boundaries

- Algae is a tank-level dated observation. Location, appearance and spread are
  recorded as user findings; appearance categories do not identify algae species.
- Plant and livestock observations use the registered local entity ID. A shared
  catalog ID never makes another specimen/group the historical subject.
- The input separates observation, intervention and follow-up. An intervention
  requires a predecessor and an explicit user description; the engine does not
  prescribe or automatically execute an intervention.
- Photoperiod, measured light information, CO2 observations and dosing history
  have explicit input fields. Installed products never fill those observations.
- Unknown findings/affected quantity remain unknown; empty observations, negative
  counts, non-positive identities and mixed unknown/known selection are rejected.
  Caller collections are copied and made unmodifiable before evaluation/storage.
- Each feature has its own pure engine and revision. Results contain observation
  coverage, gaps, actions and reused water findings, without disease labels,
  chemical doses, fabricated safety scores or a generic healthy verdict.
- Plant algae presence produces an `OPEN_ALGAE_CONTROL` action. The plant engine
  does not implement algae remediation. The detail action uses the central Safe Args
  route to the tank’s Algae Control history.
- Removed/unverified subjects and partial plant care are explicit gaps. Livestock
  affected quantity cannot exceed the registered group's captured quantity.

## Water evidence and time

`ObservationWaterEvidencePolicy` accepts an explicit versioned product recency
window. It records the sample's actual age at the observation and rejects future,
wrong-tank or outside-window evidence for reuse. The duration is an operational
linking policy, not a biological claim about how long water remains safe. No
implicit clock or same-sample chemistry join exists in these engines.

Historical water snapshots remain available as dated evidence. Current advice
reuses an existing Water Quality finding only for the exact entity kind/local ID,
within the linking window, and with the same captured context revision. A stale
sample or a changed habitat remains historical and produces a coverage gap.
The livestock engine adds no second water range comparator.

## Verification and remaining integration

The version-1 `health_observation.db` stores an immutable, checksummed snapshot
and a durable idempotency request together. Queries use exact owner/tank/subject
identity and 50-row keyset pages. The same tank deletion journal stages health
rows and water rows; prepare-abort preserves data, committed removal keeps rows
until rollback is no longer possible, and account cleanup includes this store.
Local JVM codec tests and Android-test compilation pass. Device execution for
the new observation tests still requires a subsequent checkpoint CI run.

Health images use the existing pending-media ownership journal in a bounded
`health_photos` FileProvider root. A new record accepts only a current owner's
pending health image; commit adopts it and exact deletion releases it. Startup
media recovery includes health deletion staging, so rollback images survive.
The dated Water query selects the last sample at or before the observation;
the 24-hour linking convention is recorded with its revision and maximum age.

`HealthObservationPolicyTest`, `ObservationWaterEvidencePolicyTest`,
`ObservationWaterFindingsTest` and `HealthObservationEnginesTest` cover immutable
inputs, identity, count/uncertainty, intervention requirements, time boundaries,
wrong tanks, stale/different-context findings, equipment non-inference, partial
care and the plant-to-algae action. The architecture guard now checks all four
engine domains plus observation policy for platform/data imports and ambient
I/O/time. Local debug unit and releaseSmoke Kotlin compilation passed, and the
unchanged Detekt baseline reports zero new debt. Full A/P/L acceptance also needs
live save/history/delete, media ownership, owner/tank/entity removal, restore,
process recreation, central navigation and UI/device evidence.

## UI and archive continuation

The three feature entry points now have owner-bound observation history, dated
forms, exact-record details, explicit unknown choices, photos and intervention /
follow-up entry. Plant and livestock subjects use local registered IDs. Loading,
empty, missing and failure states remain distinct. The approved Water Quality
layout is retained. All new routes live in `nav_aquarium.xml` and use
`navigateSafelyFrom`; their ViewModels are fragment-scoped with SavedState extras.

The version-4 backup declares a separate bounded/checksummed observation stream
and its exact photo references. Versions 1/2 retain their existing migration;
version 3 retains water history without inventing observations. New backups,
inspection counts, restore results and portable exports include health history.
Restore joins the existing owner transaction and deletion/session gates. Original
context, assessment, subject name and original event identity remain immutable;
local event/tank/photo references are remapped. Repeated imports are idempotent,
conflicting evidence is rejected, and rollback targets only the current owner and
restore transaction. Follow-up links survive a second export/import. Record and
photo extraction still share the existing archive byte ceilings.

Named additional regression coverage:
- `HealthObservationMutationsTest`: duplicate-save exclusion, immutable inputs,
  retry identity, cancellation, exact owner-bound route operation.
- `HealthObservationDraftInstrumentedTest`: parcelable date/subject/photo/request
  restoration; this is saved-state coverage, not a claim of a physical-device run.
- `HealthHistoryArchiveTest`, `HealthHistoryBackupCodecTest`,
  `HealthHistoryRestoreRecoveryTest`: stream integrity, exact photo declarations,
  legacy v3 migration, original provenance, JSON export and shared compensation.
- `HealthObservationArchiveInstrumentedTest`: actual Room atomicity, rollback,
  reopen/idempotency, cross-owner isolation and repeated-export follow-up links.
- `HealthObservationNavigationSmoke`: all three list/form/detail routes use the
  minified production graph and retain route ViewModels across back navigation.

The earlier CI failures are isolated in `9201035b`: Kotlin compiler GC exhaustion
is addressed by in-process compilation within the bounded Gradle heap; the media
recovery test now ages its fixture beyond the existing orphan grace period and
still verifies that a rollback-staged old photo is retained. No Detekt baseline,
suppression, test exclusion or acceptance gate was relaxed.

Latest continuation test/CI results are recorded in the production checklist.
Physical sensor, full accessibility and the outstanding scientific/catalog gates
remain separate acceptance work; these observation changes do not close them.
