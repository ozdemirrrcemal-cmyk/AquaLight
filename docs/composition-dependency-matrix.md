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
