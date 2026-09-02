#!/usr/bin/env python3
"""Run the reviewed interoperability guard against the currently pinned firmware revision."""

from __future__ import annotations

import firmware_interoperability_guard_core as guard


guard.FIRMWARE_COMMIT = "980b03f0d83cdeb997698fc6b207064aa709cec8"
guard.DOSING_FIRMWARE_COMMIT = "fa147211749c2dcb2f56e15a617a00010e071984"


if __name__ == "__main__":
    raise SystemExit(guard.main())
