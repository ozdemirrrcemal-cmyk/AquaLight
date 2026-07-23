#!/usr/bin/env python3
"""Validate an AquaLight release tag and derive Android version values."""

from __future__ import annotations

import argparse
import json
import re
from collections.abc import Iterable
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Sequence

FIRST_RELEASE_TAG = "v1.0.0"
MAX_COMPONENT = 999
MAX_PLAY_VERSION_CODE = 2_100_000_000
TAG_PATTERN = re.compile(
    r"^v(?P<major>0|[1-9][0-9]*)\."
    r"(?P<minor>0|[1-9][0-9]{0,2})\."
    r"(?P<patch>0|[1-9][0-9]{0,2})$"
)


class ReleaseVersionError(ValueError):
    """Raised when a release tag cannot produce a valid Android version."""


@dataclass(frozen=True)
class ReleaseVersion:
    tag: str
    version_name: str
    version_code: int


def parse_release_tag(tag: str) -> ReleaseVersion:
    """Parse exactly vMAJOR.MINOR.PATCH and derive a collision-free code."""
    match = TAG_PATTERN.fullmatch(tag)
    if match is None:
        raise ReleaseVersionError(
            "Release tag must use vMAJOR.MINOR.PATCH without leading zeroes; "
            f"MINOR and PATCH must be between 0 and {MAX_COMPONENT}."
        )

    major, minor, patch = (
        int(match.group("major")),
        int(match.group("minor")),
        int(match.group("patch")),
    )
    version_code = major * 1_000_000 + minor * 1_000 + patch

    if version_code < 1:
        raise ReleaseVersionError("Release tag must produce a positive versionCode.")
    if version_code > MAX_PLAY_VERSION_CODE:
        raise ReleaseVersionError(
            f"Release tag produces versionCode {version_code}, above Google Play's "
            f"{MAX_PLAY_VERSION_CODE} limit."
        )

    return ReleaseVersion(
        tag=tag,
        version_name=f"{major}.{minor}.{patch}",
        version_code=version_code,
    )


def require_newer_version(
    release: ReleaseVersion,
    previous_release: ReleaseVersion,
) -> None:
    """Require a strictly increasing Android versionCode."""
    if release.version_code <= previous_release.version_code:
        raise ReleaseVersionError(
            f"{release.tag} must be newer than {previous_release.tag}; "
            f"{release.version_code} is not greater than "
            f"{previous_release.version_code}."
        )


def require_release_sequence(
    release: ReleaseVersion,
    previous_releases: Iterable[ReleaseVersion],
) -> None:
    """Require v1.0.0 first, then a version newer than every prior release."""
    previous = tuple(previous_releases)
    if not previous:
        if release.tag != FIRST_RELEASE_TAG:
            raise ReleaseVersionError(
                f"First production release must be {FIRST_RELEASE_TAG}; "
                f"received {release.tag}."
            )
        return

    latest = max(previous, key=lambda item: item.version_code)
    require_newer_version(release, latest)


def write_github_output(path: Path, release: ReleaseVersion) -> None:
    """Append safe scalar values for later GitHub Actions steps."""
    with path.open("a", encoding="utf-8", newline="\n") as output:
        output.write(f"release_tag={release.tag}\n")
        output.write(f"version_name={release.version_name}\n")
        output.write(f"version_code={release.version_code}\n")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Derive deterministic Android versionName/versionCode values from "
            "an AquaLight release tag."
        )
    )
    parser.add_argument("tag", help="Release tag in exact vMAJOR.MINOR.PATCH form.")
    parser.add_argument(
        "--previous-tag",
        action="append",
        default=[],
        help=(
            "Prior production tag on main. Repeat for every prior release. "
            f"When omitted, the requested tag must be {FIRST_RELEASE_TAG}."
        ),
    )
    parser.add_argument(
        "--github-output",
        type=Path,
        help="Optional GitHub Actions output file to append.",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        release = parse_release_tag(args.tag)
        require_release_sequence(
            release,
            (parse_release_tag(tag) for tag in args.previous_tag),
        )
        if args.github_output is not None:
            write_github_output(args.github_output, release)
    except (OSError, ReleaseVersionError) as error:
        raise SystemExit(f"release-version error: {error}") from error

    print(json.dumps(asdict(release), sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
