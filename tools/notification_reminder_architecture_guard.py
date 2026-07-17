#!/usr/bin/env python3
"""Enforce AquaLight Stage 7 notification/reminder architecture."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
errors: list[str] = []


def load(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        errors.append(f"missing required Stage 7 file: {relative}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def require(relative: str, token: str, reason: str) -> None:
    text = load(relative)
    if token not in text:
        errors.append(f"{relative}: {reason}: missing {token}")


def forbid(relative: str, token: str, reason: str) -> None:
    text = load(relative)
    if token in text:
        errors.append(f"{relative}: {reason}: forbidden {token}")


required_files = (
    "docs/stage7-notification-reminder-contract.md",
    "app/src/main/proto/notification_preferences.proto",
    "app/src/main/java/com/aqua/aqualight/data/notifications/NotificationChannelRegistry.kt",
    "app/src/main/java/com/aqua/aqualight/data/notifications/OwnerNotificationPreferences.kt",
    "app/src/main/java/com/aqua/aqualight/data/notifications/ActiveNotificationPreferenceProjection.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderCoordinator.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderSchedulePolicy.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderIdentity.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderReconcileWorker.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderDeliveryWorker.kt",
    "app/src/main/java/com/aqua/aqualight/ui/navigation/CareTaskNotificationRoutePolicy.kt",
    "app/src/test/java/com/aqua/aqualight/data/notifications/NotificationPreferenceStoreRulesTest.kt",
    "app/src/test/java/com/aqua/aqualight/data/care/reminder/CareReminderSchedulePolicyTest.kt",
    "app/src/test/java/com/aqua/aqualight/data/care/reminder/CareReminderOwnerIdentityTest.kt",
    "app/src/test/java/com/aqua/aqualight/data/care/reminder/CareReminderReconcileRuntimeTest.kt",
    "app/src/test/java/com/aqua/aqualight/ui/navigation/CareTaskNotificationRoutePolicyTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/notifications/OwnerNotificationPreferencesInstrumentedTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/notifications/ActiveNotificationPreferenceProjectionInstrumentedTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/notifications/NotificationChannelRegistryInstrumentedTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/recovery/NotificationPreferencesCorruptionRecoveryInstrumentedTest.kt",
)
for relative in required_files:
    load(relative)

scheduler = "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskReminderScheduler.kt"
for token, reason in (
    ("CareReminderSchedulePolicy.plan", "deterministic persisted-time planning is required"),
    ("EXTRA_OWNER_UID", "alarm must carry immutable owner identity"),
    ("EXTRA_OCCURRENCE", "alarm must carry due/missed occurrence"),
    ("CareReminderIdentity.alarmData", "alarm PendingIntent must include owner/task URI data"),
    ("setAndAllowWhileIdle", "care reminders must remain inexact and idle-compatible"),
):
    require(scheduler, token, reason)
for token in (
    "UserDataScope.currentUid()",
    "setExact(",
    "setExactAndAllowWhileIdle(",
    "scheduleMissedReminder",
):
    forbid(scheduler, token, "scheduler bypasses the Stage 7 alarm policy")

receiver = "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskReminderReceiver.kt"
require(receiver, "CareReminderDeliveryWorker.enqueue", "alarm receiver must enqueue durable delivery")
for token in (
    "goAsync()",
    "FirebaseAuthenticatedOwnerProvider",
    "CareTaskDataStoreManager",
    "OwnerNotificationPreferences",
    "CoroutineScope",
    "NotificationHelper",
):
    forbid(receiver, token, "alarm receiver must remain enqueue-only")

delivery = "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderDeliveryWorker.kt"
for token, reason in (
    ("OwnerNotificationPreferences", "delivery must re-check owner preference"),
    ("FirebaseAuthenticatedOwnerProvider", "delivery must validate active owner"),
    ("CareReminderDeliveryPolicy.shouldDeliver(task, tank)", "delivery must revalidate task/tank"),
    ("ExistingWorkPolicy.KEEP", "duplicate broadcasts must not duplicate in-flight work"),
    ("addTag(ownerTag(owner))", "outgoing-owner delivery must be cancellable"),
    ("BackoffPolicy.EXPONENTIAL", "transient failure requires backoff"),
    ("MAX_ATTEMPTS", "delivery retries must be bounded"),
):
    require(delivery, token, reason)
for token in ("UserPreferencesManager", "scheduleMissedReminder"):
    forbid(delivery, token, "delivery must not use legacy preference authority or relative missed timing")

boot = "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskBootReceiver.kt"
require(boot, "CareReminderReconcileWorker.enqueue", "boot/package restore must enqueue durable work")
for token in ("goAsync()", "CareTaskDataStoreManager", "AlarmManager"):
    forbid(boot, token, "boot receiver must remain enqueue-only")

reconcile = "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderReconcileWorker.kt"
for token, reason in (
    ("enqueueUniqueWork", "owner reconciliation must be unique"),
    ("CareReminderReconcileRuntime", "owner stability boundary is required"),
    ("BackoffPolicy.EXPONENTIAL", "restore failure requires backoff"),
    ("MAX_ATTEMPTS", "restore retries must be bounded"),
):
    require(reconcile, token, reason)

coordinator = "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderCoordinator.kt"
for token, reason in (
    ("OwnerNotificationPreferences", "owner preference source must remain central"),
    ("ActiveNotificationPreferenceProjection", "legacy active projection must remain central"),
    ("UserDataScope.withOwnerUid", "task reads must be owner-pinned"),
    ("careTasks.tasksFlow.first()", "reconcile must remove stale non-pending alarms"),
    ("CareTaskReminderScheduler.cancel", "ineligible alarms must be cancelled"),
    ("CareTaskReminderScheduler.schedule", "eligible alarms must be scheduled"),
):
    require(coordinator, token, reason)

projection = "app/src/main/java/com/aqua/aqualight/data/notifications/ActiveNotificationPreferenceProjection.kt"
for token, reason in (
    ("OwnerNotificationPreferences", "projection must refresh from owner store"),
    ("legacyPreferences.updateNotificationsEnabled", "legacy field writer must remain centralized"),
    ("suspend fun clear()", "session shutdown requires explicit clear"),
):
    require(projection, token, reason)

session_services = "app/src/main/java/com/aqua/aqualight/data/auth/SessionBoundServiceManager.kt"
require(session_services, "ActiveNotificationPreferenceProjection.create(appContext).clear()", "logout must clear active projection")
require(session_services, "CareReminderDeliveryWorker.cancelOwner", "logout must cancel queued deliveries")

for source in APP.rglob("*.kt"):
    if source in {
        ROOT / projection,
        ROOT / "app/src/main/java/com/aqua/aqualight/data/user/UserPreferencesManager.kt",
    }:
        continue
    text = source.read_text(encoding="utf-8", errors="ignore")
    if "UserPreferencesManager" in text and ".updateNotificationsEnabled(" in text:
        errors.append(
            f"{source.relative_to(ROOT)}: only ActiveNotificationPreferenceProjection may write the legacy notification field"
        )

channel = "app/src/main/java/com/aqua/aqualight/data/notifications/NotificationChannelRegistry.kt"
for token, reason in (
    ("CARE_REMINDERS_CHANNEL_ID", "channel ID must be versioned and central"),
    ("getNotificationChannel", "channel block state must be readable"),
    ("IMPORTANCE_NONE", "blocked channel state must be represented"),
    ("createNotificationChannel", "channel creation must be idempotent"),
):
    require(channel, token, reason)
for token in ("channelCreated", "deleteNotificationChannel"):
    forbid(channel, token, "app must not override Android channel/user state")

helper = "app/src/main/java/com/aqua/aqualight/utils/NotificationHelper.kt"
for token in (
    "ActivityCompat.requestPermissions",
    "Settings.ACTION_APP_NOTIFICATION_SETTINGS",
    "Settings.ACTION_APPLICATION_DETAILS_SETTINGS",
    "android.app.Activity",
):
    forbid(helper, token, "Stage 6 owns permission/settings UI")
for token, reason in (
    ("NotificationChannelRegistry.CARE_REMINDERS_CHANNEL_ID", "rendering must use central channel"),
    ("CareReminderIdentity.contentData", "content PendingIntent must include owner/task URI"),
    ("manager.notify(notificationTag", "care notifications must use owner/task tags"),
    ("CareReminderIdentity.stableKey", "show/cancel must share stable owner/task tag"),
):
    require(helper, token, reason)

main = "app/src/main/java/com/aqua/aqualight/ui/main/MainActivity.kt"
require(main, "CareTaskNotificationRoutePolicy.canOpen", "notification deep links must fail closed")

manifest = "app/src/main/AndroidManifest.xml"
for token in ("android.permission.SCHEDULE_EXACT_ALARM", "android.permission.USE_EXACT_ALARM"):
    forbid(manifest, token, "AquaLight care reminders do not qualify for exact-alarm access")
require(manifest, "android.permission.RECEIVE_BOOT_COMPLETED", "boot restore permission is required")
require(manifest, "CareTaskBootReceiver", "boot restore receiver must remain registered")

ui_root = APP / "ui"
if ui_root.is_dir():
    for path in ui_root.rglob("*.kt"):
        text = path.read_text(encoding="utf-8", errors="ignore")
        for token in (
            "android.app.AlarmManager",
            "android.app.NotificationChannel",
            "androidx.work.WorkManager",
            "OwnerNotificationPreferences",
            "CareTaskReminderScheduler",
        ):
            if token in text:
                errors.append(f"{path.relative_to(ROOT)}: UI bypasses Stage 7 boundary: {token}")

for obsolete in (
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskBootRuntime.kt",
    "app/src/test/java/com/aqua/aqualight/data/care/reminder/CareTaskBootRuntimeTest.kt",
):
    if (ROOT / obsolete).exists():
        errors.append(f"obsolete synchronous boot reminder path remains: {obsolete}")

if errors:
    print("Notification/reminder architecture guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print(
    "Notification/reminder guard passed: owner preference, channel state, inexact alarms, "
    "durable delivery/restore, projection isolation and Stage 6 settings boundaries are central."
)
