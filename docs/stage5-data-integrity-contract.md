# Stage 5 — Commercial owner data integrity

This stage defines the first commercial local-store contract for AquaLight.
The application has not shipped a previous public store schema, so this work
intentionally provides no legacy compatibility or downgrade path.

## Authoritative stores

- Aquarium tanks
- Care tasks
- Encrypted user preferences
- Light Library records

## Required guarantees

1. Every persisted root store carries its explicit current schema version. Aquarium Tanks is version `2`; Care Tasks, encrypted User Preferences, and Light Library remain version `1`.
2. Aquarium tank calendar-only setup and livestock-added dates are stored as epoch days, never epoch milliseconds.
3. Unsupported or missing schema versions fail closed as corruption.
4. Owner identifiers are canonical and every record is owner-scoped.
5. Tank and care-task identifiers are positive and unique per owner.
6. Nested tank entity identifiers are positive and unique within their tank.
7. Invalid enum, date, percentage, reminder, repeat, measurement, and text values cannot be serialized.
8. Corruption recovery is reported through `LocalDataRecoveryTracker`.
9. Manual and generated care-task IDs are allocated inside the atomic `DataStore.updateData` transaction.
10. Tank deletion and dependent Care Task cleanup use a durable compensating transaction. Care-task writes are blocked before snapshots are captured, care tasks are removed before the tank, failed tank writes restore the snapshots, and owner-session startup resolves interrupted transactions.
11. Serializer, corruption, owner-isolation, schema-policy, recovery, and concurrent-write tests are release gates.
12. Light Library names are canonical and unique within one owner and record type; product channel sets and custom time points are stored exactly.
13. Light Library records never persist a device UID, firmware revision, or loaded-state flag.

## Care schedule product limits

- Repeat interval: `1..365` days.
- Missed-reminder duration: `1..30` days.
- Blank, zero, malformed, or out-of-range values are rejected; they are never silently coerced.
- UI input parsing and persistent-store validation use the same application contract.
- A completed task timestamp cannot be later than its last-update timestamp.

## User preference integrity

- `themeMode` and `languageCode` are required, canonical values.
- Blank, whitespace-padded, malformed, or unsupported values are rejected on both serializer and manager write paths.
- Defaults are used only when a new versioned preference store is created; validation never silently substitutes defaults for persisted invalid values.

## Migration status

**Aquarium Tanks schema version `2` is a deliberate clean cutover.**

The livestock catalog integration adds a stable `catalogEntryId` to tank livestock records. AquaLight
does not infer catalog identity from a saved display name and does not retain a compatibility reader
for the pre-catalog tank schema. Version `1` tank stores are rejected by the same fail-closed schema
gate used for unknown versions; no legacy `DataMigration` is installed.

Catalog-backed livestock is identified only by `catalogEntryId`. A blank catalog id is reserved for a
new user-defined custom livestock record created through the current picker flow; it is not interpreted
as a legacy catalog match. Water-requirement data remains canonical in the bundled livestock catalog
and is resolved by that stable id instead of being duplicated into every tank record.

Care Task, Light Library, and encrypted User Preferences stores remain on version `1`. Missing or
unsupported versions for every store continue to fail closed.

## Delivery rule

The branch remains draft until every read and write path uses the same store
rules and CI proves that invalid, duplicate, or orphaned records cannot reach
a stable commercial state.
