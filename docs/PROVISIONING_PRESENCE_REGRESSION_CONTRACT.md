# Provisioning and Presence Regression Contract

## Purpose

The firmware-runtime transition may replace internal WebSocket command, parser, correlation, and module-state code. It must not change the accepted commercial behavior of:

- QR pairing;
- Nearby/BLE provisioning;
- credential ownership and storage;
- provisioning transaction commit/rollback/recovery;
- registered-device presence;
- online/offline presentation;
- network-loss and network-return recovery.

This document is a release-blocking compatibility contract.

## Pinned Android baseline

- repository: `ozdemirrrcemal-cmyk/AquaLight`
- commit: `120a2c8520ad5a8d91f8c9d9a709db07243ca41b`
- provisioning commercial audit: `docs/provisioning-commercial-audit.md`

## Architectural boundaries that must remain

### Provisioning

The production provisioning shape remains one path:

```text
QR or Nearby discovery
→ owner-scoped encrypted draft/session
→ BLE GATT provisioning
→ opaque runtime handoff reference
→ runtime token staging
→ provisional untrusted device snapshot
→ authenticated WebSocket metadata validation
→ prepared registration
→ process-death recovery record
→ credential commit
→ verified snapshot commit
→ recovery-record cleanup
```

There must not be a second production provisioning adapter, second credential path, or second registration transaction path.

### Presence

`DevicePresenceRuntimeMonitor` remains the single reducer for user-visible online/offline state. It combines:

- UDP discovery evidence;
- WebSocket connection state;
- authenticated runtime messages and control proof;
- Android local-network availability;
- local-network generation changes;
- foreground/background state;
- elapsed monotonic time;
- bounded reconnect attempts.

Module data freshness (`READY`, `STALE`, `ERROR`) must not replace `DeviceOnlineState`.

## Protected provisioning invariants

### QR contract

- Both accepted production QR encodings remain supported:
  - JSON object form;
  - query/URL field form.
- QR byte-size, version, brand, device UID, serial, product, model, hardware revision, SKU, provisioning ID, claim code, and BLE name validation remain fail closed.
- QR claim code and raw QR material must not enter ViewModel state, navigation arguments, logs, analytics, or durable plaintext storage.
- The UI/application boundary receives only an opaque, owner-scoped secret reference.
- The QR secret remains TTL-limited and encrypted.
- Wrong Wi-Fi password retry in the same flow must preserve only the minimum encrypted secret material required for retry.
- Expiry, abandonment, account deletion, and owner change must remove QR secrets.

### BLE and runtime handoff

- BLE scanning/address resolution/GATT remain provisioning transports, not runtime-control transports.
- Runtime token material remains in the data layer and is represented above it only by an opaque handoff ID.
- A handoff must be rejected if its opaque reference does not resolve to the exact stored handoff identity.
- Closing or restarting transport clears transient handoff/prepared state.
- Cancellation must not commit a token or device snapshot.

### Credential transaction

The required order is:

1. read previous registered snapshot and committed runtime token;
2. register an owner/device-scoped pending transaction;
3. stage the new runtime token;
4. stage a provisional untrusted snapshot;
5. connect using the staged token;
6. validate exact authenticated identity, capabilities, modules, product catalog, and device UID;
7. prepare the registration result;
8. record the recovery journal containing the verified snapshot and runtime token;
9. commit the staged token;
10. commit the verified snapshot;
11. remove pending/transient state;
12. clear the recovery journal.

A queued WebSocket write, socket-open event, UDP packet, or unauthenticated metadata is never sufficient to commit registration.

### Rollback

For a new-device transaction failure:

- remove the provisional registration;
- clear staged/committed transaction credentials as required;
- remove pending state;
- leave no ghost device.

For existing-device reconfiguration failure:

- restore the previous runtime token;
- restore the previous verified snapshot;
- remove staged transaction state;
- preserve the original registered device.

Rollback is non-cancellable once durable commit or recovery cleanup begins.

### Process-death recovery

- The recovery journal is recorded before the first irreversible final commit.
- Startup recovery is owner-scoped and idempotent.
- Process death after token commit but before snapshot commit must converge to the verified token/snapshot pair.
- A stale or different-owner journal must not be applied.
- Recovery records must not contain plaintext outside the encrypted store.

### Owner isolation

- Runtime tokens, QR secrets, drafts, pending transactions, recovery journals, known devices, and runtime state are owner-scoped.
- Account change requires the previous owner repository/runtime state to be shut down before the new owner is installed.
- Account deletion performs exhaustive cleanup; one cleanup failure must not skip later cleanup actions.
- Old-owner WebSocket callbacks, pending responses, or reconnect jobs must not update the new owner.

## Protected metadata bootstrap contract

Provisioning depends on the same runtime metadata publication as normal registered-device operation. No parallel provisioning-only parser/reducer is allowed.

After authentication, bootstrap requires exact matching successful responses for:

- `device.identity.get`;
- `device.capabilities.get`;
- `device.status.get`.

Correlation must include:

- device UID;
- connection/session generation;
- request ID;
- module;
- action.

The resulting snapshot is valid only when:

- all three responses belong to one current authenticated generation;
- exact schemas parse successfully;
- identity UIDs and product fields are mutually consistent;
- capability/limit/module fields satisfy the commercial product catalog;
- firmware runtime endpoint metadata matches the selected transport contract.

`hasValidatedRuntimeMetadata` semantics must remain available to provisioning, OTA, menus, and settings.

The current ten-second provisioning metadata timeout may be refactored internally, but user-visible behavior and bounded failure/rollback must remain.

## Protected presence semantics

### Evidence hierarchy

From weakest to strongest:

1. locally stored known-device snapshot;
2. UDP announcement from a source-bound private endpoint;
3. WebSocket transport connection;
4. authenticated WebSocket session;
5. validated signed runtime response/control proof.

Lower evidence must never overwrite stronger authenticated identity/capability metadata.

### Device online states

The existing state vocabulary remains compatible:

- `UNKNOWN`
- `DISCOVERING`
- `ONLINE_LAN`
- `CONNECTING_WS`
- `AUTH_REQUIRED`
- `AUTHENTICATED`
- `OFFLINE`
- `STALE`
- `ERROR`
- `LOCAL_NETWORK_OFFLINE`
- `PROVISIONING`
- `OTA_UPDATING`

### UDP behavior

- A valid announcement stamps LAN evidence and may move a device to `ONLINE_LAN`.
- UDP does not prove authentication or successful control.
- Missing UDP does not immediately make an authenticated, recently responsive device offline.
- A stricter UDP parser must preserve valid real firmware packets exactly.
- Source binding/private-address enforcement may reject previously accepted spoofed packets without changing valid-device behavior.

### Authenticated runtime proof

- An exact successfully verified signed response may update runtime/control-proof timestamps.
- A queued command write is not proof.
- Events and errors may update last-runtime-message evidence but are not successful control proof.
- The periodic `network.status.get` liveness probe remains supported.
- The transition may route this probe through the common command executor, but the presence monitor must receive the same proof semantics.

### Local network loss

When Android reports no usable local network:

- visible state becomes `LOCAL_NETWORK_OFFLINE`;
- WebSocket sessions disconnect;
- authenticated/runtime/control proof timestamps are invalidated;
- discovery is reevaluated;
- no aggressive reconnect loop runs while the network is unavailable.

### Local network generation/path change

When Wi-Fi/LAN path generation changes:

- discovery scanner restarts on the new route;
- old runtime proof is invalidated;
- old-generation WebSocket sessions and pending commands are cancelled;
- foreground grace may temporarily preserve an existing authenticated visual state while revalidation occurs;
- bounded recovery reconnects only devices eligible for retry.

### Foreground/background behavior

- Background mode does not run foreground-rate discovery/probe loops.
- Foreground entry begins bounded revalidation.
- During the existing grace period, a previously authenticated device is not allowed to visibly flicker through transient candidate states.
- After grace/revalidation, the canonical presence reducer decides the state.

### Disconnect/reconnect interaction with module state

- Disconnect cancels pending commands for the affected device and generation.
- Last validated module values become `STALE`, not copied to another device and not immediately erased.
- Presence state is resolved independently by `DevicePresenceRuntimeMonitor`.
- Reauthentication triggers metadata bootstrap and relevant module refresh.
- Old-generation late responses/events are ignored.

## Changes forbidden without a separate reviewed migration

- replacing QR JSON/query support with one format;
- moving claim code or runtime token into UI/application DTOs;
- changing token stage/commit order;
- removing the process-death recovery journal;
- committing a device before authenticated metadata validation;
- using `commitProvisioningSnapshot` for normal device-name mutation;
- creating a provisioning-only identity/capability parser;
- creating a second online/offline reducer;
- deriving online state directly from module data state;
- treating UDP or socket write as authentication/control proof;
- preserving a compatibility provisioning path beside the accepted one.

## Required automated regression tests

### QR and discovery

- valid JSON QR;
- valid query/URL QR;
- malformed/oversize QR;
- wrong version/brand;
- missing/invalid claim code;
- duplicate or different-owner secret reference rejection;
- decoder close/process race and idempotent close;
- Nearby Scan candidate selection and retry.

### Provisioning transaction

- successful new-device registration;
- successful existing-device reconfiguration;
- wrong Wi-Fi password then retry;
- BLE disconnect during provisioning;
- user Back/cancel;
- runtime endpoint unavailable;
- authentication failure;
- metadata response timeout;
- metadata UID/product/catalog mismatch;
- no ghost/duplicate device after every failure;
- rollback restores previous token/snapshot for reconfiguration.

### Process death and owner isolation

- process death before prepared registration;
- process death after recovery-record creation;
- process death after token commit and before snapshot commit;
- idempotent startup recovery;
- different-owner journal isolation;
- account switch during pending runtime callbacks;
- account deletion exhaustive cleanup;
- plaintext absence checks.

### Presence

- UDP-only device becomes `ONLINE_LAN`, not authenticated;
- authentication and successful response become `AUTHENTICATED`;
- socket write without response is not control proof;
- UDP loss while authenticated does not immediately force offline;
- WebSocket loss while UDP remains valid falls back through the existing policy;
- local network loss produces `LOCAL_NETWORK_OFFLINE`;
- network return performs bounded reconnect;
- route generation change rejects old callbacks;
- foreground grace prevents visual flicker;
- background loops use bounded idle cadence;
- pending commands cancel on disconnect;
- module state becomes stale without replacing presence state.

### OTA/provisioning integration

- provisioning cannot finish on stale metadata;
- OTA cannot start without current validated metadata;
- OTA state preserves `OTA_UPDATING` presence behavior;
- reboot/reconnect does not create a duplicate device;
- post-OTA authenticated version confirmation updates the existing device.

## Required physical regression matrix

Before commercial-release approval, run on representative real products:

- QR provisioning;
- Nearby provisioning;
- wrong password retry;
- Back/cancel;
- power interruption;
- phone Wi-Fi loss and return;
- device reboot;
- process recreation;
- existing-device reconfiguration;
- owner/account switch;
- clean reinstall;
- UDP discovery refresh;
- authenticated reconnect;
- OTA start/progress/reboot/version confirmation;
- no ghost/duplicate device.

## Acceptance gate

The runtime transition is not accepted unless:

- every automated regression above passes;
- the physical matrix has recorded evidence;
- token/claim material remains absent from UI, logs, and plaintext storage;
- only one production provisioning path remains;
- only one production presence reducer remains;
- valid pinned-firmware provisioning behavior is unchanged.