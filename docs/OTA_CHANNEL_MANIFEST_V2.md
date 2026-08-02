# OTA Channel Manifest v2

Status: Android/firmware integration contract.

## Purpose

`aql.ota.manifest.v2` is a signed, cumulative channel catalog. Each artifact
owns its product identity, platform metadata, release version/tag, localized
release notes, OTA binary, and optional factory bundle.

The channel catalog may contain one entry per released product environment. A
firmware release builds only the selected product(s); it preserves the latest
signed entries for all unrelated products without rebuilding their binaries or
changing their versions.

## Android result mapping

| Condition | Result |
|---|---|
| One exact product/hardware artifact with a newer version | Update available |
| One exact artifact with the same or older version | Up to date |
| No exact artifact for this device | No update published; show up to date |
| More than one exact artifact | Fail closed as an ambiguous catalog |
| HTTP, signature, schema, hash, or integrity failure | Recoverable update-check failure |

An absent artifact is normal channel content. It must never be converted to the
generic `Try again` state.

## Exact compatibility identity

Android selects an artifact only when all of these values match authenticated
runtime metadata and the commercial catalog:

```text
env
productKey
productId
family
line
model
hardwareRevision
```

The selected artifact's product capabilities and limits must also equal the
Android commercial catalog. Duplicate environments, duplicate compatibility
identities, unknown fields, coercions, unofficial URLs, wrong filenames, and
unsupported OTA formats are rejected.

## Publication and rollout

`manifest-<tag>.json` is the immutable release-batch record.
`manifest-<channel>.json` is the cumulative channel catalog. Both documents are
canonicalized and signed independently.

The firmware publisher must deploy a valid v2 stable release before an Android
build requiring v2 is promoted. The physical signed-OTA acceptance gate remains
mandatory; parser and emulator tests do not replace a real-device install,
restart, reconnect, and post-boot version check.

## Cross-repository drift lock

Android and firmware repositories carry the same
`firmware-channel-manifest-v2.json` golden document. Both assert the canonical
unsigned payload hash:

```text
acb62ebf6bb7e90bde2ff1afae183b2622a95da9c95b3c752c6179ccae3b1fe6
```

Changing either producer or consumer contract therefore requires an explicit
schema/golden migration in both repositories.
