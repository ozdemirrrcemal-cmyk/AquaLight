#!/usr/bin/env python3
"""Fail CI when background code can open foreground device runtime."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.exists():
        errors.append(f"{relative_path}: required startup architecture file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def require(relative_path: str, text: str, token: str, reason: str) -> None:
    if token not in text:
        errors.append(f"{relative_path}: {reason}: missing {token}")


def forbid(relative_path: str, text: str, token: str, reason: str) -> None:
    if token in text:
        errors.append(f"{relative_path}: {reason}: forbidden {token}")


app_path = "app/src/main/java/com/aqua/aqualight/app/AquaApp.kt"
app = read(app_path)
for token in ("runBlocking", ".first()\n        applyTheme"):
    forbid(app_path, app, token, "Application startup must not synchronously wait for DataStore")
require(
    app_path,
    app,
    "appContainer.startupAppearanceCache",
    "startup theme/language mirror must be resolved through the composition root",
)

splash_path = "app/src/main/java/com/aqua/aqualight/ui/splash/SplashActivity.kt"
splash = read(splash_path)
for token in ("AuthSessionManager", "currentSessionState", "delay(2400", "delay(2_400"):
    forbid(splash_path, splash, token, "Splash must be a visual handoff only")
require(
    splash_path,
    splash,
    "Intent(this, MainActivity::class.java)",
    "Splash must immediately hand startup authority to MainActivity",
)

provider_path = (
    "app/src/main/java/com/aqua/aqualight/data/auth/AuthenticatedOwnerProvider.kt"
)
provider = read(provider_path)
for token, reason in (
    ("FirebaseAuth.IdTokenListener", "remote auth changes require an ID-token listener"),
    ("validateCurrentOwner", "foreground resume must validate remote session state"),
    ("FirebaseAuthInvalidUserException", "revoked users must be classified as terminal"),
):
    require(provider_path, provider, token, reason)
for token in (
    "import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider",
    "import com.aqua.aqualight.data.devices.repository.DeviceRuntimeRepository",
    "import com.aqua.aqualight.data.devices.runtime.ws.AqlWsClient",
):
    forbid(provider_path, provider, token, "lightweight owner provider must not open runtime")

runtime_path = "app/src/main/java/com/aqua/aqualight/data/auth/OwnerRuntimeSession.kt"
runtime = read(runtime_path)
require(
    runtime_path,
    runtime,
    "class OwnerRuntimeSession",
    "foreground owner runtime needs an explicit boundary",
)

coordinator_path = "app/src/main/java/com/aqua/aqualight/data/auth/AppSessionCoordinator.kt"
coordinator = read(coordinator_path)
for token, reason in (
    ("ownerProvider.state.collect", "owner changes must be observed centrally"),
    ("transitionMutex.withLock", "startup/account transitions must be serialized"),
    ("validateRemoteSession", "foreground auth must detect remote invalidation"),
    ("State.Authenticated", "navigation requires an authenticated state"),
    ("State.Unauthenticated", "navigation requires an unauthenticated state"),
):
    require(coordinator_path, coordinator, token, reason)

main_path = "app/src/main/java/com/aqua/aqualight/ui/main/MainActivity.kt"
main = read(main_path)
for token in ("currentSessionState()", "SessionBoundServiceManager.start("):
    forbid(main_path, main, token, "MainActivity must delegate startup to AppSessionCoordinator")
for token, reason in (
    ("appSessionCoordinator.state.collect", "MainActivity must observe the single session authority"),
    ("renderedSessionKey", "owner changes must replace an already-open navigation graph"),
    ("installRootGraph(startInApp = true)", "authenticated transitions must install the app graph"),
    ("installRootGraph(startInApp = false)", "invalidated/logout transitions must install the auth graph"),
):
    require(main_path, main, token, reason)

background_paths = (
    "app/src/main/java/com/aqua/aqualight/data/care/smartcare/SmartCareDailyWorker.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareReminderReconcileWorker.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskBootReceiver.kt",
    "app/src/main/java/com/aqua/aqualight/data/care/reminder/CareTaskReminderReceiver.kt",
)
background_runtime_tokens = (
    "import com.aqua.aqualight.data.auth.AuthSessionManager",
    ".currentSessionState()",
    "import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider",
    "import com.aqua.aqualight.data.devices.repository.DeviceRuntimeRepository",
    "import com.aqua.aqualight.data.devices.discovery.",
    "import com.aqua.aqualight.data.devices.runtime.ws.AqlWsClient",
    "import com.aqua.aqualight.data.auth.OwnerRuntimeSession",
)
for background_path in background_paths:
    background = read(background_path)
    for token in background_runtime_tokens:
        forbid(
            background_path,
            background,
            token,
            "background maintenance must not open foreground device runtime",
        )
    require(
        background_path,
        background,
        "FirebaseAuthenticatedOwnerProvider",
        "background maintenance must use the lightweight owner provider",
    )

smart_worker_path = background_paths[0]
smart_worker = read(smart_worker_path)
for token, reason in (
    ("KEY_OWNER_UID", "scheduled work must carry an immutable owner UID"),
    ("tanksSnapshotForOwner", "Smart Care must read only the scheduled owner's tanks"),
    ("UserDataScope.withOwnerUid", "Smart Care writes must remain owner-stable during account changes"),
):
    require(smart_worker_path, smart_worker, token, reason)

reminder_worker_path = background_paths[1]
reminder_worker = read(reminder_worker_path)
for token, reason in (
    ("KEY_OWNER_UID", "reminder reconciliation work must carry an immutable owner UID"),
    ("CareReminderReconcileRuntime", "reminder worker must use the testable owner-stability boundary"),
    ("CareReminderCoordinator", "reminder worker must delegate alarm reconciliation centrally"),
    ("OwnerNotificationPreferences", "reminder worker must load the owner-scoped app preference"),
):
    require(reminder_worker_path, reminder_worker, token, reason)

boot_path = background_paths[2]
boot = read(boot_path)
require(
    boot_path,
    boot,
    "CareReminderReconcileWorker.enqueue",
    "boot receiver must enqueue durable reminder reconciliation",
)
for token in ("goAsync()", "CareTaskDataStoreManager", "UserDataScope.withOwnerUid"):
    forbid(
        boot_path,
        boot,
        token,
        "boot receiver must not scan owner stores directly",
    )

user_scope_path = "app/src/main/java/com/aqua/aqualight/data/user/UserDataScope.kt"
user_scope = read(user_scope_path)
for token, reason in (
    ("ThreadLocal<String?>", "explicit background owner identity must be coroutine-local"),
    ("asContextElement", "owner identity must survive dispatcher switches"),
    ("withOwnerUid", "background stores require an explicit owner API"),
):
    require(user_scope_path, user_scope, token, reason)

session_tests_path = (
    "app/src/test/java/com/aqua/aqualight/data/auth/AppSessionCoordinatorTest.kt"
)
session_tests = read(session_tests_path)
for token, reason in (
    ("rapidAccountSwitchSettlesOnNewestOwner", "fast account switching needs a regression test"),
    ("remoteInvalidationReturnsSessionToUnauthenticated", "remote invalidation needs a regression test"),
    ("processRecreationResolvesCurrentOwnerWithoutOldCoordinatorState", "process recreation needs a regression test"),
    ("logoutTransitionsExistingGraphAuthorityToUnauthenticated", "logout needs a regression test"),
):
    require(session_tests_path, session_tests, token, reason)

reminder_runtime_tests_path = (
    "app/src/test/java/com/aqua/aqualight/data/care/reminder/"
    "CareReminderReconcileRuntimeTest.kt"
)
reminder_runtime_tests = read(reminder_runtime_tests_path)
for token, reason in (
    (
        "authenticatedOwnerSynchronizesPreferenceAndReconciles",
        "authenticated restore must synchronize and reconcile only its owner",
    ),
    (
        "accountSwitchDuringPreferenceProjectionCancelsOldOwner",
        "account switching during restore must cancel stale owner alarms",
    ),
    (
        "accountSwitchAfterReconcileCancelsOldOwnerAlarms",
        "post-reconcile account switching must remove stale owner alarms",
    ),
    (
        "unauthenticatedOrDifferentOwnerDoesNotReadStores",
        "unauthenticated boot must remain inert",
    ),
):
    require(reminder_runtime_tests_path, reminder_runtime_tests, token, reason)

process_death_tests_path = (
    "app/src/test/java/com/aqua/aqualight/data/devices/repository/"
    "DeviceRuntimeProcessDeathContractTest.kt"
)
process_death_tests = read(process_death_tests_path)
require(
    process_death_tests_path,
    process_death_tests,
    "processDeathEquivalentTerminalShutdownLeavesNoJobsCollectorsSocketsOrTokenAccess",
    "terminal process cleanup needs an explicit leak regression test",
)

if errors:
    print("Session/startup architecture guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print(
    "Session/startup guard passed: foreground runtime, auth coordination, "
    "owner-scoped durable background maintenance and terminal cleanup tests are intact."
)
