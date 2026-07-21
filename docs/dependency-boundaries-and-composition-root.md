# Dependency Boundaries and Composition Root

## Objective

Move object construction and platform wiring out of Fragments and ViewModels into one process-scoped composition root while preserving every existing user flow and business rule.

This architecture does not redesign the device menu protocol, persistence schemas, navigation graph, visual system or feature behavior.

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
5. Feature migration is vertical and behavior-preserving; completed boundaries are protected by CI guards.

## Dependency inventory

### Auth/session/account security

- `AuthRepository`, `LogoutManager`, account deletion and Google identity clients were previously created from UI contexts.
- Reauthentication and feedback screens accessed Firebase directly.
- Auth ViewModels depended on the concrete repository.

### Preferences/profile/settings

- Theme, language, application settings, profile edit and usage screens previously resolved `UserPreferencesManager` directly.
- Several settings screens invoked session or account managers directly.

### Aquarium and maintenance

- Aquarium ViewModels previously constructed tank and assignment repositories.
- Tank detail and care-task screens previously created DataStore managers or repository providers from UI.

### Devices and provisioning

- Devices, QR handoff, tank-device selection, device status and menu-root ViewModels previously resolved process providers directly.
- These dependencies are injected without redesigning the typed device-menu architecture.

### Feedback

- Feedback UI depends only on `FeedbackSubmissionUseCase` and renderable state.
- Firebase Auth and Firestore stay behind `FirebaseFeedbackRepository`; local photo processing
  belongs exclusively to profile and aquarium flows.

## Completed architecture workstreams

### Composition root and authentication

- Established `AppContainer` and `DefaultAppContainer`.
- Added application contracts for auth, session exit and account security.
- Added platform/data adapters.
- Constructor-injected auth ViewModels and migrated auth/settings entry points.
- Added fake-backed tests and architecture guards.

### Preferences, profile and settings

- Added application-facing preference/profile contracts.
- Centralized preference and profile dependency construction.
- Migrated theme, language, app settings, usage and profile edit surfaces.
- Preserved existing DataStore values and startup cache behavior.

### Aquarium and care

- Added aquarium/care application boundaries and feature factories.
- Injected tank and care dependencies into ViewModels and screens.
- Preserved owner scoping, repair behavior and reminder scheduling.

### Device registry, assignment and provisioning

- Added application-facing device access and assignment boundaries.
- Injected owner-scoped dependencies into existing ViewModels.
- Preserved lifecycle, owner isolation, provisioning and runtime contracts.

### Feedback vendor isolation

- Firebase Auth and Firestore calls stay behind the feedback application contract.
- Missing authentication fails closed before a document ID or write is created.
- The bounded image processor is available only to profile and aquarium photo consumers.
- Fake-backed submission tests cover fail-closed and retry behavior.

### Final architecture enforcement

- Removed remaining direct repository, manager and provider creation from UI and ViewModels.
- Enforced UI/application/data/platform access rules in CI.
- Required fake-backed tests for every migrated application boundary.
- Completed the construction-site inventory with zero unmanaged UI creation sites.

## Validation baseline

1. architecture, session, navigation and composition guards;
2. Debug unit tests;
3. Debug lint and APK build;
4. Release unit tests and lint;
5. minified Release build;
6. CodeQL;
7. API 27 and API 35 emulator tests;
8. targeted physical regression for affected flows.

## Completion baseline

The architecture is considered complete only while all of the following remain true:

- one central composition root owns concrete application wiring;
- Fragments and ViewModels do not construct repositories, managers or providers;
- UI has no direct Firebase Auth or Firestore access;
- ViewModels receive dependencies through constructors and factories;
- application contracts are Android/vendor independent;
- fake implementations drive migrated ViewModel and use-case tests;
- architecture guards prevent regression;
- automated and physical behavior gates pass for release candidates;
- no known critical or high regression remains.
