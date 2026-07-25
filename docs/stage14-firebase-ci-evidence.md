# Stage 14 — Firebase CI Evidence

Status: pending  
Commit: to be recorded after all required workflows complete

## Required automated evidence

- [ ] Repository contains no tracked `google-services.json`.
- [ ] Missing Production configuration is rejected fail-closed.
- [ ] Debug, Staging and Release Smoke CI identities are distinct.
- [ ] Three valid Debug, Staging and Production contract identities are accepted.
- [ ] Android CI passes without Production Firebase access.
- [ ] Public CodeQL uses the Release Smoke commercial variant.
- [ ] Blocking release CodeQL preserves real Release analysis and alert evidence.
- [ ] Dependency locking and verification metadata pass.
- [ ] Non-debuggable minified Release Smoke APK builds successfully.
- [ ] API 27 instrumentation and release smoke pass.
- [ ] API 36 instrumentation and release smoke pass.

## Protected infrastructure activation

These values must exist in the `production-release` GitHub Environment before an actual commercial release is attempted:

- `AQL_FIREBASE_DEBUG_CONFIG_BASE64`
- `AQL_FIREBASE_STAGING_CONFIG_BASE64`
- `AQL_FIREBASE_PRODUCTION_CONFIG_BASE64`

The final evidence record will include workflow run numbers and artifact names after all checks pass on one commit.
