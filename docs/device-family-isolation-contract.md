# Device Family Isolation Contract

## Status

This is a mandatory production architecture rule for AquaLight device families.
The executable authority is `tools/device_family_isolation_guard.py`; CI must fail
when this contract is violated.

## Core rule

**Device families never depend on another device family's implementation. Shared
behavior belongs in a genuinely shared contract/core. Family-specific behavior
stays inside the owning family boundary.**

For Dosing and Cooling this means:

```text
ui/.../cooling           X--> ui/.../dosing
ui/.../cooling           X--> application/.../dosing
ui/.../cooling           X--> data/.../dosing

application/.../cooling  X--> ui/.../dosing
application/.../cooling  X--> application/.../dosing
application/.../cooling  X--> data/.../dosing

data/.../cooling         X--> ui/.../dosing
data/.../cooling         X--> application/.../dosing
data/.../cooling         X--> data/.../dosing

ui/.../dosing            X--> ui/.../cooling
ui/.../dosing            X--> application/.../cooling
ui/.../dosing            X--> data/.../cooling

application/.../dosing   X--> ui/.../cooling
application/.../dosing   X--> application/.../cooling
application/.../dosing   X--> data/.../cooling

data/.../dosing          X--> ui/.../cooling
data/.../dosing          X--> application/.../cooling
data/.../dosing          X--> data/.../cooling
```

`X-->` means forbidden dependency.

## Family ownership

A production Kotlin file is family-owned when it lives below a Dosing or Cooling
segment inside one of these layers:

- `application/devices/**`
- `data/devices/**`
- `ui/tabs/devices/detail/**`

This intentionally covers both canonical feature roots and nested runtime roots.
For example, both of these are Cooling-owned:

- `application/devices/cooling/**`
- `data/devices/runtime/modules/cooling/**`

The same rule applies to Dosing-owned paths such as:

- `application/devices/dosing/**`
- `data/devices/dosing/**`
- `ui/tabs/devices/detail/dosing/**`

## Allowed shared dependencies

Both families may depend inward on genuinely shared contracts and presentation
infrastructure, including:

- `application/devices` shared contracts and primitive application DTOs
- `ui/common` shared presentation components
- central device navigation and route contracts
- shared AquaHeader/device-presence presentation
- design-system resources: strings, dimensions, colors, styles and icons
- standard Android/Kotlin libraries that do not contain another family implementation

A shared contract must remain family-neutral. Moving a Dosing-specific type into a
common package does **not** make it shared. The guard rejects family-specific symbol
names from the opposite family even when their package is common.

## Common extraction rule

If Dosing and Cooling need the same behavior:

1. Do not import one family's implementation from the other family.
2. Identify the truly family-neutral contract or primitive behavior.
3. Move only that neutral contract/behavior to the shared device core.
4. Keep Dosing and Cooling implementations separate behind that shared contract.

A helper is not common merely because two callers could reuse it. It becomes common
only when its semantics contain no pump-, dosing-channel-, fan-, temperature-,
humidity-, cooling-mode- or other family-specific policy.

## Composition rule

Central composition/orchestration code outside family-owned packages may know that
multiple families exist and may select or wire their implementations. This is the
only permitted direction:

```text
central composition / dispatcher
        |                 |
        v                 v
   Dosing contract    Cooling contract
        |                 |
        v                 v
   Dosing impl        Cooling impl
```

The reverse direction is forbidden. `Dosing impl -> Cooling impl` and
`Cooling impl -> Dosing impl` are always architecture violations.

For control-surface preparation, a shared coordinator may dispatch by family, but
Dosing preparation logic remains Dosing-owned and Cooling preparation logic remains
Cooling-owned. The coordinator must not contain pump/fan/temperature algorithms.

## UI rule

Family screens may share the application shell but not screen implementations.
Common UI includes header, navigation behavior, connection presentation and design
resources. Dosing cards/screens/composables are not reusable Cooling components, and
Cooling cards/screens/composables are not reusable Dosing components.

## Data/runtime rule

Wire parsing, state ownership, mutation handling and command validation are
family-owned. Dosing runtime/state cannot be used as a shortcut for Cooling, and
Cooling runtime/state cannot be used as a shortcut for Dosing.

## Enforcement

`tools/device_family_isolation_guard.py` scans production Kotlin sources and rejects:

- cross-family fully qualified package dependencies in either direction;
- cross-family family-specific identifiers such as `DeviceDosing...` from Cooling
  code or `DeviceCooling...` from Dosing code;
- a source path that attempts to carry both family ownership markers.

`tools/tests/test_device_family_isolation_guard.py` contains regression tests for UI,
application and data/runtime boundaries in both directions, plus tests proving that
shared contracts and central composition remain legal.

The guard is invoked by `tools/navigation_guard.py`, which is part of the existing
Android CI, CodeQL and commercial release-quality guard chain.
