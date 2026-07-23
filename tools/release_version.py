#!/usr/bin/env python3
"""Derive Android versionName/versionCode from a controlled SemVer tag."""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import asdict, dataclass
from pathlib import Path

ANDROID_MAX_VERSION_CODE = 2_100_000_000
TAG_PATTERN = re.compile(
    r"^v(?P<major>0|[1-9]\d*)\."
    r"(?P<minor>0|[1-9]\d*)\."
    r"(?P<patch>0|[1-9]\d*)"
    r"(?:-(?P<channel>alpha|beta|rc)\.(?P<sequence>[1-9]\d?))?$"
)
CHANNEL_BASE = {"alpha": 100, "beta": 300, "rc": 600}
STABLE_STAGE = 999


@dataclass(frozen=True)
class ReleaseVersion:
    tag: str
    version_name: str
    version_code: int
    prerelease: bool


def parse_release_tag(tag: str) -> ReleaseVersion:
    match = TAG_PATTERN.fullmatch(tag.strip())
    if not match:
        raise ValueError(
            "Release tag must match vMAJOR.MINOR.PATCH or "
            "vMAJOR.MINOR.PATCH-(alpha|beta|rc).N (N=1..99)."
        )

    major = int(match.group("major"))
    minor = int(match.group("minor"))
    patch = int(match.group("patch"))
    channel = match.group("channel")
    sequence = int(match.group("sequence")) if match.group("sequence") else None

    if major > 20 or minor > 99 or patch > 999:
        raise ValueError("Version components exceed Android versionCode allocation limits.")

    stage = STABLE_STAGE if channel is None else CHANNEL_BASE[channel] + sequence
    version_code = major * 100_000_000 + minor * 1_000_000 + patch * 1_000 + stage
    if version_code < 1 or version_code > ANDROID_MAX_VERSION_CODE:
        raise ValueError(f"Derived versionCode {version_code} is outside Android limits.")

    return ReleaseVersion(
        tag=tag.strip(),
        version_name=tag.strip()[1:],
        version_code=version_code,
        prerelease=channel is not None,
    )


def ci_version(run_number: int) -> ReleaseVersion:
    if run_number < 1 or run_number > 98_999_999:
        raise ValueError("CI run number must be between 1 and 98999999.")
    return ReleaseVersion(
        tag=f"ci-{run_number}",
        version_name=f"0.0.0-ci.{run_number}",
        version_code=1_000_000 + run_number,
        prerelease=True,
    )


def append_lines(path: str | None, lines: list[str]) -> None:
    if not path:
        return
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("a", encoding="utf-8") as stream:
        for line in lines:
            stream.write(f"{line}\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=False)
    source.add_argument("--tag", help="Git tag, for example v1.2.3 or v1.2.3-rc.1")
    source.add_argument("--ci-run-number", type=int, help="Generate a non-publishable CI version")
    parser.add_argument("--github-output", default=os.getenv("GITHUB_OUTPUT"))
    parser.add_argument("--github-env", default=os.getenv("GITHUB_ENV"))
    parser.add_argument("--json-output", type=Path)
    args = parser.parse_args()

    try:
        if args.ci_run_number is not None:
            version = ci_version(args.ci_run_number)
        else:
            tag = args.tag or os.getenv("GITHUB_REF_NAME", "")
            version = parse_release_tag(tag)
    except ValueError as exc:
        parser.error(str(exc))

    append_lines(
        args.github_output,
        [
            f"version_name={version.version_name}",
            f"version_code={version.version_code}",
            f"prerelease={'true' if version.prerelease else 'false'}",
        ],
    )
    append_lines(
        args.github_env,
        [
            f"AQL_VERSION_NAME={version.version_name}",
            f"AQL_VERSION_CODE={version.version_code}",
        ],
    )

    payload = asdict(version)
    if args.json_output:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
