# Timer commercial architecture

## Scope

Standalone Timer products use the same owner-scoped device entry and control-surface preparation
flow as Dosing, Cooling and Light. The standalone Timer API remains isolated from the internal
Dosing timer engine.

## Single owner-scoped dependency path

`OwnerDependencyGraph` constructs one `DeviceTimerControlOperations` adapter over the owner-scoped
`DevicesRepository`. That exact instance is injected into:

- `DefaultDeviceControlSurfacePreparationOperations` for fail-closed menu preparation;
- `DeviceTimerRootViewModel` for the dashboard;
- `DeviceTimerChannelViewModel` for channel control;
- `DeviceTimerProgramViewModel` for schedule replacement.

No destination constructs another Timer adapter, runtime repository or state store. Runtime status,
mutations, readback reconciliation and typed events converge on the one Timer runtime state owner.

## Package boundaries

- Application contract: `application/devices/timer/control`
- Data adapter: `data/devices/timer/control`
- Firmware V1 failure mapping: `data/devices/timer/v1`
- Firmware-aligned runtime core: `data/devices/runtime/modules/timer`
- Presentation root: `ui/tabs/devices/detail/timer/presentation/root`
- Presentation destinations: `presentation/dashboard`, `channel`, `program`, `settings`, `common`

Fragments are lifecycle and navigation shells. Bottom-sheet presentation/result routing belongs to
destination-specific coordinators. Presentation code depends only on the application contract and
cannot import Timer runtime or repository types.

## Quality contract

Timer source, tests and Timer-specific XML must not use Detekt, Android Lint, ktlint or inspection
suppressions. Timer paths must not appear in the Detekt advisory debt inventory or Android Lint
baseline. `tools/timer_v1_contract_guard.py` enforces these conditions together with the pinned
firmware command and golden-wire contract.

The refactor does not change Timer command names, payload fields, revision CAS, scoped readback,
event ordering, retry behavior, capability gates or fail-closed interaction behavior.
