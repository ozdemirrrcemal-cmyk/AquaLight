# Stage 3 — Dependency Boundaries and Composition Root

## Objective

Move object construction and platform wiring out of Fragments and ViewModels into one process-scoped composition root while preserving every existing user flow and business rule.

This stage does not redesign the device menu protocol, persistence schemas, navigation graph, visual system or feature behavior.

## Non-negotiable behavior contract

- Authentication validation, Firebase call order and navigation outcomes remain unchanged.
- Existing owner/session/runtime lifecycle barriers remain authoritative.
- Device provisioning, device deletion, tank assignment and runtime recovery are not redesigned.
- Tank and care-task data semantics are not changed.
- Theme, language, notification and profile values keep their existing storage and defaults.
- Existing error, loading and success presentation remains unchanged unless a defect is proven by a regression test.

## Target dependency direction

```text
UI -> application contracts/use cases -> data implementations -> platform/vendor SDKs
                 \-> domain/value models
composition root -> all concrete construction
```

Rules:

1. UI may request already-wired application contracts and ViewModel factories from `AppContainer`.
2. UI and ViewModels must not construct repositories, providers, DataStore managers or Firebase/Google clients.
3. Application contracts must remain free of Android, Firebase, data implementation, platform and UI imports.
4. Data/platform implementations may depend on Android/vendor SDKs, but those types must not leak through application contracts.
5. Feature migration is vertical and behavior-preserving; completed slices are protected by CI guards.

## Dependency inventory

### Auth/session/account security

- `AuthRepository`, `LogoutManager`, account deletion and Google identity clients were previously created from UI contexts.
- Reauthentication and feedback screens accessed Firebase directly.
- Auth ViewModels depended on the concrete repository.

### Preferences/profile/settings

- Theme, language, application settings, profile edit and usage screens resolve `UserPreferencesManager` directly.
- Several settings screens invoke session or account managers directly.

### Aquarium and maintenance

- Aquarium ViewModels construct tank and assignment repositories.
- Tank detail and care-task screens create DataStore managers or repository providers from UI.

### Devices and provisioning

- Devices, QR handoff, tank-device selection, device status and menu-root ViewModels resolve process providers directly.
- These dependencies must be injected without redesigning the typed device-menu architecture reserved for the later roadmap gate.

### Feedback

- Feedback UI directly coordinates Firebase Auth, Firestore and Storage.
- Stage 3 removes vendor SDK access from UI; Stage 9 will later own media decoding, compression, rollback and orphan cleanup hardening.

## Migration slices

### Slice A — Composition root and authentication

- Establish `AppContainer` and `DefaultAppContainer`.
- Add application contracts for auth, session exit and account security.
- Add platform/data adapters.
- Constructor-inject auth ViewModels and migrate auth/settings entry points.
- Add fake-backed tests and incremental guard.

### Slice B — Preferences, profile and settings

- Add application-facing preference/profile contracts.
- Centralize preference and profile dependency construction.
- Migrate theme, language, app settings, usage and profile edit surfaces.
- Preserve existing DataStore values and startup cache behavior.

### Slice C — Aquarium and care

- Add aquarium/care application boundaries and feature factories.
- Inject tank and care dependencies into ViewModels/screens.
- Preserve owner scoping, repair behavior and reminder scheduling.

### Slice D — Device registry, assignment and provisioning

- Add application-facing device access and assignment boundaries.
- Inject provider-backed dependencies into existing ViewModels.
- Keep lifecycle, owner isolation, provisioning and runtime contracts unchanged.

### Slice E — Feedback vendor isolation

- Move Firebase Auth/Firestore/Storage calls behind a feedback application contract.
- Keep current image-processing behavior unchanged for Stage 9.
- Add fake submission tests and fail-closed result handling.

### Slice F — Final architecture enforcement

- Remove remaining direct repository/manager/provider creation from UI and ViewModels.
- Enforce UI/application/data/platform access rules in CI.
- Require fake-backed tests for every migrated application boundary.
- Produce a final construction-site inventory with zero unmanaged UI creation sites.

## Per-slice validation gate

1. architecture/session/navigation/composition guards;
2. Debug unit tests;
3. Debug lint and APK build;
4. Release unit tests and lint;
5. minified Release build;
6. CodeQL;
7. API 27 and API 35 emulator tests;
8. targeted physical regression for affected flows.

## Definition of done

Stage 3 is complete only when:

- one central composition root owns concrete application wiring;
- Fragments and ViewModels do not construct repositories/managers/providers;
- UI has no direct Firebase Auth, Firestore or Storage access;
- ViewModels receive dependencies through constructors/factories;
- application contracts are Android/vendor independent;
- fake implementations can drive migrated ViewModel/use-case tests;
- architecture guards prevent regression;
- all automated and physical behavior gates pass;
- no known critical or high regression remains.
