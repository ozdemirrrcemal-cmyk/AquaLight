# Product-Scoped OTA Firmware Handoff

Firmware implementation branch:

```text
ozdemirrrcemal-cmyk/AquaLight-Firmware@agent/product-scoped-ota-channels
```

The Android client consumes this exact firmware/release contract:

```text
schema: aql.ota.manifest.v1
channel path: channels/<channel>/<env>.json
release tag: <env>-v<version>
OTA filename: AquaLight-<env>-v<version>-ota.bin
```

Merge and validation order:

1. Firmware/product-channel PR passes and merges first.
2. Android client is rebased if required.
3. Android PR passes and merges.
4. Both repositories run the non-stable physical OTA gate together.
5. Stable product channels advance only after physical acceptance.
