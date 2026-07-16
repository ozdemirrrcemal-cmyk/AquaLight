# Stage 3 Aquarium and Care Boundaries

Branch: `feature/stage-3-aquarium-care-boundaries`

## Scope

- Move owner tank observation and mutations behind application contracts.
- Preserve authoritative tank deletion followed by care-task and device-assignment cleanup.
- Move maintenance operations and models out of the data-facing UI contract.
- Remove DataStore managers and concrete care/assignment repositories from aquarium and maintenance ViewModels.
- Enforce production/release-smoke parity with fake-backed tests and CI guards.

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
