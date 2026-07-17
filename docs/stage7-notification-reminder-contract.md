# Stage 7 — Central notification commercial contract

## Scope and ownership

Stage 7 owns AquaLight's central notification platform:

- owner-scoped application preference;
- Android delivery-readiness evaluation;
- permanent notification-channel registry;
- care-reminder alarm and durable-work scheduling;
- visible notification rendering;
- owner-specific cancellation;
- boot, package-replacement and account lifecycle reconciliation.

Stage 6 remains the sole owner of runtime permission requests, rationale/settings sheets and process-safe settings navigation. Stage 7 evaluates delivery readiness but never launches a permission dialog or Android settings intent directly.

## Permanent notification categories

AquaLight has exactly three commercial notification categories:

| Category | Permanent ID | Purpose | Default importance |
|---|---|---|---|
| Care reminders | `care_reminders` | Feeding, maintenance, water testing and other aquarium care reminders | Default |
| Device alerts | `device_alerts` | Important device connectivity, operating and safety alerts | High |
| Device updates | `device_updates` | Device firmware availability, transfer/progress, restart, success and failure | Default |

These IDs are semantic and intentionally unversioned because AquaLight has not shipped a legacy channel contract. After release, IDs remain stable across normal app versions. A new ID is permitted only for a deliberate, genuinely incompatible channel migration.

`device_updates` means AquaLight hardware/firmware updates. It is not an application-update channel.

Channel creation is idempotent. AquaLight may refresh localized names and descriptions, but it never deletes channels or attempts to overwrite user-selected importance, sound or vibration after Android has created them.

## One owner preference source

- `notification_preferences.pb` is the only notification-preference store.
- The preference is keyed by authenticated owner UID.
- A missing owner record means disabled.
- Owner A's preference cannot enable or disable Owner B.
- The removed global `UserPreferences.notificationsEnabled` field number and name are permanently reserved and cannot be reused.
- There is no legacy projection, compatibility adapter, dual write, fallback or secondary notification boolean.
- Disabling the preference cancels that owner's alarm/work and visible notifications without deleting the saved preference record.
- Account logout/switch cancels only the outgoing owner's runtime state.

## Central application boundary

The product boundary consists of:

- `NotificationPermissionPolicy`
- `NotificationChannelRegistry`
- `NotificationPreferenceUseCase`
- `NotificationScheduler`
- `NotificationRenderer`

`NotificationPreferenceUseCase` is the application-facing orchestration boundary. App Settings and Add Care Task use the same instance from `AppContainer`.

UI code must not use:

- `AlarmManager`;
- `WorkManager`;
- `NotificationManager` or `NotificationManagerCompat`;
- `NotificationChannelRegistry`;
- notification renderer/scheduler implementations;
- owner notification DataStore;
- raw channel IDs.

`NotificationHelper` does not exist. Channel management, readiness checks, scheduling, rendering and cancellation are separate responsibilities.

## Preference, permission and channel state

The following states are independent:

1. AquaLight owner preference;
2. Android 13+ `POST_NOTIFICATIONS` runtime grant;
3. Android app-level notification enablement;
4. each category channel's state.

A category channel is `NOT_REQUIRED`, `MISSING`, `BLOCKED` or `ENABLED`.

The App Settings switch represents only the owner preference. If Android or a channel is later blocked, the switch remains enabled and the UI shows the delivery problem separately. Repair navigation is delegated to the Stage 6 coordinator:

- missing runtime permission → central runtime permission flow;
- app-level block → app notification settings;
- category block → exact category channel settings.

Add Care Task evaluates only the care-reminder category before saving a reminder-enabled task, but uses the same use-case as App Settings.

## Rendering contract

`AndroidNotificationRenderer` is the only production component allowed to build, post, update or cancel visible notifications.

- Care reminders use an owner + task identity and fail-closed care-task deep link.
- Device alerts use an owner + device identity and high-priority alert rendering.
- Device updates use an owner + device identity, stable progress updates and `onlyAlertOnce` behavior.
- Visible notifications use stable owner/category/entity tags rather than relying only on integer IDs.
- Owner-specific cancellation enumerates active notifications and removes only tags belonging to the requested owner.
- No account transition uses app-wide `cancelAll()`.

Device alert and device-update screens/services will call the central application boundary when those product flows are connected. They will not select channel IDs or construct notifications.

## Scheduler and alarm contract

Care reminders use inexact `AlarmManager` scheduling. AquaLight does not request `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`, because care reminders do not require alarm-clock-level exactness.

- Scheduler APIs require explicit owner UID and task ID.
- Alarm PendingIntent identity includes owner + task URI data; extras alone are not treated as identity.
- Scheduling is idempotent and replaces the same owner/task occurrence safely.
- Eligibility is evaluated centrally from owner preference, task status, task reminder setting, referenced tank and tank care-reminder setting.
- Completed, deleted, disabled, owner-mismatched or tank-disabled tasks have their alarms cancelled.
- Care persistence never calls AlarmManager or the alarm backend directly; it asks `NotificationPreferenceUseCase` to schedule/cancel the task.

## Deterministic due and missed reminders

- A future pending task schedules the persisted `dueAtMillis` occurrence.
- A past-due task schedules a missed occurrence only when missed reminders are enabled and `dueAtMillis + missedReminderDays` remains in the future.
- Missed timing is derived from persisted task data, never process uptime or previous notification time.
- If due and missed times are both past, boot/reconcile does not repeatedly recreate an old notification.
- After delivery, the same deterministic policy computes whether another occurrence remains.

## Durable delivery and restore

The AlarmManager receiver is enqueue-only. It parses owner/task/occurrence and enqueues unique WorkManager delivery; it performs no Firebase, DataStore, coroutine or notification rendering work.

The delivery worker:

1. validates the active authenticated owner;
2. reads the owner preference through the central use-case;
3. evaluates care-channel delivery readiness;
4. revalidates task, tank and occurrence;
5. renders through the central renderer;
6. reconciles the next occurrence through the central scheduler.

Duplicate owner/task/occurrence delivery uses unique work with `KEEP`. Transient failures use bounded exponential backoff. WorkManager cancellation is propagated rather than retried.

Boot and `MY_PACKAGE_REPLACED` receivers enqueue owner-scoped reconciliation only. The worker verifies owner identity before and after reconciliation. Session startup enqueues reconciliation for the committed owner. Account shutdown cancels the outgoing owner's reconciliation, delivery work, alarms and visible notifications.

## Fail-closed rules

- Blank owner IDs and invalid task IDs are rejected.
- Ownerless or cross-owner care notification intents do not open a task.
- An owner change during reconciliation cancels the outgoing owner's newly reconciled state.
- A delivery worker does nothing when the authenticated owner no longer matches.
- A missing/blocked permission or channel prevents posting without changing the owner preference.
- Store corruption uses the existing recovery path and defaults to notifications disabled.

## Commercial verification gates

Stage 7 is not complete until all of the following pass:

- owner A/B preference isolation and persistence;
- removed global field/projection/helper absence;
- exact permanent channel-ID contract for all three categories;
- API 27 channel-not-required behavior;
- API 35 channel creation and blocked/enabled behavior;
- app preference, runtime permission, app-level block and per-channel block separation;
- App Settings and Add Care Task common-use-case verification;
- due/missed/future/past deterministic schedule matrix;
- idempotent scheduling and duplicate delivery prevention;
- owner-specific alarm, work and visible-notification cancellation;
- account switch during reconciliation and delivery;
- boot/package-replacement restore for the authenticated owner only;
- process death during settings, receiver, delivery and reconciliation flows;
- notification deep-link owner validation;
- notification-preference corruption recovery;
- device-alert and device-update renderer/category contract tests;
- architecture guards in Android CI, CodeQL and release workflows;
- API 27/API 35 instrumentation and minified release smoke;
- real-device acceptance for permission denial/settings return, category blocking, reminder delivery, reboot and account switch.

The PR remains draft and unmerged until automated gates and physical acceptance are complete.
