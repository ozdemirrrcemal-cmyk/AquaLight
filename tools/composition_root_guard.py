#!/usr/bin/env python3
"""AquaLight composition-root migration guard.

The migration is incremental, but completed vertical slices are permanent. This
script prevents concrete construction, vendor access, or Android ownership from
creeping back into migrated UI and ViewModel surfaces.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight"
UI_ROOT = SOURCE_ROOT / "ui"
APPLICATION_ROOT = SOURCE_ROOT / "application"
CONTAINER_PATH = SOURCE_ROOT / "composition/AppContainer.kt"
FACTORY_PATH = SOURCE_ROOT / "composition/AquaViewModelFactory.kt"
AQUA_APP_PATH = SOURCE_ROOT / "app/AquaApp.kt"
AUTH_FACTORY_PATH = SOURCE_ROOT / "ui/auth/viewmodel/AuthViewModelFactory.kt"
PROFILE_CONTRACT_PATH = SOURCE_ROOT / "application/user/UserProfileOperations.kt"
SETTINGS_IMPL_PATH = SOURCE_ROOT / "data/user/DefaultUserSettingsOperations.kt"
PROFILE_IMPL_PATH = SOURCE_ROOT / "data/user/DefaultUserProfileOperations.kt"
PROVISIONING_OPERATIONS_PATH = (
    SOURCE_ROOT / "ui/tabs/devices/add/DeviceProvisioningProgressOperations.kt"
)
PROVISIONING_DEFAULT_PATH = (
    SOURCE_ROOT / "composition/DefaultDeviceProvisioningProgressOperations.kt"
)
AUTH_FACTORY_TEST_PATH = (
    ROOT / "app/src/test/java/com/aqua/aqualight/ui/auth/viewmodel/AuthViewModelFactoryTest.kt"
)
FEEDBACK_TEST_PATH = (
    ROOT / "app/src/test/java/com/aqua/aqualight/application/feedback/FeedbackSubmissionUseCaseTest.kt"
)
PROVISIONING_TEST_PATH = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/ui/tabs/devices/add/DeviceProvisioningProgressViewModelBoundaryTest.kt"
)
PLAN_PATH = ROOT / "docs/stage-3-dependency-boundaries-plan.md"

errors: list[str] = []


def read(path: Path) -> str:
    if not path.exists():
        errors.append(f"{path.relative_to(ROOT)}: required composition-root file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


container = read(CONTAINER_PATH)
factory = read(FACTORY_PATH)
aqua_app = read(AQUA_APP_PATH)
auth_factory = read(AUTH_FACTORY_PATH)
auth_factory_test = read(AUTH_FACTORY_TEST_PATH)
feedback_test = read(FEEDBACK_TEST_PATH)
provisioning_test = read(PROVISIONING_TEST_PATH)
profile_contract = read(PROFILE_CONTRACT_PATH)
provisioning_operations = read(PROVISIONING_OPERATIONS_PATH)
provisioning_default = read(PROVISIONING_DEFAULT_PATH)
plan = read(PLAN_PATH)

for token, reason in (
    ("interface AppContainer", "composition root contract must remain explicit"),
    ("internal class DefaultAppContainer", "production wiring must have one implementation"),
    ("val authViewModelFactory: ViewModelProvider.Factory", "auth UI needs one wired factory"),
    ("val defaultViewModelFactory: ViewModelProvider.Factory", "feature UI needs one wired factory"),
    ("val sessionExitOperations: SessionExitOperations", "logout must use an application boundary"),
    ("val accountSecurityOperations: AccountSecurityOperations", "account security must use an application boundary"),
    ("val googleIdentityClient: GoogleIdentityClient", "Google identity access must be centralized"),
    ("val userSettingsOperations: UserSettingsOperations", "settings must use an application boundary"),
    ("val userProfileOperations: UserProfileOperations", "profile persistence must use an application boundary"),
    ("val feedbackSubmissionOperations: FeedbackSubmissionUseCase", "feedback must use an application use case"),
    ("AuthRepository.create(appContext)", "AuthRepository construction must remain centralized"),
    ("LogoutManager.create(appContext)", "logout manager construction must remain centralized"),
    ("FirebaseAccountSecurityOperations.create(appContext)", "account security construction must remain centralized"),
    ("DefaultGoogleIdentityClient(appContext)", "Google identity construction must remain centralized"),
    ("DefaultUserSettingsOperations(", "settings implementation construction must remain centralized"),
    ("DefaultUserProfileOperations(", "profile implementation construction must remain centralized"),
    ("FirebaseFeedbackSubmissionOperations.create()", "Firebase feedback construction must remain centralized"),
    ("FeedbackSubmissionUseCase(", "feedback adapter must be wrapped by a use case"),
    ("LazyThreadSafetyMode.SYNCHRONIZED", "process dependencies must initialize safely"),
):
    if token not in container:
        errors.append(f"{CONTAINER_PATH.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("lateinit var appContainer: AppContainer", "Application must own the process composition root"),
    ("appContainer = DefaultAppContainer(this)", "container must initialize before app services"),
    ("appContainer.startupAppearanceCache", "startup cache must resolve through the container"),
    ("appContainer.userPreferencesManager", "startup preferences must resolve through the container"),
):
    if token not in aqua_app:
        errors.append(f"{AQUA_APP_PATH.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("UI -> application contracts/use cases", "plan must define dependency direction"),
    ("Definition of done", "plan must retain its commercial completion gate"),
    ("Feedback vendor isolation", "Firebase UI removal must stay visible"),
):
    if token not in plan:
        errors.append(f"{PLAN_PATH.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("suspend fun updateUsername", "profile boundary must own username updates"),
    ("suspend fun saveAddress", "profile boundary must own address updates"),
    ("val fullName: String", "profile snapshot must expose full name"),
    ("data class UserAddressInput", "address persistence must use an application input"),
):
    if token not in profile_contract:
        errors.append(f"{PROFILE_CONTRACT_PATH.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("interface DeviceProvisioningProgressOperations", "provisioning needs a fakeable operations boundary"),
    ("val ownerUid: String", "owner identity must be injected"),
    ("val gattEvents: Flow<AqlBleProvisioningGattEvent>", "GATT events must be injected"),
    ("suspend fun prepareAndConnect", "registration prepare must cross the boundary"),
    ("suspend fun commitPreparedRegistration", "registration commit must cross the boundary"),
    ("suspend fun rollbackProvisioningRegistrationForOwner", "owner rollback must cross the boundary"),
):
    if token not in provisioning_operations:
        errors.append(f"{PROVISIONING_OPERATIONS_PATH.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("AqlBleProvisioningAddressResolver(appContext)", "address resolver construction must remain in composition"),
    ("AqlBleProvisioningGattClient(appContext)", "GATT client construction must remain in composition"),
    ("AqlProvisioningHandoffSaver(appContext)", "handoff saver construction must remain in composition"),
    ("AqlProvisioningDraftStore.get", "draft store access must remain behind the boundary"),
    ("UserDataScope.requireCurrentUid()", "owner scope resolution must remain in composition"),
    ("DeviceRouteResolver()", "route resolver construction must remain in composition"),
):
    if token not in provisioning_default:
        errors.append(f"{PROVISIONING_DEFAULT_PATH.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("DefaultDeviceProvisioningProgressOperations(", "provisioning operations must be created by the feature factory"),
    ("DeviceProvisioningProgressViewModel(", "provisioning ViewModel must be created by the feature factory"),
):
    if token not in factory:
        errors.append(f"{FACTORY_PATH.relative_to(ROOT)}: {reason}: {token}")

for forbidden, reason in (
    ("android.content.Context", "auth factory must not construct dependencies from Context"),
    ("AuthRepository.create(", "auth factory must receive its dependency"),
    ("com.google.firebase", "auth factory must not know Firebase"),
):
    if forbidden in auth_factory:
        errors.append(f"{AUTH_FACTORY_PATH.relative_to(ROOT)}: {reason}: {forbidden}")

application_forbidden_import = re.compile(
    r"^import\s+(?:android(?:x)?\.|com\.google\.|com\.aqua\.aqualight\.(?:data|platform|ui|composition)\.)",
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

central_construction_tokens = {
    "AuthRepository.create(": set(),
    "DefaultUserSettingsOperations(": {SETTINGS_IMPL_PATH},
    "DefaultUserProfileOperations(": {PROFILE_IMPL_PATH},
    "FirebaseFeedbackSubmissionOperations.create(": set(),
}
for kotlin_file in SOURCE_ROOT.rglob("*.kt"):
    if kotlin_file == CONTAINER_PATH:
        continue
    text = kotlin_file.read_text(encoding="utf-8", errors="ignore")
    for token, declaration_paths in central_construction_tokens.items():
        if kotlin_file in declaration_paths:
            continue
        if token in text:
            errors.append(
                f"{kotlin_file.relative_to(ROOT)}: {token} may only be constructed in AppContainer"
            )

if UI_ROOT.exists():
    for kotlin_file in UI_ROOT.rglob("*.kt"):
        if kotlin_file == AUTH_FACTORY_PATH:
            continue
        text = kotlin_file.read_text(encoding="utf-8", errors="ignore")
        if "AuthViewModelFactory(" in text:
            errors.append(
                f"{kotlin_file.relative_to(ROOT)}: UI must resolve the shared auth factory from AppContainer"
            )

migrated_auth_screens = {
    "ui/auth/LoginFragment.kt": ("authViewModelFactory", "googleIdentityClient"),
    "ui/auth/SignInFragment.kt": ("authViewModelFactory",),
    "ui/auth/RegisterFragment.kt": ("authViewModelFactory",),
    "ui/auth/ResetPasswordFragment.kt": ("authViewModelFactory",),
    "ui/auth/security/ReAuthenticateFragment.kt": ("accountSecurityOperations", "googleIdentityClient"),
    "ui/tabs/settings/logout/LogoutFragment.kt": ("sessionExitOperations", "accountSecurityOperations"),
    "ui/tabs/settings/logout/ChangeEmailFragment.kt": ("authViewModelFactory", "sessionExitOperations"),
    "ui/tabs/settings/logout/ChangePasswordFragment.kt": ("authViewModelFactory",),
}
auth_ui_forbidden = (
    "import com.google.firebase.",
    "import com.google.android.gms.auth.api.signin",
    "import com.aqua.aqualight.data.auth.AuthRepository",
    "import com.aqua.aqualight.data.auth.LogoutManager",
    "import com.aqua.aqualight.data.auth.AccountDeletionManager",
    "import com.aqua.aqualight.data.auth.GoogleSignInClientFactory",
    "import com.aqua.aqualight.ui.auth.security.ReAuthManager",
    "FirebaseAuth.getInstance()",
    "LogoutManager.create(",
    "AccountDeletionManager.create(",
)
for relative, required_tokens in migrated_auth_screens.items():
    path = SOURCE_ROOT / relative
    text = read(path)
    for token in ("requireAppContainer()", *required_tokens):
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: migrated auth UI missing {token}")
    for forbidden in auth_ui_forbidden:
        if forbidden in text:
            errors.append(f"{path.relative_to(ROOT)}: migrated auth UI contains {forbidden}")

migrated_settings_screens = {
    "ui/common/bottomsheet/ThemeBottomSheet.kt": "userSettingsOperations",
    "ui/tabs/settings/app/LanguageSettingsFragment.kt": "userSettingsOperations",
    "ui/tabs/settings/app/AppSettingsFragment.kt": "userSettingsOperations",
    "ui/tabs/settings/usage/UsageFragment.kt": "userSettingsOperations",
    "ui/tabs/settings/profile/EditProfileFragment.kt": "userProfileOperations",
    "ui/tabs/settings/userinfo/UserInfoFragment.kt": "userProfileOperations",
    "ui/tabs/settings/userinfo/UserAddressFragment.kt": "userProfileOperations",
    "ui/tabs/maintenance/AddCareTaskFragment.kt": "userSettingsOperations",
}
settings_ui_forbidden = (
    "import com.aqua.aqualight.data.user.UserPreferencesManager",
    "UserPreferencesManager.create(",
    "import com.aqua.aqualight.data.care.CareTaskDataStoreManager",
    "CareTaskDataStoreManager.create(",
    "import com.aqua.aqualight.data.care.reminder.CareTaskReminderScheduler",
    ".startupAppearanceCache",
)
for relative, required_token in migrated_settings_screens.items():
    path = SOURCE_ROOT / relative
    text = read(path)
    for token in ("requireAppContainer()", required_token):
        if token not in text:
            errors.append(f"{path.relative_to(ROOT)}: migrated settings/profile UI missing {token}")
    for forbidden in settings_ui_forbidden:
        if forbidden in text:
            errors.append(f"{path.relative_to(ROOT)}: migrated settings/profile UI contains {forbidden}")

feedback_path = SOURCE_ROOT / "ui/tabs/settings/feedback/FeedbackFragment.kt"
feedback = read(feedback_path)
for token in ("requireAppContainer()", "feedbackSubmissionOperations", "FeedbackSubmissionRequest"):
    if token not in feedback:
        errors.append(f"{feedback_path.relative_to(ROOT)}: feedback UI missing {token}")
for forbidden in (
    "import com.google.firebase.",
    "FirebaseAuth.getInstance()",
    "FirebaseFirestore",
    "Firebase.storage",
    "FieldValue.serverTimestamp()",
):
    if forbidden in feedback:
        errors.append(f"{feedback_path.relative_to(ROOT)}: feedback UI contains {forbidden}")

migrated_viewmodels = (
    "ui/tabs/settings/SettingsViewModel.kt",
    "ui/tabs/settings/device/DeviceStatusViewModel.kt",
    "ui/tabs/devices/DevicesViewModel.kt",
    "ui/tabs/devices/add/DeviceAddViewModel.kt",
    "ui/tabs/devices/add/DeviceQrScanViewModel.kt",
    "ui/tabs/devices/add/DeviceProvisioningProgressViewModel.kt",
    "ui/tabs/aquarium/AquariumTankViewModel.kt",
    "ui/tabs/aquarium/detail/TankDetailViewModel.kt",
    "ui/tabs/aquarium/detail/devices/TankDetailDevicesViewModel.kt",
    "ui/tabs/aquarium/detail/devices/select/TankDeviceSelectViewModel.kt",
    "ui/tabs/maintenance/MaintenanceViewModel.kt",
    "ui/tabs/devices/detail/common/DeviceRootOverviewViewModel.kt",
    "ui/tabs/devices/detail/light/DeviceLightRootViewModel.kt",
    "ui/tabs/devices/detail/cooling/DeviceCoolingRootViewModel.kt",
    "ui/tabs/devices/detail/dosing/DeviceDosingRootViewModel.kt",
    "ui/tabs/devices/detail/timer/DeviceTimerRootViewModel.kt",
)
viewmodel_forbidden = (
    "AndroidViewModel",
    "android.app.Application",
    "getApplication<",
    "DevicesRepositoryProvider.get(",
    "TankDeviceAssignmentRepositoryProvider.get(",
    "CareTaskDataStoreManager.create(",
    "AquariumTankDataStoreManager(",
    "UserPreferencesManager.create(",
    "AqlBleProvisioningAddressResolver(",
    "AqlBleProvisioningGattClient(",
    "AqlProvisioningHandoffSaver(",
    "AqlProvisioningDraftStore.",
    "UserDataScope.",
    "DeviceRouteResolver(",
)
for relative in migrated_viewmodels:
    path = SOURCE_ROOT / relative
    text = read(path)
    if Path(relative).stem not in factory:
        errors.append(f"{FACTORY_PATH.relative_to(ROOT)}: migrated ViewModel is not wired: {Path(relative).stem}")
    for forbidden in viewmodel_forbidden:
        if forbidden in text:
            errors.append(f"{path.relative_to(ROOT)}: migrated ViewModel contains {forbidden}")

for token, reason in (
    ("FakeAuthOperations", "auth ViewModels need a deterministic fake boundary"),
    ("createsEveryAuthViewModelFromOneFakeBoundary", "auth factory wiring needs regression coverage"),
    ("blankGoogleTokenIsRejectedBeforeFakeBoundaryIsInvoked", "validation must be testable without Firebase"),
    ("unknownViewModelTypeFailsClosed", "auth factory must reject unsupported ViewModels"),
):
    if token not in auth_factory_test:
        errors.append(f"{AUTH_FACTORY_TEST_PATH.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("FakeFeedbackSubmissionOperations", "feedback requires a deterministic fake adapter"),
    ("forwardsRequestFileAndSuccessThroughOneFakeBoundary", "feedback forwarding needs regression coverage"),
    ("forwardsTypedFailureWithoutFirebaseOrAndroidDependencies", "feedback failure mapping needs pure coverage"),
):
    if token not in feedback_test:
        errors.append(f"{FEEDBACK_TEST_PATH.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("FakeProvisioningOperations", "provisioning requires a deterministic fake operations boundary"),
    ("missing draft renders expired state without opening device runtime", "expired session behavior needs pure coverage"),
    ("existing draft renders ready state through one fake boundary", "ready state needs pure coverage"),
    ("binding the same session twice remains idempotent", "bind idempotency needs regression coverage"),
):
    if token not in provisioning_test:
        errors.append(f"{PROVISIONING_TEST_PATH.relative_to(ROOT)}: {reason}: {token}")

if errors:
    print("Composition root guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Composition root guard passed.")
