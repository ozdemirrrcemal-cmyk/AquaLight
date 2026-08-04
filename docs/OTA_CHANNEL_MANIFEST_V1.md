# OTA Channel Manifest v1

## Status

`aql.ota.manifest.v1` is the only supported Android and firmware OTA manifest contract. The application has not been commercially released, so no earlier/later schema compatibility layer, alias, or fallback parser exists.

## Purpose

The stable, beta, and dev channel manifests are signed cumulative catalogs. Each exact product and hardware identity keeps its own latest published release metadata and immutable firmware URL.

A release for Dose Pro 4 therefore does not remove the previously published WRGB Pro Elite entry.

## Root shape

```text
schema
brand
channel
releaseRepo
generatedAt
artifacts
signature
```

Version, tag, platform, and localized notes are intentionally not global. They belong to each artifact.

## Artifact shape

```text
env
product
compatibility
platform
release
firmware
factory
```

The `release` object contains:

```text
version
tag
generatedAt
releaseNotes
```

## Exact selection

Android selects an artifact only when all of these authenticated values match:

```text
env
productKey
productId
family
line
model
hardwareRevision
```

Zero exact matches is normal: the channel has no published update for this device. Android reports the currently installed version as up to date instead of showing a catalog-validation error.

More than one exact match, a duplicate product environment, or a duplicate exact identity is ambiguous and fails closed.

## Authenticity and integrity

Before planning an update, Android verifies the canonical payload hash and ECDSA P-256 signature. It then validates exact root and nested keys, commercial catalog identity, capabilities, limits, platform values, release tag/version, immutable GitHub URL, SHA-256, size, format, and OTA-slot compatibility.

Every artifact must explicitly authorize OTA and the selected firmware URL must remain within the ESP32 production limit of 300 characters. The same rules are enforced by firmware publication before signing and again when a signed previous catalog is reused.

The ESP32 continues to validate the exact start-request identity, HTTPS/TLS connection, expected size, SHA-256, flash operation, restart, and installed target version.

## Publication behavior

Firmware release tooling verifies the previous signed channel catalog, replaces only the selected exact product identity, preserves unrelated products, rejects duplicate identities, duplicate environments, device-limit violations and non-increasing versions, signs tag and channel manifests independently, and never deletes or rewrites an existing release.

## Golden fixture

The Android fixture is:

```text
app/src/test/resources/ota/firmware-channel-manifest-v1.json
```

It must remain byte-contract compatible with the firmware fixture:

```text
protocol/fixtures/aql_ota_channel_manifest_v1.json
```
