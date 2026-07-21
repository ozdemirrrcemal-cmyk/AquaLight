#!/usr/bin/env python3
"""Privacy/legal release guard for the text-only Firebase configuration."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app" / "src" / "main"
APPROVAL = ROOT / "docs" / "stage12-release-approval.json"


class Guard:
    def __init__(self) -> None:
        self.errors: list[str] = []

    def require(self, condition: bool, message: str) -> None:
        if not condition:
            self.errors.append(message)

    def text(self, path: Path) -> str:
        self.require(path.is_file(), f"Missing required file: {path.relative_to(ROOT)}")
        return path.read_text(encoding="utf-8") if path.is_file() else ""


def h2_count(value: str) -> int:
    return len(re.findall(r"<h2(?:\s[^>]*)?>", value, flags=re.IGNORECASE))


def technical_checks(guard: Guard) -> None:
    app_gradle = guard.text(ROOT / "app" / "build.gradle")
    settings_gradle = guard.text(ROOT / "settings.gradle")
    combined_gradle = app_gradle + "\n" + settings_gradle

    for dependency in (
        "firebase-analytics",
        "firebase-crashlytics",
        "firebase-perf",
        "firebase-database",
        "firebase-messaging",
        "firebase-config",
        "firebase-storage",
    ):
        guard.require(
            dependency not in combined_gradle,
            f"Forbidden Firebase dependency remains: {dependency}",
        )

    for dependency in ("firebase-auth", "firebase-firestore"):
        guard.require(
            dependency in app_gradle,
            f"Required Firebase dependency is missing: {dependency}",
        )

    guard.require(
        not (ROOT / "storage.rules").exists(),
        "Storage rules must be removed when Cloud Storage is not used.",
    )

    firebase_config = guard.text(ROOT / "firebase.json")
    guard.require('"storage"' not in firebase_config, "firebase.json still configures Storage.")

    legal_paths = {
        "privacy_en": APP / "assets" / "privacy_policy_en.html",
        "privacy_tr": APP / "assets" / "privacy_policy_tr.html",
        "terms_en": APP / "assets" / "terms_of_use_en.html",
        "terms_tr": APP / "assets" / "terms_of_use_tr.html",
    }
    legal = {name: guard.text(path) for name, path in legal_paths.items()}

    guard.require(
        h2_count(legal["privacy_en"]) == h2_count(legal["privacy_tr"]),
        "English and Turkish Privacy Policy section counts differ.",
    )
    guard.require(
        h2_count(legal["terms_en"]) == h2_count(legal["terms_tr"]),
        "English and Turkish Terms section counts differ.",
    )
    guard.require("18 and over" in legal["privacy_en"], "English Privacy age policy is not 18+.")
    guard.require("18 yaş" in legal["privacy_tr"], "Turkish Privacy age policy is not 18+.")
    guard.require("at least 18" in legal["terms_en"], "English Terms age policy is not 18+.")
    guard.require("En az 18" in legal["terms_tr"], "Turkish Terms age policy is not 18+.")

    legal_text = "\n".join(legal.values()).lower()
    for obsolete in (
        "feedback screenshot",
        "geri bildirim ekran görünt",
        "cloud storage for firebase",
        "firebase storage",
    ):
        guard.require(obsolete not in legal_text, f"Obsolete Storage disclosure remains: {obsolete}")

    webview = guard.text(
        APP / "java/com/aqua/aqualight/ui/common/web/LegalDocumentWebView.kt"
    )
    for token in (
        "WebViewAssetLoader",
        "appassets.androidplatform.net",
        "allowFileAccess = false",
        "allowContentAccess = false",
        "blockNetworkLoads = true",
        "MIXED_CONTENT_NEVER_ALLOW",
        "PRIVACY_POLICY",
        "TERMS_OF_USE",
    ):
        guard.require(token in webview, f"Secure legal WebView invariant missing: {token}")

    feedback_fragment = guard.text(
        APP / "java/com/aqua/aqualight/ui/tabs/settings/feedback/FeedbackFragment.kt"
    )
    feedback_view_model = guard.text(
        APP / "java/com/aqua/aqualight/ui/tabs/settings/feedback/FeedbackViewModel.kt"
    )
    feedback_repository = guard.text(
        APP / "java/com/aqua/aqualight/data/feedback/FirebaseFeedbackSubmissionOperations.kt"
    )
    feedback_layout = guard.text(APP / "res/layout/fragment_feedback.xml")
    combined_feedback = "\n".join(
        (feedback_fragment, feedback_view_model, feedback_repository, feedback_layout)
    )
    for token in (
        "FirebaseStorage",
        "feedback_screenshots",
        "screenshotFile",
        "mediaTransactionExpiresAt",
        "cardScreenshot",
        "rowAddScreenshot",
    ):
        guard.require(token not in combined_feedback, f"Removed feedback media token remains: {token}")

    feedback_en = guard.text(APP / "res/values/stage12_privacy_strings.xml")
    feedback_tr = guard.text(APP / "res/values-tr/stage12_privacy_strings.xml")
    guard.require(
        "feedback_privacy_notice" in feedback_en and "Firebase" in feedback_en,
        "English feedback pre-submit disclosure is missing.",
    )
    guard.require(
        "feedback_privacy_notice" in feedback_tr and "Firebase" in feedback_tr,
        "Turkish feedback pre-submit disclosure is missing.",
    )
    guard.require("screenshot" not in feedback_en.lower(), "English notice still mentions screenshots.")
    guard.require("ekran görünt" not in feedback_tr.lower(), "Turkish notice still mentions screenshots.")

    firestore_rules = guard.text(ROOT / "firestore.rules")
    for token in (
        "textFeedbackIsValid",
        "allow create: if textFeedbackIsValid()",
        "allow read, delete: if isOwner(resource.data.userId)",
        "allow update: if false",
    ):
        guard.require(token in firestore_rules, f"Firestore invariant missing: {token}")
    for token in ("screenshotPath", "screenshotUrl", "mediaTransaction"):
        guard.require(token not in firestore_rules, f"Firestore media field remains: {token}")

    cloud_cleaner = guard.text(
        APP / "java/com/aqua/aqualight/data/user/CloudUserDataCleaner.kt"
    )
    guard.require(
        "feedbackDocumentCleaner.deleteAll(uid)" in cloud_cleaner,
        "Account deletion no longer removes owner feedback documents.",
    )
    guard.require("FirebaseStorage" not in cloud_cleaner, "Account deletion still depends on Storage.")

    provider_register = guard.text(ROOT / "docs/stage12-firebase-provider-region-register.md")
    guard.require("europe-west1" in provider_register, "Verified Firestore location is not recorded.")
    guard.require("Cloud Storage" not in provider_register, "Provider register still lists Cloud Storage.")

    for required_doc in (
        "stage12-retention-deletion-policy.md",
        "stage12-firebase-provider-region-register.md",
        "stage12-kvkk-gdpr-compliance-matrix.md",
    ):
        guard.require(
            (ROOT / "docs" / required_doc).is_file(),
            f"Missing privacy/legal register: {required_doc}",
        )


def release_checks(guard: Guard) -> None:
    raw = guard.text(APPROVAL)
    try:
        approval = json.loads(raw)
    except json.JSONDecodeError as error:
        guard.errors.append(f"Invalid release approval JSON: {error}")
        return

    legal = approval.get("legal_review", {})
    firebase = approval.get("firebase", {})
    retention = approval.get("retention", {})
    play = approval.get("google_play", {})

    guard.require(legal.get("status") == "approved", "Qualified legal review is not approved.")
    guard.require(bool(str(legal.get("reviewer", "")).strip()), "Legal reviewer identity is missing.")
    guard.require(bool(str(legal.get("review_date", "")).strip()), "Legal review date is missing.")
    guard.require(
        bool(re.fullmatch(r"[0-9a-f]{40}", str(legal.get("reviewed_commit", "")))),
        "Legal reviewed_commit must be a full 40-character commit SHA.",
    )

    guard.require(
        firebase.get("firestore_location") == "europe-west1",
        "Verified production Firestore location is missing or incorrect.",
    )
    guard.require(
        retention.get("feedback_retention_process_verified") is True,
        "Feedback retention/deletion process has not been verified.",
    )
    guard.require(
        retention.get("account_deletion_test_verified") is True,
        "Production account-deletion verification is missing.",
    )

    policy_url = str(play.get("privacy_policy_url", "")).strip()
    guard.require(
        policy_url.startswith("https://"),
        "Google Play privacy policy URL is missing or not HTTPS.",
    )
    guard.require(
        play.get("data_safety_reviewed") is True,
        "Google Play Data Safety declaration is not reviewed.",
    )
    guard.require(
        approval.get("release_approved") is True,
        "Commercial release approval remains false.",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--technical",
        action="store_true",
        help="Run repository checks without requiring external approval evidence.",
    )
    args = parser.parse_args()

    guard = Guard()
    technical_checks(guard)
    if not args.technical:
        release_checks(guard)

    if guard.errors:
        print("Privacy/legal guard failed:", file=sys.stderr)
        for error in guard.errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    mode = "technical" if args.technical else "commercial release"
    print(f"Privacy/legal {mode} guard passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
