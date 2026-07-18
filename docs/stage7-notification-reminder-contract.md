# Stage 7 — Central notification commercial contract

## Scope and ownership

Stage 7 owns AquaLight's central notification platform:

- owner-scoped application preference;
- Android delivery-readiness evaluation;
- permanent notification-channel registry;
- care-reminder alarm and durable-work scheduling;
- visible notification rendering;
- owner-specific cancellation;
- boot, package-replacement, precise-timing-access and account lifecycle reconciliation.

Stage 6 remains the sole owner of runtime permission requests, rationale/settings sheets and process-safe settings navigation. Its capability boundary also routes the Android **Alarms & reminders** special-access screen. Stage 7 evaluates delivery state and schedules alarms but never launches Android settings directly.

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
- raw channel IDs;
- raw Android permission or special-access Settings intents.

`NotificationHelper` does not exist. Channel management, readiness checks, scheduling, rendering and cancellation are separate responsibilities.

## Preference, permission, channel and timing state

The following states are independent:

1. AquaLight owner preference;
2. Android 13+ `POST_NOTIFICATIONS` runtime grant;
3. Android app-level notification enablement;
4. each category channel's state;
5. Android 12+ **Alarms & reminders** special access for precise user-selected care times.

A category channel is `NOT_REQUIRED`, `MISSING`, `BLOCKED` or `ENABLED`.

The App Settings switch represents only the owner preference. If Android, a channel or precise timing access is later blocked, the switch remains enabled and the UI shows the delivery problem separately. Repair navigation is delegated to the Stage 6 coordinator:

- missing runtime permission → central runtime permission flow;
- app-level block → app notification settings;
- category block → exact category channel settings;
- missing precise timing access → Android Alarms & reminders settings.

Add Care Task evaluates only the care-reminder category and precise timing capability before saving a reminder-enabled task. It uses the same use-case and central coordinator as App Settings. Returning from Settings resumes the save exactly once only when access is actually granted.

## Rendering contract

`AndroidNotificationRenderer` is the only production component allowed to build, post, update or cancel visible notifications.

- Care reminders use an owner + task identity and fail-closed care-task deep link.
- Device alerts use an owner + device identity and high-priority alert rendering.
- Device updates use an owner + device identity, stable progress updates and `onlyAlertOnce` behavior.
- Visible notifications use stable owner/category/entity tags rather than relying only on integer IDs.
- Owner-specific cancellation enumerates active notifications and removes only tags belonging to the requested owner.
- No account transition uses app-wide `cancelAll()`.

Device alert and device-update screens/services will call the central application boundary when those product flows are connected. They will not select channel IDs or construct notifications.

## Scheduler and precise-alarm contract

A user selecting a clock time for a care reminder creates a time-sensitive user-facing alarm contract.

- API 27–30 schedule the persisted time with `setExactAndAllowWhileIdle` without special access.
- API 31+ use `SCHEDULE_EXACT_ALARM`, query `AlarmManager.canScheduleExactAlarms()` and route missing access through the common permission/settings sheet.
- `USE_EXACT_ALARM` is forbidden. AquaLight uses the user-granted special-access path rather than the restricted automatic-grant permission.
- When access is granted, `setExactAndAllowWhileIdle` is used.
- If access is revoked between the grant check and scheduling call, a caught `SecurityException` installs an inexact fallback instead of losing the reminder.
- When Android broadcasts `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` after a grant, the authenticated owner's reminders are reconciled and upgraded to exact alarms.
- Missing access is visible in App Settings and blocks saving a reminder-enabled care task until the user grants access or turns the task's reminder off.

Additional scheduler rules:

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

## Durable and prompt delivery

The AlarmManager receiver remains lightweight. It parses owner/task/occurrence and enqueues one unique durable delivery job; it performs no Firebase, DataStore or notification rendering work.

The delivery request:

- is expedited on Android 12+ because it was triggered by an exact user-visible alarm;
- uses `RUN_AS_NON_EXPEDITED_WORK_REQUEST` as a durability fallback if expedited quota is unavailable;
- uses unique `REPLACE` semantics so an older deferred request cannot block the current alarm occurrence;
- uses bounded exponential retry for transient failures.

The delivery worker:

1. validates the active authenticated owner;
2. reads the owner preference through the central use-case;
3. evaluates care-channel delivery readiness;
4. revalidates task, tank and occurrence;
5. renders through the central renderer;
6. reconciles the next occurrence through the central scheduler.

Boot, `MY_PACKAGE_REPLACED` and precise-access-grant broadcasts enqueue owner-scoped reconciliation only. The worker verifies owner identity before and after reconciliation. Session startup enqueues reconciliation for the committed owner. Account shutdown cancels the outgoing owner's reconciliation, delivery work, alarms and visible notifications.

## Durable alarm ledger

Android does not expose a production API for enumerating all alarms owned by an application. AquaLight therefore persists only the minimum owner/task scheduling identity in `notification_schedule_state.pb`.

- The ledger is owner-scoped and contains no notification text or sensitive device data.
- Successful scheduling adds the owner/task identity.
- Cancellation removes it.
- Reconciliation compares the ledger to current tasks and cancels stale alarms left by process death, rollback, corruption recovery or destructive transactions.
- Logout/account switch cancels the union of persisted tasks and ledger identities before clearing that owner ledger.

The ledger is a permanent scheduler-state contract, not a compatibility projection or secondary preference source.

## Fail-closed rules

- Blank owner IDs and invalid task IDs are rejected.
- Ownerless or cross-owner care notification intents do not open a task.
- An owner change during reconciliation cancels the outgoing owner's newly reconciled state.
- A delivery worker does nothing when the authenticated owner no longer matches.
- A missing/blocked notification permission or channel prevents posting without changing the owner preference.
- Missing precise timing access never silently claims on-time delivery.
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
- precise-reminder special-access denial, Settings return and exactly-once continuation;
- API 27–30 exact-alarm selection without special access;
- API 31+ exact/inexact selection based on `canScheduleExactAlarms()`;
- due/missed/future/past deterministic schedule matrix;
- idempotent scheduling and duplicate delivery prevention;
- expedited alarm-triggered WorkManager delivery contract;
- owner-specific alarm, work and visible-notification cancellation;
- account switch during reconciliation and delivery;
- boot/package-replacement/access-grant restore for the authenticated owner only;
- process death during settings, receiver, delivery and reconciliation flows;
- notification deep-link owner validation;
- notification-preference and schedule-ledger corruption recovery;
- device-alert and device-update renderer/category contract tests;
- architecture guards in Android CI, CodeQL and release workflows;
- API 27/API 35 instrumentation and minified release smoke;
- real-device closed-app acceptance at the selected time, reboot restore and account switch.

The PR remains draft and unmerged until automated gates and physical acceptance are complete.
