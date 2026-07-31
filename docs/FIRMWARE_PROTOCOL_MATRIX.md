# AquaLight Firmware Protocol Matrix

## Baseline and authority

This document records the external Android-facing contract of AquaLight-Firmware at:

- repository: `ozdemirrrcemal-cmyk/AquaLight-Firmware`
- commit: `39e942588247017340d51d201bde41194199ccdd`
- WebSocket schema: `aql.ws.v1`
- WebSocket schema version: `1`
- WebSocket protocol version: `1`
- UDP discovery schema: `aql.discovery.v1`
- UDP discovery version: `1`
- OTA manifest schema: `aql.ota.manifest.v1`
- OTA release-notes schema: `aql.ota.release-notes.v1`

Authority order:

1. command registration and handler source;
2. protocol golden fixtures and release-generation scripts;
3. product compile gates and product catalog;
4. documentation.

Documentation is not allowed to override the executable source or golden fixture.

## WebSocket transport and security

| Item | Contract |
|---|---|
| Endpoint | `ws://<private-lan-ip>:80/aql/v1/ws` |
| Application message types | `hello`, `auth`, `cmd`, `res`, `err`, `evt` |
| Public application commands | none |
| Authenticated command union | 41 commands |
| Authentication | mutual HMAC-SHA256 challenge/response |
| Runtime frames | HMAC-SHA256 signed in both directions |
| Replay protection | session ID plus strictly monotonic per-direction sequence |
| First sequence | `1` |
| Maximum sequence | `9007199254740991` |
| Maximum wire message | 8192 UTF-8 bytes |
| Maximum decoded `data` | 4096 UTF-8 bytes |
| Maximum encoded `data` | 5464 base64url characters |
| Maximum JSON depth | 12 |
| Maximum JSON key count | 128 |
| Maximum JSON key length | 64 UTF-8 bytes |
| Maximum clients | 2 |
| Handshake timeout | 5000 ms |
| Auth attempts | one per connection |
| Heartbeat interval | 5000 ms |
| Pong timeout | 2000 ms |
| Miss threshold | 2 |

Runtime `data` is always a compact JSON object encoded as unpadded base64url. Unknown or duplicate envelope fields, malformed JSON, an incompatible schema/version, a wrong device UID, invalid MAC, stale/replayed sequence, oversize data, or an unauthenticated command are protocol failures.

Golden command fixture:

- path: `protocol/fixtures/aql_ws_v1_golden.json`
- SHA-256: `765cd113b848d4b17c173e513b714b806466ec994483a34c19970e7a1b984591`
- authenticated commands: 41
- public commands: 0

## Runtime event envelope

A normal mutating command sends its signed response before its signed event. The event envelope module and action are obtained by splitting the qualified event name at the first dot.

Normal command event `data`:

```json
{
  "commandId": "<request id>",
  "module": "<original command module>",
  "action": "<original command action>",
  "sessionId": "<authenticated session id>",
  "publishedAtMs": 0,
  "result": { "...": "the complete successful response data object" }
}
```

Confirmed active event routes:

| Envelope module | Envelope action | Qualified source | Meaning |
|---|---|---|---|
| `device` | `status.changed` | `device.status.changed` | Device custom-name mutation result |
| `light` | `status.changed` | `light.status.changed` | Light mutation result |
| `timer` | `status.changed` | `timer.status.changed` | Timer mutation result |
| `dosing` | `status.changed` | `dosing.status.changed` | Dosing mutation result |
| `cooling` | `status.changed` | `cooling.status.changed` | Cooling mutation result |
| `time` | `status.changed` | `time.status.changed` | Time mutation/sync result |
| `firmware` | `ota.progress` | `firmware.ota.progress` | Direct OTA snapshot |
| `firmware` | `ota.completed` | `firmware.ota.completed` | Direct terminal OTA snapshot |

The constants `network.state.changed`, `temperature.changed`, and `system.restarting` are declared in this baseline, but no active publication path was found. Android must not invent runtime behavior for declared-only events.

## Product and module matrix

Android must use authenticated `capabilities`, `limits`, `supportedFeatures`, and `modules`. It must not infer module availability from a display name or from an internal engine flag.

| Product model | Public runtime modules | Important rule |
|---|---|---|
| `wrgb_pro_elite_120` | Light, Cooling, Temperature | Four light channels, two fixed fans, one temperature sensor; temperature protection available; fan display names not editable |
| `rgb_pro_slim` | Light | Three light channels; no Cooling/Temperature module |
| `relay_pro_2` | Timer | Two timer channels; timer-channel display-name feature |
| `relay_pro_4` | Timer | Four timer channels; timer-channel display-name feature |
| `dose_pro_2` | Dosing | Two dosing channels; internal timer engine exists but standalone Timer API is not registered |
| `dose_pro_4` | Dosing | Four dosing channels; internal timer engine exists but standalone Timer API is not registered |
| `cool_pro_1f` | Cooling, Temperature | One fan; cooling-fan display-name feature |
| `cool_pro_2f` | Cooling, Temperature | Two fans; cooling-fan display-name feature |
| `cool_pro_3f` | Cooling, Temperature | Three fans; cooling-fan display-name feature |

All commercial profiles expose Device, Security, Network, Time, Firmware/OTA, Discovery, and System infrastructure. Module command handlers are compile-gated by `AQL_MODULE_*` definitions.

## Complete authenticated command union

Every row below must have an Android typed request, typed response/error handling, exact correlation, and an owning per-device state/reducer where applicable.

### Device

| Command | Request | Successful response and side effects |
|---|---|---|
| `device.identity.get` | empty object | Exact immutable identity/product/runtime fields plus `customName`, `effectiveDisplayName`, `nameEditable`, and `customNameMaxBytes` |
| `device.status.get` | empty object | Boot/auth/uptime, top-level `device`, product, runtime, and compile-gated module flags |
| `device.capabilities.get` | empty object | Product identity, exact capabilities, limits, feature tokens, and editable-feature policy |
| `device.name.set` | `customName` string or null; optional `save` | Trimmed name or clear; max 64 UTF-8 bytes; control characters forbidden; storage failure restores previous name; changed result refreshes UDP and emits `device.status.changed` |

`device.identity.get` exact fields:

- `productKey`, `productId`, `setupCode`, `deviceUid`, `shortId`, `serialNumber`, `firmwareSerial`, `macAddress`
- `brand`, `family`, `line`, `model`, `displayName`
- `customName`, `effectiveDisplayName`, `nameEditable`, `customNameMaxBytes`
- `skuId`, `skuCode`, `firmwareVersion`, `hardwareRevision`, `apiVersion`, `protocolVersion`
- `runtime.transport`, `runtime.wsSchema`, `runtime.wsPath`, `runtime.wsPort`, `runtime.wsProtocolVersion`

`device.status.get` exact root fields:

- `state`, `authenticated`, `uptimeMs`
- `device.productDisplayName`, `device.customName`, `device.effectiveDisplayName`, `device.editable`, `device.maxBytes`
- `product.productKey`, `product.family`, `product.model`, `product.displayName`
- `runtime.transport`, `runtime.wsSchema`, `runtime.wsPath`, `runtime.wsPort`
- `modules.light`, `modules.cooling`, `modules.temperature`, `modules.timerApi`, `modules.timerEngine`, `modules.dosing`, `modules.network`, `modules.discovery`, `modules.firmware`, `modules.system`

### Security

| Command | Request | Successful response and side effects |
|---|---|---|
| `security.status.get` | empty object | Ownership/token-storage/auth/replay status; token metadata fields are conditional on `paired=true` |
| `security.pair` | ownership-status-only request; token creation/rotation fields are forbidden | Reports existing BLE ownership; never creates or returns a runtime token |
| `security.unpair` | authenticated mutation | Sends the final signed response, removes ownership, revokes sessions, and requires BLE ownership again |
| `security.reset` | authenticated mutation | Sends the final signed response, clears security ownership/state, revokes sessions |

Security status includes the runtime authentication scheme, token storage backend/format, plaintext prohibition, device identity, pairing state, and conditional `tokenVersion`, `pairedAtMs`, and `lastRotatedAtMs`.

### Network

| Command | Request | Successful response and side effects |
|---|---|---|
| `network.status.get` | empty object | Exact station/setup-AP/discovery/runtime status; read-only authenticated liveness proof |

WebSocket network-mode values are:

- `off`
- `client`
- `setup_ap`
- `client_and_setup_ap`
- `unknown`

These values are intentionally different from the UDP discovery `network.mode` values.

### Time

| Command | Request | Successful response and persistence |
|---|---|---|
| `time.status.get` | empty object | Complete current time, uptime, timezone/config, sync source, parts, and runtime capability status |
| `time.config.apply` | at least one supported config field; optional `save` | Applies timezone/NTP/gadget configuration; persists only when requested and confirmed; emits `time.status.changed` |
| `time.phone.sync` | required `epochMillis`; optional config fields and `save` | Sets runtime time from phone, optionally persists config; emits `time.status.changed` |
| `time.ntp.sync` | empty object | Performs NTP sync; hardware/network failure is an error; emits `time.status.changed` on success |
| `time.rtc.set` | either `epochMillis` or exact date/time parts; optional config and `save` | Sets RTC/time, optionally persists config; emits `time.status.changed` |

Time status includes:

- `timeSet`, `timeString`, `uptime`, `uptimeMs`, `millisStartDay`
- `timeZone`, `utcOffsetMinutes`, `timezoneId`, `posixTimeZone`
- `autoSyncNtpEnabled`, `autoSyncGadgetEnabled`
- `ntpServerPrimary`, `ntpServerSecondary`
- `lastSyncSource`, `lastSyncEpochMillis`, `lastSyncUptimeMs`
- `parts.year`, `parts.month`, `parts.day`, `parts.weekday`, `parts.hour`, `parts.minute`, `parts.second`
- runtime capability/event fields

### Firmware and OTA

| Command | Request | Successful response and side effects |
|---|---|---|
| `firmware.status.get` | empty object | Firmware/product/flash/partition/OTA capability and current OTA snapshot |
| `firmware.ota.status` | empty object | Exact OTA snapshot and event/transport metadata |
| `firmware.ota.start` | HTTPS URL, SHA-256, version, expected size, apply policy, exact product identity | Returns HTTP-style status 202 with exact request echo and initial OTA snapshot; emits progress/completed events |
| `firmware.ota.clear` | empty object | Clears terminal OTA state and returns previous/current snapshots |

`firmware.ota.start` requires:

- `url` with HTTPS scheme
- `sha256` as 64 hex characters
- `version`
- `expectedSize > 0`
- `productKey`, `productId`, `model`, `hardwareRevision`
- `allowInsecureHttp` must be absent/false

OTA success is not complete until the device reconnects after reboot and authenticated `firmware.status.get` confirms the target version.

### Light

| Command | Request | Successful response and persistence |
|---|---|---|
| `light.status.get` | empty object | Exact channel/program/runtime status |
| `light.manual.set` | canonical `channels[]` by `channelKey`, exactly one value representation; or explicit clear | Applies runtime manual values; duplicate/unknown keys are errors; emits `light.status.changed` |
| `light.channel.regime.set` | canonical target channel(s) and exact regime | Changes regime; optional persistence according to request; emits `light.status.changed` |
| `light.program.apply` | program index and exact point/channel payload | Validates program/channel/point structure, applies and optionally persists; emits `light.status.changed` |
| `light.program.delete` | program index and persistence policy | Deletes program, optionally persists; emits `light.status.changed` |
| `light.temperature-protection.status.get` | empty object | WRGB-only protection state and supported threshold range |
| `light.temperature-protection.set` | threshold in 50.0..70.0 °C and persistence policy | Cannot disable protection; only supported on compatible Light+Temperature profile; emits `light.status.changed` |

Android must reject ambiguous requests that provide both percent and normalized value, both textual and millisecond time, duplicate channel keys, non-finite values, or unsupported channels.

### Timer

| Command | Request | Successful response and persistence |
|---|---|---|
| `timer.status.get` | empty object | Exact channel/schedule/runtime status |
| `timer.config.apply` | canonical `channels` and/or `schedules`, optional `save` | Applies config, reports applied counts and config snapshot; emits `timer.status.changed` |
| `timer.channel.set` | exact channel key and supported runtime/config fields | Applies one channel update and returns channel snapshot plus list index; emits `timer.status.changed` |

Timer status consistency rules:

- `channelCount == channels.size`
- `scheduleCount == schedules.size`
- maximum 24 schedules
- channel indices and keys are unique
- schedule indices are unique
- weekdays contain exactly seven booleans
- a bound schedule references an existing channel key
- channel regimes are exactly `Auto`, `On`, or `Off`

Timer channel display names are feature-gated and trimmed. This pinned firmware baseline does **not** declare a separate 32-byte Timer display-name limit. Android must not invent one. The overall WebSocket `data` limit still applies.

### Dosing

| Command | Request | Successful response and persistence |
|---|---|---|
| `dosing.status.get` | empty object | Exact pump-channel/schedule/runtime status |
| `dosing.config.apply` | canonical `channels` and/or `schedules`, optional `save` | Applies dosing config through the dosing-owned timer engine; emits `dosing.status.changed` |
| `dosing.prime.start` | `channelKey` | Starts runtime prime; not persisted; emits `dosing.status.changed` |
| `dosing.prime.stop` | `channelKey` | Stops runtime prime; not persisted; emits `dosing.status.changed` |
| `dosing.calibration.start` | `channelKey`, duration 1000..60000 ms | Starts runtime calibration; emits `dosing.status.changed` |
| `dosing.calibration.finish` | `channelKey`, measured amount 0.05..1000 ml | Produces pending calibration; not yet durable; emits `dosing.status.changed` |
| `dosing.calibration.confirm` | `channelKey` | Commits calibration and persists it; emits `dosing.status.changed` |
| `dosing.calibration.cancel` | `channelKey` | Cancels pending calibration; runtime-only; emits `dosing.status.changed` |
| `dosing.dose.now` | `channelKey`, amount 0..1000 ml, optional pending-calibration policy | Requires calibration and sufficient reservoir when tracking is enabled; emits `dosing.status.changed` |
| `dosing.dose.stop` | `channelKey` | Stops runtime dose; emits `dosing.status.changed` |
| `dosing.reservoir.refill` | `channelKey` | Refills to configured capacity through the reservoir persistence mechanism; emits `dosing.status.changed` |

Exact dosing object fields:

- `unit`
- `doseMsPerMl`
- `lastCalibratedAt`
- `calibrated`
- `reservoirTrackingEnabled`
- `reservoirCapacityMl`
- `reservoirRemainingMl`
- `reservoirRemainingPercent`

There is no `reservoirStatus` field in this firmware baseline.

Dosing channel display names are feature-gated and trimmed. This pinned firmware baseline does **not** declare a separate 32-byte Dosing display-name limit. Android must not invent one.

### Cooling

| Command | Request | Successful response and persistence |
|---|---|---|
| `cooling.status.get` | empty object | Exact global/fan/rule/runtime status |
| `cooling.config.apply` | global mode/range and/or exact `fans[{fanKey,displayName}]`; optional `save` | Atomic validation, applies global config and/or fan names, returns config snapshot; emits `cooling.status.changed` |

Cooling fan display-name rules:

- feature-gated by `COOLING_FAN_DISPLAY_NAME`
- WRGB fixed fan names are not editable
- one through configured fan count, hard maximum eight updates
- each item has exactly `fanKey` and `displayName`
- canonical trimmed lowercase fan key
- duplicate or unknown fan keys reject the request
- `displayName` is string or null
- blank/null clears the override
- maximum 32 UTF-8 bytes

Cooling status consistency rules:

- `fanOutputCount == fans.size`
- `ruleCount == rules.size`
- fan indices/keys are unique
- bound rules reference a configured fan/channel
- sensor bindings are valid for the product
- runtime includes `supportsFanDisplayName`

## UDP discovery contract

| Item | Contract |
|---|---|
| Port | 10888 |
| Maximum datagram | 768 bytes |
| Schema | `aql.discovery.v1` |
| Type | `device.announce` |
| Version | 1 |

Exact announce object:

```json
{
  "schema": "aql.discovery.v1",
  "type": "device.announce",
  "version": 1,
  "sentAtMs": 0,
  "device": {
    "uid": "...",
    "shortId": "...",
    "name": "..."
  },
  "product": {
    "family": "...",
    "model": "...",
    "name": "..."
  },
  "network": {
    "mode": "off|sta|ap|ap_sta|unknown",
    "connected": true
  },
  "runtime": {
    "transport": "websocket",
    "host": "private source-bound IPv4",
    "port": 80,
    "path": "/aql/v1/ws",
    "protocol": "aql.ws.v1",
    "protocolVersion": 1
  }
}
```

UDP is an untrusted endpoint hint. Android must bind the advertised host to the datagram source, require a private/local IPv4 route, reject malformed UTF-8, duplicate/unknown/type-coerced fields, and never treat UDP as authentication or control proof.

## OTA release manifest and signature

Manifest generation produces exact product artifacts from the firmware product catalog. Root fields before signature are:

- `schema`, `brand`, `channel`, `version`, `tag`, `releaseRepo`, `generatedAt`
- `platform`
- `artifacts`
- `releaseNotes` attached before signing

Each artifact contains:

- `env`
- exact `product` identity, capabilities, limits, and hardware revision
- exact `compatibility` identity
- `firmware` version, filename, HTTPS URL, SHA-256, size, format, OTA-slot compatibility
- optional `factory`

Signature object:

```json
{
  "scheme": "ECDSA_P256_SHA256",
  "keyId": "aql-ota-manifest-2026-01",
  "payloadHash": "<64 hex>",
  "value": "<base64 DER ECDSA signature>"
}
```

Canonical payload generation:

1. remove root `signature`;
2. UTF-8 JSON with sorted object keys;
3. compact separators `,` and `:`;
4. preserve array order and Unicode;
5. SHA-256 the canonical bytes;
6. verify `SHA256withECDSA` using the configured P-256 public key.

Release notes:

```json
{
  "schema": "aql.ota.release-notes.v1",
  "defaultLocale": "tr",
  "items": [
    { "tr": "...", "en": "..." }
  ]
}
```

Rules:

- maximum 20 items
- maximum 500 characters per localized item
- Turkish and English entries are both non-blank
- locale item counts are equal
- release notes are covered by the manifest signature

## Completion gate

The Android transition is contract-complete only when:

- all 41 commands are registered and reachable only when the authenticated device reports support;
- every command has typed request/response/error handling;
- all confirmed active events have exact routing and payload parsing;
- every response is correlated by device UID, connection generation, message ID, module, and action;
- per-device module state is isolated;
- UDP and OTA contracts are exact and fail closed;
- provisioning and presence regression contracts remain intact;
- no legacy or permissive production path remains.