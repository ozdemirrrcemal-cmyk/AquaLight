# Stage 6 — Central Android capability permission contract

## Product rule

A screen may know only which product action should run after access is granted. It must not derive API-level permission requirements, permanent-denial state, rationale state, settings destinations, permission copy, icons, or visual states.

Normal feature controls keep their product meaning. For example, the nearby-device button remains **Scan**. A permanent denial opens the common capability sheet; the feature button itself does not mutate into an unrelated settings-navigation control.

## Capability model

- `CAMERA_PHOTO`
- `CAMERA_QR`
- `BLE_SCAN`
- `BLE_CONNECT`
- `BLE_PROVISIONING`
- `WIFI_SSID`
- `NOTIFICATIONS`

`BLE_PROVISIONING` is the composite setup capability used where scanning and secure GATT configuration are one uninterrupted user operation. The individual scan/connect capabilities remain available for future typed device features.

## Standard decisions

`PermissionPolicy` is the only runtime-permission decision source and returns exactly one of:

1. `GRANTED`
2. `REQUEST`
3. `RATIONALE`
4. `OPEN_SETTINGS`

The decision combines the current API level, current grants, Android rationale state, and installation-scoped request history.

## Central visual-language contract

- `CapabilityPermissionUiSpecResolver` is the only authority allowed to map an `AppCapability` to an icon, rationale/settings title, message, primary action label, or blocked-state badge.
- Feature Fragments provide only the capability and the action token. They never provide drawable IDs, custom text, colors, or per-screen permission-sheet styles.
- `CapabilityPermissionBottomSheet` renders the resolved UI spec and contains no capability-specific `when` branch.
- Every capability has one explicit commercial icon: photo camera, QR scanner, BLE scan, BLE connection, BLE provisioning, Wi-Fi, and notifications.
- Every icon uses the same AquaLight permission surface, outline, scale, spacing, and turquoise technology accent.
- `OPEN_SETTINGS` uses the same capability icon plus one shared lock badge. `RATIONALE` uses the capability icon without the blocked badge.
- Permission colors and dimensions are centralized in shared bottom-sheet resource files. Existing non-permission bottom sheets keep their established root spacing and styles.
- Permission artwork is decorative because the adjacent title fully describes the capability; TalkBack does not announce duplicate icon labels.
- CI fails when a screen or XML layout references capability-permission artwork directly.

## UI and lifecycle contract

- Activity Result launchers are registered by a lifecycle-owner Fragment through `CapabilityPermissionCoordinator`.
- The rationale/settings sheet has a public no-argument constructor.
- Sheet input uses arguments only.
- Sheet output uses Fragment Result only.
- Pending work is a stable action token, never a lambda.
- Pending capability/action/settings state is saved through `SavedStateRegistry`.
- Returning from Android settings resumes the pending action only when access is actually granted.
- Returning without granting access clears the pending action and never loops the user back into settings.
- Configuration recreation inside the same process restores the current screen, bottom sheet, and pending token normally.
- A runtime-permission revocation can terminate the application process. UI state saved by that previous process must not restore an owner-scoped Fragment graph before the new owner runtime is committed.
- After process death, AquaLight rebuilds the authenticated session first and installs a clean navigation graph. An externally interrupted camera/BLE/Wi-Fi action is cancelled rather than being replayed automatically.

## API matrix

A permanent unit-test matrix covers API 27 through current API 37:

- Camera: `CAMERA`
- BLE scan, API 27–30: `ACCESS_FINE_LOCATION`
- BLE scan, API 31+: `BLUETOOTH_SCAN`
- BLE connect, API 31+: `BLUETOOTH_CONNECT`
- Connected Wi-Fi SSID: `ACCESS_FINE_LOCATION` for the current SSID implementation
- Notifications, API 33+: `POST_NOTIFICATIONS`

Bluetooth or Wi-Fi being switched off is a capability-readiness issue, not a runtime-permission decision, and must remain separate from `PermissionPolicy`.

## Stage closure gate

Stage 6 is complete only when every current camera, QR, BLE, Wi-Fi SSID, and notification runtime-permission path uses this contract; legacy per-screen permission controllers and constructor/callback permission sheets are removed; manifest permissions are audited; the visual resolver covers every capability and both sheet modes; and API 27/API 37 instrumentation plus process-recreation/settings-return tests pass.
