#!/usr/bin/env python3
"""Derive Android release versions from an AquaLight release tag.

The commercial release contract is:
- tag: vMAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH-(alpha|beta|rc).N
- versionName: the tag without the leading ``v``
- versionCode: repository baseline + GitHub run number

The baseline must be set to at least the highest versionCode already published in
Google Play. GitHub's monotonically increasing ``run_number`` then guarantees a
new code for each release workflow run while keeping re-runs deterministic.
"""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import asdict, dataclass
from pathlib import Path

MAX_ANDROID_VERSION_CODE = 2_100_000_000
TAG_PATTERN = re.compile(
    r"^v(?P<major>0|[1-9]\d*)\."
    r"(?P<minor>0|[1-9]\d*)\."
    r"(?P<patch>0|[1-9]\d*)"
    r"(?:-(?P<channel>alpha|beta|rc)\.(?P<sequence>0|[1-9]\d*))?$"
)


class VersioningError(ValueError):
    """Raised when release inputs violate the version contract."""


@dataclass(frozen=True)
class ReleaseVersion:
    tag: str
    version_name: str
    version_code: int
    prerelease: bool


def derive_release_version(tag: str, run_number: int, version_code_base: int) -> ReleaseVersion:
    normalized_tag = tag.strip()
    match = TAG_PATTERN.fullmatch(normalized_tag)
    if match is None:
        raise VersioningError(
            "Release tag must match vMAJOR.MINOR.PATCH or "
            "vMAJOR.MINOR.PATCH-(alpha|beta|rc).N"
        )
    if run_number <= 0:
        raise VersioningError("GitHub run number must be a positive integer")
    if version_code_base < 0:
        raise VersioningError("versionCode baseline cannot be negative")

    version_code = version_code_base + run_number
    if version_code > MAX_ANDROID_VERSION_CODE:
        raise VersioningError(
            f"Calculated versionCode {version_code} exceeds Android limit "
            f"{MAX_ANDROID_VERSION_CODE}"
        )

    return ReleaseVersion(
        tag=normalized_tag,
        version_name=normalized_tag[1:],
        version_code=version_code,
        prerelease=match.group("channel") is not None,
    )


def _write_github_output(path: Path, version: ReleaseVersion) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as stream:
        stream.write(f"release_tag={version.tag}\n")
        stream.write(f"version_name={version.version_name}\n")
        stream.write(f"version_code={version.version_code}\n")
        stream.write(f"prerelease={'true' if version.prerelease else 'false'}\n")


def _positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return parsed


def _non_negative_int(value: str) -> int:
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("must be zero or greater")
    return parsed


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--run-number", required=True, type=_positive_int)
    parser.add_argument("--version-code-base", required=True, type=_non_negative_int)
    parser.add_argument(
        "--github-output",
        type=Path,
        default=Path(os.environ["GITHUB_OUTPUT"]) if os.environ.get("GITHUB_OUTPUT") else None,
        help="Append calculated values to a GitHub Actions output file",
    )
    parser.add_argument(
        "--json-output",
        type=Path,
        help="Optionally write the release metadata as JSON",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        version = derive_release_version(
            tag=args.tag,
            run_number=args.run_number,
            version_code_base=args.version_code_base,
        )
    except VersioningError as error:
        raise SystemExit(f"versioning error: {error}") from error

    if args.github_output is not None:
        _write_github_output(args.github_output, version)
    if args.json_output is not None:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(
            json.dumps(asdict(version), indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    print(json.dumps(asdict(version), sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
