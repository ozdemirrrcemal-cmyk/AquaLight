# Stage 3 Provisioning Commercial Audit

## Scope

Branch: `feature/stage-3-provisioning-platform-boundaries`

PR: `#36 Stage 3: close provisioning application and platform boundaries`

This audit evaluates whether the provisioning migration is commercially closable without temporary compatibility paths, UI-owned credentials, incomplete transaction semantics, or platform lifecycle leaks.

## Commercial acceptance standard

The branch is approved only when all of the following are true:

- exactly one production provisioning path remains;
- QR and Nearby behavior preserve the accepted firmware contract;
- UI depends on application boundaries rather than data/GATT/vendor implementations;
- Wi-Fi, QR claim and runtime token material never persist in plaintext;
- owner isolation holds across account changes, account deletion and process recreation;
- cancellation and failure never create a ghost device or duplicate registration;
- final registration commit is recoverable if Android kills the process between durable writes;
- QR decoder and camera frame lifecycle are race-safe and idempotently closable;
- production and release-smoke composition remain behaviorally aligned;
- all architecture guards, Debug/Release tests, lint, minified release builds, CodeQL and API 27/API 35 emulator gates pass on the final head;
- targeted physical regression passes before merge.

## Findings discovered and closed during audit

### 1. Final commit process-death window

Risk: during existing-device reconfiguration, Android could terminate the process after the runtime token became durable but before the verified device snapshot completed its durable commit. Startup orphan cleanup covered a new-device orphan, but an existing device could retain a token/snapshot generation mismatch.

Closure:

- added an Android Keystore-backed `ProvisioningCommitRecoveryStore`;
- journaled the verified snapshot and runtime token before final commit begins;
- added idempotent owner-session startup recovery;
- added API 27/API 35 instrumentation for recovery, owner isolation and plaintext absence;
- added a CI architecture guard for commit ordering and startup recovery sequencing.

Status: closed in code; final CI and physical regression remain required.

### 2. Runtime token crossing the application/UI boundary

Risk: the runtime WebSocket token was represented in an application handoff DTO and could reach ViewModel/UI memory even though persistence was encrypted.

Closure:

- replaced the credential-bearing handoff with an opaque `handoffId` reference;
- retained the concrete token only inside `DefaultProvisioningProgressOperations`;
- added mapping and boundary tests proving the application/UI representation contains no runtime token;
- added CI guards preventing token fields from reappearing in application or UI code.

Status: closed in code; final CI remains required.

### 3. QR claim material crossing SafeArgs/UI

Risk: `claimCode` and raw QR payload were passed through ViewModel events and navigation arguments.

Closure:

- introduced owner-scoped, TTL-limited, Android Keystore-backed QR secret storage;
- replaced claim/raw payload navigation fields with a random opaque secret reference;
- resolved and consumed the secret only inside the data-layer draft adapter;
- discarded blocked or abandoned QR secrets immediately;
- cleared QR secrets during authoritative account deletion;
- added API instrumentation for process recreation, expiry, owner isolation and plaintext absence;
- added CI guards preventing secret navigation arguments or UI fields from returning.

Status: closed in code; final CI and physical QR regression remain required.

### 4. Account deletion provisioning cleanup gap

Risk: known devices and credentials were deleted, but encrypted provisioning sessions and transaction journals were not originally part of the authoritative local-data cleaner.

Closure:

- added provisioning draft, QR secret and commit-journal cleanup to `UserDataCleaner`;
- made provisioning cleanup exhaustive so failure of one cleanup action does not skip the remaining actions;
- retained combined error reporting for post-delete diagnostics.

Status: closed in code; final CI remains required.

### 5. QR decoder close/process race

Risk: camera shutdown could race with `scanner.process()`, allowing a synchronous vendor exception before the frame was closed.

Closure:

- made decoder close idempotent with an atomic closed state;
- closed frames on synchronous and asynchronous failures;
- prevented processing after decoder closure;
- locked lifecycle invariants with the discovery guard.

Status: closed in code; final CI and physical camera regression remain required.

## Temporary solution / compatibility-path review

No legacy fallback or parallel production provisioning path is accepted.

Current intended production shape:

- one discovery adapter;
- one draft adapter;
- one progress transaction adapter;
- one QR platform decoder factory;
- one owner-scoped encrypted session path;
- one owner-scoped credential path;
- one final commit recovery path.

The obsolete UI provisioning operations contract and obsolete composition adapter are deleted. The audit found no justified reason to preserve a second path.

## Remaining merge gates

Commercial approval is not yet issued. The final head must pass:

1. architecture and navigation guards;
2. uncached Debug APK build;
3. Debug and Release unit tests;
4. Debug and Release lint;
5. Debug APK and minified signed Release APK;
6. CodeQL;
7. API 27 and API 35 instrumentation plus minified release-smoke;
8. physical QR, Nearby, setup-mode recovery, wrong Wi-Fi retry, Back/cancel, power interruption, process recreation, duplicate/ghost and account-isolation regression.

Merge is forbidden until the automated matrix and the physical matrix both pass and the commercial audit is updated with the final head SHA and explicit approval.
