# Android ↔ Firmware Gap Matrix

## Pinned comparison

- Android repository: `ozdemirrrcemal-cmyk/AquaLight`
- Android base commit: `120a2c8520ad5a8d91f8c9d9a709db07243ca41b`
- Firmware repository: `ozdemirrrcemal-cmyk/AquaLight-Firmware`
- Firmware authority commit: `39e942588247017340d51d201bde41194199ccdd`
- Firmware authenticated command union: 41
- Android registered authenticated command union: 38

This matrix is the implementation checklist. A command is not considered complete merely because its module/action string can be sent through the generic `JSONObject` API.

## Status definitions

| Status | Meaning |
|---|---|
| `MATCH` | Current Android implementation is exact and complete for the stated scope |
| `PARTIAL` | Some model/send/parser behavior exists, but the complete request→response/error→event→state path does not |
| `MISSING` | No production typed implementation exists |
| `STALE` | Android implements an older firmware contract |
| `PERMISSIVE` | Android silently accepts malformed/legacy/type-coerced data or invents defaults |
| `BLOCKED` | Code exists but cannot operate because another protocol gate rejects it |
| `SECURITY` | The gap affects trust, correlation, endpoint, signature, replay, or credential safety |

## Executive findings

1. Android is missing exactly three WebSocket commands from its authenticated allowlist:
   - `device.name.set`
   - `light.temperature-protection.status.get`
   - `light.temperature-protection.set`
2. Current authenticated metadata bootstrap rejects current firmware:
   - `device.identity.get` contains four new required name fields Android rejects as unknown;
   - `device.status.get` contains the new required root `device` object Android rejects as unknown.
3. Most module repositories return only `sent/messageId`; a queued socket write is incorrectly exposed as command success.
4. Only OTA has a substantial response/event coordinator. It still lacks common generation-scoped pending handling and final post-reboot version confirmation.
5. Light, Timer, Dosing, and Cooling status parsers are permissive and manufacture defaults.
6. Security and Network have no complete typed response/state layer.
7. UDP parsing is type-coercing, not source-bound, and cannot detect truncation reliably.
8. Android OTA release notes implement a stale unsigned-presentation shape rather than the signed bilingual firmware shape.

## Complete 41-command matrix

### Device

| Command | Current Android | Gaps required to close |
|---|---|---|
| `device.identity.get` | `PARTIAL`, `STALE`, `BLOCKED` | Metadata bootstrap sends and correlates it, but exact parser rejects `customName`, `effectiveDisplayName`, `nameEditable`, `customNameMaxBytes`. Model/projection must preserve immutable product name separately from custom/effective name. |
| `device.status.get` | `PARTIAL`, `STALE`, `BLOCKED` | Bootstrap sends and correlates it, but exact parser rejects the required root `device` object. Device-name metadata and module gates must be parsed exactly. |
| `device.capabilities.get` | `PARTIAL` | Bootstrap parser is substantially aligned, but the result is not integrated into the final shared per-device module-state contract. Capability/feature/module command gating must be centralized. |
| `device.name.set` | `MISSING` | Not in allowlist. Settings currently writes only a local snapshot, uses 32 characters instead of 64 UTF-8 bytes, cannot clear, does not wait for firmware, and misuses provisioning persistence. Add typed request/result/event and persist only after exact firmware confirmation. |

### Security

| Command | Current Android | Gaps required to close |
|---|---|---|
| `security.status.get` | `PARTIAL` | Send helper exists. No exact response parser, conditional paired fields, typed state, correlation timeout, or reducer. |
| `security.pair` | `MISSING` | Allowlist string exists but no typed ownership-status request/result. Android must not imply WebSocket token creation or rotation. |
| `security.unpair` | `MISSING`, `SECURITY` | No typed operation. Current token invalidation watches only a successful response ID, not exact module/action/generation. Add exact final-response handling, credential removal, pending cancellation, and session transition. |
| `security.reset` | `MISSING`, `SECURITY` | Same issues as unpair; exact security reset result and credential/session cleanup are required. |

### Network

| Command | Current Android | Gaps required to close |
|---|---|---|
| `network.status.get` | `PARTIAL` | Send helper and liveness request ID exist, but no full typed parser/state. Preserve current behavior that every exact successful authenticated response can be control proof; add exact network status for UI/domain without changing presence semantics. |

### Time

| Command | Current Android | Gaps required to close |
|---|---|---|
| `time.status.get` | `PARTIAL`, `PERMISSIVE` | Parser omits uptime, `timeZone`, `parts`, and runtime capability fields; invents NTP defaults and old `timeZone*60` fallback. Replace with exact status parser/state. |
| `time.config.apply` | `PARTIAL` | Typed payload exists but represents only one all-fields form, has insufficient field/range validation, and returns socket-write status only. Add exact response/event/persistence result. |
| `time.phone.sync` | `PARTIAL` | Payload exists but lacks complete validation and response/event/state handling. |
| `time.ntp.sync` | `PARTIAL` | Send-only. Add exact hardware/network error mapping and result/event handling. |
| `time.rtc.set` | `PARTIAL` | Parts payload exists but lacks calendar/range/exclusivity validation and exact response/event handling. Firmware also supports epoch form; Android needs an explicit typed variant. |

### Firmware and OTA

| Command | Current Android | Gaps required to close |
|---|---|---|
| `firmware.status.get` | `PARTIAL`, `PERMISSIVE` | Send helper exists. Firmware status parser defaults missing product/flash/partition/OTA fields and has no common runtime state reducer. Replace production use with exact parsing. |
| `firmware.ota.status` | `PARTIAL` | Exact parser/coordinator exists and checks device/id/action, but no shared executor timeout or session generation. Pending entries survive disconnect. Integrate into common correlation and state. |
| `firmware.ota.start` | `PARTIAL`, `STALE`, `SECURITY` | Exact firmware response echo/event validation exists, but manifest release-note schema is stale and signature verifier accepts any key ID when configured expected key ID is blank. Add strict manifest input limits/duplicate keys, common command outcome, generation, timeout, and final reboot-version verification. |
| `firmware.ota.clear` | `PARTIAL` | Exact parser exists, but pending lifecycle/generation/timeout must move to the common executor. |

### Light

| Command | Current Android | Gaps required to close |
|---|---|---|
| `light.status.get` | `PARTIAL`, `PERMISSIVE` | Send-only repository; parser defaults missing fields, drops invalid array items, accepts legacy shapes, derives percentages, and maps unknown regimes to `OFF`. Build exact status model/parser/reducer. |
| `light.manual.set` | `PARTIAL` | Payload allows both percent and value and silently chooses one; duplicate keys and non-finite values are insufficiently checked. No typed response/error/event/state. |
| `light.channel.regime.set` | `PARTIAL` | Typed payload exists but command is send-only; exact result/persistence/event/state absent. |
| `light.program.apply` | `PARTIAL` | Payload can ambiguously contain multiple time/value representations and silently chooses one. Program/channel/point uniqueness and exact firmware response/state are incomplete. Decide and emit the canonical v1 field, not an undocumented alias. |
| `light.program.delete` | `PARTIAL` | Send-only; no exact response/persistence/event/state. |
| `light.temperature-protection.status.get` | `BLOCKED` | Typed strict parser/repository exists, but global authenticated command registry rejects the command because it is missing from `AqlWsContract`. Integrate with common state/capability gate. |
| `light.temperature-protection.set` | `BLOCKED` | Typed payload/strict result parser exists, but global registry blocks the send. Integrate exact response/event/state and WRGB-only capability gating. |

### Timer

| Command | Current Android | Gaps required to close |
|---|---|---|
| `timer.status.get` | `PARTIAL`, `PERMISSIVE` | Parser manufactures defaults and omits exact key, count, duplicate, weekday, binding, enum, schema/root/runtime checks. |
| `timer.config.apply` | `PARTIAL` | Typed payload exists but duplicate channel keys/schedules, canonical fields, exact response, applied counts, config snapshot, event, persistence, and state are incomplete. |
| `timer.channel.set` | `PARTIAL` | Typed payload exists but exact response channel snapshot/list index, error, event, and state handling are absent. |

Important: this firmware baseline does not declare a separate 32-byte Timer channel display-name limit. Android must not invent one. Blank string is the canonical clear representation for the current service behavior; null/absence means no field update.

### Dosing

| Command | Current Android | Gaps required to close |
|---|---|---|
| `dosing.status.get` | `PARTIAL`, `PERMISSIVE` | Parser accepts old aliases/root fields, defaults invalid data, and invents `reservoirStatus`. Add exact channel/schedule/dosing/runtime schema and consistency validation. |
| `dosing.config.apply` | `PARTIAL` | Payload exists but duplicate channels, schedule binding, finite values, editability, exact response/applied counts/config/event/persistence/state are incomplete. |
| `dosing.prime.start` | `PARTIAL` | Send-only; add exact channel result, firmware errors, event, and state. |
| `dosing.prime.stop` | `PARTIAL` | Send-only; add exact result/event/state. |
| `dosing.calibration.start` | `PARTIAL` | Duration range exists; blank key/complete finite/exact response/event/state and busy/error mapping absent. |
| `dosing.calibration.finish` | `PARTIAL` | Measured range exists; exact pending-calibration result/event/state absent. |
| `dosing.calibration.confirm` | `PARTIAL` | Send-only; durable calibration confirmation and persistence result must be exact. |
| `dosing.calibration.cancel` | `PARTIAL` | Send-only; runtime-only result/event/state absent. |
| `dosing.dose.now` | `PARTIAL` | Amount range exists; finite check, calibration/reservoir firmware errors, exact duration/result/event/state absent. |
| `dosing.dose.stop` | `PARTIAL` | Send-only; exact result/event/state absent. |
| `dosing.reservoir.refill` | `PARTIAL` | Send-only; exact persisted reservoir result/event/state absent. |

Important: this firmware baseline does not declare a separate 32-byte Dosing channel display-name limit. Android must not invent one. The firmware exact dosing object has no `reservoirStatus` field.

### Cooling

| Command | Current Android | Gaps required to close |
|---|---|---|
| `cooling.status.get` | `PARTIAL`, `PERMISSIVE` | Parser defaults missing fields, unknown mode→`OFF`, drops invalid arrays/bindings, and omits `runtime.supportsFanDisplayName`. Add exact fan/rule/count/binding/capability state. |
| `cooling.config.apply` | `PARTIAL` | Android only supports global mode/range. Firmware `fans[{fanKey,displayName}]` writes, 32 UTF-8-byte limit, null/blank clear, exact item keys, duplicate/unknown key rejection, feature gate, response counts/config/event/state are missing. |

## Shared WebSocket infrastructure gaps

### Already correct and retained

- mutual HMAC-SHA256 authentication;
- server-proof verification;
- derived session key;
- signed client/device runtime frames;
- per-direction monotonic sequence and maximum sequence;
- exact envelope fields;
- duplicate envelope-field rejection;
- message/data byte limits;
- private-LAN route validation;
- five-second handshake timeout;
- per-device WebSocket client/session isolation in `DeviceRuntimeRepository`.

### Required changes

1. Add the missing three commands and enforce the 41-command golden union.
2. Add exact active event registry and validate envelope `module + action` routes.
3. Decode base64url data with a strict UTF-8 decoder; malformed bytes are protocol errors.
4. Enforce firmware JSON depth, total-key-count, and key-byte limits.
5. Introduce one pending request executor keyed by:
   - device UID;
   - connection/session generation;
   - message ID;
   - module;
   - action.
6. Register pending requests before sending.
7. Cancel device pending requests on disconnect, retire, owner change, and shutdown.
8. Drop timeout/late/duplicate/old-generation responses.
9. Replace `sent == success` module results with typed outcomes.
10. Keep the generic `JSONObject` command API internal to the transport boundary.
11. Keep one runtime repository/source of truth; do not create a parallel production repository.

## Shared typed outcomes required

```text
Success<T>
NotConnected
NotAuthenticated
UnsupportedByDevice
SendFailed
Timeout
FirmwareError
ProtocolError
Cancelled
```

A `FirmwareError` is a valid signed `err` frame. A `ProtocolError` means the signed/envelope/module payload does not satisfy the pinned contract. They must not be collapsed.

## Per-device state gaps

Current module repositories do not feed a common per-device runtime state. The target is one owner-scoped store:

```kotlin
StateFlow<Map<DeviceUid, DeviceRuntimeState>>
```

Each device state must isolate:

- current connection/session generation;
- authentication status;
- supported module/feature set;
- Device, Security, Network, Time, Light, Timer, Dosing, Cooling, Firmware and OTA state;
- last validated values and freshness phase;
- command/protocol faults;
- timestamps and source message IDs.

Disconnect marks previously validated module values `STALE`; it does not copy or share values across devices.

## Metadata and provisioning blockers

Current provisioning waits for authenticated identity, capabilities, and module status. The new firmware fields make current Android bootstrap reject valid responses. Required fixes must preserve:

- the existing `connectRuntime(deviceUid)` boundary;
- generation-scoped metadata publication;
- `hasValidatedRuntimeMetadata` semantics;
- product catalog validation;
- token staging and rollback;
- no local commit until metadata is validated.

Device-name mutation must not call `commitProvisioningSnapshot`. It is an authenticated runtime command with its own firmware persistence result.

## Presence compatibility requirements

`DevicePresenceRuntimeMonitor` remains the single online/offline authority. Runtime module phases are not online states.

The runtime transition must preserve:

- UDP evidence as `ONLINE_LAN`, not authenticated;
- authenticated WebSocket response as runtime/control proof;
- local-network loss and network-generation invalidation;
- foreground grace/revalidation;
- bounded reconnect behavior;
- current state names and UI behavior;
- OTA/provisioning presence states.

## UDP discovery gaps

| Gap | Current behavior | Required behavior |
|---|---|---|
| Size | Checks Kotlin character length | Check original datagram UTF-8 byte length |
| Truncation | Uses a 768-byte receive buffer; a larger packet may appear valid at 768 | Receive into a 769-byte sentinel buffer and reject length > 768 |
| UTF-8 | Replacement decoding accepts malformed bytes | Strict decoder with malformed/unmappable input rejection |
| Duplicate keys | Not rejected | Reject at every object depth |
| Unknown keys | Not rejected | Exact root/nested key sets |
| Types | Number/string and boolean/string coercions accepted | Exact JSON types only |
| Network mode | Not exact | `off`, `sta`, `ap`, `ap_sta`, `unknown` only |
| Host trust | Advertised host accepted independently | Host must equal datagram source and be private/local IPv4 |
| Authority | Snapshot may look trusted | UDP remains an untrusted endpoint/name hint below authenticated metadata |

## OTA manifest gaps

| Gap | Current Android | Required behavior |
|---|---|---|
| Release notes | Legacy `mandatory/locales/title/summary/changes/warnings` | Signed `schema/defaultLocale/items[{tr,en}]` |
| Item limit | 50 | 20 |
| Text limit | 2000 | 500 |
| Configured key ID | Blank expected key ID accepts any manifest key ID | Blank configured key ID is a hard failure |
| Raw JSON safety | No explicit duplicate-key/manifest-size gate before `JSONObject` | Bounded strict UTF-8 JSON and duplicate-key rejection |
| Final success | Terminal event may show success/restart-required | Reconnect and authenticated firmware version must equal the selected target |

## Legacy behavior to remove after cutover

- module status `opt...(..., default)` production parsers;
- unknown enum→`OFF` fallbacks;
- old field aliases and root/nested fallbacks;
- generated fields absent from firmware, including dosing `reservoirStatus`;
- local-only device rename;
- module-specific send-only command-result classes;
- duplicate OTA pending/correlation mechanism after common executor migration;
- any second runtime repository or state store;
- permissive production parser paths retained beside exact paths.

## Definition of parity complete

All entries in the 41-command table must be moved to `MATCH`. In addition:

- all active events must be exact;
- metadata bootstrap must accept the pinned firmware and reject malformed variants;
- provisioning and presence regression contracts must pass unchanged;
- UDP and OTA security gaps must be closed;
- no permissive/legacy production path may remain;
- real-device qualification is required before commercial-release approval.