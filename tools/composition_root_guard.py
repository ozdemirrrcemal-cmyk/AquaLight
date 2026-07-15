#!/usr/bin/env python3
"""AquaLight composition-root migration guard.

The migration is intentionally incremental. This guard protects each completed
vertical slice so object construction cannot drift back into Fragments or
ViewModels while the remaining slices are moved into AppContainer.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight"
UI_ROOT = SOURCE_ROOT / "ui"
APPLICATION_ROOT = SOURCE_ROOT / "application"
CONTAINER_PATH = SOURCE_ROOT / "composition/AppContainer.kt"
AQUA_APP_PATH = SOURCE_ROOT / "app/AquaApp.kt"
AUTH_FACTORY_PATH = SOURCE_ROOT / "ui/auth/viewmodel/AuthViewModelFactory.kt"
AUTH_FACTORY_TEST_PATH = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/ui/auth/viewmodel/AuthViewModelFactoryTest.kt"
)
PLAN_PATH = ROOT / "docs/stage-3-dependency-boundaries-plan.md"

errors: list[str] = []


def read(path: Path) -> str:
    if not path.exists():
        errors.append(f"{path.relative_to(ROOT)}: required composition-root file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


container = read(CONTAINER_PATH)
aqua_app = read(AQUA_APP_PATH)
auth_factory = read(AUTH_FACTORY_PATH)
auth_factory_test = read(AUTH_FACTORY_TEST_PATH)
plan = read(PLAN_PATH)

for token, reason in (
    ("interface AppContainer", "composition root contract must remain explicit"),
    ("internal class DefaultAppContainer", "production wiring must have one implementation"),
    ("val authViewModelFactory: ViewModelProvider.Factory", "UI must receive an already-wired factory"),
    ("val sessionExitOperations: SessionExitOperations", "logout must use an application boundary"),
    ("val accountSecurityOperations: AccountSecurityOperations", "account security must use an application boundary"),
    ("val googleIdentityClient: GoogleIdentityClient", "Google identity platform access must be centralized"),
    ("val userSettingsOperations: UserSettingsOperations", "settings must use an application boundary"),
    ("val userProfileOperations: UserProfileOperations", "profile persistence must use an application boundary"),
    ("AuthRepository.create(appContext)", "AuthRepository construction must remain centralized"),
    ("LogoutManager.create(appContext)", "logout manager construction must remain centralized"),
    ("FirebaseAccountSecurityOperations.create(appContext)", "account security construction must remain centralized"),
    ("DefaultGoogleIdentityClient(appContext)", "Google identity client construction must remain centralized"),
    ("DefaultUserSettingsOperations(", "settings implementation construction must remain centralized"),
    ("DefaultUserProfileOperations(", "profile implementation construction must remain centralized"),
    ("LazyThreadSafetyMode.SYNCHRONIZED", "process-scoped dependencies must be initialized safely"),
):
    if token not in container:
        errors.append(f"{CONTAINER_PATH.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("lateinit var appContainer: AppContainer", "Application must own the process composition root"),
    ("appContainer = DefaultAppContainer(this)", "container must be initialized before app services"),
    ("appContainer.startupAppearanceCache", "startup cache must be resolved by the container"),
    ("appContainer.userPreferencesManager", "startup preferences must be resolved by the container"),
):
    if token not in aqua_app:
        errors.append(f"{AQUA_APP_PATH.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("UI -> application contracts/use cases", "migration plan must define dependency direction"),
    ("Definition of done", "migration plan must retain its commercial completion gate"),
    ("Feedback vendor isolation", "remaining Firebase UI removal must stay visible"),
):
    if token not in plan:
        errors.append(f"{PLAN_PATH.relative_to(ROOT)}: {reason}: {token}")

for forbidden, reason in (
    ("android.content.Context", "ViewModel factory must not construct dependencies from Context"),
    ("AuthRepository.create(", "ViewModel factory must receive its dependency by constructor"),
    ("com.google.firebase", "ViewModel factory must not know Firebase"),
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
                f"Android/Firebase/data/platform/UI independent: {match.group(0)}"
            )

central_construction_tokens = (
    "AuthRepository.create(",
    "DefaultUserSettingsOperations(",
    "DefaultUserProfileOperations(",
)
for kotlin_file in SOURCE_ROOT.rglob("*.kt"):
    if kotlin_file == CONTAINER_PATH:
        continue
    text = kotlin_file.read_text(encoding="utf-8", errors="ignore")
    for token in central_construction_tokens:
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

migrated_screen_requirements = {
    "ui/auth/LoginFragment.kt": (
        "requireAppContainer()",
        "authViewModelFactory",
        "googleIdentityClient",
    ),
    "ui/auth/SignInFragment.kt": (
        "requireAppContainer()",
        "authViewModelFactory",
    ),
    "ui/auth/RegisterFragment.kt": (
        "requireAppContainer()",
        "authViewModelFactory",
    ),
    "ui/auth/ResetPasswordFragment.kt": (
        "requireAppContainer()",
        "authViewModelFactory",
    ),
    "ui/auth/security/ReAuthenticateFragment.kt": (
        "requireAppContainer()",
        "accountSecurityOperations",
        "googleIdentityClient",
    ),
    "ui/tabs/settings/logout/LogoutFragment.kt": (
        "requireAppContainer()",
        "sessionExitOperations",
        "accountSecurityOperations",
    ),
    "ui/tabs/settings/logout/ChangeEmailFragment.kt": (
        "requireAppContainer()",
        "authViewModelFactory",
        "sessionExitOperations",
    ),
    "ui/tabs/settings/logout/ChangePasswordFragment.kt": (
        "requireAppContainer()",
        "authViewModelFactory",
    ),
}

migrated_ui_forbidden = (
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

for relative, required_tokens in migrated_screen_requirements.items():
    path = SOURCE_ROOT / relative
    text = read(path)

    missing = [token for token in required_tokens if token not in text]
    if missing:
        errors.append(
            f"{path.relative_to(ROOT)}: migrated screen must resolve dependencies from "
            f"AppContainer; missing {', '.join(missing)}"
        )

    for forbidden in migrated_ui_forbidden:
        if forbidden in text:
            errors.append(
                f"{path.relative_to(ROOT)}: migrated UI must not access concrete auth/platform "
                f"implementations: {forbidden}"
            )

settings_screen_requirements = {
    "ui/common/bottomsheet/ThemeBottomSheet.kt": "userSettingsOperations",
    "ui/tabs/settings/app/LanguageSettingsFragment.kt": "userSettingsOperations",
    "ui/tabs/settings/app/AppSettingsFragment.kt": "userSettingsOperations",
    "ui/tabs/settings/usage/UsageFragment.kt": "userSettingsOperations",
    "ui/tabs/settings/profile/EditProfileFragment.kt": "userProfileOperations",
}
settings_ui_forbidden = (
    "import com.aqua.aqualight.data.user.UserPreferencesManager",
    "UserPreferencesManager.create(",
    "import com.aqua.aqualight.data.care.CareTaskDataStoreManager",
    "CareTaskDataStoreManager.create(",
    "import com.aqua.aqualight.data.care.reminder.CareTaskReminderScheduler",
    ".startupAppearanceCache",
)
for relative, required_token in settings_screen_requirements.items():
    path = SOURCE_ROOT / relative
    text = read(path)
    for token in ("requireAppContainer()", required_token):
        if token not in text:
            errors.append(
                f"{path.relative_to(ROOT)}: settings/profile UI must resolve {required_token} "
                f"from AppContainer; missing {token}"
            )
    for forbidden in settings_ui_forbidden:
        if forbidden in text:
            errors.append(
                f"{path.relative_to(ROOT)}: settings/profile UI must not access concrete "
                f"DataStore/reminder implementations: {forbidden}"
            )

for token, reason in (
    ("FakeAuthOperations", "auth ViewModels need a deterministic fake boundary"),
    ("createsEveryAuthViewModelFromOneFakeBoundary", "factory wiring needs regression coverage"),
    ("blankGoogleTokenIsRejectedBeforeFakeBoundaryIsInvoked", "validation must be testable without Firebase"),
    ("unknownViewModelTypeFailsClosed", "factory must reject unsupported ViewModels"),
):
    if token not in auth_factory_test:
        errors.append(f"{AUTH_FACTORY_TEST_PATH.relative_to(ROOT)}: {reason}: {token}")

if errors:
    print("Composition root guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Composition root guard passed.")
