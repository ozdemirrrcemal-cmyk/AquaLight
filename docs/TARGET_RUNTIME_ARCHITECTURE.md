# Target Runtime Architecture

## Objective

Build one production Android runtime data layer that mirrors the pinned AquaLight firmware contract exactly while preserving the existing provisioning and presence boundaries.

The architecture must provide:

- one shared WebSocket security/transport core;
- separate typed module contracts and reducers;
- one owner-scoped runtime repository;
- isolated state and pending commands per device UID and connection generation;
- all 41 authenticated firmware commands;
- exact response/error/event parsing;
- no parallel legacy production path after cutover.

## Non-goals

- copying firmware internal C++ architecture into Android;
- changing QR/BLE provisioning behavior;
- replacing the existing online/offline reducer;
- enabling modules by product-name guesswork;
- retaining old permissive parsers as compatibility fallbacks;
- exposing generic JSON commands to UI/application code.

## High-level shape

```text
                        Owner-scoped DevicesRepository
                                   │
                                   ▼
                         DeviceRuntimeRepository
                                   │
          ┌────────────────────────┼────────────────────────┐
          │                        │                        │
          ▼                        ▼                        ▼
 Shared WebSocket Core      Per-device State Store   Refresh Coordinator
          │                        │                        │
          ▼                        ▼                        ▼
 Exact message router ──► Typed module reducers ◄── Module refresh targets
          │
          ├── Device
          ├── Security
          ├── Network
          ├── Time
          ├── Light
          ├── Timer
          ├── Dosing
          ├── Cooling
          └── Firmware / OTA
```

## Package layout

```text
runtime/
  core/
    RuntimeCommand.kt
    RuntimeCommandExecutor.kt
    RuntimeCommandOutcome.kt
    RuntimePendingRequestRegistry.kt
    RuntimeProtocolFault.kt
    RuntimeRefreshCoordinator.kt
    RuntimeSessionGeneration.kt

  ws/
    AqlWsClient.kt
    AqlWsWireCodec.kt
    AqlWsCrypto.kt
    AqlWsCommandFactory.kt
    AqlWsIncomingMessage.kt
    AqlWsEvent.kt

  parsing/
    ExactJsonObject.kt
    ExactJsonArray.kt
    ExactJsonNumbers.kt
    StrictUtf8.kt
    JsonStructuralLimits.kt

  state/
    DeviceRuntimeState.kt
    DeviceRuntimeStateStore.kt
    RuntimeValue.kt
    RuntimeFreshness.kt
    RuntimeMessageRouter.kt

  modules/
    device/
      DeviceContract.kt
      DeviceRequests.kt
      DeviceResponses.kt
      DeviceEvents.kt
      DeviceParser.kt
      DeviceReducer.kt

    security/
      SecurityContract.kt
      SecurityRequests.kt
      SecurityResponses.kt
      SecurityParser.kt
      SecurityReducer.kt

    network/
      NetworkContract.kt
      NetworkRequests.kt
      NetworkResponses.kt
      NetworkParser.kt
      NetworkReducer.kt

    time/
      TimeContract.kt
      TimeRequests.kt
      TimeResponses.kt
      TimeEvents.kt
      TimeParser.kt
      TimeReducer.kt

    light/
      LightContract.kt
      LightRequests.kt
      LightResponses.kt
      LightEvents.kt
      LightParser.kt
      LightReducer.kt
      LightTemperatureProtection.kt

    timer/
      TimerContract.kt
      TimerRequests.kt
      TimerResponses.kt
      TimerEvents.kt
      TimerParser.kt
      TimerReducer.kt

    dosing/
      DosingContract.kt
      DosingRequests.kt
      DosingResponses.kt
      DosingEvents.kt
      DosingParser.kt
      DosingReducer.kt

    cooling/
      CoolingContract.kt
      CoolingRequests.kt
      CoolingResponses.kt
      CoolingEvents.kt
      CoolingParser.kt
      CoolingReducer.kt

    firmware/
      FirmwareContract.kt
      FirmwareRequests.kt
      FirmwareResponses.kt
      FirmwareEvents.kt
      FirmwareParser.kt
      FirmwareReducer.kt
      OtaManifest.kt
      OtaCoordinator.kt
```

Existing package names may be evolved incrementally, but final ownership and dependency direction must match this design. Do not duplicate existing classes merely to achieve the example filenames.

## Single owner-scoped repository

`DevicesRepositoryProvider` continues to own exactly one `DevicesRepository` for the authenticated owner. That repository owns exactly one `DeviceRuntimeRepository`.

The transition must not add a second production runtime repository beside the current one. New executor/store/router responsibilities are installed inside or directly owned by the existing runtime repository and are shut down with it.

Owner change sequence:

1. stop discovery/presence collectors;
2. cancel all device runtime sessions and pending requests;
3. close security material and module refresh jobs;
4. clear owner-scoped runtime state;
5. close the previous owner repository;
6. install the new owner repository.

## Per-device isolation

Canonical state container:

```kotlin
StateFlow<Map<DeviceUid, DeviceRuntimeState>>
```

Example state shape:

```kotlin
data class DeviceRuntimeState(
    val deviceUid: DeviceUid,
    val generation: Long,
    val authenticated: Boolean,
    val support: DeviceRuntimeSupport,
    val device: RuntimeValue<DeviceStatus>,
    val security: RuntimeValue<SecurityStatus>,
    val network: RuntimeValue<NetworkStatus>,
    val time: RuntimeValue<TimeStatus>,
    val light: RuntimeValue<LightStatus>,
    val timer: RuntimeValue<TimerStatus>,
    val dosing: RuntimeValue<DosingStatus>,
    val cooling: RuntimeValue<CoolingStatus>,
    val firmware: RuntimeValue<FirmwareStatus>,
    val ota: RuntimeValue<OtaStatus>,
    val protocolFault: RuntimeProtocolFault?
)
```

`RuntimeValue<T>` records:

```kotlin
data class RuntimeValue<T>(
    val phase: RuntimeFreshness,
    val value: T?,
    val receivedAtMillis: Long?,
    val receivedAtElapsedMillis: Long?,
    val sourceMessageId: String?,
    val fault: RuntimeModuleFault?
)
```

Freshness phases:

- `UNAVAILABLE`: device does not support the module or no value has ever been obtained;
- `LOADING`: a current-generation refresh is pending;
- `READY`: value was validated in the current authenticated generation;
- `STALE`: a previous validated value exists but the transport/generation is no longer current;
- `ERROR`: current operation or protocol validation failed; previous value may remain available.

A state update for one device UID must never mutate another device entry.

## Capability and module gating

Support is derived only from one current authenticated metadata generation:

- `device.capabilities.get` capabilities and limits;
- supported feature tokens;
- `device.status.get.modules` compile gates.

Examples:

- Dosing products have an internal timer engine but `modules.timerApi=false`; Timer commands and UI remain unavailable.
- WRGB supports Cooling but has fixed fan display names; `supportsFanDisplayName=false`.
- Cooling products may expose fan display-name writes when the feature token is present.
- Temperature protection requires the compatible Light+Temperature product feature.

Command execution checks support before serialization/send and returns `UnsupportedByDevice`, not a fabricated firmware error.

## Typed command contract

Generic `module/action/JSONObject` construction remains private to the WebSocket/core boundary. Public data/application APIs use command-specific types.

Example:

```kotlin
sealed interface RuntimeCommand<R> {
    val module: String
    val action: String
    fun encodeData(): JSONObject
    fun parseSuccess(data: JSONObject): R
}

data class LightManualSetCommand(
    val channels: List<LightManualChannelValue>,
    val clear: Boolean
) : RuntimeCommand<LightManualSetResult>
```

Each command type owns:

- canonical module/action;
- support predicate;
- request invariants;
- exact serialization;
- success status range where command-specific;
- exact successful response parser;
- event-refresh/reducer policy;
- persistence semantics.

## Common command executor

Pending key:

```kotlin
data class RuntimeCorrelationKey(
    val deviceUid: DeviceUid,
    val generation: Long,
    val messageId: String,
    val module: String,
    val action: String
)
```

Execution order:

1. require repository and device session active;
2. require current authenticated generation;
3. require device support for command;
4. validate and serialize typed request;
5. create command/message ID;
6. register pending entry before socket send;
7. send signed frame;
8. if send fails, atomically remove pending entry and return `SendFailed`;
9. route signed response/error by exact key;
10. parse successful response exactly;
11. apply typed reducer/state update;
12. complete caller with typed outcome;
13. ignore late or duplicate completion.

Typed outcomes:

```kotlin
sealed interface RuntimeCommandOutcome<out T> {
    data class Success<T>(val value: T, val messageId: String) : RuntimeCommandOutcome<T>
    data class NotConnected(...) : RuntimeCommandOutcome<Nothing>
    data class NotAuthenticated(...) : RuntimeCommandOutcome<Nothing>
    data class UnsupportedByDevice(...) : RuntimeCommandOutcome<Nothing>
    data class SendFailed(...) : RuntimeCommandOutcome<Nothing>
    data class Timeout(...) : RuntimeCommandOutcome<Nothing>
    data class FirmwareError(...) : RuntimeCommandOutcome<Nothing>
    data class ProtocolError(...) : RuntimeCommandOutcome<Nothing>
    data class Cancelled(...) : RuntimeCommandOutcome<Nothing>
}
```

### Correlation rules

A response/error completes a pending request only when all of the following match:

- current owner repository;
- device UID;
- active connection generation;
- message ID;
- module;
- action.

A message ID match with a different module/action is a protocol fault, not success. A response from an old generation is ignored and cannot mutate state. A duplicate response cannot complete twice.

### Cancellation rules

Cancel relevant pending entries on:

- WebSocket disconnect/failure;
- reconnect/generation replacement;
- device retire/forget;
- local-network loss;
- owner switch/account deletion;
- repository stop/shutdown;
- coroutine caller cancellation where ownership permits.

## Exact JSON boundary

Shared exact parsing utilities enforce:

- required/optional key sets;
- exact JSON types without string/number/boolean coercion;
- duplicate-key rejection at every depth;
- maximum depth 12;
- maximum total keys 128;
- maximum key length 64 UTF-8 bytes;
- finite numeric values;
- exact integers where required;
- bounded UTF-8 strings;
- exact enums;
- exact array counts and item types;
- cross-field consistency.

Unknown fields are rejected when the pinned firmware response schema is exact. Optional/conditional fields are modeled explicitly; strict parsing must not invent fields or make firmware-optional fields unconditionally required.

Wire and UDP byte decoding use a `CharsetDecoder` configured with `CodingErrorAction.REPORT`.

## Message routing

The signed envelope router validates transport/security first, then dispatches by exact `module + action + type`.

### Responses and errors

- `res` and `err` route to the common pending executor.
- Successful response parsing belongs to the command type/module parser.
- A valid firmware `err` becomes `FirmwareError`.
- A signed but structurally invalid response becomes `ProtocolError` and records a module/device fault.

### Events

Only confirmed active routes are accepted:

- `device/status.changed`
- `light/status.changed`
- `timer/status.changed`
- `dosing/status.changed`
- `cooling/status.changed`
- `time/status.changed`
- `firmware/ota.progress`
- `firmware/ota.completed`

Normal status-changed events parse the exact event metadata and typed `result`. A module may:

- reduce directly from a complete mutation result; and/or
- schedule a bounded status refresh when the result is not a complete canonical module status.

Refresh decisions are module-specific and never based on action text alone.

Declared-only events remain unsupported until an active firmware publication contract is added.

## Reducer rules

Reducers receive typed models, never raw `JSONObject`.

A reducer:

- updates only its device/module entry;
- verifies generation/current support;
- records source message ID/time;
- preserves immutable product identity rules;
- never writes durable state unless command semantics and firmware confirmation require it;
- never changes user-visible online/offline state directly.

Normal runtime module values are in-memory. Durable device identity/custom-name and other local caches are updated only through their explicit persistence policies.

## Refresh coordinator

Bootstrap after authentication:

1. `device.identity.get`
2. `device.capabilities.get`
3. `device.status.get`
4. project and validate one metadata generation
5. derive support matrix
6. request required always-present modules:
   - Security
   - Network
   - Time
   - Firmware
7. request only supported product modules:
   - Light and temperature protection
   - Timer
   - Dosing
   - Cooling

Refresh jobs are device/generation scoped, deduplicated, bounded, and cancelled on disconnect. Mutation events may debounce one module refresh rather than create unbounded repeated status calls.

Provisioning continues to wait only for the existing validated metadata gate; it does not need to wait for every product-module status.

## Presence integration

Presence remains owned by `DevicePresenceRuntimeMonitor`.

The runtime layer emits evidence callbacks/events for:

- connecting;
- socket connected;
- authenticated;
- exact successful signed response;
- runtime message received;
- closed/failure;
- authentication required.

A queued write is never evidence. The runtime module state store does not publish `DeviceOnlineState`.

## Device-name persistence

`device.name.set` flow:

1. validate 64 UTF-8-byte/control-character/clear semantics;
2. require authenticated current generation and `nameEditable`;
3. send typed request with explicit `save=true` for durable settings operation;
4. wait for exact matching success;
5. require expected operation/save flags and exact returned status;
6. apply device reducer;
7. update known snapshot only after firmware confirms durable save;
8. let authenticated metadata remain stronger than UDP name hints.

Do not call provisioning snapshot commit APIs for this mutation.

## Module-specific rules

### Light

- exactly one value representation per channel/point;
- unique channel keys;
- finite and range-checked values;
- exact regimes;
- exact program/point/channel counts and bindings;
- temperature protection only when supported.

### Timer

- exact channel/schedule schemas;
- unique channel/schedule identifiers;
- exactly seven booleans for weekdays;
- bound schedules reference known channels;
- no invented 32-byte display-name limit.

### Dosing

- exact pump/schedule schemas;
- finite ml/calibration/reservoir values;
- prime/dose/calibration state transitions are separate typed results;
- calibration confirmation and reservoir refill persistence are explicit;
- no fabricated `reservoirStatus` field;
- no standalone Timer API from internal timer-engine presence;
- no invented 32-byte display-name limit.

### Cooling

- exact fans/rules/counts/bindings;
- runtime `supportsFanDisplayName` is modeled;
- fan-name update items are exact and unique;
- maximum 32 UTF-8 bytes applies only to Cooling fan display names;
- WRGB fixed fan names are not writable.

### Firmware/OTA

- signed manifest verified before release content or plan use;
- configured public key and expected key ID are both mandatory;
- signed bilingual release notes use firmware limits;
- OTA command correlation moves into common executor;
- progress/completed events remain per-device;
- disconnect enters recovering state;
- reauthentication requests status;
- terminal success requires post-reboot authenticated target-version confirmation.

## Migration sequence

1. Commit protocol/gap/regression/architecture documents.
2. Add golden fixture and a 41-command drift guard.
3. Close metadata bootstrap blockers without altering provisioning transaction semantics.
4. Harden shared wire/JSON/UTF-8 structural limits.
5. Introduce common generation-scoped command executor and typed outcomes inside the existing runtime repository.
6. Move Device/Security/Network/Time to the common pipeline.
7. Move Light and temperature protection.
8. Move Timer.
9. Move Dosing.
10. Move Cooling.
11. Move Firmware/OTA pending handling and update manifest release-note contract.
12. Harden UDP parser/scanner without changing presence policy.
13. Install one per-device state store and refresh coordinator as the existing runtime repository's source of truth.
14. Adapt application/settings APIs to typed outcomes.
15. Delete send-only results, permissive parsers, old aliases, and duplicate OTA correlation paths.
16. Run automated regression, static analysis, emulator integration, and physical-device qualification.
17. Open one PR only after the branch is internally complete and reviewed.

## Completion checklist

- 41/41 command registry parity;
- 41/41 typed command paths;
- exact active event routing;
- strict wire/JSON/UDP/OTA boundaries;
- one runtime repository and one per-device state store;
- device/generation/id/module/action correlation;
- disconnect cancellation and stale state;
- capability-gated module access;
- unchanged provisioning and presence behavior;
- no legacy/permissive production path;
- no open critical/high defect;
- real-device evidence before commercial-ready declaration.