#!/usr/bin/env python3
"""Finalize and validate the one-time Detekt CLI migration idempotently."""

from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP_BUILD = ROOT / "app" / "build.gradle"
SETTINGS = ROOT / "settings.gradle"
INITIAL_MIGRATION = ROOT / "tools" / "ci" / "apply_detekt_cli_migration.py"


def main() -> int:
    app = APP_BUILD.read_text(encoding="utf-8")
    already_migrated = (
        'tasks.register("aqlDetekt", JavaExec)' in app
        and 'aqlDetekt "io.gitlab.arturbosch.detekt:detekt-cli:1.23.8"' in app
        and "id 'io.gitlab.arturbosch.detekt'" not in app
    )

    if not already_migrated:
        subprocess.run(["python3", str(INITIAL_MIGRATION)], cwd=ROOT, check=True)
        app = APP_BUILD.read_text(encoding="utf-8")

    settings = SETTINGS.read_text(encoding="utf-8")
    required_app_markers = (
        'tasks.register("aqlDetekt", JavaExec)',
        'aqlDetekt "io.gitlab.arturbosch.detekt:detekt-cli:1.23.8"',
        'outputs.files(aqlDetektReportFiles.values())',
        'missingReports.collect { it.absolutePath }',
    )
    missing = [marker for marker in required_app_markers if marker not in app]
    if missing:
        raise SystemExit(f"Detekt CLI migration is incomplete; missing markers: {missing}")
    if "id 'io.gitlab.arturbosch.detekt'" in app or "id 'io.gitlab.arturbosch.detekt'" in settings:
        raise SystemExit("Conflicting Detekt Gradle plugin reference remains")

    print("Detekt CLI migration finalized and validated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
