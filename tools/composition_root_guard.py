#!/usr/bin/env python3
"""Protect the closed process/owner composition architecture."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight"
UI_ROOT = SOURCE_ROOT / "ui"
APPLICATION_ROOT = SOURCE_ROOT / "application"

paths = {
    "container": SOURCE_ROOT / "composition/AppContainer.kt",
    "dispatcher": SOURCE_ROOT / "composition/AquaViewModelFactory.kt",
    "scoped_factory": SOURCE_ROOT / "composition/ScopedViewModelFactory.kt",
    "process_factory": SOURCE_ROOT / "composition/ProcessViewModelFactory.kt",
    "owner_factory": SOURCE_ROOT / "composition/OwnerViewModelFactory.kt",
    "owner_graph": SOURCE_ROOT / "composition/OwnerDependencyGraph.kt",
    "owner_identity": SOURCE_ROOT / "application/auth/AuthenticatedOwnerIdentity.kt",
    "assignment_provider": SOURCE_ROOT / "data/aquarium/devices/TankDeviceAssignmentRepositoryProvider.kt",
    "devices_provider": SOURCE_ROOT / "data/devices/repository/DevicesRepositoryProvider.kt",
    "aqua_app": SOURCE_ROOT / "app/AquaApp.kt",
    "auth_factory": SOURCE_ROOT / "ui/auth/viewmodel/AuthViewModelFactory.kt",
    "factory_test": ROOT / "app/src/test/java/com/aqua/aqualight/composition/AquaViewModelFactoryTest.kt",
    "owner_session_test": ROOT / "app/src/test/java/com/aqua/aqualight/composition/OwnerDependencyGraphSessionTest.kt",
    "plan": ROOT / "docs/dependency-boundaries-and-composition-root.md",
    "matrix": ROOT / "docs/composition-dependency-matrix.md",
}

errors: list[str] = []


def read(name: str) -> str:
    path = paths[name]
    if not path.exists():
        errors.append(f"{path.relative_to(ROOT)}: required architecture file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def require(name: str, *tokens: str) -> None:
    text = read(name)
    path = paths[name].relative_to(ROOT)
    for token in tokens:
        if token not in text:
            errors.append(f"{path}: required architecture token is missing: {token}")


def forbid(name: str, *tokens: str) -> None:
    text = read(name)
    path = paths[name].relative_to(ROOT)
    for token in tokens:
        if token in text:
            errors.append(f"{path}: forbidden architecture token remains: {token}")


require(
    "container",
    "interface AppContainer",
    "internal class DefaultAppContainer",
    "ActiveOwnerDependencyGraphResolver(",
    "notificationDispatchUseCase = notificationDispatchUseCase",
    "ResolvingAuthenticatedOwnerIdentity(ownerGraphResolver)",
    "ResolvingProvisioningDraftOperations(ownerGraphResolver)",
    "val authenticatedOwnerIdentity: AuthenticatedOwnerIdentity",
    "processFactory = ProcessViewModelFactory()",
    "ownerFactory = OwnerViewModelFactory(",
    "val authViewModelFactory: ViewModelProvider.Factory",
    "val defaultViewModelFactory: ViewModelProvider.Factory",
    "val sessionExitOperations: SessionExitOperations",
    "val accountSecurityOperations: AccountSecurityOperations",
    "val googleIdentityClient: GoogleIdentityClient",
    "LazyThreadSafetyMode.SYNCHRONIZED",
)
forbid(
    "container",
    "DevicesRepositoryProvider",
    "TankDeviceAssignmentRepositoryProvider",
    "DefaultProvisioningDraftOperations(appContext)",
)

require(
    "owner_identity",
    "fun interface AuthenticatedOwnerIdentity",
    "fun requireOwnerUid(): String",
)

require(
    "dispatcher",
    "processFactory: ScopedViewModelFactory",
    "ownerFactory: ScopedViewModelFactory",
    "processFactory.supports(modelClass)",
    "ownerFactory.supports(modelClass)",
    "duplicate process and owner bindings",
    "No registered AquaLight ViewModel binding",
)
forbid(
    "dispatcher",
    "AndroidViewModelFactory",
    "fallbackFactory",
    "isAssignableFrom",
    "DevicesRepositoryProvider",
    "TankDeviceAssignmentRepositoryProvider",
)

require(
    "scoped_factory",
    "internal interface ScopedViewModelFactory",
    "fun supports(modelClass: Class<out ViewModel>): Boolean",
)
require(
    "process_factory",
    "modelClass == TankDetailViewModel::class.java",
    "No process-scoped ViewModel binding",
)
forbid("process_factory", "UserDataScope", "RepositoryProvider")

require(
    "owner_graph",
    "internal data class OwnerDependencyGraph",
    "val sessionGeneration: Long",
    "internal fun interface OwnerDependencyGraphResolver",
    "internal fun requireActiveOwnerGeneration(",
    "snapshot.activeOwnerUid == ownerUid",
    "snapshot.pendingOwnerUid == null",
    "OwnerSessionCoordinator.create(appContext)",
    "DevicesRepositoryProvider.currentRepository(ownerUid)",
    "TankDeviceAssignmentRepositoryProvider.currentRepository(ownerUid)",
    "Authenticated owner session is not committed.",
    "Authenticated owner device runtime is not active.",
    "Authenticated owner assignment repository is not active.",
    "confirmedGeneration == initialGeneration",
    "sessionGeneration = dependencies.sessionGeneration",
    "DevicesRepositoryProvider.currentRepository(dependencies.ownerUid) ===",
    "dependencies.devicesRepository",
    "assignmentRepository",
    "AndroidDeviceFirmwareUpdateNotificationPublisher(",
    "statePublisher = notificationPublisher::publish",
    "registerOwnerScopedResource",
    "ownerUidProvider = ownerUidProvider",
    "class ResolvingAuthenticatedOwnerIdentity",
    "ownerGraphResolver.requireActive().ownerUid",
    "ResolvingProvisioningDraftOperations",
)
forbid(
    "owner_graph",
    "DevicesRepositoryProvider.get(",
    "TankDeviceAssignmentRepositoryProvider.get(",
)

require(
    "owner_factory",
    "internal class OwnerViewModelFactory",
    "notificationPreferenceUseCase: NotificationPreferenceUseCase",
    "modelClass in OWNER_BINDINGS",
    "val graph = ownerGraphResolver.requireActive()",
    "ownerUidProvider = { graph.ownerUid }",
    "notificationPreferences = notificationPreferenceUseCase",
    "cancelCareTaskReminder =",
    "No owner-scoped ViewModel binding",
)
forbid(
    "owner_factory",
    "isAssignableFrom",
    "AndroidViewModelFactory",
    "DevicesRepositoryProvider",
    "TankDeviceAssignmentRepositoryProvider",
    "DefaultProvisioningProgressOperations(appContext)",
    "val currentGraph = ownerGraphResolver.requireActive()",
    "Authenticated owner changed while constructing",
)

require(
    "devices_provider",
    "fun currentRepository(",
    "current.ownerUid == normalizedOwnerUid",
)
require(
    "assignment_provider",
    "Owner assignment repository must be cleared before switching owners.",
    "fun currentRepository(",
    "current.ownerUid == normalizedOwnerUid",
)

require(
    "factory_test",
    "routes exact process and owner bindings",
    "unknown ViewModel fails closed",
    "duplicate scope binding fails closed",
)
require(
    "owner_session_test",
    "committed matching owner exposes generation",
    "pending transition fails before dependency construction",
    "different active owner fails closed",
    "signed out session fails closed",
)
require(
    "matrix",
    "Process scope",
    "Authenticated owner scope",
    "Fail-closed rules",
    "DevicesRepositoryProvider.get",
    "Physical validation baseline",
)
require(
    "plan",
    "UI -> application contracts/use cases",
    "Completion baseline",
    "Final architecture enforcement",
)
require(
    "aqua_app",
    "lateinit var appContainer: AppContainer",
    "appContainer = DefaultAppContainer(this)",
)
forbid(
    "auth_factory",
    "android.content.Context",
    "AuthRepository.create(",
    "com.google.firebase",
)

application_forbidden_import = re.compile(
    r"^import\s+(?:android(?:x)?\.|com\.google\.|"
    r"com\.aqua\.aqualight\.(?:data|platform|ui|composition)\.)",
    re.MULTILINE,
)
if APPLICATION_ROOT.exists():
    for kotlin_file in APPLICATION_ROOT.rglob("*.kt"):
        text = kotlin_file.read_text(encoding="utf-8", errors="ignore")
        match = application_forbidden_import.search(text)
        if match:
            errors.append(
                f"{kotlin_file.relative_to(ROOT)}: application contracts must remain "
                f"Android/vendor/data/platform/UI independent: {match.group(0)}"
            )

ui_forbidden = (
    "DevicesRepositoryProvider.get(",
    "TankDeviceAssignmentRepositoryProvider.get(",
    "AuthRepository.create(",
    "DefaultProvisioningProgressOperations(",
)
if UI_ROOT.exists():
    for kotlin_file in UI_ROOT.rglob("*.kt"):
        text = kotlin_file.read_text(encoding="utf-8", errors="ignore")
        for token in ui_forbidden:
            if token in text:
                errors.append(
                    f"{kotlin_file.relative_to(ROOT)}: UI dependency construction "
                    f"must resolve through AppContainer: {token}"
                )

if errors:
    print("Composition root guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Composition root guard passed.")
