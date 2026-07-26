# Commercial Architecture Closure Record

This document records the completed execution order and the permanent engineering rules for AquaLight's application boundaries and composition-root architecture.

## Non-negotiable engineering rules

- Every development branch starts from the latest validated `main`.
- Each branch must leave the application buildable, testable and behavior-compatible.
- UI and ViewModels receive application/domain contracts, never concrete repositories, providers, DataStore managers or vendor SDK clients.
- Concrete construction belongs to the composition root.
- No dual path, service-locator fallback, temporary bridge or silent compatibility mechanism may remain after a workstream is complete.
- Every completed vertical workstream receives fake-backed unit tests and a CI architecture guard before merge.
- Unknown ViewModel bindings and unsupported application operations fail closed.
- A release candidate is accepted only after Debug/Release unit tests, lint, minified Release, CodeQL, API 27/API 37 instrumentation and targeted physical regression pass.

## Completed workstreams

### 1. Device application boundaries

Status: completed and merged through PR #34.

Completed scope:

- Owner-device observation, refresh and transactional deletion application boundary.
- Typed device-menu availability and current-liveness proof boundary.
- Read-only device status and Settings overview boundary with deterministic clock tests.
- Device-root read boundary for Light, Cooling, Timer and Dosing screens.
- Separate typed firmware-update command boundary for the Light OTA test surface.
- Tank-device assigned/available list, assignment, conflict and removal boundary.
- Production/release-smoke composition parity, deterministic fakes and dedicated CI guards for every migrated boundary.

Permanent acceptance baseline:

- Device list and card state remain behavior-compatible.
- Device deletion remains transactional and assignment-safe.
- Offline menu access remains blocked and online routing remains unchanged.
- Owner/account isolation and runtime lifecycle tests remain green.

### 2. Aquarium and care boundaries

Status: completed and merged.

Completed scope:

- Application/domain contracts for aquarium and care operations.
- DataStore managers and concrete assignment/care repositories removed from UI and ViewModels.
- Maintenance contract moved out of the data package.
- Fake-backed aquarium, assignment and care tests added.

Permanent acceptance baseline:

- Tank create, update, duplicate and delete behavior remains unchanged.
- Tank deletion frees devices and repairs care-task relations.
- Concurrent and owner-isolation behavior remains protected.

### 3. Provisioning platform boundaries

Status: completed and audited through PR #36.

Completed scope:

- Provisioning operations moved out of the UI package.
- Application DTOs introduced for provisioning sessions, events, failures and completion.
- BLE, GATT, QR, draft-store and route implementation types hidden behind application/platform contracts.
- QR/Scan, rollback, duplicate and process-recreation coverage expanded.

Permanent acceptance baseline:

- QR and Nearby Scan remain behaviorally equivalent.
- Existing-device setup-mode decision tree remains intact.
- Wrong QR, wrong Wi-Fi, power loss, cancellation and rollback remain ghost-free.

### 4. Composition-root closure

Status: completed and merged through PR #37.

Completed scope:

- Process-scoped and owner-scoped composition separated.
- Remaining provider and service-locator access removed from ViewModel construction.
- Monolithic feature ViewModel construction split into exact, fail-closed factories.
- Final UI/application/domain/data/platform dependency matrix enforced.
- Binding completeness, production/smoke parity and zero-construction-site inventory tests added.

Permanent acceptance baseline:

- Production and release-smoke wiring expose the same required bindings.
- UI and ViewModels do not import concrete data/platform implementations.
- No unmanaged repository, provider, DataStore or Firebase construction site remains.
- Owner-session generation and repository identity are validated before owner ViewModel creation.

## Closure evidence

- Final cumulative PR: #37.
- Final release-candidate head: `5e46bd12a01bd190cd1acbd17e342ae464d6bf6e`.
- Merge commit on `main`: `cddcf15865ad2ab2a1ba3ebdfb592cef2ba0ddbe`.
- Automated result at approval: 0 failing, 0 pending and 4 successful required checks.
- Physical regression T01–T18 passed, including QR, Nearby Scan, wrong-password retry, power/network interruption, process death, account switching, reset/reconfiguration, tank/device cleanup and clean reinstall.
- No known critical or high defect remained at merge approval.

## Ongoing rule

Future feature development must preserve these boundaries. Any change that weakens owner isolation, opens a second runtime, reintroduces UI-owned infrastructure, bypasses fail-closed factories or creates a parallel provisioning path requires an explicit architecture review and the full affected validation matrix.
