#!/usr/bin/env python3
"""Enforce AquaLight's commercial central notification/reminder architecture."""

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
LEDGER = "app/src/main/java/com/aqua/aqualight/data/notifications/CareReminderScheduleLedger.kt"
RENDERER = "app/src/main/java/com/aqua/aqualight/platform/notifications/AndroidNotificationRenderer.kt"
PLATFORM = "app/src/main/java/com/aqua/aqualight/data/notifications/NotificationPlatform.kt"
IDENTITY = "app/src/main/java/com/aqua/aqualight/data/notifications/NotificationIdentity.kt"
CAPABILITY = "app/src/main/java/com/aqua/aqualight/platform/permissions/AppCapability.kt"
PERMISSION_POLICY = "app/src/main/java/com/aqua/aqualight/platform/permissions/PermissionPolicy.kt"
PRECISE_POLICY = "app/src/main/java/com/aqua/aqualight/platform/permissions/PreciseReminderAccessPolicy.kt"
PERMISSION_COORDINATOR = "app/src/main/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionCoordinator.kt"
PERMISSION_UI = "app/src/main/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionUiSpecResolver.kt"
NOTIFICATION_ENABLEMENT = "app/src/main/java/com/aqua/aqualight/ui/common/notification/NotificationEnablementCoordinator.kt"
NOTIFICATION_ENABLEMENT_GATE = "app/src/main/java/com/aqua/aqualight/ui/common/notification/NotificationEnablementOperationGate.kt"
APP_SETTINGS = "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/app/AppSettingsFragment.kt"
ADD_CARE = "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/AddCareTaskFragment.kt"
TANK_SETTINGS = "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/settings/TankSettingsOthersFragment.kt"
DOSING_RESERVOIR = "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/reservoir/DeviceDosingReservoirFragment.kt"
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
USER_MANAGER = "app/src/main/java/com/aqua/aqualight/data/user/UserPreferencesManager.kt"
USER_PROTO = "app/src/main/proto/user_prefs.proto"
MANIFEST = "app/src/main/AndroidManifest.xml"
DOC = "docs/stage7-notification-reminder-contract.md"

required_files = (
    CONTRACT,
    REGISTRY,
    POLICY,
    REPOSITORY,
    SCHEDULER,
    LEDGER,
    RENDERER,
    PLATFORM,
    IDENTITY,
    CAPABILITY,
    PERMISSION_POLICY,
    PRECISE_POLICY,
    PERMISSION_COORDINATOR,
    PERMISSION_UI,
    NOTIFICATION_ENABLEMENT,
    NOTIFICATION_ENABLEMENT_GATE,
    APP_SETTINGS,
    ADD_CARE,
    TANK_SETTINGS,
    DOSING_RESERVOIR,
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
    "app/src/main/proto/notification_schedule_state.proto",
    "app/src/test/java/com/aqua/aqualight/application/notifications/NotificationPreferenceUseCaseTest.kt",
    "app/src/test/java/com/aqua/aqualight/application/notifications/NotificationDispatchUseCaseTest.kt",
    "app/src/test/java/com/aqua/aqualight/data/care/reminder/CareReminderSchedulePolicyTest.kt",
    "app/src/test/java/com/aqua/aqualight/data/care/reminder/CareTaskReminderSchedulerTest.kt",
    "app/src/test/java/com/aqua/aqualight/platform/permissions/PreciseReminderAccessPolicyTest.kt",
    "app/src/test/java/com/aqua/aqualight/ui/common/notification/NotificationEnablementDecisionResolverTest.kt",
    "app/src/test/java/com/aqua/aqualight/ui/common/notification/NotificationEnablementOperationGateTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/notifications/OwnerNotificationPreferencesInstrumentedTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/notifications/NotificationChannelRegistryInstrumentedTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/notifications/OwnerNotificationCancellationInstrumentedTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/notifications/CareReminderScheduleLedgerInstrumentedTest.kt",
)
for relative in required_files:
    load(relative)

for obsolete in (
    "app/src/main/java/com/aqua/aqualight/utils/NotificationHelper.kt",
    "app/src/main/java/com/aqua/aqualight/data/notifications/AndroidNotificationRenderer.kt",
    "app/src/main/java/com/aqua/aqualight/data/notifications/ActiveNotificationPreferenceProjection.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderCoordinator.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskBootRuntime.kt",
):
    if path(obsolete).exists():
        errors.append(f"obsolete/temporary notification path remains: {obsolete}")

for token in (
    "enum class NotificationCategory",
    "CARE_REMINDERS",
    "DEVICE_ALERTS",
    "DEVICE_UPDATES",
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
    forbid(REGISTRY, token, "pre-release product uses stable semantic channel IDs")

for token, reason in (
    ("Manifest.permission.POST_NOTIFICATIONS", "runtime notification permission must be evaluated centrally"),
    ("areNotificationsEnabled", "Android app notification state must be separate"),
    ("NotificationChannelRegistry.readState", "category channel state must be separate"),
):
    require(POLICY, token, reason)
for token in ("requestPermissions", "ActivityResultContracts", "Settings.ACTION_"):
    forbid(POLICY, token, "Stage 6 coordinator owns permission and Settings UI")

for token, reason in (
    ("PRECISE_REMINDERS", "precise care timing must be a product capability"),
    ("PreciseReminderAccessPolicy", "special access must be centrally evaluated"),
):
    require(CAPABILITY if token == "PRECISE_REMINDERS" else PERMISSION_POLICY, token, reason)
require(PRECISE_POLICY, "canScheduleExactAlarms", "Android 12+ grant must be queried through AlarmManager")
require(PRECISE_POLICY, "Build.VERSION_CODES.S", "special access starts at API 31")
require(PERMISSION_COORDINATOR, "ACTION_REQUEST_SCHEDULE_EXACT_ALARM", "special access Settings routing must remain central")
require(PERMISSION_UI, "AppCapability.PRECISE_REMINDERS", "precise timing must use the common professional permission sheet")
require(ADD_CARE, "AppCapability.PRECISE_REMINDERS", "care-task save must require precise timing access")
require(APP_SETTINGS, "AppCapability.PRECISE_REMINDERS", "App Settings must surface precise timing access")

for token, reason in (
    ("NotificationPreferenceUseCase", "feature enablement must use the Stage 7 application boundary"),
    ("CapabilityPermissionCoordinator", "feature enablement must use the Stage 6 UI boundary"),
    ("NotificationEnablementDecisionResolver", "readiness ordering must stay deterministic and testable"),
    ("evaluation.notificationPreferences.setEnabled", "ready feature opt-in must reconcile the existing owner preference"),
    ("NotificationEnablementOperationGate", "feature enablement must serialize user intent"),
    ("operationGate.cancel()", "feature cancellation must invalidate active async work"),
):
    require(NOTIFICATION_ENABLEMENT, token, reason)
for token, reason in (
    ("activeJob?.cancel()", "superseded enablement work must be cancelled as a Job"),
    ("job.cancel()", "a stale Job must be cancelled before it can start"),
):
    require(NOTIFICATION_ENABLEMENT_GATE, token, reason)
for token in ("DataStore", "SharedPreferences", "NotificationDispatchUseCase", "NotificationManager"):
    forbid(NOTIFICATION_ENABLEMENT, token, "feature enablement must not create a second preference or delivery path")

for screen, category in (
    (TANK_SETTINGS, "NotificationCategory.CARE_REMINDERS"),
    (DOSING_RESERVOIR, "NotificationCategory.DEVICE_ALERTS"),
):
    require(screen, "NotificationEnablementCoordinator", "feature switch must use shared notification enablement")
    require(screen, category, "feature switch must resolve its stable central category")

require(REPOSITORY, "NotificationPreferenceRepository", "owner store must implement the application repository")
require(REPOSITORY, "notification_preferences.pb", "owner preference must have a dedicated Proto DataStore")
for token in ("UserPreferencesManager", "SharedPreferences", "fallback", "legacy"):
    forbid(REPOSITORY, token, "owner preference store must be the sole authority")

for token, reason in (
    ("NotificationRenderer", "platform renderer must implement the application contract"),
    ("NotificationCompat.Builder", "visible notification construction belongs only in renderer"),
    ("renderCareReminder", "care rendering contract is required"),
    ("renderDeviceAlert", "device alert rendering contract is required"),
    ("renderDeviceUpdate", "device update rendering contract is required"),
    ("NotificationIdentity.tag", "visible notifications require stable owner/category/entity tags"),
    ("activeNotifications", "owner-specific visible cancellation is required"),
):
    require(RENDERER, token, reason)
for token in ("AlarmManager", "WorkManager", "OwnerNotificationPreferences"):
    forbid(RENDERER, token, "renderer must not schedule work or own preferences")

for token, reason in (
    ("NotificationScheduler", "scheduler must implement the application contract"),
    ("CareTaskReminderScheduler.schedule", "care alarms must use the internal backend"),
    ("CareTaskReminderScheduler.cancel", "care alarm cancellation must be central"),
    ("CareReminderScheduleLedger.create", "alarm identities require a durable owner/task ledger"),
    ("scheduleLedger.markScheduled", "successful alarms must be recorded"),
    ("scheduleLedger.markCancelled", "cancelled alarms must leave the ledger"),
    ("scheduleLedger.taskIds", "reconciliation must discover stale identities"),
    ("CareReminderDeliveryWorker.cancelOwner", "queued delivery must be owner-cancellable"),
    ("CareReminderReconcileWorker.cancel", "owner reconciliation must be cancellable"),
    ("preferences.isEnabled", "scheduler eligibility must use owner preference"),
):
    require(SCHEDULER, token, reason)
for token in ("NotificationCompat.Builder", "NotificationManager.notify"):
    forbid(SCHEDULER, token, "scheduler must delegate visible rendering")

for token, reason in (
    ("notification_schedule_state.pb", "alarm identity ledger must be durable"),
    ("suspend fun taskIds", "owner alarm identities must be enumerable"),
    ("suspend fun markScheduled", "scheduled identities must be recorded"),
    ("suspend fun markCancelled", "cancelled identities must be removed"),
    ("suspend fun clearOwner", "owner ledger cleanup must be explicit"),
):
    require(LEDGER, token, reason)

for screen in (APP_SETTINGS, ADD_CARE):
    require(screen, "notificationPreferenceUseCase", "screen must use the shared application use-case")
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
        "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM",
    ):
        forbid(screen, token, "UI must not bypass the central notification/permission boundary")

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

for token in (
    "notificationPreferences.scheduleCareTask",
    "notificationPreferences.cancelCareTask",
    "notificationPreferences.reconcileOwner",
):
    require(MAINTENANCE_OPS, token, "maintenance commands must use the notification application boundary")
require(TANK_OPS, "notificationPreferences.reconcileOwner", "tank reminder changes must reconcile centrally")
require(TANK_CLEANER, "cancelCareTaskReminder", "tank deletion must cancel owner/task reminders")
require(SMART_CARE_WORKER, ".preferenceUseCase", "Smart Care must reconcile through the central use-case")

for token, reason in (
    ("CareReminderSchedulePolicy.plan", "alarm timing must derive from persisted task time"),
    ("CareReminderIdentity.alarmData", "PendingIntent identity must contain owner and task"),
    ("canScheduleExactAlarms", "Android 12+ exact access must be checked immediately before scheduling"),
    ("setExactAndAllowWhileIdle", "granted user-selected reminder times require exact idle delivery"),
    ("setAndAllowWhileIdle", "revocation race requires a safe inexact fallback"),
    ("shouldUseExactAlarm", "API/access selection must be unit-testable"),
):
    require(ALARM_BACKEND, token, reason)
for token in ("setAlarmClock(", "UserDataScope.currentUid()", "NotificationManager"):
    forbid(ALARM_BACKEND, token, "low-level alarm backend must remain owner-explicit and notification-neutral")

require(ALARM_RECEIVER, "CareReminderDeliveryWorker.enqueue", "alarm receiver must enqueue durable delivery")
for token in ("FirebaseAuthenticatedOwnerProvider", "CareTaskDataStoreManager", "NotificationPlatform", "NotificationCompat"):
    forbid(ALARM_RECEIVER, token, "alarm receiver must remain a lightweight boundary")

for token, reason in (
    ("NotificationPlatform.get", "delivery must use central composition"),
    ("dispatchUseCase.dispatchCareReminder", "posting must pass through the dispatch use-case"),
    ("FirebaseAuthenticatedOwnerProvider", "delivery must verify the active owner"),
    ("CareReminderDeliveryPolicy.shouldDeliver", "delivery must revalidate task and tank"),
    ("setExpedited", "alarm-triggered user-visible work must request expedited execution"),
    ("OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST", "quota exhaustion needs a durable fallback"),
    ("ExistingWorkPolicy.REPLACE", "a stale deferred delivery must not block the current alarm"),
    ("BackoffPolicy.EXPONENTIAL", "transient failures require bounded backoff"),
    ("MAX_ATTEMPTS", "delivery retries must be bounded"),
):
    require(DELIVERY_WORKER, token, reason)

for token, reason in (
    ("CareReminderReconcileWorker.enqueue", "boot/package/access grant must enqueue reconciliation"),
    ("ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED", "grant changes must restore exact alarms"),
    ("PreciseReminderAccessPolicy", "grant broadcast must be verified before reconciliation"),
):
    require(BOOT_RECEIVER, token, reason)
for token in ("CareTaskDataStoreManager", "NotificationPlatform"):
    forbid(BOOT_RECEIVER, token, "boot receiver must remain enqueue-only")

for token, reason in (
    ("NotificationPlatform.get", "reconciliation must use central composition"),
    ("CareReminderReconcileRuntime", "owner stability must be checked"),
    ("enqueueUniqueWork", "owner reconciliation must be unique"),
    ("BackoffPolicy.EXPONENTIAL", "transient restore failures require backoff"),
):
    require(RECONCILE_WORKER, token, reason)

require(SESSION_MANAGER, "NotificationPlatform.get", "account shutdown must use central composition")
require(SESSION_MANAGER, "cancelOwner", "account shutdown must cancel only the outgoing owner")
for token in ("cancelAll", "NotificationHelper", "ActiveNotificationPreferenceProjection"):
    forbid(SESSION_MANAGER, token, "account change must not use app-wide or temporary cleanup")
require(USER_CLEANER, ".preferenceUseCase", "destructive cleanup must cancel owner notification state")

require(USER_PROTO, "reserved 9;", "removed global notification field number must never be reused")
require(USER_PROTO, 'reserved "notificationsEnabled";', "removed global notification field name must never be reused")
for token in ("notificationsEnabled", "updateNotificationsEnabled"):
    forbid(USER_MANAGER, token, "global notification preference API must not exist")

manifest_text = load(MANIFEST)
for token in (
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "CareTaskBootReceiver",
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
):
    if token not in manifest_text:
        errors.append(f"{MANIFEST}: reminder delivery contract missing {token}")
if "android.permission.USE_EXACT_ALARM" in manifest_text:
    errors.append(f"{MANIFEST}: restricted USE_EXACT_ALARM permission is forbidden")

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
    for alarm_call in (
        "CareTaskReminderScheduler.schedule(",
        "CareTaskReminderScheduler.cancel(",
    ):
        if alarm_call in text and source != path(SCHEDULER):
            errors.append(f"{relative}: only DefaultNotificationScheduler may call {alarm_call}")
    if "setExactAndAllowWhileIdle(" in text and source != path(ALARM_BACKEND):
        errors.append(f"{relative}: exact care alarms belong only in CareTaskReminderScheduler")
    if "ACTION_REQUEST_SCHEDULE_EXACT_ALARM" in text and source != path(PERMISSION_COORDINATOR):
        errors.append(f"{relative}: precise-reminder Settings routing belongs only in the coordinator")

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
    "Central notification guard passed: owner-scoped preferences, three stable channels, "
    "precise user-selected care alarms, expedited durable delivery, central use-cases, "
    "durable owner/task ledger and owner-specific cleanup are enforced."
)
