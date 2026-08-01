# AquaLight WS v1 — Stage 00 CI Entry

This file is non-executable operational documentation.

Purpose:

- Trigger the repository's existing Android CI and emulator workflows for Stage 00.
- Record that no custom runtime guard or test is introduced.
- Keep provisioning, discovery, WebSocket transport/crypto and online/offline runtime code unchanged.

Expected existing checks:

- Android CI
- Existing commercial and architecture guards
- Existing unit, golden and WebSocket protocol tests
- Lint, Detekt and coverage gates
- Debug APK build
- Emulator API 27 and API 36
- CodeQL

Detailed results are recorded in `docs/AQL_WS_V1_STAGE_00_BASELINE.md`.
