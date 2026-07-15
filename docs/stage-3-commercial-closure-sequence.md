# Stage 3 Commercial Closure Sequence

This document is the authoritative execution order for completing AquaLight Stage 3 without broad, unreviewable refactors or temporary compatibility layers.

## Non-negotiable engineering rules

- Every branch starts from the latest validated `main`.
- Each branch must leave the application buildable, testable and behavior-compatible.
- UI and ViewModels receive application/domain contracts, never concrete repositories, providers, DataStore managers or vendor SDK clients.
- Concrete construction belongs to the composition root.
- No dual path, service-locator fallback, temporary bridge or silent compatibility mechanism may remain after a slice is complete.
- Every completed vertical slice receives fake-backed unit tests and a CI architecture guard before merge.
- Unknown ViewModel bindings and unsupported application operations fail closed.
- A branch is merged only after Debug/Release unit tests, lint, minified Release, CodeQL, API 27/API 35 instrumentation and targeted physical regression pass.

## Locked branch order

### 1. `feature/stage-3-device-application-boundaries`

Scope:

- Introduce application/domain contracts for owner device observation, refresh, deletion, assignment access and device-menu availability.
- Remove concrete device repository, assignment repository, cleaner and provider dependencies from device-related ViewModels.
- Add deterministic fakes and ViewModel/use-case tests.
- Extend guards so migrated device UI cannot import or construct concrete device data infrastructure.

Merge gate:

- Device list/card state remains identical.
- Device delete remains transactional and assignment-safe.
- Offline menu access remains blocked and online routing remains unchanged.
- Owner/account isolation and runtime lifecycle tests remain green.

### 2. `feature/stage-3-aquarium-care-boundaries`

Scope:

- Introduce application/domain contracts for aquarium and care operations.
- Remove DataStore managers and concrete assignment/care repositories from UI and ViewModels.
- Move the maintenance contract out of the data package.
- Add fake-backed aquarium, assignment and care tests.

Merge gate:

- Tank create/update/duplicate/delete behavior remains unchanged.
- Tank deletion frees devices and repairs care-task relations.
- Concurrent and owner-isolation behavior remains green.

### 3. `feature/stage-3-provisioning-platform-boundaries`

Scope:

- Move provisioning operations out of the UI package.
- Introduce application DTOs for provisioning sessions, events, failures and completion.
- Hide BLE, GATT, QR, draft-store and route implementation types behind application/platform contracts.
- Expand QR/Scan, rollback, duplicate and process-recreation tests.

Merge gate:

- QR and Nearby Scan remain behaviorally equivalent.
- Existing-device setup-mode decision tree remains intact.
- Wrong QR, wrong Wi-Fi, power loss, cancellation and rollback remain ghost-free.

### 4. `feature/stage-3-composition-root-closure`

Scope:

- Separate process-scoped and owner-scoped composition.
- Remove remaining provider/service-locator access from ViewModel construction.
- Split the monolithic feature ViewModel factory and make all bindings fail closed.
- Enforce the final UI/application/domain/data/platform dependency matrix.
- Add binding completeness, production/smoke parity and zero-construction-site inventory tests.

Merge gate:

- Production and release-smoke wiring expose the same required bindings.
- No UI/ViewModel imports concrete data/platform implementations.
- No unmanaged repository/provider/DataStore/Firebase construction site remains.
- Stage 3 definition of done is fully satisfied.

## Completion rule

Stage 3 is closed only after all four branches are merged in this order and the final `main` commit passes the complete commercial validation gate. Device-menu feature development starts only after that closure.