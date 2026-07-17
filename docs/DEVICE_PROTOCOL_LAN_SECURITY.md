# AquaLight Device Protocol and LAN Security

This document defines the first commercial Android wire contract for Stage 4.
Firmware's authoritative counterpart is `docs/WEBSOCKET_RUNTIME_CONTRACT.md` in
`AquaLight-Firmware`. This is the single clean baseline for the unreleased product.

The `v1` identifier below is the first public wire-schema version. It does not refer
to an older Android or firmware release.

## Production profile

| Property | Value |
|---|---|
| Schema | `aql.ws.v1` |
| Version | `1` |
| Path | `/aql/v1/ws` |
| Maximum frame | 8192 UTF-8 bytes |
| Maximum decoded `data` | 4096 UTF-8 bytes |
| Authentication | Mutual HMAC-SHA-256 challenge-response |
| Replay protection | Per-session, per-direction monotonic sequence |
| Application frames | `hello`, `auth`, `cmd`, `res`, `err`, `evt` |

`AqlWsWireCodec` is the only WebSocket JSON serializer/parser. Wire field names,
limits and all 38 command keys are defined by `AqlWsContract`.

## Security decision

The production hardware profile does not yet have a manufacturer-issued per-device
X.509 identity, protected private-key enrollment, certificate rotation/revocation,
or an Android pin-distribution lifecycle. Self-signed TLS without those controls
would not reliably authenticate the selected physical device.

The commercial LAN profile therefore uses:

- a 256-bit runtime credential delivered only through encrypted BLE ownership provisioning;
- a fresh firmware challenge and fresh Android nonce;
- mutual credential proof without putting the credential on the WebSocket wire;
- an HMAC-derived per-connection session key;
- direction-bound MAC and exact-next sequence on every runtime frame;
- constant-time proof/MAC comparison and key zeroization on close;
- fail-closed rejection of identity/schema/version drift, duplicate/unknown/missing
  fields, binary frames, malformed data, oversize input, invalid MAC and replay.

HMAC authenticates and protects integrity; it does not encrypt ordinary runtime
payloads. No secret may be added to an application payload. Confidential application
payloads require a separately versioned WSS profile after the certificate/pinning
lifecycle is available.

## Android LAN boundary

Global cleartext is disabled in the manifest and base network policy. The only
cleartext exception is the app-owned `*.device.aql.local` hostname tree. The runtime
client:

1. accepts only the exact commercial endpoint with a private IPv4 literal;
2. derives a synthetic hostname from the selected device UID;
3. uses a connection-scoped DNS implementation that resolves only that exact hostname
   to the already validated private address;
4. rejects every other DNS lookup;
5. verifies the selected UID again in `hello` before authentication.

This keeps the non-WSS allowance narrow and prevents a global
`usesCleartextTraffic=true` exposure.

## Shared contract gate

Both repositories contain byte-identical
`protocol/fixtures/aql_ws_v1_golden.json` fixtures with SHA-256:

```text
5fd72666b4f744f0556edfb97bde13c3d1b3688da349dc11b56abd542e4ab48d
```

CI recomputes both handshake proofs, the session key, command/response/event/error
MACs, the command access matrix, and fixture bounds. Android unit tests execute the
real codec against the fixture and explicitly cover fake identity, forged proof,
invalid MAC, sequence gap, replay, duplicate/unknown/missing fields, unsigned frames
and oversize input.
