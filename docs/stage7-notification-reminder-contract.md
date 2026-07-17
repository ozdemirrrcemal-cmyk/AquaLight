# Stage 7 — Notification and reminder commercial contract

## Product boundaries

Stage 7 owns AquaLight's app-level notification preference, notification-channel state, care-reminder scheduling, owner-specific cancellation, deterministic delivery, and restore after boot/package replacement.

Stage 6 remains the sole owner of Android runtime notification-permission decisions and app/channel settings routing. Stage 7 consumes the resulting system state but must not create another runtime-permission flow.

## Owner-scoped application preference

- The AquaLight notification switch is an application preference, separate from Android runtime permission and system/channel blocking.
- The preference is stored per authenticated owner. Account A enabling notifications must not enable them for Account B.
- A missing owner preference means disabled.
- The owner-scoped Stage 7 store is the only source of truth.
- The former `user_prefs.notifications_enabled` value is only an active-session compatibility projection for existing care-task write paths; it is never consulted by reminder delivery as authority.
- The active projection is refreshed from the committed owner's store during reconciliation and cleared to `false` during logout/account switch before a new owner is exposed.
- Only `ActiveNotificationPreferenceProjection` may write the compatibility field.
- Disabling the preference cancels that owner's alarms, queued deliveries, and visible care-task notifications.
- Logging out or switching account cancels the previous owner's reminder work without deleting that owner's saved preference.
- Enabling the preference reconciles all eligible reminders for that owner.

## System and channel state

- Android app notification availability, runtime permission, and channel importance are independent inputs.
- The care-reminder channel is created idempotently on every relevant startup; no process-local `channelCreated` flag is authoritative.
- The channel ID and behavioral defaults are versioned constants.
- The app may update a channel name/description, but it never attempts to override user-selected sound, vibration, or importance after channel creation.
- Channel state is one of `NOT_REQUIRED`, `MISSING`, `BLOCKED`, or `ENABLED`.
- The App Settings switch represents AquaLight's owner preference. When Android or the channel blocks delivery, the preference remains intact and the UI shows the system-blocked state separately.
- App-level blocking opens the application notification settings; care-channel blocking opens that exact channel through the process-safe Stage 6 coordinator.

## Alarm and PendingIntent policy

- Care reminders use inexact `AlarmManager` scheduling. AquaLight does not request exact-alarm special access.
- Reminder delivery must never occur before the persisted trigger time, but Android may defer an inexact alarm for battery optimization.
- Every alarm identity contains both owner UID and task ID.
- Alarm and notification-content PendingIntents include collision-resistant owner + task URI data because extras alone are not part of PendingIntent identity.
- Visible care notifications use the same owner + task value as a NotificationManager tag, so hash collisions cannot replace another task's notification.
- Scheduler APIs require an explicit owner UID; no default current-user argument is allowed.
- Scheduling is idempotent: the existing PendingIntent is cancelled before replacement.
- Completed, deleted, disabled, owner-mismatched, tank-disabled, or past-ineligible tasks are cancelled rather than scheduled.

## Deterministic due and missed reminders

- A future pending task with reminders enabled schedules its due reminder at `dueAtMillis`.
- A past-due pending task schedules a missed reminder only when missed reminders are enabled and `dueAtMillis + missedReminderDays` is still in the future.
- Missed reminder time is derived from the persisted due date, not from process uptime or the moment the first notification happened.
- When both due and missed timestamps are in the past, no alarm is scheduled automatically; this prevents duplicate reminders after every boot.
- After any reminder is delivered, the same scheduling policy computes whether another occurrence remains.

## Durable delivery, boot, and session restore

- The AlarmManager receiver parses owner/task/occurrence only and enqueues a unique WorkManager delivery job. It performs no Firebase, DataStore, coroutine, or notification work itself.
- The delivery worker uses `KEEP` for the same owner/task/occurrence, rechecks the active owner, owner preference, task, tank, channel, and runtime permission, then reconciles the deterministic next occurrence.
- Delivery and restore use bounded exponential retry; normal WorkManager cancellation is rethrown rather than retried.
- Android cancels AlarmManager alarms when the device shuts down, so boot/package replacement triggers owner-scoped reconciliation from persisted care tasks.
- The boot receiver enqueues durable WorkManager reconciliation rather than performing the full DataStore scan inside `onReceive`.
- Reconciliation verifies the authenticated owner before preference projection, before scheduling, and after scheduling.
- Session startup enqueues reconciliation for the committed owner.
- Session shutdown cancels reconciliation work, owner-tagged delivery work, alarms, the active preference projection, and visible notifications for the outgoing owner.

## Notification delivery

A care notification is delivered only when all conditions are true:

1. authenticated owner equals the alarm owner;
2. owner application preference is enabled;
3. Android runtime notification permission is granted where required;
4. app notifications are enabled by Android;
5. the care-reminder channel is enabled where channels exist;
6. task remains pending and reminder-enabled;
7. task belongs to the owner;
8. referenced tank exists and care reminders are enabled.

Notification deep links require a positive task ID, a nonblank notification owner, an authenticated session, and exact equality with the committed active owner. Ownerless and cross-owner intents fail closed.

## Commercial test gates

- owner A/B preference isolation;
- owner-specific PendingIntent URI and notification-tag isolation;
- disable, logout, and account-switch cancellation;
- active projection clear/refresh without cross-owner leakage;
- due/missed/past schedule matrix;
- idempotent rescheduling;
- channel missing/blocked/enabled state matrix;
- app-level and exact-channel settings return;
- boot and package-replacement reconciliation;
- process death during alarm receiver, delivery worker, and reconciliation worker execution;
- notification tap owner validation;
- owner-preference corruption recovery;
- API 27 and API 35 instrumentation plus minified release smoke;
- architecture guards preventing UI or receivers from bypassing the Stage 6 permission boundary or Stage 7 reminder coordinator.
