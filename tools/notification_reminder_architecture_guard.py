#!/usr/bin/env python3
"""Enforce AquaLight's commercial central notification architecture."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
UI = APP / "ui"
errors: list[str] = []


def path(relative: str) -> Path:
    return ROOT / relative


def load(relative: str) -> str:
    target = path(relative)
    if not target.is_file():
        errors.append(f"missing required notification component: {relative}")
        return ""
    return target.read_text(encoding="utf-8", errors="ignore")


def require(relative: str, token: str, reason: str) -> None:
    if token not in load(relative):
        errors.append(f"{relative}: {reason}; missing {token}")


def forbid(relative: str, token: str, reason: str) -> None:
    if token in load(relative):
        errors.append(f"{relative}: {reason}; forbidden {token}")


CONTRACT = "app/src/main/java/com/aqua/aqualight/application/notifications/NotificationContracts.kt"
REGISTRY = "app/src/main/java/com/aqua/aqualight/data/notifications/NotificationChannelRegistry.kt"
POLICY = "app/src/main/java/com/aqua/aqualight/data/notifications/AndroidNotificationPermissionPolicy.kt"
REPOSITORY = "app/src/main/java/com/aqua/aqualight/data/notifications/OwnerNotificationPreferences.kt"
SCHEDULER = "app/src/main/java/com/aqua/aqualight/data/notifications/DefaultNotificationScheduler.kt"
RENDERER = "app/src/main/java/com/aqua/aqualight/platform/notifications/AndroidNotificationRenderer.kt"
PLATFORM = "app/src/main/java/com/aqua/aqualight/data/notifications/NotificationPlatform.kt"
IDENTITY = "app/src/main/java/com/aqua/aqualight/data/notifications/NotificationIdentity.kt"
APP_SETTINGS = "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/app/AppSettingsFragment.kt"
ADD_CARE = "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/AddCareTaskFragment.kt"
CARE_STORE = "app/src/main/java/com/aqua/aqualight/data/care/CareTaskDataStoreManager.kt"
MAINTENANCE_OPS = "app/src/main/java/com/aqua/aqualight/data/care/DefaultMaintenanceOperations.kt"
TANK_OPS = "app/src/main/java/com/aqua/aqualight/data/aquarium/DefaultAquariumTankOperations.kt"
TANK_CLEANER = "app/src/main/java/com/aqua/aqualight/data/aquarium/delete/OwnerTankDataCleaner.kt"
SMART_CARE_WORKER = "app/src/main/java/com/aqua/aqualight/data/care/smartcare/SmartCareDailyWorker.kt"
ALARM_BACKEND = "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskReminderScheduler.kt"
ALARM_RECEIVER = "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskReminderReceiver.kt"
DELIVERY_WORKER = "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderDeliveryWorker.kt"
BOOT_RECEIVER = "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskBootReceiver.kt"
RECONCILE_WORKER = "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderReconcileWorker.kt"
SESSION_MANAGER = "app/src/main/java/com/aqua/aqualight/data/auth/SessionBoundServiceManager.kt"
USER_CLEANER = "app/src/main/java/com/aqua/aqualight/data/user/UserDataCleaner.kt"
USER_PROTO = "app/src/main/proto/user_prefs.proto"
USER_MANAGER = "app/src/main/java/com/aqua/aqualight/data/user/UserPreferencesManager.kt"
MANIFEST = "app/src/main/AndroidManifest.xml"
DOC = "docs/stage7-notification-reminder-contract.md"

required_files = (
    CONTRACT,
    REGISTRY,
    POLICY,
    REPOSITORY,
    SCHEDULER,
    RENDERER,
    PLATFORM,
    IDENTITY,
    APP_SETTINGS,
    ADD_CARE,
    CARE_STORE,
    MAINTENANCE_OPS,
    TANK_OPS,
    TANK_CLEANER,
    SMART_CARE_WORKER,
    ALARM_BACKEND,
    ALARM_RECEIVER,
    DELIVERY_WORKER,
    BOOT_RECEIVER,
    RECONCILE_WORKER,
    SESSION_MANAGER,
    USER_CLEANER,
    USER_PROTO,
    USER_MANAGER,
    MANIFEST,
    DOC,
    "app/src/main/proto/notification_preferences.proto",
    "app/src/test/java/com/aqua/aqualight/application/notifications/NotificationPreferenceUseCaseTest.kt",
    "app/src/test/java/com/aqua/aqualight/application/notifications/NotificationDispatchUseCaseTest.kt",
    "app/src/test/java/com/aqua/aqualight/application/notifications/CareReminderKindContractTest.kt",
    "app/src/test/java/com/aqua/aqualight/data/notifications/NotificationIdentityTest.kt",
    "app/src/test/java/com/aqua/aqualight/data/care/reminder/CareReminderSchedulePolicyTest.kt",
    "app/src/test/java/com/aqua/aqualight/data/care/reminder/CareReminderReconcileRuntimeTest.kt",
    "app/src/test/java/com/aqua/aqualight/ui/navigation/CareTaskNotificationRoutePolicyTest.kt",
    "app/src/test/java/com/aqua/aqualight/data/aquarium/delete/OwnerTankDataCleanerTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/notifications/OwnerNotificationPreferencesInstrumentedTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/notifications/NotificationChannelRegistryInstrumentedTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/notifications/OwnerNotificationCancellationInstrumentedTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/recovery/NotificationPreferencesCorruptionRecoveryInstrumentedTest.kt",
)
for relative in required_files:
    load(relative)

for obsolete in (
    "app/src/main/java/com/aqua/aqualight/utils/NotificationHelper.kt",
    "app/src/main/java/com/aqua/aqualight/data/notifications/AndroidNotificationRenderer.kt",
    "app/src/main/java/com/aqua/aqualight/data/notifications/ActiveNotificationPreferenceProjection.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderCoordinator.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskBootRuntime.kt",
    "app/src/test/java/com/aqua/aqualight/data/care/reminder/CareTaskBootRuntimeTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/notifications/ActiveNotificationPreferenceProjectionInstrumentedTest.kt",
):
    if path(obsolete).exists():
        errors.append(f"obsolete/temporary notification path remains: {obsolete}")

for token in (
    "enum class NotificationCategory",
    "CARE_REMINDERS",
    "DEVICE_ALERTS",
    "DEVICE_UPDATES",
    "enum class CareReminderKind",
    "interface NotificationPermissionPolicy",
    "interface NotificationScheduler",
    "interface NotificationRenderer",
    "class NotificationPreferenceUseCase",
    "class NotificationDispatchUseCase",
    "dispatchCareReminder",
    "dispatchDeviceAlert",
    "dispatchDeviceUpdate",
):
    require(CONTRACT, token, "central application notification contract is incomplete")
for token in ("APP_UPDATES", "app_updates", "Application update"):
    forbid(CONTRACT, token, "third category is device firmware updates, not app updates")

for token, reason in {
    'const val CARE_REMINDERS = "care_reminders"': "care channel ID must be permanent",
    'const val DEVICE_ALERTS = "device_alerts"': "device alert channel ID must be permanent",
    'const val DEVICE_UPDATES = "device_updates"': "device update channel ID must be permanent",
    "createNotificationChannels": "all channels must be created idempotently together",
    "IMPORTANCE_HIGH": "device alerts require a high-importance category",
    "getNotificationChannel": "user channel state must be readable",
    "IMPORTANCE_NONE": "blocked channels must be represented",
}.items():
    require(REGISTRY, token, reason)
for token in ("_v1", "APP_UPDATES", "app_updates", "deleteNotificationChannel"):
    forbid(REGISTRY, token, "unreleased product uses semantic stable channel IDs without legacy migration")

for token, reason in (
    ("Manifest.permission.POST_NOTIFICATIONS", "API 33 runtime permission must be evaluated centrally"),
    ("areNotificationsEnabled", "Android app-level notification state must be separate"),
    ("NotificationChannelRegistry.readState", "category channel state must be separate"),
):
    require(POLICY, token, reason)
for token in ("requestPermissions", "ActivityResultContracts", "Settings.ACTION_"):
    forbid(POLICY, token, "Stage 6 owns permission and settings UI")

require(REPOSITORY, "NotificationPreferenceRepository", "owner store must implement the application repository")
require(REPOSITORY, "notification_preferences.pb", "owner preference must have a dedicated Proto DataStore")
for token in ("UserPreferencesManager", "SharedPreferences", "fallback", "legacy"):
    forbid(REPOSITORY, token, "owner preference store must be the sole authority")

for token, reason in (
    ("NotificationRenderer", "platform renderer must implement the application contract"),
    ("NotificationCompat.Builder", "visible notification construction belongs only in renderer"),
    ("renderCareReminder", "care rendering contract is required"),
    ("renderDeviceAlert", "device alert rendering contract is required"),
    ("renderDeviceUpdate", "device firmware update rendering contract is required"),
    ("NotificationIdentity.tag", "visible notifications require stable owner/category/entity tags"),
    ("activeNotifications", "owner-specific visible cancellation is required"),
    ("MainActivity", "notification navigation belongs in the Android platform adapter"),
):
    require(RENDERER, token, reason)
for token in ("AlarmManager", "WorkManager", "OwnerNotificationPreferences"):
    forbid(RENDERER, token, "renderer must not schedule work or own preferences")

for token, reason in (
    ("NotificationScheduler", "scheduler must implement the application contract"),
    ("NotificationRenderer", "scheduler depends only on the application renderer contract"),
    ("CareTaskReminderScheduler.schedule", "care alarms must use the internal backend"),
    ("CareTaskReminderScheduler.cancel", "care alarm cancellation must be central"),
    ("CareReminderDeliveryWorker.cancelOwner", "queued delivery must be owner-cancellable"),
    ("CareReminderReconcileWorker.cancel", "owner reconciliation must be cancellable"),
    ("preferences.isEnabled", "scheduler eligibility must use owner preference"),
):
    require(SCHEDULER, token, reason)
for token in (
    "NotificationCompat.Builder",
    "NotificationManager.notify",
    "AndroidNotificationRenderer(",
    "platform.notifications.AndroidNotificationRenderer",
):
    forbid(SCHEDULER, token, "scheduler must not construct a platform renderer or visible notification")

for token, reason in (
    ("NotificationPlatform", "process composition must be explicit"),
    ("preferenceUseCase", "one preference use-case must be exposed"),
    ("dispatchUseCase", "one dispatch use-case must be exposed"),
    ("scheduler", "one scheduler instance must be exposed"),
    ("renderer", "one renderer instance must be exposed"),
    ("platform.notifications.AndroidNotificationRenderer", "renderer implementation must be platform-owned"),
):
    require(PLATFORM, token, reason)

for screen in (APP_SETTINGS, ADD_CARE):
    require(screen, "notificationPreferenceUseCase", "screen must use the same application preference use-case")
    for token in (
        "NotificationChannelRegistry",
        "AndroidNotificationPermissionPolicy",
        "AndroidNotificationRenderer",
        "DefaultNotificationScheduler",
        "NotificationPlatform",
        "OwnerNotificationPreferences",
        "NotificationHelper",
        "AlarmManager",
        "WorkManager",
        "NotificationManager",
        "CareTaskReminderScheduler",
    ):
        forbid(screen, token, "UI must not bypass the notification application boundary")

for token in (
    "NotificationPlatform",
    "NotificationPreferenceUseCase",
    "NotificationScheduler",
    "CareTaskReminderScheduler",
    "OwnerNotificationPreferences",
    "UserPreferencesManager",
    "notificationsEnabled",
    "NotificationHelper",
    "AlarmManager",
    "WorkManager",
):
    forbid(CARE_STORE, token, "care persistence must remain notification-neutral")
require(CARE_STORE, "Pure owner-scoped care-task persistence", "care store must document its notification-neutral contract")

for token in (
    "notificationPreferences.scheduleCareTask",
    "notificationPreferences.cancelCareTask",
    "notificationPreferences.reconcileOwner",
):
    require(MAINTENANCE_OPS, token, "maintenance commands must reconcile through the notification use-case")
for token in ("CareTaskReminderScheduler", "NotificationPlatform", "AlarmManager"):
    forbid(MAINTENANCE_OPS, token, "maintenance application adapter must not bypass central contracts")

require(TANK_OPS, "notificationPreferences.reconcileOwner", "tank reminder setting changes must reconcile centrally")
require(TANK_CLEANER, "cancelCareTaskReminder", "tank deletion must cancel deleted task reminders by owner and task")
require(TANK_CLEANER, "reconcileCareReminders", "tank rollback must restore reminder state")
require(SMART_CARE_WORKER, ".preferenceUseCase", "Smart Care persistence must reconcile through the central use-case")
require(SMART_CARE_WORKER, ".reconcileOwner(scheduledOwnerUid)", "Smart Care must reconcile the captured owner")

for token, reason in (
    ("CareReminderSchedulePolicy.plan", "alarm timing must come from deterministic persisted-time policy"),
    ("CareReminderIdentity.alarmData", "PendingIntent identity must contain owner and task"),
    ("setAndAllowWhileIdle", "care reminders must remain inexact and idle-compatible"),
):
    require(ALARM_BACKEND, token, reason)
for token in ("setExact(", "setExactAndAllowWhileIdle(", "UserDataScope.currentUid()"):
    forbid(ALARM_BACKEND, token, "exact alarms and implicit owner lookup are forbidden")

require(ALARM_RECEIVER, "CareReminderDeliveryWorker.enqueue", "alarm receiver must enqueue durable delivery only")
for token in (
    "goAsync()",
    "CoroutineScope",
    "FirebaseAuthenticatedOwnerProvider",
    "CareTaskDataStoreManager",
    "NotificationPlatform",
    "NotificationCompat",
):
    forbid(ALARM_RECEIVER, token, "alarm receiver must remain enqueue-only")

for token, reason in (
    ("NotificationPlatform.get", "delivery must use the central composition"),
    ("dispatchUseCase.dispatchCareReminder", "delivery must pass final posting through the dispatch use-case"),
    ("FirebaseAuthenticatedOwnerProvider", "delivery must verify the active owner"),
    ("CareReminderDeliveryPolicy.shouldDeliver", "delivery must revalidate task and tank"),
    ("ExistingWorkPolicy.KEEP", "duplicate alarm broadcasts must not duplicate delivery"),
    ("BackoffPolicy.EXPONENTIAL", "transient delivery failure requires backoff"),
    ("MAX_ATTEMPTS", "delivery retries must be bounded"),
):
    require(DELIVERY_WORKER, token, reason)
for token in ("NotificationHelper", "OwnerNotificationPreferences", "UserPreferencesManager"):
    forbid(DELIVERY_WORKER, token, "worker must use central use-cases instead of parallel authorities")

require(BOOT_RECEIVER, "CareReminderReconcileWorker.enqueue", "boot/package replacement must enqueue durable reconciliation")
for token in ("goAsync()", "CareTaskDataStoreManager", "AlarmManager", "NotificationPlatform"):
    forbid(BOOT_RECEIVER, token, "boot receiver must remain enqueue-only")

for token, reason in (
    ("NotificationPlatform.get", "reconciliation must use central composition"),
    ("CareReminderReconcileRuntime", "owner stability must be checked"),
    ("enqueueUniqueWork", "owner reconciliation must be unique"),
    ("BackoffPolicy.EXPONENTIAL", "transient restore failure requires backoff"),
    ("MAX_ATTEMPTS", "restore retries must be bounded"),
):
    require(RECONCILE_WORKER, token, reason)
for token in ("ActiveNotificationPreferenceProjection", "CareReminderCoordinator"):
    forbid(RECONCILE_WORKER, token, "temporary/parallel reconciliation authority is forbidden")

require(SESSION_MANAGER, "NotificationPlatform.get", "account shutdown must use central composition")
require(SESSION_MANAGER, "cancelOwner", "account shutdown must cancel only the outgoing owner")
for token in ("cancelAll", "NotificationHelper", "ActiveNotificationPreferenceProjection"):
    forbid(SESSION_MANAGER, token, "account change must not use app-wide or temporary notification cleanup")
require(USER_CLEANER, ".preferenceUseCase", "destructive account cleanup must cancel owner notification state")
require(USER_CLEANER, ".cancelOwner(targetOwnerUid)", "destructive cleanup must target only the selected owner")

require(USER_PROTO, "reserved 9;", "removed global notification field number must never be reused")
require(USER_PROTO, 'reserved "notificationsEnabled";', "removed global notification field name must never be reused")
for token in ("bool notificationsEnabled", "bool notifications_enabled"):
    forbid(USER_PROTO, token, "notification preference must exist only in the owner-scoped store")
for token in ("notificationsEnabled", "updateNotificationsEnabled"):
    forbid(USER_MANAGER, token, "global notification preference API must not exist")

manifest_text = load(MANIFEST)
for token in ("android.permission.SCHEDULE_EXACT_ALARM", "android.permission.USE_EXACT_ALARM"):
    if token in manifest_text:
        errors.append(f"{MANIFEST}: care reminders do not qualify for exact-alarm special access")
for token in ("android.permission.RECEIVE_BOOT_COMPLETED", "CareTaskBootReceiver"):
    if token not in manifest_text:
        errors.append(f"{MANIFEST}: boot restore contract missing {token}")

for source in APP.rglob("*.kt"):
    text = source.read_text(encoding="utf-8", errors="ignore")
    relative = source.relative_to(ROOT)

    if "NotificationCompat.Builder" in text and source != path(RENDERER):
        errors.append(f"{relative}: only AndroidNotificationRenderer may build notifications")
    if "createNotificationChannel" in text or "createNotificationChannels" in text:
        if source != path(REGISTRY):
            errors.append(f"{relative}: only NotificationChannelRegistry may create channels")
    if "NotificationManagerCompat.from" in text and source != path(POLICY):
        errors.append(f"{relative}: app notification state belongs in NotificationPermissionPolicy")

if UI.is_dir():
    forbidden_ui_tokens = (
        "android.app.AlarmManager",
        "android.app.NotificationChannel",
        "android.app.NotificationManager",
        "androidx.core.app.NotificationManagerCompat",
        "androidx.work.WorkManager",
        "NotificationChannelRegistry",
        "NotificationPlatform",
        "AndroidNotificationRenderer",
        "DefaultNotificationScheduler",
        "OwnerNotificationPreferences",
        "CareTaskReminderScheduler",
        "NotificationHelper",
    )
    for source in UI.rglob("*.kt"):
        text = source.read_text(encoding="utf-8", errors="ignore")
        for token in forbidden_ui_tokens:
            if token in text:
                errors.append(f"{source.relative_to(ROOT)}: UI bypasses central notification use-case: {token}")

if errors:
    print("Central notification architecture guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print(
    "Central notification guard passed: one owner preference source, three permanent "
    "channels, pure care persistence, one policy, one scheduler, one platform renderer, "
    "central preference/dispatch use-cases and owner-scoped lifecycle cleanup."
)
