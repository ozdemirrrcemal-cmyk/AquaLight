# Stage 3 Aquarium and Care Boundaries

Branch: `feature/stage-3-aquarium-care-boundaries`

## Scope

- Move owner tank observation and mutations behind application contracts.
- Preserve authoritative tank deletion followed by care-task and device-assignment cleanup.
- Move maintenance operations and models out of the data-facing UI contract.
- Remove DataStore managers and concrete care/assignment repositories from aquarium and maintenance ViewModels.
- Enforce production/release-smoke parity with fake-backed tests and CI guards.

## Completed aquarium slice

- `AquariumTankOperations` is the owner-scoped application contract for observation and mutations.
- Tank, draft, plant, material and livestock values crossing into UI are application DTOs.
- Authoritative deletion and dependent care-task/device-assignment cleanup remain behind the data adapter.
- `AquariumTankViewModel` receives a single application dependency.
- Production and release-smoke use the same adapter binding.
- Aquarium list, create, detail, settings, export and care-profile UI call-sites no longer consume data aquarium models.
- Fake-backed ViewModel tests and the aquarium CI boundary guard are present.

## Remaining care slice

- Introduce application care task models and operations.
- Move Smart Care synchronization and manual/automatic task commands behind the application adapter.
- Remove data care repository/models from `MaintenanceViewModel` and maintenance UI.
- Add deterministic fakes, behavior tests and a dedicated care boundary guard.

## Non-negotiable behavior

- Tank create, update, duplicate and delete behavior remains unchanged.
- Tank deletion frees assigned devices even when dependent cleanup reports a recoverable issue.
- Smart Care synchronization and reminder scheduling/cancellation remain owner-scoped.
- Manual and automatic care task behavior, filtering, completion and history remain unchanged.
- No temporary adapter in UI, dual constructor path or service-locator fallback may remain.

## Merge gate

- Architecture guards.
- Debug and Release unit tests and lint.
- Uncached Debug and minified signed Release builds.
- CodeQL.
- API 27 and API 35 minified release-smoke instrumentation.
- Targeted physical regression for tank lifecycle, assignment cleanup and care reminders.
