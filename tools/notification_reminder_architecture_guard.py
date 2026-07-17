#!/usr/bin/env python3
"""Enforce the Stage 7 owner-scoped notification and reminder contract."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
TESTS = ROOT / "app/src/test/java/com/aqua/aqualight"
errors: list[str] = []


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        errors.append(f"missing required Stage 7 file: {relative}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def require(relative: str, text: str, token: str, reason: str) -> None:
    if token not in text:
        errors.append(f"{relative}: {reason}: missing {token}")


def forbid(relative: str, text: str, token: str, reason: str) -> None:
    if token in text:
        errors.append(f"{relative}: {reason}: forbidden {token}")


required_files = (
    "docs/stage7-notification-reminder-contract.md",
    "app/src/main/proto/notification_preferences.proto",
    "app/src/main/java/com/aqua/aqualight/data/notifications/NotificationChannelRegistry.kt",
    "app/src/main/java/com/aqua/aqualight/data/notifications/OwnerNotificationPreferences.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderCoordinator.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderSchedulePolicy.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderReconcileRuntime.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderReconcileWorker.kt",
    "app/src/test/java/com/aqua/aqualight/data/notifications/NotificationPreferenceStoreRulesTest.kt",
    "app/src/test/java/com/aqua/aqualight/data/care/reminder/CareReminderSchedulePolicyTest.kt",
    "app/src/test/java/com/aqua/aqualight/data/care/reminder/CareReminderReconcileRuntimeTest.kt",
)
for relative in required_files:
    read(relative)

scheduler_path = (
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/"
    "CareTaskReminderScheduler.kt"
)
scheduler = read(scheduler_path)
for token, reason in (
    ("CareReminderSchedulePolicy.plan", "scheduler must use deterministic persisted-time planning"),
    ("EXTRA_OWNER_UID", "alarm identity must carry an immutable owner"),
    ("EXTRA_OCCURRENCE", "alarm identity must carry due/missed occurrence"),
    ("setAndAllowWhileIdle", "commercial care reminders use supported inexact idle scheduling"),
):
    require(scheduler_path, scheduler, token, reason)
for token, reason in (
    ("UserDataScope.currentUid()", "scheduler APIs must never infer the current user"),
    ("setExact(", "care reminders must not request alarm-clock precision"),
    ("setExactAndAllowWhileIdle(", "care reminders must not request exact-alarm special access"),
    ("scheduleMissedReminder", "missed timing must be derived by the central policy"),
):
    forbid(scheduler_path, scheduler, token, reason)

receiver_path = (
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/"
    "CareTaskReminderReceiver.kt"
)
receiver = read(receiver_path)
for token, reason in (
    ("OwnerNotificationPreferences", "delivery must re-check the owner app preference"),
    ("FirebaseAuthenticatedOwnerProvider", "delivery must validate the active owner"),
    ("CareReminderDeliveryPolicy.shouldDeliver(task, tank)", "delivery must revalidate persisted task and tank state"),
    ("CareTaskReminderScheduler.schedule", "delivery must reconcile the deterministic next occurrence"),
):
    require(receiver_path, receiver, token, reason)
for token in ("UserPreferencesManager", "scheduleMissedReminder"):
    forbid(
        receiver_path,
        receiver,
        token,
        "receiver must not depend on the legacy global preference or relative missed timers",
    )

boot_path = (
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskBootReceiver.kt"
)
boot = read(boot_path)
require(
    boot_path,
    boot,
    "CareReminderReconcileWorker.enqueue",
    "boot/package replacement must enqueue durable owner reconciliation",
)
for token in ("goAsync()", "CareTaskDataStoreManager", "AlarmManager"):
    forbid(
        boot_path,
        boot,
        token,
        "boot receiver must remain a lightweight enqueue-only boundary",
    )

worker_path = (
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/"
    "CareReminderReconcileWorker.kt"
)
worker = read(worker_path)
for token, reason in (
    ("enqueueUniqueWork", "owner restore must be idempotent durable work"),
    ("KEY_OWNER_UID", "work must carry an immutable owner UID"),
    ("CareReminderReconcileRuntime", "worker must use a testable owner-stability boundary"),
):
    require(worker_path, worker, token, reason)

coordinator_path = (
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/"
    "CareReminderCoordinator.kt"
)
coordinator = read(coordinator_path)
for token, reason in (
    ("OwnerNotificationPreferences", "coordinator must own the owner-scoped app preference"),
    ("UserDataScope.withOwnerUid", "task reads must remain pinned to one owner"),
    ("CareTaskReminderScheduler.cancel", "reconciliation must remove ineligible owner alarms"),
    ("CareTaskReminderScheduler.schedule", "reconciliation must schedule eligible owner alarms"),
):
    require(coordinator_path, coordinator, token, reason)

channel_path = (
    "app/src/main/java/com/aqua/aqualight/data/notifications/"
    "NotificationChannelRegistry.kt"
)
channel = read(channel_path)
for token, reason in (
    ("CARE_REMINDERS_CHANNEL_ID", "care channel ID must be versioned and centralized"),
    ("getNotificationChannel", "channel block state must be observable"),
    ("IMPORTANCE_NONE", "channel-level blocking must be represented"),
    ("createNotificationChannel", "channel creation must be idempotent"),
):
    require(channel_path, channel, token, reason)
for token in ("channelCreated", "deleteNotificationChannel"):
    forbid(
        channel_path,
        channel,
        token,
        "process memory and channel recreation must not override Android user choices",
    )

helper_path = "app/src/main/java/com/aqua/aqualight/utils/NotificationHelper.kt"
helper = read(helper_path)
for token in (
    "ActivityCompat.requestPermissions",
    "Settings.ACTION_APP_NOTIFICATION_SETTINGS",
    "Settings.ACTION_APPLICATION_DETAILS_SETTINGS",
    "android.app.Activity",
):
    forbid(
        helper_path,
        helper,
        token,
        "Stage 6 owns runtime permission and settings UI",
    )
require(
    helper_path,
    helper,
    "NotificationChannelRegistry.CARE_REMINDERS_CHANNEL_ID",
    "notification rendering must use the Stage 7 channel registry",
)

manifest_path = "app/src/main/AndroidManifest.xml"
manifest = read(manifest_path)
for token in (
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.USE_EXACT_ALARM",
):
    forbid(
        manifest_path,
        manifest,
        token,
        "AquaLight care reminders do not qualify for exact-alarm special access",
    )
for token, reason in (
    ("android.permission.RECEIVE_BOOT_COMPLETED", "boot restore requires the boot permission"),
    ("CareTaskBootReceiver", "boot restore receiver must remain registered"),
):
    require(manifest_path, manifest, token, reason)

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
                errors.append(
                    f"{path.relative_to(ROOT)}: UI bypasses the Stage 7 application boundary: {token}"
                )

obsolete_paths = (
    ROOT / "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskBootRuntime.kt",
    ROOT / "app/src/test/java/com/aqua/aqualight/data/care/reminder/CareTaskBootRuntimeTest.kt",
)
for path in obsolete_paths:
    if path.exists():
        errors.append(f"obsolete synchronous boot reminder path remains: {path.relative_to(ROOT)}")

if errors:
    print("Notification/reminder architecture guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print(
    "Notification/reminder guard passed: owner preference, channel state, inexact alarms, "
    "durable restore and Stage 6 permission boundaries remain centralized."
)
