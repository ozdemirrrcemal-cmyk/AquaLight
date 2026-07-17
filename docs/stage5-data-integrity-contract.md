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
9. Orphaned care tasks are repaired against the authoritative tank store.
10. Serializer, corruption, owner-isolation, migration-version, and
    concurrent-write tests are release gates.

## Delivery rule

The branch remains draft until every read and write path uses the same store
rules and CI proves that invalid or duplicate records cannot reach disk.
