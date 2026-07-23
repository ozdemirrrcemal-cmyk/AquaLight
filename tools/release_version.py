#!/usr/bin/env python3
"""Derive immutable Android release identity from an AquaLight semantic version tag."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

TAG_PATTERN = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
PLAY_MAX_VERSION_CODE = 2_100_000_000
MINOR_PATCH_BASE = 1_000
MAJOR_BASE = MINOR_PATCH_BASE * MINOR_PATCH_BASE


@dataclass(frozen=True, order=True)
class ReleaseIdentity:
    version_code: int
    version_name: str
    tag: str


def identity_from_tag(tag: str) -> ReleaseIdentity:
    match = TAG_PATTERN.fullmatch(tag.strip())
    if not match:
        raise ValueError("release tag must match vMAJOR.MINOR.PATCH without leading zeroes")

    major, minor, patch = (int(part) for part in match.groups())
    if minor >= MINOR_PATCH_BASE or patch >= MINOR_PATCH_BASE:
        raise ValueError("MINOR and PATCH must be between 0 and 999")

    version_code = major * MAJOR_BASE + minor * MINOR_PATCH_BASE + patch
    if version_code <= 0:
        raise ValueError("v0.0.0 is not a publishable release")
    if version_code > PLAY_MAX_VERSION_CODE:
        raise ValueError(f"derived versionCode exceeds Play maximum {PLAY_MAX_VERSION_CODE}")

    return ReleaseIdentity(
        version_code=version_code,
        version_name=f"{major}.{minor}.{patch}",
        tag=tag.strip(),
    )


def release_identities(tags: Iterable[str]) -> list[ReleaseIdentity]:
    identities: list[ReleaseIdentity] = []
    for tag in tags:
        try:
            identities.append(identity_from_tag(tag))
        except ValueError:
            continue
    return identities


def git_release_tags() -> list[str]:
    completed = subprocess.run(
        ["git", "tag", "--list", "v*"],
        check=True,
        capture_output=True,
        text=True,
    )
    return [line.strip() for line in completed.stdout.splitlines() if line.strip()]


def verify_monotonic(identity: ReleaseIdentity, tags: Iterable[str]) -> None:
    previous = [candidate for candidate in release_identities(tags) if candidate.tag != identity.tag]
    if not previous:
        return

    highest = max(previous)
    if identity.version_code <= highest.version_code:
        raise ValueError(
            "release version must be greater than every existing semantic release tag; "
            f"highest is {highest.tag} (versionCode {highest.version_code})"
        )


def append_github_output(path: str, identity: ReleaseIdentity) -> None:
    output = Path(path)
    with output.open("a", encoding="utf-8") as stream:
        stream.write(f"version_name={identity.version_name}\n")
        stream.write(f"version_code={identity.version_code}\n")
        stream.write(f"release_tag={identity.tag}\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True, help="Release tag in vMAJOR.MINOR.PATCH format")
    parser.add_argument(
        "--verify-git-tags",
        action="store_true",
        help="Require this tag to be newer than all semantic release tags in the repository",
    )
    parser.add_argument(
        "--github-output",
        default=os.environ.get("GITHUB_OUTPUT"),
        help="Optional GitHub Actions output file",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        identity = identity_from_tag(args.tag)
        if args.verify_git_tags:
            verify_monotonic(identity, git_release_tags())
    except (ValueError, subprocess.CalledProcessError) as error:
        print(f"release identity rejected: {error}", file=sys.stderr)
        return 2

    print(f"AQL_VERSION_NAME={identity.version_name}")
    print(f"AQL_VERSION_CODE={identity.version_code}")
    if args.github_output:
        append_github_output(args.github_output, identity)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
