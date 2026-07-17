# Stage 5 — Commercial tank and care data integrity

This stage defines the first commercial local-store contract for AquaLight.
The application has not shipped a previous public store schema, so this work
intentionally provides no legacy compatibility or downgrade path.

## Authoritative stores

- Aquarium tanks
- Care tasks
- Encrypted user preferences

## Required guarantees

1. Every persisted root store carries schema version `1`.
2. Unsupported or missing schema versions fail closed as corruption.
3. Owner identifiers are canonical and every record is owner-scoped.
4. Tank and care-task identifiers are positive and unique per owner.
5. Nested tank entity identifiers are positive and unique within their tank.
6. Invalid enum, date, percentage, reminder, repeat, measurement, and text
   values cannot be serialized.
7. Corruption recovery is reported through `LocalDataRecoveryTracker`.
8. Manual and generated care-task IDs are allocated inside the atomic
   `DataStore.updateData` transaction.
9. Tank deletion and dependent Care Task cleanup use a durable compensating
   transaction. Care-task writes are blocked before snapshots are captured,
   care tasks are removed before the tank, failed tank writes restore the
   snapshots, and owner-session startup resolves interrupted transactions.
10. Serializer, corruption, owner-isolation, schema-policy, recovery, and
    concurrent-write tests are release gates.

## Care schedule product limits

- Repeat interval: `1..365` days.
- Missed-reminder duration: `1..30` days.
- Blank, zero, malformed, or out-of-range values are rejected; they are never silently coerced.
- UI input parsing and persistent-store validation use the same application contract.
- A completed task timestamp cannot be later than its last-update timestamp.

## User preference integrity

- `themeMode` and `languageCode` are required, canonical values.
- Blank, whitespace-padded, malformed, or unsupported values are rejected on
  both serializer and manager write paths.
- Defaults are used only when a new versioned preference store is created;
  validation never silently substitutes defaults for persisted invalid values.

## Migration status

**Status: N/A for the first commercial release schema.**

AquaLight has not shipped a public Tank, Care Task, or encrypted User
Preferences schema before version `1`. Therefore there is no legitimate
source schema to migrate and no legacy `DataMigration` is installed. Clean
installation is the required validation baseline for this unreleased build.

Version `1` is still explicit and tested. Missing version `0` and future
versions fail closed. The first post-release schema change must increment the
relevant version constant, add an explicit `DataMigration`, and include
upgrade, interruption, rollback-safety, and downgrade-rejection tests before
that version may be released.

## Delivery rule

The branch remains draft until every read and write path uses the same store
rules and CI proves that invalid, duplicate, or orphaned records cannot reach
a stable commercial state.