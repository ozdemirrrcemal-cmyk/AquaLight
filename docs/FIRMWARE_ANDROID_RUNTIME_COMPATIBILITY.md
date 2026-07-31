# AquaLight Firmware ↔ Android Runtime Compatibility

## Baseline

This branch is audited against:

- Firmware repository: `ozdemirrrcemal-cmyk/AquaLight-Firmware`
- Firmware ref: `main`
- Firmware commit: `39e942588247017340d51d201bde41194199ccdd`
- Android base commit: `120a2c8520ad5a8d91f8c9d9a709db07243ca41b`
- WebSocket fixture SHA-256: `765cd113b848d4b17c173e513b714b806466ec994483a34c19970e7a1b984591`

Any firmware change to a command, field, limit, authentication rule or signed OTA schema must update this Android mirror and the shared fixture atomically.

## Transport and trust boundaries

### WebSocket

- Schema/protocol: `aql.ws.v1` / version `1`
- Endpoint: `ws://<private-ip>:80/aql/v1/ws`
- Maximum wire message: `8192` UTF-8 bytes
- Maximum decoded `data`: `4096` bytes
- Authentication: mutual HMAC-SHA256 challenge/response
- Runtime frames: HMAC-protected, strictly increasing sequence numbers
- Application commands before authentication: none
- Unknown, duplicate, missing or type-coerced envelope fields: fail closed

### UDP discovery

- Schema: `aql.discovery.v1`
- Type/version: `device.announce` / `1`
- Port: `10888`
- Maximum packet: `768` UTF-8 bytes
- UDP is an untrusted private-LAN endpoint hint only.
- Datagram source IP must match the advertised runtime host.
- Public/non-private endpoints, duplicate fields, unknown fields and type coercion are rejected.
- Online/control proof comes only from a successful authenticated WebSocket response.

## Authenticated command matrix

The firmware golden fixture exposes exactly 41 authenticated commands and zero public application commands.

| Module | Commands | Android owner |
|---|---|---|
| `device` | `identity.get`, `status.get`, `capabilities.get`, `name.set` | metadata bootstrap and typed device-name runtime |
| `security` | `status.get`, `pair`, `unpair`, `reset` | security runtime repository |
| `network` | `status.get` | network runtime repository and liveness proof |
| `time` | `status.get`, `config.apply`, `phone.sync`, `ntp.sync`, `rtc.set` | time runtime repository/coordinator |
| `light` | `status.get`, `manual.set`, `channel.regime.set`, `program.apply`, `program.delete`, `temperature-protection.status.get`, `temperature-protection.set` | light runtime repositories |
| `cooling` | `status.get`, `config.apply` | cooling runtime repository |
| `timer` | `status.get`, `config.apply`, `channel.set` | timer runtime repository |
| `dosing` | `status.get`, `config.apply`, `prime.start`, `prime.stop`, `calibration.start`, `calibration.finish`, `calibration.confirm`, `calibration.cancel`, `dose.now`, `dose.stop`, `reservoir.refill` | dosing runtime repository |
| `firmware` | `status.get`, `ota.status`, `ota.start`, `ota.clear` | firmware/OTA runtime repository |

## Current contract-specific guarantees

### Device identity and name

- `displayName` is immutable product identity.
- `customName` is the user-owned override.
- `effectiveDisplayName = customName.ifBlank { displayName }`.
- Maximum custom name: `64` UTF-8 bytes.
- Blank or JSON `null` clears the override.
- Android persists the local snapshot only after the matching authenticated `device.name.set` response reports `saved=true`.
- Authenticated identity/status bootstrap replaces stale local name data atomically.

### Light

- The core command registry includes temperature-protection read/write commands.
- Temperature protection is mandatory and cannot be disabled.
- Setpoint range: `50..70 °C`; firmware default: `60 °C`.

### Timer, dosing and cooling display names

- Display-name overrides are separate from stable channel/fan keys.
- Maximum display name: `32` UTF-8 bytes.
- Blank/JSON `null` clears an override.
- Duplicate channel/fan keys are rejected before sending.
- Cooling supports partial `fans[]` updates with exact `{fanKey, displayName}` items.

### OTA

- OTA control and status remain on authenticated WebSocket.
- Package selection is bound to product key, product ID, model and hardware revision.
- Release notes use the signed schema `aql.ota.release-notes.v1`.
- `defaultLocale` is `tr`.
- Every item contains exact `tr` and `en` strings.
- Item count: `1..20`; each localized string: maximum `500` characters.
- Android does not invent unsigned titles, summaries, warnings or mandatory flags.

## Automated enforcement

- Shared WebSocket golden-vector interoperability test
- 41-command access-matrix equality test
- HMAC proof, server identity, MAC, replay and size-limit tests
- Exact authenticated identity/status bootstrap tests
- Firmware-confirmed device-name persistence tests
- UDP duplicate/unknown/type/private-IP/source-IP/UTF-8-limit tests
- Timer/dosing/cooling display-name payload tests
- Signed bilingual OTA release-note tests
- `tools/ws_protocol_guard.py` fixture checksum and contract drift gate

## Hardware validation still required before release

Repository tests cannot replace device-in-the-loop validation. Release qualification must additionally run against each commercial ESP32 product profile:

1. Provision and authenticate on a real private LAN.
2. Exercise every product-supported command and event.
3. Verify reboot persistence for device/channel/fan names and schedules.
4. Verify UDP refresh after device-name changes.
5. Perform valid and intentionally invalid OTA package tests without boot-slot corruption.
6. Run disconnect/reconnect, replay, malformed-frame and long-duration soak tests.
