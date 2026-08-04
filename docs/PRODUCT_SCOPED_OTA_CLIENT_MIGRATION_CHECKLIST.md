# Product-Scoped OTA Client Migration Checklist

Status: implementation branch `agent/product-scoped-ota-client`.

This checklist is the Android release gate for consuming one signed `aql.ota.manifest.v1` channel manifest per commercial product.

## Application and presentation boundary

- [x] Remove the global `DEVICE_FIRMWARE_MANIFEST_URL` constant.
- [x] Delete `DeviceFirmwareManifestConfig.kt`.
- [x] Remove manifest URL constructor arguments from `DeviceFirmwareUpdateViewModel`.
- [x] Remove manifest URL constructor arguments from `DeviceFamilySettingsViewModel`.
- [x] Remove caller-provided manifest URLs from `DeviceFirmwareUpdateOperations`.
- [x] Introduce typed `DeviceFirmwareChannel` values for stable, beta and dev.
- [x] Keep stable as the explicit application default.
- [x] Verify UI tests receive no URL and request the stable channel.

## Authenticated product channel resolution

- [x] Resolve the channel URL in the data layer.
- [x] Use only authenticated immutable `productKey`.
- [x] Reject runtime metadata that is not authenticated and current.
- [x] Reject unsafe productKey values before network access.
- [x] Map `productKey.lowercase(Locale.ROOT)` to the product environment.
- [x] Ignore owner custom name, display name and presentation model labels.
- [x] Resolve canonical URLs as `channels/<channel>/<environment>.json`.
- [x] Add independent WRGB and Dose Pro 4 resolver coverage.

## HTTP source and signed path agreement

- [x] Remove support for `releases/latest/download/manifest-*.json`.
- [x] Remove support for versioned release manifests as channel entry points.
- [x] Accept only HTTPS `raw.githubusercontent.com` product channel paths.
- [x] Require the official owner, repository, branch and channels namespace.
- [x] Reject query strings, fragments, credentials, non-default ports and non-canonical paths.
- [x] Reject unsupported channels and unsafe environment names.
- [x] Verify the signed manifest channel matches the channel path.
- [x] Verify the signed manifest contains exactly one artifact.
- [x] Verify the signed artifact environment matches the product path.

## Planner and immutable release validation

- [x] Keep schema `aql.ota.manifest.v1`.
- [x] Require exactly one artifact in a product channel manifest.
- [x] Require exact productKey, productId, family, line, model and hardware revision.
- [x] Require capabilities and limits to match authenticated firmware metadata.
- [x] Require product release tag `<env>-v<version>`.
- [x] Require OTA filename `AquaLight-<env>-v<version>-ota.bin`.
- [x] Require the exact official immutable release URL.
- [x] Preserve SHA-256, size, format and OTA-slot compatibility validation.
- [x] Keep zero compatible artifacts fail-closed.
- [x] Keep multiple artifacts fail-closed.
- [x] Return `UpToDate` only after exact signed product validation and version comparison.

## Regression coverage

- [x] WRGB `1.0.1` against WRGB stable `1.0.1` returns `UpToDate`.
- [x] Dose Pro 4 `1.0.1` against Dose Pro 4 stable `1.0.2` returns `UpdateAvailable`.
- [x] Advancing Dose Pro 4 does not change WRGB channel resolution.
- [x] Global version-only release tags are rejected.
- [x] Global latest manifest URLs are rejected.
- [x] Multi-product channel manifests are rejected.
- [x] Owner custom names cannot change artifact or channel identity.
- [x] Add an architecture guard that forbids OTA source strings in UI/application layers.
- [x] Run the architecture guard through CI Python test discovery.

## Automated validation

- [ ] Android commercial policy and architecture guards pass.
- [ ] Debug and staging unit tests pass.
- [ ] Detekt zero-new-debt policy passes.
- [ ] Debug and staging Android Lint pass.
- [ ] Debug APK assembles.
- [ ] API 27 and API 36 emulator integration workflows pass.
- [ ] CodeQL completes with no new critical/high finding.

## Cross-repository contract gate

- [ ] Firmware product-scoped release branch is merged first.
- [ ] Android product-scoped client branch is rebased on its final target main.
- [ ] Firmware and Android agree on `<env>-v<version>` release tags.
- [ ] Firmware and Android agree on `AquaLight-<env>-v<version>-ota.bin` filenames.
- [ ] Firmware and Android agree on official release repository and TLS policy.
- [ ] Firmware and Android retain exact product and hardware compatibility checks.

## Physical commercial gate

- [ ] Publish WRGB and Dose Pro 4 fixtures to a non-stable channel.
- [ ] Verify WRGB reports current while Dose Pro 4 has a newer release.
- [ ] Verify Dose Pro 4 downloads only its own binary.
- [ ] Verify wrong-product, wrong-hardware and wrong-channel manifests fail safely.
- [ ] Verify OTA progress, restart, UDP rediscovery and WebSocket reauthentication.
- [ ] Verify installed target version after restart.
- [ ] Verify factory identity, settings and schedules survive OTA.
- [ ] Promote to stable only after physical acceptance.
