#!/usr/bin/env python3
"""Run the reviewed interoperability guard against the currently pinned firmware revision."""

from __future__ import annotations

import firmware_interoperability_guard_core as guard


guard.FIRMWARE_COMMIT = "dc89a37262ba982c577db0812eeb8f94ffd18e12"
guard.REQUEST_CONTRACT_BLOBS = dict(guard.REQUEST_CONTRACT_BLOBS)
guard.REQUEST_CONTRACT_BLOBS["src/api/v1/commands/AqlTimeCommands.hpp"] = (
    "ee6e87ab0e1152ffd3d9004fe8b5c7e380488a4f"
)
guard.REQUEST_CONTRACT_BLOBS["src/modules/timer/AqlTimerService.hpp"] = (
    "27dd1a51d532f9deef91ee2da86a4b3055fd001f"
)
guard.REQUEST_CONTRACT_BLOBS["src/api/v1/commands/AqlDosingCommands.hpp"] = (
    "5c0201f9a4e09f93f4dd54af8c1fba9dec167105"
)


if __name__ == "__main__":
    raise SystemExit(guard.main())
