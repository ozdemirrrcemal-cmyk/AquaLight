# Production Device and Tank Assignment Roadmap

## Delivery contract

- The application is not released yet.
- There is no backward compatibility, legacy migration, dual-read, or fallback storage.
- JSON and Preferences-based device/assignment records are removed rather than migrated.
- Every persisted device, credential, and tank assignment is scoped to one authenticated owner UID.
- Every write is atomic, validated, observable, and returns an explicit domain result.
- UI success is emitted only after durable persistence succeeds.

## Non-negotiable invariants

1. One device can be assigned to at most one tank for the same owner.
2. A device or tank owned by another account can never be observed, opened, assigned, deleted, or authenticated.
3. Deleting a tank removes only its relationships; it does not delete device records or credentials.
4. Deleting a device removes its known-device record, runtime session, credential, and tank relationship as one coordinated operation.
5. Failed bulk deletion leaves failed devices selected and reports partial success.
6. Stale assignments are repaired at authenticated session startup.
7. Repeated taps and concurrent writes cannot create duplicates, overwrite another owner, or emit false success.
8. Corrupt Proto content fails closed through strict serializers; invalid records are never silently skipped.

## Phase 1 — Assignment persistence and domain contract

- Replace `SharedPreferences` JSON assignment storage with Proto DataStore.
- Add strict serializer validation.
- Add owner-scoped assignment repository/provider.
- Add explicit `Assigned`, `AlreadyAssigned`, `Conflict`, `TankNotFound`, `DeviceNotFound`, and persistence-failure outcomes.
- Add atomic removal and owner cleanup operations.
- Add deterministic stale-record repair.
- Bind tank detail and selection UI to repository results with loading/error states.
- Add reducer and serializer tests.

Exit criteria:

- No `tank_device_assignments_v2`, JSON, `JSONArray`, or `JSONObject` assignment path remains.
- Assignment UI never navigates back on a failed write.
- Duplicate and cross-owner assignments are rejected by tests.

## Phase 2 — Known-device persistence

- Replace Preferences DataStore JSON with one strict Proto DataStore.
- Store owner UID in every known-device record.
- Centralize normalization/deduplication in a pure reducer.
- Persist ignored/forgotten device identity in the same owner scope so discovery cannot resurrect forgotten devices.
- Remove context-free repository construction.

Exit criteria:

- There is one durable known-device source.
- Corrupt, duplicate, cross-owner, and malformed records fail closed.
- Restart preserves all non-secret metadata.

## Phase 3 — Credentials and runtime ownership

- Introduce a central credential key factory using owner UID and device UID.
- Remove global credential cleanup.
- Make every runtime repository/session immutable to one owner generation.
- Close WebSockets, command clients, time-sync memory, and registry state during owner transition.

Exit criteria:

- A token written by owner A cannot be read or used in owner B's session.
- Logout/account switch leaves no active runtime session from the previous owner.

## Phase 4 — Session state machine and cleanup orchestration

- Add generation-controlled owner session transitions.
- Add owner-aware repository lifecycle and startup repair.
- Make session shutdown best-effort across all steps while collecting failures.
- Add owner device-data cleaner and integrate it with account deletion/local-data deletion.

Exit criteria:

- A → B → A switching is deterministic under delayed asynchronous work.
- Old generation work cannot repopulate the new session.

## Phase 5 — Device save/delete commercial flow

- Make provisioning commit transactional from the UI perspective.
- Coordinate known device, credential, registry, runtime, and assignment cleanup.
- Add operation locks and idempotent results.
- Implement partial-success bulk deletion and user-facing recovery messages.
- Show assigned tank name on device cards.

Exit criteria:

- Save, assign, unassign, single delete, and bulk delete have explicit loading/success/failure behavior.
- No swallowed exception can produce a success UI state.

## Phase 6 — CI, tests, and release gate

- Unit-test serializers, reducers, credential keys, owner transitions, repair, cleanup, and partial deletion.
- Run all Debug unit tests plus Debug APK assembly in CI.
- Add static scans for removed legacy APIs and forbidden context-free providers.
- Run release lint/resource shrinking and signed release assembly before merge.

## Merge gate

The branch is ready for `main` only when:

- Debug build and all unit tests pass.
- Release lint/resource linking passes.
- No legacy persistence/fallback symbols remain.
- All owner/session invariants have tests.
- Manual smoke tests cover clean install, restart, rapid taps, A → B → A, tank deletion, device deletion, and partial failure.
