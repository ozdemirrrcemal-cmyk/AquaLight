# Algae, plant and livestock observation implementation

27 September 2026 continuation. These concrete application models and engines
are implemented and locally tested. Storage, media, operations, composition and
UI integration are the next continuation. This document does not close all A/P/L
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
  does not implement algae remediation. Central UI routing remains to be bound.
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
