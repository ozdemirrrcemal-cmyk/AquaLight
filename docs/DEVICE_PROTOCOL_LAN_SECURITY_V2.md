# AquaLight Device Protocol and LAN Security v2

This document records the Android-side production contract for Stage 4. Firmware's
authoritative counterpart is `docs/WEBSOCKET_RUNTIME_MIGRATION.md` in
`AquaLight-Firmware`. Protocol v1 is removed; no downgrade path is supported.

## Production profile

| Property | Value |
|---|---|
| Schema | `aql.ws.v2` |
| Version | `2` |
| Path | `/aql/v2/ws` |
| Maximum frame | 8192 UTF-8 bytes |
| Maximum decoded `data` | 4096 UTF-8 bytes |
| Authentication | Mutual HMAC-SHA-256 challenge-response |
| Replay protection | Per-session, per-direction monotonic sequence |
| Application frames | `hello`, `auth`, `cmd`, `res`, `err`, `evt` |

`AqlWsWireCodec` is the only WebSocket JSON serializer/parser. Wire field names,
limits and all 38 command keys are defined by `AqlWsContract`.

## Security decision

The deployed firmware does not yet have a manufacturer-issued per-device X.509
identity, protected private-key enrollment, certificate rotation/revocation, or an
Android pin-distribution lifecycle. Self-signed TLS without those controls would not
reliably authenticate the selected physical device.

The v2 production fallback therefore uses:

- a 256-bit runtime credential delivered only through encrypted BLE ownership provisioning;
- a fresh firmware challenge and fresh Android nonce;
- mutual credential proof without putting the credential on the WebSocket wire;
- an HMAC-derived per-connection session key;
- direction-bound MAC and exact-next sequence on every runtime frame;
- constant-time proof/MAC comparison and key zeroization on close;
- fail-closed rejection of identity/schema/version drift, duplicate/unknown/missing
  fields, binary frames, malformed data, oversize input, invalid MAC and replay.

HMAC authenticates and protects integrity; it does not encrypt ordinary runtime
payloads. No secret may be added to a v2 payload. Confidential application payloads
require a separately versioned WSS profile after the certificate/pinning lifecycle is
available.

## Android LAN boundary

Global cleartext is disabled in the manifest and base network policy. The only
cleartext exception is the app-owned `*.device.aql.local` hostname tree. The runtime
client:

1. accepts only an exact v2 endpoint with a private IPv4 literal;
2. derives a synthetic hostname from the selected device UID;
3. uses a connection-scoped DNS implementation that resolves only that exact hostname
   to the already validated private address;
4. rejects every other DNS lookup;
5. verifies the selected UID again in `hello` before authentication.

This keeps the non-WSS allowance narrow and prevents the previous global
`usesCleartextTraffic=true` exposure.

## Shared compatibility gate

Both repositories contain byte-identical
`protocol/fixtures/aql_ws_v2_golden.json` fixtures with SHA-256:

```text
ccab0a132208030575b718e611aff4269d155b495c45422ccbb0c4a28fc150ef
```

CI recomputes both handshake proofs, the session key, command/response/event/error
MACs, the command access matrix, and fixture bounds. Android unit tests execute the
real codec against the fixture and explicitly cover fake identity, forged proof,
invalid MAC, sequence gap, replay, duplicate/unknown/missing fields, unsigned frames
and oversize input.
