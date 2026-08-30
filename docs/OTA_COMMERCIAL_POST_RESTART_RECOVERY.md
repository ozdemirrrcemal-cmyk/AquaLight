# OTA Commercial Post-Restart Recovery

## Reviewed revisions

- Android base: `agent/centralized-device-open-preparation-android-20260829` at
  `317eaee2ee4213d8bb21f961c0b9a62c35e21aed`.
- Rollback-capable firmware: `AquaLight-Firmware/main` at
  `e669313ecc2a7f959b566e3051cfd3b67247ccbd`.
- The firmware WebSocket and signed-manifest schemas are unchanged. Android continues to verify
  the existing authenticated runtime metadata rather than introducing a new wire field.
- Physical failing-image, healthy-image, rollback/quarantine, and post-restart timeout/retry
  acceptance was confirmed by the release operator on 2026-08-30 for this commercial integration.
  The protected production release pipeline remains responsible for recording its required manual
  acceptance artifact before finalizing customer release bytes.

## Android recovery contract

The exact selected plan is synchronously journaled before `firmware.ota.start` can be dispatched.
The Android Keystore-backed encrypted journal is isolated by authenticated owner and device and
contains no device credentials. A pending attempt is restored when the Android process is
recreated.

After the device becomes unavailable, Android performs a UDP discovery refresh and a
device-scoped runtime replacement. Attempts are spaced 30 seconds apart so that one WebSocket
attempt can use the existing 8-second connection, 5-second authentication, and 10-second metadata
budgets without being destroyed by the next retry. The complete recovery window is 120 seconds.

| Authenticated post-restart result | Android terminal state | Release policy |
| --- | --- | --- |
| Exact target version and product identity | `Succeeded` | Clear the active journal. |
| Exact previous version and product identity | `RolledBack` | Clear the active journal and quarantine the rejected tag/SHA identity. |
| No proof within 120 seconds | `PostRestartTimeout` | Keep the journal and offer an explicit bounded reconnect retry. |
| Another version or product identity | `UnexpectedFirmware` | Stop automatic recovery and direct the user to support. |

The quarantine matches device, product, hardware revision, target version, manifest tag, and SHA-256.
The exact rejected release cannot be installed again on that device. A genuinely different signed
artifact remains eligible.

## Release acceptance

Automated Android acceptance requires unit tests, static-analysis/architecture guards, Android
lint, Debug/Staging builds, emulator integration, and installable APK evidence on one exact commit.

Commercial release also requires physical evidence because CI cannot prove ESP32 bootloader
behavior. The acceptance procedure is:

1. USB-flash the rollback-capable full factory image.
2. Install a deliberately failing signed OTA image and verify Android reports `RolledBack` with the
   previous version online.
3. Verify the same tag/SHA is not offered again.
4. Install a healthy signed OTA image and verify Android reports `Succeeded` only after the target
   version reconnects with authenticated metadata.
5. Power off or isolate the test device after restart and verify `PostRestartTimeout` appears after
   120 seconds with an enabled reconnect action.

These checks do not change the normal stable-release workflow. Their completion must still be
represented by the protected manual-acceptance evidence consumed by the production finalize job.
