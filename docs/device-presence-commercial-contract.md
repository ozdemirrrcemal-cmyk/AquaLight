# AquaLight Commercial Device Presence Contract

## Scope

This contract governs every user-facing AquaLight device surface:

- Devices tab cards;
- Tank-assigned device cards;
- Settings device status;
- Light, Dosing, Timer and Cooling root screens;
- device-menu access checks;
- foreground/background transitions;
- Wi-Fi or Ethernet loss, restore and route changes.

No UI screen may implement a separate Online/Offline engine. All surfaces consume the canonical
`DevicesRepository` snapshot.

## Product states

The user-facing product remains deliberately binary:

- **Online**: authenticated device controls are currently usable;
- **Offline**: authenticated device controls are not currently proven usable.

Technical states such as LAN discovery, WebSocket connection, authentication handshake, stale
proof, retry and route recovery remain internal. During a short foreground verification window the
last stable product state may be retained, but controls must never bypass authenticated liveness.

## Evidence hierarchy

From strongest to weakest:

1. successful correlated control response from the requested device;
2. decoded authenticated runtime message from the requested device;
3. freshly completed authenticated session bootstrap;
4. connected WebSocket without authentication;
5. UDP discovery announcement;
6. Android local-network availability.

UDP proves LAN visibility and endpoint discovery only. It never authorizes a control surface.
Android Wi-Fi/Ethernet availability proves only that checking a local device is possible; it does
not prove that a specific AquaLight device is reachable.

## Time semantics

Presence freshness, retry backoff and timeout decisions use monotonic elapsed realtime. Wall-clock
timestamps are retained only for human-readable `last seen` presentation and durable historical
metadata.

Current policy:

| Evidence | Freshness |
| --- | ---: |
| correlated control/runtime proof | 15 seconds |
| authenticated bootstrap | 5 seconds |
| connected WebSocket | 8 seconds |
| UDP fresh | 20 seconds |
| UDP stale boundary | 35 seconds |
| authenticated heartbeat cadence | 8 seconds |
| foreground verification grace | 3 seconds |

These values are one central policy. UI code must not define independent timing thresholds.

## Menu-access invariant

For one device and one canonical registry revision:

- an Online card may open only through an authenticated session and a recent control proof;
- a newly successful menu proof is written to the canonical registry before navigation;
- a menu may not render Online from a private transport state while its canonical card remains
  Offline;
- a local-network failure returns immediately;
- a definitive Offline, Error or AuthRequired state returns immediately;
- an uncertain state receives a bounded verification budget of at most 2.5 seconds;
- long-running recovery continues outside the blocking user interaction.

Repeated taps for the same device are serialized. The UI shows progress only on the selected card;
it does not block the entire application shell.

## Foreground and background behavior

When the app enters foreground:

1. start one foreground verification generation;
2. retain a previously stable Online label for at most the grace window while active proof is
   requested;
3. force one runtime probe regardless of periodic backoff;
4. send a read-only authenticated network-status heartbeat;
5. send the bounded foreground UDP discovery burst;
6. publish the verified canonical result.

When the app enters background:

- high-frequency UDP refresh, runtime probe and heartbeat loops stop;
- cached timestamps are not treated as perpetual liveness;
- the next foreground transition always revalidates the current local route and device proof.

## Android local-network routing

One `DeviceConnectivityObserver` selects the canonical Wi-Fi or Ethernet `Network`.

- UDP listener sockets are bound with `Network.bindSocket`;
- UDP refresh sockets are bound with `Network.bindSocket`;
- WebSocket sockets are created from the selected `Network.socketFactory`;
- VPN and mobile-data default routes therefore cannot silently capture private-LAN traffic;
- a `Network` or `LinkProperties` generation change invalidates old runtime proof and socket reuse;
- recovery is device-scoped so one stalled device cannot interrupt another authenticated device.

The target device still needs an authenticated response. Route binding is not itself presence proof.

## User-facing failure reasons

| Reason | User guidance |
| --- | --- |
| local network unavailable | connect the phone to the same Wi-Fi network |
| authentication required | pair the device with the account again |
| device unresponsive | check device power and Wi-Fi connection |
| verification timed out | recovery continues automatically |
| invalid or missing registration | generic device-unavailable guidance |

## Commercial service-level objectives

| Scenario | Objective |
| --- | ---: |
| phone has no local network | offline feedback under 100 ms |
| canonical device is definitively Offline | offline feedback under 300 ms |
| recent control proof exists | menu navigation under 200 ms |
| uncertain device state | blocking verification at most 2.5 seconds |
| foreground return | no visible Online → Offline → Online flash |
| device power-off while phone remains on Wi-Fi | stable Offline within 15 seconds, never over 20 seconds |
| device recovery on the same LAN | automatic Online recovery without card tap |
| Wi-Fi A → Wi-Fi B or route generation change | old socket and proof never reused |

Timing measurements must be captured as elapsed realtime and reported as P50/P95/P99 in physical
acceptance evidence.

## Required automated coverage

- fresh correlated proof remains Online;
- authentication alone expires after the bootstrap window;
- wall-clock jumps do not alter presence freshness;
- local-network loss overrides all cached proof;
- definitive Offline and AuthRequired states do not wait for timeout;
- stalled authentication remains within the 2.5-second interaction budget;
- firmware responses that omit optional command metadata still correlate by device and request id;
- contradictory metadata, stale ids, failed responses and wrong devices are rejected;
- successful menu proof updates the canonical card state before route navigation;
- foreground verification does not publish a transient false Offline state;
- network-generation changes invalidate old sessions;
- one device recovery does not replace another authenticated device session.

## Required physical acceptance matrix

Test both a Debug build and the signed/minified release candidate where applicable.

1. app cold start with device powered and on the same LAN;
2. app cold start with device powered off;
3. phone Wi-Fi off while card is Online;
4. phone Wi-Fi on and automatic recovery without tapping the card;
5. app background for 10 seconds, 60 seconds and 10 minutes, then foreground;
6. device power-off and power-on while phone Wi-Fi stays enabled;
7. repeated first-tap menu access immediately after recovery;
8. VPN plus Wi-Fi plus mobile data;
9. Wi-Fi A → Wi-Fi B → Wi-Fi A;
10. DHCP/IP change on the same SSID;
11. multiple devices where only one device stalls;
12. force stop, process death and phone reboot;
13. account A → account B → account A isolation;
14. 30-minute endurance run with periodic foreground/background transitions.

Acceptance requires no crash, ANR, duplicate/ghost device, cross-account data leak, stale Online
control surface or user-visible Connecting label.

## Android platform migration gate

The current application targets SDK 36. Before target SDK 37:

- run Android 16 compatibility tests with local-network restrictions enabled;
- add and exercise the Android 17 local-network runtime permission flow when the SDK contract is
  available in the build toolchain;
- keep all permission denial paths typed as local-network unavailable rather than device failure;
- preserve direct local routing through the selected Android `Network` after permission grant;
- include denial, one-time grant, permanent denial and Settings recovery in the release matrix.

Target SDK must not be raised to 37 until this matrix passes on physical Android 17 hardware or an
official final emulator image.
