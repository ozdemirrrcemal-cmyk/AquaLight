#!/usr/bin/env python3
"""AquaLight composition-root migration guard.

The migration is intentionally incremental. This guard protects each completed
vertical slice so object construction cannot drift back into Fragments or
ViewModels while the remaining slices are moved into AppContainer.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight"
UI_ROOT = SOURCE_ROOT / "ui"
CONTAINER_PATH = SOURCE_ROOT / "composition/AppContainer.kt"
AQUA_APP_PATH = SOURCE_ROOT / "app/AquaApp.kt"
AUTH_FACTORY_PATH = SOURCE_ROOT / "ui/auth/viewmodel/AuthViewModelFactory.kt"

errors: list[str] = []


def read(path: Path) -> str:
    if not path.exists():
        errors.append(f"{path.relative_to(ROOT)}: required composition-root file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


container = read(CONTAINER_PATH)
aqua_app = read(AQUA_APP_PATH)
auth_factory = read(AUTH_FACTORY_PATH)

for token, reason in (
    ("interface AppContainer", "composition root contract must remain explicit"),
    ("internal class DefaultAppContainer", "production wiring must have one implementation"),
    ("val authViewModelFactory: ViewModelProvider.Factory", "UI must receive an already-wired factory"),
    ("AuthRepository.create(appContext)", "AuthRepository construction must remain centralized"),
    ("LazyThreadSafetyMode.SYNCHRONIZED", "process-scoped dependencies must be initialized safely"),
):
    if token not in container:
        errors.append(f"{CONTAINER_PATH.relative_to(ROOT)}: {reason}: {token}")

for token, reason in (
    ("lateinit var appContainer: AppContainer", "Application must own the process composition root"),
    ("appContainer = DefaultAppContainer(this)", "container must be initialized before app services"),
    ("appContainer.startupAppearanceCache", "startup cache must be resolved by the container"),
    ("appContainer.userPreferencesManager", "preferences must be resolved by the container"),
):
    if token not in aqua_app:
        errors.append(f"{AQUA_APP_PATH.relative_to(ROOT)}: {reason}: {token}")

for forbidden, reason in (
    ("android.content.Context", "ViewModel factory must not construct dependencies from Context"),
    ("AuthRepository.create(", "ViewModel factory must receive its repository by constructor"),
):
    if forbidden in auth_factory:
        errors.append(f"{AUTH_FACTORY_PATH.relative_to(ROOT)}: {reason}: {forbidden}")

for kotlin_file in SOURCE_ROOT.rglob("*.kt"):
    if kotlin_file == CONTAINER_PATH:
        continue
    text = kotlin_file.read_text(encoding="utf-8", errors="ignore")
    if "AuthRepository.create(" in text:
        errors.append(
            f"{kotlin_file.relative_to(ROOT)}: AuthRepository may only be constructed in AppContainer"
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

migrated_auth_screens = (
    "ui/auth/LoginFragment.kt",
    "ui/auth/SignInFragment.kt",
    "ui/auth/RegisterFragment.kt",
    "ui/auth/ResetPasswordFragment.kt",
    "ui/tabs/settings/logout/ChangeEmailFragment.kt",
    "ui/tabs/settings/logout/ChangePasswordFragment.kt",
)
for relative in migrated_auth_screens:
    path = SOURCE_ROOT / relative
    text = read(path)
    required = "requireAppContainer().authViewModelFactory"
    if required not in text:
        errors.append(
            f"{path.relative_to(ROOT)}: migrated auth screen must use AppContainer: {required}"
        )

if errors:
    print("Composition root guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Composition root guard passed.")
