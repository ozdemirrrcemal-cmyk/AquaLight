# AquaLight WS v1 Commercial Closure

Date: 2026-08-02
Base: `main` at `b11bef019a4b93a5c1c34539705512758d1f29a6`
Branch: `chore/ws-v1-commercial-closure`

## Objective

Close the WS v1 migration without parallel command paths, permissive compatibility parsers,
raw-message consumers outside the transport/router boundary, tracked build output, or obsolete
placeholder files. This closure follows the Stage 00–10 migration and the commercial OTA update
experience merged by PR #193.

## Production decisions

1. All authenticated application commands use `DeviceRuntimeCommandGateway` and correlated
   firmware outcomes. A queued WebSocket write is never accepted as liveness or command success.
   Menu and presence proof writes commit only while the outcome's originating connection generation
   remains current and authenticated.
2. Raw `AqlWsEvent` messages remain an internal transport primitive only. Module coordinators
   consume generation-validated typed events and a lifecycle-only projection.
3. Time status, firmware status and UDP discovery accept only their exact firmware schemas. No
   alias, scalar coercion, casing/whitespace normalization, missing-field default or extra-key
   compatibility path is retained.
4. OTA progress is accepted only through the typed event router. The detached raw OTA mapper is
   removed rather than kept as a fallback.
5. Repository build output, placeholder `.gitkeep` files in populated source directories, and
   temporary patch/archive artifacts are forbidden by an executable closure guard.
6. Physical signed-OTA validation cannot be simulated or inferred from CI. It is the only manual
   commercial release gate and remains open until evidence from a real supported device exists.

## Automated closure scope

- remove the unused menu raw-event port and raw response proof policy;
- migrate authenticated presence probes to correlated `network.status.get` outcomes;
- bind menu and presence proof writes atomically to the originating connection generation;
- cancel and deduplicate device-scoped liveness jobs on background and local-route changes;
- remove the legacy `AqlWsCommandClient` and its repository accessors;
- project authenticated/unavailable runtime lifecycle events without exposing wire messages;
- remove permissive time and firmware-status parsing and enforce the exact UDP packet shape;
- remove the detached OTA raw event mapper;
- remove tracked Gradle state and obsolete source-directory placeholders;
- align migration tracker state with PR #192 and PR #193;
- add CI/release guard coverage with explicit raw-event and transport boundary allowlists.

## Canonical physical signed-OTA release gate

The following steps must use a signed artifact selected for the device's exact commercial identity.
Record the device family/model, hardware revision, source version, target version, manifest tag,
artifact SHA-256, test timestamp, and redacted evidence references.

- [ ] Settings proves both no-update and update-available states.
- [ ] A signed OTA-capable device downloads and verifies the selected artifact.
- [ ] Real byte/progress phases and the background notification agree.
- [ ] Background navigation, restart, UDP rediscovery, authenticated reconnect, and recovery pass.
- [ ] Menu/presence proof never survives a Wi-Fi route or WebSocket generation replacement.
- [ ] The reported installed version equals the exact target; terminal and recoverable failures
      show the correct actions.

The release is blocked while any item above is open. The code migration may merge independently so
that the acceptance test runs against the exact release-candidate implementation.

## Automated gate evidence

- Tested implementation head: `43eb1c6481530d384b814c2c7a21617aa6df7ac2`
- Tested source tree: `08008db21f0d406345593a1a0aa37c48ff3ff862`
- Android CI: #2925
- Installable Debug APK: #216
- CodeQL Security Scan: #4710
- Android Emulator Integration Tests: #1047 (API 27 and API 36)
- Firebase Repository Configuration Guard: #217
- Dependency integrity: not triggered because no dependency input or lock contract changed

## Definition of done

- all repository Python tests and architecture/protocol/OTA guards pass;
- Detekt reports zero blockers and zero new advisory debt;
- Debug/Staging/ReleaseSmoke/Release compile, unit, lint, and packaging gates pass in CI;
- API 27 and API 36 instrumentation plus minified smoke pass;
- the closure branch leaves no forbidden tracked artifact or obsolete compatibility symbol;
- exact UDP/firmware parser and generation-bound proof regressions pass;
- raw WebSocket event and transport consumers remain inside the explicit boundary allowlists;
- PR evidence records the tested head SHA;
- physical signed-OTA evidence is attached before commercial release approval.
