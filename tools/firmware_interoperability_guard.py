#!/usr/bin/env python3
"""Run the reviewed interoperability guard against the currently pinned firmware revision."""

from __future__ import annotations

import firmware_interoperability_guard_core as guard


guard.FIRMWARE_COMMIT = "e198a893d909c06c540452165cfe4e8c21fd6434"
guard.REQUEST_CONTRACT_BLOBS = dict(guard.REQUEST_CONTRACT_BLOBS)
guard.REQUEST_CONTRACT_BLOBS["src/api/v1/commands/AqlDosingCommands.hpp"] = (
    "5c0201f9a4e09f93f4dd54af8c1fba9dec167105"
)


if __name__ == "__main__":
    raise SystemExit(guard.main())
