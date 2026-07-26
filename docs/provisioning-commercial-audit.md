# Provisioning Commercial Audit

## Scope

This audit records the provisioning application and platform boundary work delivered through PR #36 and finalized in the cumulative commercial release candidate merged through PR #37.

The audit evaluates whether provisioning is commercially closed without temporary compatibility paths, UI-owned credentials, incomplete transaction semantics or platform lifecycle leaks.

## Commercial acceptance standard

The provisioning architecture remains approved only while all of the following are true:

- exactly one production provisioning path remains;
- QR and Nearby Scan behavior preserve the accepted firmware contract;
- UI depends on application boundaries rather than data, GATT or vendor implementations;
- Wi-Fi, QR claim and runtime token material never persist in plaintext;
- owner isolation holds across account changes, account deletion and process recreation;
- cancellation and failure never create a ghost device or duplicate registration;
- final registration commit is recoverable if Android kills the process between durable writes;
- QR decoder and camera frame lifecycle are race-safe and idempotently closable;
- production and release-smoke composition remain behaviorally aligned;
- architecture guards, Debug/Release tests, lint, minified release builds, CodeQL and API 27/API 36 emulator gates pass for affected release candidates;
- targeted physical regression passes before release approval.

## Findings discovered and closed during audit

### 1. Final commit process-death window

Risk: during existing-device reconfiguration, Android could terminate the process after the runtime token became durable but before the verified device snapshot completed its durable commit. Startup orphan cleanup covered a new-device orphan, but an existing device could retain a token/snapshot generation mismatch.

Closure:

- added an Android Keystore-backed `ProvisioningCommitRecoveryStore`;
- journaled the verified snapshot and runtime token before final commit begins;
- added idempotent owner-session startup recovery;
- added API 27/API 36 instrumentation for recovery, owner isolation and plaintext absence;
- added a CI architecture guard for commit ordering and startup recovery sequencing.

Status: closed and validated.

### 2. Runtime token crossing the application/UI boundary

Risk: the runtime WebSocket token was represented in an application handoff DTO and could reach ViewModel/UI memory even though persistence was encrypted.

Closure:

- replaced the credential-bearing handoff with an opaque `handoffId` reference;
- retained the concrete token only inside `DefaultProvisioningProgressOperations`;
- added mapping and boundary tests proving the application/UI representation contains no runtime token;
- added CI guards preventing token fields from reappearing in application or UI code.

Status: closed and validated.

### 3. QR claim material crossing SafeArgs/UI

Risk: `claimCode` and raw QR payload were passed through ViewModel events and navigation arguments.

Closure:

- introduced owner-scoped, TTL-limited, Android Keystore-backed QR secret storage;
- replaced claim/raw payload navigation fields with a random opaque secret reference;
- resolved the secret only inside the data-layer draft adapter;
- retained the short-lived secret for a same-flow credential retry and removed it through expiry, abandonment or owner cleanup;
- cleared QR secrets during authoritative account deletion;
- added API instrumentation for process recreation, expiry, owner isolation and plaintext absence;
- added CI guards preventing secret navigation arguments or UI fields from returning.

Status: closed and physically validated with wrong-password retry through both QR and Nearby Scan flows.

### 4. Account deletion provisioning cleanup gap

Risk: known devices and credentials were deleted, but encrypted provisioning sessions and transaction journals were not originally part of the authoritative local-data cleaner.

Closure:

- added provisioning draft, QR secret and commit-journal cleanup to `UserDataCleaner`;
- made provisioning cleanup exhaustive so failure of one cleanup action does not skip the remaining actions;
- retained combined error reporting for post-delete diagnostics.

Status: closed and validated.

### 5. QR decoder close/process race

Risk: camera shutdown could race with `scanner.process()`, allowing a synchronous vendor exception before the frame was closed.

Closure:

- made decoder close idempotent with an atomic closed state;
- closed frames on synchronous and asynchronous failures;
- prevented processing after decoder closure;
- locked lifecycle invariants with the discovery guard.

Status: closed and validated.

## Temporary solution and compatibility-path review

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

## Final validation result

The final release-candidate head `5e46bd12a01bd190cd1acbd17e342ae464d6bf6e` passed the required automated checks and physical regression T01–T18. The physical matrix covered QR, Nearby Scan, setup-mode recovery, wrong Wi-Fi retry, Back/cancel, power interruption, network loss, process recreation, account isolation, duplicate/ghost prevention, reset/reconfiguration, tank/device cleanup and clean reinstall.

The cumulative architecture was approved and merged to `main` as commit `cddcf15865ad2ab2a1ba3ebdfb592cef2ba0ddbe` with no known critical or high defect.
