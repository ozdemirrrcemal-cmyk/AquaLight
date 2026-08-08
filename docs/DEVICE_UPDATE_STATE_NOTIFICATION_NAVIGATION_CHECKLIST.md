# Device Update State, Notification, and Navigation Remediation Checklist

## Scope and baseline

- Branch: `fix/device-update-state-notification-navigation`
- Baseline: `main@0fd053b6c4a20151223d13f275a1a5b266fb417e`
- Scope: foreground firmware availability, OTA execution state, centralized device-update notifications, notification deep links, and firmware-update navigation.
- Out of scope: product-family-specific OTA forks, direct Android notification calls outside the central renderer, permissive firmware compatibility fallbacks, and changes to `main` before review.

## Tracking protocol

- [x] Create a dedicated branch from the recorded `main` baseline.
- [x] Keep every remediation change isolated from `main` until review and CI completion.
- [x] Record every logical implementation commit and its DUN scope before PR validation.
- [ ] Check a DUN item only after its implementation, focused tests, and relevant architecture guards pass.
- [ ] Do not merge until the final completion gate is fully checked.

Checkboxes mean:

- `[ ]` not yet fully verified;
- `[x]` implemented and verified on the recorded commit/branch head.

## Implementation commit sequence

### Commit 1 — Normalize firmware availability semantics

- [x] Implementation committed.
- [ ] Validation complete.
- Checklist scope: `DUN-001` through `DUN-003`.
- Commit: `2266a2f3d45389f1e05031fbc4210f0f75f7300f`
- Message: `Normalize firmware availability semantics`
- Evidence added: typed availability/execution failure stage, typed unpublished-manifest HTTP result, foreground/background no-artifact semantics, planner/repository/probe regressions.
- Remaining gate: PR unit tests, guards, Detekt, Android build, and instrumentation.

### Commit 2 — Correct firmware availability presentation

- [x] Implementation committed.
- [ ] Validation complete.
- Checklist scope: `DUN-004` and `DUN-005`.
- Commit: `cf4bc619beefc374953aee3858c0163ca9f6241a`
- Message: `Correct firmware availability presentation`
- Evidence added: stage-aware full-screen hero/progress copy, English/Turkish localized resources, presentation mapper regressions.
- Remaining gate: PR unit tests, resource validation, Detekt, and Android build.

### Commit 3 — Harden central firmware notification emission

- [x] Implementation committed.
- [ ] Validation complete.
- Checklist scope: `DUN-006` through `DUN-008`.
- Commit: `f4cda2e3f9da45827712047af39f10a8a9cc2464`
- Message: `Harden central firmware notification emission`
- Evidence added: central process-foreground authority, availability-failure suppression, foreground availability suppression, execution-only failure notifications, retry-safe semantic emission, instrumentation regressions.
- Remaining gate: PR unit/instrumentation tests, notification architecture guards, Detekt, and Android build.

### Commit 4 — Revalidate firmware notification routes

- [x] Implementation committed.
- [ ] Validation complete.
- Checklist scope: `DUN-009` and `DUN-010`.
- Commit: `8506c9fe43affb9154cc23f71d2b277ec79aefe5`
- Message: `Revalidate firmware notification routes`
- Evidence added: availability/operation notification kind, target-version metadata, fail-closed intent parsing, owner/device/OTA/actionability revalidation, route gate and intent regressions.
- Remaining gate: PR unit/instrumentation tests, route architecture guards, Detekt, and Android build.

### Commit 5 — Make firmware deep-link navigation idempotent

- [x] Implementation committed.
- [ ] Validation complete.
- Checklist scope: `DUN-011` and `DUN-012`.
- Commit: `d055d642a77981583126726fa0840549fc41d21d`
- Message: `Make firmware deep-link navigation idempotent`
- Evidence added: current destination/device identity policy, `launchSingleTop`, already-open consume result, semantic acknowledgement isolation, repeated-route regression.
- Remaining gate: PR navigation tests, Android build/instrumentation, and manual one-Back acceptance.

### Commit 6 — Add end-to-end regression coverage

- [x] Implementation committed.
- [ ] Validation complete.
- Checklist scope: `DUN-013` through `DUN-015` and the automated acceptance matrix.
- Commit: `aaf232ab2bbc43b00b771de1a3ffeb94667dd96b`
- Message: `Add end-to-end device update regression coverage`
- Evidence added: Dose Pro 4 no-artifact/no-release tests, real transport-failure test, background parity tests, stale route tests, five-retry/five-route coverage, strengthened OTA and notification acceptance guards.
- Remaining gate: full PR CI, Android instrumentation, debug APK installation, and manual hardware acceptance.

## Master remediation checklist

- [ ] **DUN-001 — Separate firmware availability outcomes from OTA execution failures at the application boundary.** Introduce an explicit typed origin/stage so a manifest or compatibility check failure cannot be interpreted as an installation failure. Do not infer the origin from diagnostic strings. Existing owner/device isolation and the shared OTA coordinator must remain intact.

- [ ] **DUN-002 — Treat the absence of a compatible published artifact as a normal no-update result.** A valid OTA-capable device with no matching published artifact must preserve its installed version and resolve to a non-error `NoPublishedRelease`/no-update semantic. Ambiguous matches, invalid metadata, signature failures, and incompatible artifact contents must remain hard failures.

- [ ] **DUN-003 — Distinguish an unpublished latest release from real transport or security failures.** A missing latest release or missing published manifest asset must produce the typed no-release outcome. Offline access, DNS/TLS failures, timeouts, rate limits, server failures, malformed manifests, unsupported schemas, and invalid signatures must remain structured check failures with diagnostics.

- [ ] **DUN-004 — Correct the Settings firmware action mapping.** Map a newer compatible version to `Update available`; map up-to-date or no-published-release outcomes to a neutral `Software up to date`/no-newer-update presentation; map only genuine technical check failures to `Retry`; and preserve distinct presentation for real OTA execution failures.

- [ ] **DUN-005 — Correct the full-screen firmware-update presentation.** Availability failures must not display copy implying that an installation was safely stopped. No-release and up-to-date outcomes must render as successful neutral states. Installation failure copy and recovery actions must be reserved for failures occurring after OTA execution actually started.

- [ ] **DUN-006 — Restrict centralized device-update notifications to valid notification states.** Foreground `Checking`, `UpToDate`, no-release, foreground `UpdateAvailable`, and availability-check failures must not create system notifications. Real OTA progress may update an ongoing operation notification, and only a real OTA execution failure may create an operation-failure notification.

- [ ] **DUN-007 — Apply foreground delivery policy centrally.** Suppress non-essential availability notifications while the application is foreground without adding UI-owned Android notification logic. Preserve centralized permission/preference enforcement and preserve necessary ongoing OTA operation visibility where Android lifecycle behavior requires it.

- [ ] **DUN-008 — Make notification deduplication semantic and retry-safe.** `Failed → Checking → same Failed` and repeated identical retries must not create repeated notifications. Dedupe must remain owner/device isolated, must not hide a genuinely changed failure or target version, and must survive the relevant durable availability-notification lifecycle.

- [ ] **DUN-009 — Add semantic identity to firmware notification routes.** A firmware notification intent must identify whether it represents availability or an OTA operation, together with the isolated owner/device identity and any required target/state identity. A plain device UID must no longer be sufficient to authorize navigation.

- [ ] **DUN-010 — Revalidate notification actionability immediately before navigation.** Verify authenticated owner continuity, repository readiness, device existence, OTA capability, notification kind, and the corresponding current actionable availability/operation state. Stale, deleted, owner-mismatched, unsupported, or non-actionable notifications must fail closed.

- [ ] **DUN-011 — Make firmware-update navigation idempotent.** Repeated taps for the same device must resolve to one firmware-update destination. Use single-top and explicit destination/device identity handling without breaking navigation from either the Devices or Aquarium graph.

- [ ] **DUN-012 — Harden notification consume and acknowledgement behavior.** Consume successful notification intents once, clear rejected stale intents, prevent repeated deferred attempts from stacking destinations, and preserve centralized availability dismissal without cancelling an active OTA operation.

- [ ] **DUN-013 — Keep foreground and background availability semantics equivalent.** Shared compatibility and no-release behavior must produce the same commercial result in manual/foreground checks and durable background discovery. Background no-update outcomes must clear stale availability notifications centrally; genuine newer versions must still produce one deduplicated background notification.

- [ ] **DUN-014 — Add executable regressions for every reported scenario.** Cover Dose Pro 4 with no published artifact, no latest release, real network/check failure, WRGB Pro Elite 120 with a genuine update, foreground notification suppression, execution-failure notification delivery, five retries without notification spam, five notification taps with one destination, and one Back action returning to the originating Settings screen.

- [ ] **DUN-015 — Re-run and strengthen architecture enforcement.** Prove that presentation does not access `NotificationManager`, WorkManager, the concrete Android renderer, firmware transport repositories, or family-specific OTA paths. Preserve the shared OTA coordinator, owner-scoped composition, central dispatch use case, central renderer, and commercial architecture guards.

## Required acceptance matrix

- [ ] Dose Pro 4 with no compatible published firmware shows a neutral no-update/up-to-date state and posts no notification.
- [ ] No GitHub latest release or manifest asset is treated as no published firmware, while real connectivity/security failures remain retryable or terminal check failures as appropriate.
- [ ] WRGB Pro Elite 120 with a genuine newer release shows `Update available` and does not post a foreground availability notification.
- [ ] A genuine background-discovered update posts one centralized, owner/device-isolated, deduplicated notification.
- [ ] Repeating the same failed check five times posts no OTA execution-failure notification and creates no notification spam.
- [ ] A real OTA execution failure posts the correct centralized operation-failure notification once.
- [ ] Tapping the same valid firmware notification five times leaves exactly one firmware-update destination in the back stack.
- [ ] One Back action from that destination returns to the correct originating Settings surface.
- [ ] A stale, deleted-device, unsupported-device, owner-mismatched, or non-actionable notification cannot open the firmware-update screen.
- [ ] Opening an availability notification dismisses only the visible availability notification and preserves target deduplication; it never cancels an active OTA operation notification.

## Final completion gate

- [ ] All `DUN-001` through `DUN-015` items are checked with commit evidence.
- [ ] Focused unit tests pass for domain, planner, coordinator, Settings, update screen, notification publisher, route policy, and navigation.
- [ ] Required Android instrumentation tests pass on the supported emulator/API matrix.
- [ ] Detekt and zero-new-debt checks pass.
- [ ] OTA, notification, composition-root, application-boundary, and commercial architecture guards pass.
- [ ] Debug APK builds and installs successfully.
- [ ] Pull-request CI and security checks are green.
- [ ] Manual acceptance matrix is reproduced on the branch head.
- [ ] `main` contains no remediation changes before approved merge.

## Progress log

```text
Commit: 2266a2f3d45389f1e05031fbc4210f0f75f7300f — Normalize firmware availability semantics
Scope: DUN-001, DUN-002, DUN-003
Tests added/updated: failure-stage, planner no-artifact, background probe, manifest HTTP, repository no-release
Guards: validation pending on PR CI
Notes: hard-failure behavior retained for ambiguous/malformed/incompatible release data

Commit: cf4bc619beefc374953aee3858c0163ca9f6241a — Correct firmware availability presentation
Scope: DUN-004, DUN-005
Tests added/updated: hero and progress presentation mappers
Guards: validation pending on PR CI
Notes: no-release reuses neutral UpToDate presentation; availability failure has separate check-failed copy

Commit: f4cda2e3f9da45827712047af39f10a8a9cc2464 — Harden central firmware notification emission
Scope: DUN-006, DUN-007, DUN-008
Tests added/updated: process lifecycle, central notification policy instrumentation
Guards: validation pending on PR CI
Notes: real OTA operation progress/failure notifications remain centralized and enabled

Commit: 8506c9fe43affb9154cc23f71d2b277ec79aefe5 — Revalidate firmware notification routes
Scope: DUN-009, DUN-010
Tests added/updated: intent contract, renderer intent, destination policy, route gate, actionability policy
Guards: validation pending on PR CI
Notes: missing/unknown semantic route metadata fails closed

Commit: d055d642a77981583126726fa0840549fc41d21d — Make firmware deep-link navigation idempotent
Scope: DUN-011, DUN-012
Tests added/updated: repeated same-device route idempotency
Guards: validation pending on PR CI
Notes: manual one-Back behavior remains an acceptance item until emulator/device verification

Commit: aaf232ab2bbc43b00b771de1a3ffeb94667dd96b — Add end-to-end device update regression coverage
Scope: DUN-013, DUN-014, DUN-015
Tests added/updated: Dose Pro 4 no artifact/no release, background parity, transport failure, stale route, five retries/routes
Guards: OTA coordinator and device-update notification acceptance guards strengthened; execution pending on PR CI
Notes: full Android build/instrumentation intentionally deferred to one PR validation gate
```
