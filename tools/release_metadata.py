#!/usr/bin/env python3
"""Validate an AquaLight production tag and derive Android release metadata."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass

ANDROID_MAX_VERSION_CODE = 2_100_000_000
SEMVER_COMPONENT_LIMIT = 1_000
TAG_PATTERN = re.compile(
    r"^v(?P<major>0|[1-9][0-9]*)\."
    r"(?P<minor>0|[1-9][0-9]*)\."
    r"(?P<patch>0|[1-9][0-9]*)$"
)


class ReleaseMetadataError(ValueError):
    """Raised when a production tag cannot safely identify an Android release."""


@dataclass(frozen=True, order=True)
class ReleaseVersion:
    major: int
    minor: int
    patch: int

    @property
    def version_name(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"

    @property
    def tag(self) -> str:
        return f"v{self.version_name}"

    @property
    def version_code(self) -> int:
        return self.major * 1_000_000 + self.minor * 1_000 + self.patch

    @property
    def artifact_base(self) -> str:
        return f"AquaLight-{self.version_name}"


def parse_release_tag(tag: str) -> ReleaseVersion:
    match = TAG_PATTERN.fullmatch(tag)
    if match is None:
        raise ReleaseMetadataError(
            "Production tags must use the exact format vMAJOR.MINOR.PATCH "
            "with non-negative decimal components and no prerelease suffix."
        )

    version = ReleaseVersion(
        major=int(match.group("major")),
        minor=int(match.group("minor")),
        patch=int(match.group("patch")),
    )

    if version.minor >= SEMVER_COMPONENT_LIMIT or version.patch >= SEMVER_COMPONENT_LIMIT:
        raise ReleaseMetadataError("MINOR and PATCH must each be lower than 1000.")

    if not 0 < version.version_code <= ANDROID_MAX_VERSION_CODE:
        raise ReleaseMetadataError(
            f"Derived versionCode {version.version_code} is outside Android's "
            f"supported range 1..{ANDROID_MAX_VERSION_CODE}."
        )

    return version


def validate_monotonic_release(
    current: ReleaseVersion,
    previous: ReleaseVersion | None,
) -> None:
    if previous is None:
        return
    if current <= previous:
        raise ReleaseMetadataError(
            f"Release tag {current.tag} must be newer than previous production tag "
            f"{previous.tag}."
        )
    if current.version_code <= previous.version_code:
        raise ReleaseMetadataError(
            f"Derived versionCode {current.version_code} must be greater than previous "
            f"versionCode {previous.version_code}."
        )


def render_github_output(
    current: ReleaseVersion,
    previous: ReleaseVersion | None,
) -> str:
    values = {
        "release_tag": current.tag,
        "version_name": current.version_name,
        "version_code": str(current.version_code),
        "artifact_base": current.artifact_base,
        "previous_release_tag": previous.tag if previous else "",
    }
    return "\n".join(f"{key}={value}" for key, value in values.items())


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Validate an immutable AquaLight production tag and emit deterministic "
            "Android release metadata as GitHub Actions key=value output."
        )
    )
    parser.add_argument("--tag", required=True, help="Current production tag")
    parser.add_argument(
        "--previous-tag",
        help="Most recent existing production tag, when one exists",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        current = parse_release_tag(args.tag)
        previous = parse_release_tag(args.previous_tag) if args.previous_tag else None
        validate_monotonic_release(current, previous)
    except ReleaseMetadataError as exc:
        print(f"release metadata error: {exc}", file=sys.stderr)
        return 2

    print(render_github_output(current, previous))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
