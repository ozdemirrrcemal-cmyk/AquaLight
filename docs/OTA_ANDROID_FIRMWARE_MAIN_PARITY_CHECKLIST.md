# Android ↔ Firmware `main` OTA Parity Checklist

## Scope and payload pin

The OTA payload contract was extracted from these authoritative revisions:

- Android baseline: `ozdemirrrcemal-cmyk/AquaLight@main` — `6b8118d85c17f2c8f5bb4723679d52f930f9ce80`
- Firmware payload baseline: `ozdemirrrcemal-cmyk/AquaLight-Firmware@main` — `38e8812c1bcecf948ebab85979bff21a24f4b79c`

Firmware `main` subsequently advanced to `37dc7a4176e3550e05fc5efcb1bf8e26f60b9ec5` through the OTA TLS trust hardening merge. That merge changed only the TLS trust bundle, its policy/guard, and firmware CI; the OTA command, event, snapshot, manifest, and failure payload sources pinned by this checklist remain unchanged.

Deleted branches, superseded branches, local-only commits, and legacy contracts are explicitly out of scope.

## Confirmed Android defects and contract gaps

- [x] **OTA-001 — Separate WebSocket action names from fully qualified firmware event names.** Android previously reused `ota.progress` / `ota.completed` both as top-level event actions and as response payload values. Firmware response payloads emit `firmware.ota.progress` / `firmware.ota.completed`.
- [x] **OTA-002 — Align `firmware.status.get` OTA metadata parsing.** Validate `progressEvent` and `completedEvent` against the fully qualified firmware names.
- [x] **OTA-003 — Align `firmware.ota.status` response parsing.** Validate `progressEvent` and `completedEvent` against the fully qualified firmware names.
- [x] **OTA-004 — Align `firmware.ota.start` acceptance parsing.** Validate `event`, `progressEvent`, and `completedEvent` against the fully qualified firmware names without changing top-level WebSocket routing.
- [x] **OTA-005 — Correct regression fixtures that encoded the Android-invented short payload names.** Status and OTA-start parser tests now use the exact values emitted by firmware `main`.
- [x] **OTA-006 — Accept signed firmware transport error codes in OTA snapshots.** Firmware stores the signed `HTTPClient::GET()` result in `httpStatus`; transport failures may be negative and are valid diagnostics, not protocol corruption.
- [x] **OTA-007 — Classify non-positive OTA HTTP diagnostics as retryable transport/download failures.** Preserve negative firmware diagnostics through the typed failure model.
- [x] **OTA-008 — Remove the Android-only `finishedAtMs > 0` terminal-state assumption.** Firmware owns the monotonic millisecond value and zero is representable at the wire level.
- [x] **OTA-009 — Match firmware release-note control-character validation.** The signed firmware pipeline rejects C0 controls; Android no longer rejects otherwise valid signed C1/DEL characters through a broader `isISOControl` rule.
- [x] **OTA-010 — Add an executable OTA response-payload parity fixture.** Cover command metadata names, full event names, snapshot fields, event-only fields, phase values, request fields, and signed `httpStatus` semantics.
- [x] **OTA-011 — Extend the interoperability guard to fail closed on OTA payload contract drift.** Existing command/event registry pins did not inspect `event`, `progressEvent`, or `completedEvent` response values.
- [x] **OTA-012 — Add parser regression coverage for negative `httpStatus`, terminal timestamp zero, and exact full event names.**
- [x] **OTA-013 — Keep a newly prepared signed update available when the recovery probe returns an old terminal `failed` snapshot.** A historical device failure has no request correlation to the new installation attempt and must not replace `UpdateAvailable` with a stale error. Active and succeeded snapshots continue through normal recovery handling.
- [x] **OTA-014 — Preserve the exact post-start firmware download failure class.** Android now distinguishes ESP32 HTTPClient diagnostics (`-1` through `-11`), HTTP redirects, access denial, missing release assets, rate limiting, server failures, URL-open failures, interrupted streams and size mismatches instead of collapsing all of them into `DOWNLOAD_FAILED`.
- [x] **OTA-015 — Remove false generic network claims from the failure screen.** The headline now states that OTA stopped safely, while the detail line renders the specific structured firmware cause and recovery guidance.

## Verified compatible surfaces

- [x] `firmware.ota.start` request fields remain exactly: `url`, `version`, `sha256`, `expectedSize`, `applyNow`, `productKey`, `productId`, `model`, `hardwareRevision`, `allowInsecureHttp`.
- [x] Top-level authenticated event routing remains `module=firmware`, `action=ota.progress|ota.completed`.
- [x] OTA snapshot keys remain exactly aligned with firmware.
- [x] OTA event-only keys remain exactly aligned with firmware.
- [x] OTA phase wire values remain exactly aligned with firmware.
- [x] Final signed manifest root, platform, release-notes, artifact, product, compatibility, firmware, factory, and signature structures remain exactly aligned.
- [x] Android continues to use the emitted `binaryTransfer=firmware-download` value. Firmware's separate stale `WS_OTA_BINARY_TRANSPORT="url-download"` constant is not normalized on Android.

## Completion gate

- [x] All identified checklist items are implemented on this branch.
- [x] Focused OTA parser/model/failure/coordinator tests pass on head `29ce882f44a2bda4e388238d17f16c2b348cf5f2`.
- [x] Interoperability and OTA payload parity guards pass on the same head.
- [x] Android CI, API 27/API 36 emulator integration, installable Debug APK, and CodeQL workflows all completed successfully.
- [x] No fallback aliases or permissive legacy schema are introduced.
- [x] User-facing failure copy is localized from structured authenticated firmware diagnostics rather than guessing every OTA failure is a network outage.
