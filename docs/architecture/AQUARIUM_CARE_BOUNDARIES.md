# Aquarium and Care Boundaries

## Scope

- Move owner tank observation and mutations behind application contracts.
- Preserve authoritative tank deletion followed by care-task and device-assignment cleanup.
- Move maintenance operations and models out of the data-facing UI contract.
- Remove DataStore managers and concrete care/assignment repositories from aquarium and maintenance ViewModels.
- Enforce production/release-smoke parity with fake-backed tests and CI guards.

## Completed aquarium boundary

- `AquariumTankOperations` is the owner-scoped application contract for observation and mutations.
- Tank, draft, plant, material and livestock values crossing into UI are application DTOs.
- Authoritative deletion and dependent care-task/device-assignment cleanup remain behind the data adapter.
- Tank deletion and reminder mutations are pinned to the immutable owner captured at operation start.
- `AquariumTankViewModel` receives a single application dependency.
- Production and release-smoke use the same adapter binding.
- Aquarium list, create, detail, settings, export and care-profile UI call-sites no longer consume data aquarium models.
- ViewModel behavior, adapter mappings, cleanup stages and owner-scope propagation have deterministic tests.
- The aquarium CI guard rejects data-layer regressions, missing bindings, missing mapping tests or removal of owner pinning.

## Completed care boundary

- `MaintenanceOperations` is the owner-scoped application contract for Smart Care and manual task commands.
- Maintenance UI consumes application task models and typed command inputs only.
- The old data-layer maintenance repository and presentation contract are removed; no dual path remains.
- Android text/icon resolution stays in the presentation/platform boundary.
- Every care command is pinned to one immutable owner scope, including Smart Care generation and persistence.
- Smart Care synchronization uses a single `collectLatest` stream; equal tank snapshots do not create duplicate work and newer state cancels stale work.
- Delivery-time reminder policy revalidates task state, tank existence and the tank reminder setting before showing a notification.
- Adapter mappings, enum parity, owner propagation, single-flight synchronization and reminder suppression have deterministic tests.
- The care CI guard rejects obsolete paths, data-model leakage, ownerless Smart Care models, missing tests, missing delivery policy or production/smoke divergence.

## Commercial audit findings

- No UI service-locator fallback, temporary runtime bridge or parallel repository constructor remains.
- Account changes cannot split one multi-step aquarium/care operation across two owners.
- Already-scheduled alarms cannot notify after a tank is deleted or its reminder setting is disabled.
- Application DTO conversion is directly tested rather than inferred only through ViewModel tests.
- One-shot migration workflows and scripts are absent from the final tree.

## Non-negotiable behavior

- Tank create, update, duplicate and delete behavior remains unchanged.
- Tank deletion frees assigned devices even when dependent cleanup reports a recoverable issue.
- Smart Care synchronization and reminder scheduling/cancellation remain owner-scoped.
- Manual and automatic care task behavior, filtering, completion and history remain unchanged.
- No temporary adapter in UI, dual constructor path or service-locator fallback may remain.

## Validation baseline

- Architecture guards.
- Debug and Release unit tests and lint.
- Uncached Debug and minified signed Release builds.
- CodeQL.
- API 27 and target API 36 minified release-smoke instrumentation.
- Targeted physical regression for tank lifecycle, assignment cleanup, Smart Care, care commands and reminders.
