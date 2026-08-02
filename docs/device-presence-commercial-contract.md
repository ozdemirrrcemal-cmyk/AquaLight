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

## Source of truth and verified protocol baseline

When prose and executable behavior disagree, the source order is:

1. the Android and firmware protocol constants, parsers and automated tests;
2. this commercial presence contract;
3. README files and historical protocol notes.

The deployed firmware source currently uses the following UDP discovery contract:

| Field | Runtime value |
| --- | --- |
| schema | `aql.discovery.v1` |
| announce type | `device.announce` |
| refresh type | `refresh` |
| version | `1` |
| UDP port | `10888` |
| maximum datagram | `768` bytes |

The firmware definitions in `src/contracts/AqlDiscoveryContract.hpp` and
`src/network/AqlDiscoveryService.hpp` agree with Android's `AqlDiscoveryContract` and strict
`AqlDiscoveryParser`. Firmware prose that describes `aql.discovery.v2`, `device_announce`, version
`20260624` or a 1536-byte packet is not the active runtime contract and must not be copied into
Android.

Firmware `sentAtMs` is boot-relative diagnostic metadata. Android does not compare it with the
phone's clock or use it for freshness. Android stamps every accepted datagram on receipt with its
own elapsed-realtime clock.

## Product states

The user-facing product remains deliberately binary:

- **Online**: the canonical state is `AUTHENTICATED`, `PROVISIONING` or `OTA_UPDATING`;
- **Offline**: every other technical state.

Technical states such as LAN discovery, WebSocket connection, authentication handshake, stale
proof, retry and route recovery remain internal. During a short foreground verification window the
last stable product state may be retained, but controls must never bypass authenticated liveness.
`PROVISIONING` and `OTA_UPDATING` are protected transaction-display exceptions; they do not
authorize the ordinary device menu. `ONLINE_LAN` and `CONNECTING_WS` always present as Offline.

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

| Policy | Value |
| --- | ---: |
| correlated control/runtime proof for product presence | 15 seconds |
| menu control-proof reuse with the same active authenticated session | 4 seconds |
| authenticated bootstrap | 5 seconds |
| connected WebSocket | 8 seconds |
| UDP fresh | 20 seconds |
| UDP stale boundary | 35 seconds |
| authenticated application-liveness probe cadence | 8 seconds |
| foreground verification grace | 3 seconds |
| runtime-endpoint discovery wait inside the menu budget | 350 milliseconds |
| complete menu-verification budget | 2.5 seconds |

These are canonical runtime constants. UI and presentation code must not define independent timing
thresholds.

The Android client also sends RFC 6455 transport pings every 20 seconds. The current firmware
WebSocket server sends transport pings every 5 seconds, waits 2 seconds for a pong and disconnects
after two missed pongs. Transport control frames detect a broken socket; they do not replace the
authenticated, correlated `network.status.get` application proof used by product presence and menu
access.

Firmware emits a periodic UDP announcement approximately every 40 seconds. While Android is in the
foreground it sends a refresh every 5 seconds, plus the bounded foreground burst. Consequently the
20/35-second UDP thresholds describe active foreground discovery behavior; a periodic firmware
announcement alone is not an authenticated Online guarantee.

## Menu-access invariant

For one device and one canonical registry revision:

- an Online card may open only through an authenticated session and a recent control proof;
- fresh UDP without a usable WebSocket endpoint returns `CURRENT_LIVENESS_NOT_PROVEN` and never
  opens controls;
- a newly successful menu proof is written to the canonical registry before navigation, only
  while its originating WebSocket generation is still current and authenticated;
- a menu may not render Online from a private transport state while its canonical card remains
  Offline;
- a local-network failure returns immediately;
- a definitive Offline, Error or AuthRequired state returns immediately;
- an uncertain state receives a bounded verification budget of at most 2.5 seconds;
- long-running recovery continues outside the blocking user interaction.

Repeated taps for the same device are serialized. The UI shows progress only on the selected card;
it does not block the entire application shell.

## Foreground and background behavior

`ProcessLifecycleOwner` is the sole process foreground authority. Activities observe session state
but do not start or stop device presence. Its delayed background dispatch prevents Activity
recreation and configuration changes from producing a false process stop/start cycle.

When the app enters foreground:

1. start one foreground verification generation;
2. retain a previously stable Online label for at most the grace window while active proof is
   requested;
3. force one runtime probe regardless of periodic backoff;
4. send a read-only authenticated network-status liveness probe;
5. send the bounded foreground UDP discovery burst;
6. publish the verified canonical result.

When the app enters background:

- high-frequency UDP refresh, runtime connection probe and application-liveness loops stop;
- every in-flight authenticated liveness probe is cancelled and loses scheduler ownership;
- cached timestamps are not treated as perpetual liveness;
- the next foreground transition always revalidates the current local route and device proof.

## Android local-network routing

One `DeviceConnectivityObserver` selects the canonical, non-VPN Wi-Fi or Ethernet `Network`.

- `NetworkCallback` callback values are the route-state source of truth;
- on API 29 and later, networks reported as blocked for this app are excluded from selection;
- UDP listener sockets are bound with `Network.bindSocket`;
- UDP refresh sockets are bound with `Network.bindSocket`;
- WebSocket sockets are created from the selected `Network.socketFactory`;
- VPN and mobile-data default routes therefore cannot silently capture private-LAN traffic;
- a `Network` or `LinkProperties` generation change invalidates old runtime proof and socket reuse;
- recovery is device-scoped so one stalled device cannot interrupt another authenticated device.

The target device still needs an authenticated response. Route binding is not itself presence proof.

## Firmware network-lifecycle obligations

The ESP-IDF network lifecycle and the Android route-generation lifecycle are symmetrical:

- firmware discovery and WebSocket services start only after the station has a usable IP address;
- Wi-Fi disconnect, IP loss or IP change invalidates existing sockets;
- services close and recreate sockets after connectivity returns instead of reusing stale handles;
- firmware uptime values never determine Android freshness;
- RFC 6455 ping/pong remains transport-owned, while AquaLight application liveness remains an
  authenticated request/response.

## User-facing failure reasons

| Reason | User guidance |
| --- | --- |
| local network unavailable | connect the phone to the same Wi-Fi network |
| authentication required | pair the device with the account again |
| device unresponsive | check device power and Wi-Fi connection |
| verification timed out | recovery continues automatically |
| invalid or missing registration | generic device-unavailable guidance |

## Commercial service-level objectives

These are physical release-acceptance targets, not guarantees created by timing constants alone.

| Scenario | Objective |
| --- | ---: |
| phone has no usable local network | P95 offline feedback at or below 100 ms |
| canonical device is definitively Offline | P95 offline feedback at or below 300 ms |
| recent control proof and authenticated session exist | P95 menu navigation at or below 200 ms |
| uncertain device state | blocking verification at most 2.5 seconds |
| foreground return | no visible Online → Offline → Online flash |
| device power-off while phone remains on Wi-Fi | P95 stable Offline at or below 20 seconds |
| device recovery on the same LAN | automatic Online recovery without card tap |
| Wi-Fi A → Wi-Fi B or route generation change | old socket and proof never reused |

Timing measurements must be captured as elapsed realtime and reported as P50/P95/P99 in physical
acceptance evidence.

## Required automated coverage

- fresh correlated proof remains Online;
- authentication alone expires after the bootstrap window;
- wall-clock jumps do not alter presence freshness;
- firmware `sentAtMs` never determines Android freshness;
- local-network loss overrides all cached proof;
- a blocked or VPN route is not selected as the local device path;
- definitive Offline and AuthRequired states do not wait for timeout;
- stalled authentication remains within the 2.5-second interaction budget;
- fresh UDP without an authenticated runtime endpoint never authorizes menu access;
- exact firmware responses correlate by exact module, action, device generation and request id;
- a successful response from a replaced generation cannot write menu or presence proof;
- background and route changes cancel in-flight liveness probes without a stale proof write;
- UDP discovery rejects extra keys, aliases, casing/whitespace normalization and scalar coercion;
- contradictory metadata, stale ids, failed responses and wrong devices are rejected;
- successful menu proof updates the canonical card state before route navigation;
- foreground verification does not publish a transient false Offline state;
- Activity recreation does not create a process foreground/background cycle;
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
14. 30-minute endurance run with periodic foreground/background transitions;
15. Activity recreation by rotation, locale and theme changes;
16. API-29-or-later Android-blocked local network and VPN coexistence.

Acceptance requires no crash, ANR, duplicate/ghost device, cross-account data leak, stale Online
control surface or user-visible Connecting label.

## Android platform migration gate

The current application targets SDK 36. Under the Android 17 local-network model:

- while targeting SDK 36 or lower, keep `INTERNET` and do **not** declare or request
  `ACCESS_LOCAL_NETWORK`;
- exercise Android 16's `RESTRICT_LOCAL_NETWORK` compatibility mode; its temporary
  `NEARBY_WIFI_DEVICES` grant is test scaffolding, not the target-SDK-37 production flow;
- when raising the target to SDK 37, declare and request `ACCESS_LOCAL_NETWORK` before UDP broadcast,
  UDP receive or direct WebSocket/TCP access;
- AquaLight's proprietary UDP broadcast plus direct device WebSocket needs broad local-network
  access; do not describe a system picker or mDNS-only path as sufficient;
- treat denial or later revocation as local-network unavailable before socket work, and recover
  after a grant in Settings;
- test fresh grant, denial, revocation, Settings recovery and permission-group pre-grant. Another
  already-granted nearby-device permission can pre-grant local-network access, so the presence or
  absence of a prompt is not a reliable state check;
- do not classify raw socket symptoms as the permission source of truth: denied UDP commonly fails
  immediately while TCP may only time out.

Target SDK must not be raised to 37 until this matrix passes on physical Android 17 hardware or an
official final emulator image.

## Normative platform references

- Android
  [`ProcessLifecycleOwner`](https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner)
- Android
  [read network state with `NetworkCallback`](https://developer.android.com/develop/connectivity/network-ops/reading-network-state)
- Android
  [`Network` socket binding](https://developer.android.com/reference/android/net/Network)
- Android
  [local-network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- ESP-IDF
  [high-resolution timer and boot-relative time](https://docs.espressif.com/projects/esp-idf/en/stable/esp32/api-reference/system/esp_timer.html)
- ESP-IDF 5.5.4
  [Wi-Fi station/IP and socket lifecycle](https://docs.espressif.com/projects/esp-idf/en/v5.5.4/esp32s3/api-guides/wifi.html)
