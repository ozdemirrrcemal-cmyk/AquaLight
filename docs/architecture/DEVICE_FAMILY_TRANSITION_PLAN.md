# Device Family Transition Plan

## Non-negotiable product rules

- Firmware `AqlProductCatalog.hpp` is the source of truth for product identity,
  capabilities, limits, feature keys and screen keys.
- Android does not infer a family or channel count from a display name.
- Unsupported features do not produce a menu item or a runtime API call.
- Dosing may use the timer engine inside firmware, but Android never exposes the
  standalone Timer API for a Dosing product.
- The OTA transport, verification and progress engine is shared infrastructure.
- There is no global or shared firmware-update screen.
- Each family opens firmware update from that device's own Settings route:
  - Light device menu → Settings → Firmware update
  - Timer device menu → Settings → Firmware update
  - Dosing device menu → Settings → Firmware update
  - Cooling device menu → Settings → Firmware update
- That Settings route may show only the package matching the open device's exact
  `productKey + productId + model + hardwareRevision` identity. Missing or
  ambiguous matches fail closed and show no update.

## Firmware product matrix

| Family | Model | Light channels | Timer channels | Dosing channels | Fan outputs | Temperature sensors |
|---|---|---:|---:|---:|---:|---:|
| Light | WRGB Pro Elite 120 | 4 | 0 | 0 | 2 | 1 |
| Light | RGB Pro Slim | 3 | 0 | 0 | 0 | 0 |
| Timer | Relay Pro 2 | 0 | 2 | 0 | 0 | 0 |
| Timer | Relay Pro 4 | 0 | 4 | 0 | 0 | 0 |
| Dosing | Dose Pro 2 | 0 | 0 | 2 | 0 | 0 |
| Dosing | Dose Pro 4 | 0 | 0 | 4 | 0 | 0 |
| Cooling | Cool Pro 1 Fan | 0 | 0 | 0 | 1 | 1 |
| Cooling | Cool Pro 2 Fan | 0 | 0 | 0 | 2 | 1 |
| Cooling | Cool Pro 3 Fan | 0 | 0 | 0 | 3 | 1 |

## Delivery stages

### Stage 1 — Firmware contract foundation

- Mirror exact firmware feature and screen wire keys with typed Android values.
- Keep raw unknown keys for forward compatibility, but never let them unlock UI.
- Add one shared golden fixture covering all nine firmware products.
- Verify family, capabilities, limits, menus and allowed runtime modules per model.

Exit gate:

- All nine fixtures resolve to the correct family and exact channel counts.
- No legacy alias such as `channels`, `settings` or `quick_setup` unlocks a menu.
- Light simulation remains hidden for both current Light products.
- Dose Pro never gains a standalone Timer menu or Timer API request.

### Stage 2 — Capability-gated connection bootstrap

- After authentication, request only device identity and capabilities.
- Wait for both successful responses.
- Request common status and only the modules allowed by the reported family and
  capabilities.
- Treat unknown family or incomplete capability data as unsupported and fail closed.

Expected optional module requests:

| Model group | Runtime status modules |
|---|---|
| WRGB Pro Elite 120 | Time, Firmware, Light, Cooling |
| RGB Pro Slim | Time, Firmware, Light |
| Relay Pro 2/4 | Time, Firmware, Timer |
| Dose Pro 2/4 | Time, Firmware, Dosing |
| Cool Pro 1/2/3 Fan | Time, Firmware, Cooling |

Exit gate:

- No Light, Cooling, Timer or Dosing status request is sent before metadata.
- No product receives an unsupported family-module request.
- Reconnect starts a fresh metadata gate and cannot reuse stale capability state.

### Stage 3 — Readiness, menu and channel-slot model

- Carry `Loading`, `Ready`, `Offline` and `Unsupported` readiness explicitly.
- Carry `temperatureSensorCount` through the application boundary.
- Generate channel slots only from firmware limits:
  - Light: `lightChannelCount`
  - Timer: `timerChannelCount`
  - Dosing: `dosingChannelCount`
  - Cooling: `fanOutputCount`
- Build family menu specifications from typed capabilities and screen keys.

Exit gate:

- Dose Pro 2/4 render exactly 2/4 channel slots.
- Relay Pro 2/4 render exactly 2/4 channel slots.
- Cool Pro 1/2/3 render exactly 1/2/3 fan slots.
- Light products render exactly 4/3 light slots.
- Missing metadata never appears as an empty but ready menu.

### Stage 4 — Fixture-driven family menu design

- Design Light, Timer, Dosing and Cooling roots independently.
- Design each family's own Settings route.
- Use the nine product fixtures before connecting live repositories.
- Keep unsupported items absent rather than disabled placeholders.

Exit gate:

- Every product fixture renders the correct family layout and menu inventory.
- Family Settings contains a firmware-update entry only when OTA capability exists.
- There is no app-level or cross-family firmware-update screen.

### Stage 5 — Runtime data binding

- Bind each screen to only its family runtime repository.
- Address controls with firmware channel identifiers and validated limits.
- Preserve device UID, custom name, serial identity and tank assignment when
  metadata refreshes.
- Keep current online/offline and reconnect behavior intact.

Exit gate:

- UI actions cannot emit a command for an unsupported module or channel.
- Reconnect restores only the open device's family state.
- Metadata refresh cannot duplicate or replace the user's saved device identity.

### Stage 6 — Per-device OTA Settings flow

- Reuse the shared OTA engine for manifest fetch, signature verification, package
  selection, download, upload, progress and reboot recovery.
- Host the UI inside each family's own device Settings route.
- Pass the open device UID and immutable product identity into the OTA coordinator.
- Filter to one exact compatible package before rendering update availability.
- Never fall back to another model, family or generic package.

Exit gate:

- Every model sees only its own compatible release.
- A missing, duplicate or partially matching manifest entry offers no update.
- OTA progress and reconnect remain scoped to the device that initiated the update.

### Stage 7 — Commercial validation

- Run nine models across online, offline, reconnect and process-restart scenarios.
- Verify menu visibility, channel count, command allow-list and identity retention.
- Verify OTA success, failure, reboot, reconnect and metadata refresh per model.
- Require Android unit tests, lint, Detekt, CodeQL and supported emulator checks.

## Branch delivery strategy

The transition is delivered in reviewable slices on
`agent/device-family-foundation`:

1. Typed catalog contract, nine-product fixture and gated bootstrap.
2. Readiness plus dynamic menu/channel-slot specifications.
3. Fixture-driven family menu designs.
4. Family runtime binding.
5. Per-family Settings OTA UI backed by the shared OTA engine.

No slice is merged until its exit gate is green.
