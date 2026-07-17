# Composition Dependency Matrix

## Commercial architecture objective

The process composition root is owner-neutral. Authenticated owner runtime is opened only by the session coordinator. UI and ViewModel factories may consume a committed owner graph, but they may never create a device repository, socket runtime, assignment repository, DataStore manager provider or Firebase client.

## Process scope

| Dependency | Owner | Construction site | Lifetime |
| --- | --- | --- | --- |
| `AppContainer` | `AquaApp` | `DefaultAppContainer` | Android process |
| startup appearance cache | `AppContainer` | `DefaultAppContainer` | Android process |
| user settings/profile application adapters | `AppContainer` | `DefaultAppContainer` | Android process |
| auth/session/account application adapters | `AppContainer` | `DefaultAppContainer` | Android process |
| QR frame decoder factory | `AppContainer` | `DefaultAppContainer` | Android process |
| `ProcessViewModelFactory` | `AppContainer` | `DefaultAppContainer` | Android process |
| `OwnerViewModelFactory` dispatcher | `AppContainer` | `DefaultAppContainer` | Android process; resolves owner per creation |

A process-scoped object must not retain `DevicesRepository`, `TankDeviceAssignmentRepository`, runtime tokens, WebSocket clients or a previously authenticated owner UID.

## Authenticated owner scope

| Dependency | Open authority | Consumer | Close authority |
| --- | --- | --- | --- |
| `DevicesRepository` and device runtime | `OwnerSessionCoordinator` through `DevicesRepositoryProvider.get` | `OwnerDependencyGraph` via `currentRepository(ownerUid)` | awaited `DevicesRepositoryProvider.clear` barrier |
| tank-device assignment repository | `OwnerSessionCoordinator` through `TankDeviceAssignmentRepositoryProvider.get` | `OwnerDependencyGraph` via `currentRepository(ownerUid)` | `TankDeviceAssignmentRepositoryProvider.clear` |
| aquarium/care stores used by foreground ViewModels | `OwnerDependencyGraph` after committed session activation | owner ViewModels | navigation owner replacement; all writes remain owner-scoped |
| provisioning QR secret/draft stores | `OwnerDependencyGraph` with an immutable owner UID provider | provisioning application boundaries | encrypted TTL/removal and owner session replacement |
| provisioning progress adapter | `OwnerViewModelFactory` with captured `graph.ownerUid` | one progress ViewModel | ViewModel transport close/cancellation contract |

The `currentRepository(ownerUid)` methods are read-only dependency handoff points. They must never create or start runtime work. A missing or mismatched repository is a hard failure because an owner screen must not repair session ordering by opening a second runtime.

`OwnerDependencyGraph` also captures the committed owner-session generation. A graph is resolved only when `activeOwnerUid` matches, `pendingOwnerUid` is empty, and the session generation and repository identities remain stable through dependency composition. This rejects startup, logout and account-switch transition windows before any ViewModel constructor can start a collector.

## ViewModel factory matrix

| Factory | Allowed bindings | Forbidden behavior |
| --- | --- | --- |
| `AuthViewModelFactory` | authentication ViewModels using `AuthOperations` | Context/Firebase/repository construction |
| `ProcessViewModelFactory` | owner-independent ViewModels only | owner UID, repositories, providers |
| `OwnerViewModelFactory` | authenticated aquarium, care, device and provisioning ViewModels | provider `get`, assignable matching, fallback construction |
| `AquaViewModelFactory` | exact dispatch between process and owner factories | Android fallback, duplicate binding, unknown binding |

## Provider and service-locator containment

`DevicesRepositoryProvider.get` and `TankDeviceAssignmentRepositoryProvider.get` remain contained inside the foreground owner-session data boundary because they implement the validated awaited shutdown barrier. They are forbidden in UI, ViewModels, `AppContainer` and all ViewModel factories.

The composition root consumes only `currentRepository(ownerUid)`. This preserves one runtime instance and avoids rewriting an already validated lifecycle barrier during composition closure. A later replacement of the two lifecycle holders must keep the same open/clear barrier and can be performed without changing UI or ViewModel construction.

## Fail-closed rules

1. Unknown ViewModel classes throw; Android default fallback is forbidden.
2. A class bound in both process and owner scopes throws.
3. Owner ViewModel creation requires a committed session whose authenticated UID, generation, device repository and assignment repository match.
4. Pending startup, logout or account-switch sessions fail before a ViewModel constructor can start collectors; committed-session teardown remains the single authority for already-created ViewModels.
5. Provisioning storage captures the immutable graph owner UID.
6. Owner dependency resolution never calls a provider `get` method and therefore can never open UDP discovery, collectors or WebSockets.

## Automated evidence

- `composition_root_guard.py` enforces the matrix and forbidden construction sites.
- `AquaViewModelFactoryTest` verifies exact routing, unknown-binding rejection and duplicate-scope rejection.
- `OwnerDependencyGraphSessionTest` verifies committed, pending, mismatched and signed-out session behavior before dependency construction.
- Existing architecture, session, provisioning and UI construction guards remain in the CI chain.
- Debug/Release unit tests, lint, minified Release, CodeQL and API 27/API 35 emulator workflows remain mandatory for affected release candidates.

## Physical validation baseline

Code-level commercial approval does not replace physical provisioning regression. QR, Nearby Scan, cancellation, rollback, Wi-Fi failure, process recreation and owner-switch scenarios must pass on each affected final Release Candidate.

The closure release candidate passed this baseline through physical tests T01–T18 before merge to `main`.
