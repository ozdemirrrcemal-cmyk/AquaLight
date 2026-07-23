#!/usr/bin/env python3
"""Derive deterministic Android version metadata from a stable release tag."""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path

TAG_PATTERN = re.compile(r"^v(?P<major>0|[1-9]\d*)\.(?P<minor>0|[1-9]\d*)\.(?P<patch>0|[1-9]\d*)$")
ANDROID_MAX_VERSION_CODE = 2_100_000_000
SEMVER_COMPONENT_LIMIT = 999


@dataclass(frozen=True)
class ReleaseVersion:
    tag: str
    version_name: str
    version_code: int
    major: int
    minor: int
    patch: int


def parse_release_tag(tag: str) -> ReleaseVersion:
    """Parse vMAJOR.MINOR.PATCH and produce a monotonically increasing versionCode."""
    match = TAG_PATTERN.fullmatch(tag.strip())
    if match is None:
        raise ValueError("release tag must match stable SemVer exactly: vMAJOR.MINOR.PATCH")

    major = int(match.group("major"))
    minor = int(match.group("minor"))
    patch = int(match.group("patch"))
    if minor > SEMVER_COMPONENT_LIMIT or patch > SEMVER_COMPONENT_LIMIT:
        raise ValueError("minor and patch components must be between 0 and 999")

    version_code = major * 1_000_000 + minor * 1_000 + patch
    if version_code <= 0:
        raise ValueError("Android versionCode must be positive")
    if version_code > ANDROID_MAX_VERSION_CODE:
        raise ValueError(
            f"derived Android versionCode exceeds {ANDROID_MAX_VERSION_CODE}: {version_code}"
        )

    return ReleaseVersion(
        tag=tag.strip(),
        version_name=f"{major}.{minor}.{patch}",
        version_code=version_code,
        major=major,
        minor=minor,
        patch=patch,
    )


def write_github_output(path: Path, release: ReleaseVersion) -> None:
    with path.open("a", encoding="utf-8") as output:
        output.write(f"tag={release.tag}\n")
        output.write(f"version_name={release.version_name}\n")
        output.write(f"version_code={release.version_code}\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True, help="Stable release tag, for example v1.2.3")
    parser.add_argument(
        "--github-output",
        type=Path,
        help="Append tag, version_name, and version_code to this GitHub output file",
    )
    parser.add_argument("--json", action="store_true", help="Print JSON instead of key=value lines")
    args = parser.parse_args()

    try:
        release = parse_release_tag(args.tag)
    except ValueError as exc:
        parser.error(str(exc))

    if args.github_output is not None:
        write_github_output(args.github_output, release)

    if args.json:
        print(json.dumps(asdict(release), sort_keys=True))
    else:
        print(f"tag={release.tag}")
        print(f"version_name={release.version_name}")
        print(f"version_code={release.version_code}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
