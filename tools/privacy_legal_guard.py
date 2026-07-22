#!/usr/bin/env python3
"""Protect the commercial privacy/legal implementation from silent drift."""

from __future__ import annotations

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]

REQUIRED_FILES = (
    "app/src/main/assets/privacy_policy_en.html",
    "app/src/main/assets/privacy_policy_tr.html",
    "app/src/main/assets/terms_of_use_en.html",
    "app/src/main/assets/terms_of_use_tr.html",
    "app/src/main/assets/legal.css",
    "app/src/main/java/com/aqua/aqualight/ui/common/web/SecureLocalWebContent.kt",
    "app/src/main/java/com/aqua/aqualight/data/feedback/FeedbackFirestoreProvider.kt",
    "app/src/main/java/com/aqua/aqualight/data/auth/AccountDeletionCheckpointStore.kt",
    "app/src/test/java/com/aqua/aqualight/data/auth/AccountDeletionProcessDeathMatrixTest.kt",
    "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/AccountDeletionProcessDeathSmokeActivity.kt",
    ".github/workflows/google_play_publication_readiness.yml",
    "firestore.rules",
    "firestore.indexes.json",
    "docs/commercial/data-inventory-and-retention.md",
    "docs/commercial/account-deletion-process-death-matrix.md",
    "docs/commercial/firebase-production-evidence.md",
    "docs/commercial/privacy-legal-release-checklist.md",
    "tools/google_play_publication_guard.py",
)

REQUIRED_WEBVIEW_TOKENS = (
    "WebViewAssetLoader",
    "javaScriptEnabled = false",
    "domStorageEnabled = false",
    "allowFileAccess = false",
    "allowContentAccess = false",
    "blockNetworkLoads = true",
    "MIXED_CONTENT_NEVER_ALLOW",
)


def text(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def main() -> int:
    failures: list[str] = []
    for relative_path in REQUIRED_FILES:
        if not (ROOT / relative_path).is_file():
            failures.append(f"required commercial privacy file is missing: {relative_path}")

    if failures:
        return fail(failures)

    source_text = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (ROOT / "app" / "src" / "main").rglob("*")
        if path.is_file() and path.suffix in {".kt", ".java", ".xml"}
    )
    if "file:///android_asset" in source_text:
        failures.append("file:// Android asset loading is forbidden")

    secure_web = text(
        "app/src/main/java/com/aqua/aqualight/ui/common/web/SecureLocalWebContent.kt"
    )
    for token in REQUIRED_WEBVIEW_TOKENS:
        if token not in secure_web:
            failures.append(f"secure legal WebView control is missing: {token}")

    policy_text = text("app/src/main/assets/privacy_policy_en.html") + text(
        "app/src/main/assets/privacy_policy_tr.html"
    )
    for token in (
        "europe-west1",
        "12 months",
        "12 ay",
        "180 days",
        "180 gün",
        "screenshot",
        "ekran görüntüsü",
        "Firebase Authentication",
    ):
        if token not in policy_text:
            failures.append(f"bilingual privacy disclosure is missing: {token}")

    terms_text = (text("app/src/main/assets/terms_of_use_en.html") + text(
        "app/src/main/assets/terms_of_use_tr.html"
    )).lower()
    for token in ("at least 18", "en az 18", "stored locally", "yerel olarak saklanır"):
        if token not in terms_text:
            failures.append(f"bilingual Terms control is missing: {token}")

    feedback_text = text("app/src/main/res/values/feedback_hardening_strings.xml") + text(
        "app/src/main/res/values-tr/feedback_hardening_strings.xml"
    )
    for token in (
        "provide support",
        "destek sağlamak",
        "improve AquaLight",
        "AquaLight’ı geliştirmek",
        "Privacy Policy",
        "Gizlilik ve KVKK Metni",
        "sensitive personal information",
        "hassas kişisel bilgi",
    ):
        if token not in feedback_text:
            failures.append(f"feedback point-of-collection notice is missing: {token}")

    deletion_text = text(
        "app/src/main/java/com/aqua/aqualight/data/auth/AccountDeletionManager.kt"
    ) + text("app/src/main/java/com/aqua/aqualight/app/AquaApp.kt")
    for token in (
        "CLOUD_CLEARED",
        "AUTH_DELETE_REQUESTED",
        "ACCOUNT_DELETED",
        "resumePendingDeletion",
    ):
        if token not in deletion_text:
            failures.append(f"restartable account deletion control is missing: {token}")

    deletion_test_text = text(
        "app/src/test/java/com/aqua/aqualight/data/auth/AccountDeletionProcessDeathMatrixTest.kt"
    ) + text(
        "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/AccountDeletionProcessDeathSmokeActivity.kt"
    ) + text("tools/run_release_smoke.sh")
    for token in (
        "started",
        "cloud-cleared",
        "auth-delete-requested",
        "auth-confirmed-before-checkpoint",
        "account-deleted",
        "ACCOUNT_DELETION_PROCESS_DEATH_PASS",
        "am force-stop",
    ):
        if token not in deletion_test_text:
            failures.append(f"process-death deletion matrix is missing: {token}")

    release_checklist = text("docs/commercial/privacy-legal-release-checklist.md")
    for token in (
        "assembleRelease",
        "not approved for Google Play production publication",
        "tools/google_play_publication_guard.py",
    ):
        if token not in release_checklist:
            failures.append(f"release-build/publication-gate separation is missing: {token}")

    firestore_rules = text("firestore.rules")
    for token in (
        "admin_access",
        "retention_audits",
        "manual-admin-panel",
        "feedback-admin",
        "deletedCount <= 100",
        "allow list, create, update, delete: if false",
    ):
        if token not in firestore_rules:
            failures.append(f"manual retention security control is missing: {token}")

    if failures:
        return fail(failures)

    print("Privacy/legal commercial guard passed.")
    return 0


def fail(failures: list[str]) -> int:
    print("Privacy/legal commercial guard failed:", file=sys.stderr)
    for failure in failures:
        print(f"- {failure}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
