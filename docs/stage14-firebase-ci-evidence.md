# Stage 14 — Firebase CI Evidence

Status: pending

This file records pull-request validation evidence for the fail-closed Firebase environment migration.

## Required checks

- [ ] Android CI completes without a tracked `google-services.json`.
- [ ] Debug, Staging and Release Smoke configuration materialization succeeds in CI.
- [ ] Android Lint uses `lintReleaseSmoke` in public CI.
- [ ] CodeQL unit test, lint and minified build use the Release Smoke variant.
- [ ] Emulator release-smoke preparation succeeds without the Production Firebase secret.
- [ ] Production release configuration remains unavailable outside the protected release job.
- [ ] Production isolation rejects a missing Debug, Staging or Production configuration.
- [ ] Production isolation accepts three valid and distinct protected Firebase projects.

Evidence will be filled from the pull-request workflow runs before this migration is marked complete.
