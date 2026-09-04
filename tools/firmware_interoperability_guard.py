#!/usr/bin/env python3
"""Run the reviewed interoperability guard against the currently pinned firmware revision."""

from __future__ import annotations

import firmware_interoperability_guard_core as guard


guard.FIRMWARE_COMMIT = "980b03f0d83cdeb997698fc6b207064aa709cec8"
guard.DOSING_FIRMWARE_COMMIT = "fa147211749c2dcb2f56e15a617a00010e071984"

# Product catalog parity advances independently from the still-pinned broad runtime contract.
guard.PRODUCT_CATALOG_EXPORT_COMMIT = "2e3688f266d7ed34a6773badafcd62af73cf4aac"
guard.EXPECTED_FIXTURES["aql_product_catalog_v1.json"] = (
    "5eb7c027ecff23c5fa939ee0a16f62804737b0c9c7b0d9a3ea4b479c4d604a59",
    None,
    False,
)


if __name__ == "__main__":
    raise SystemExit(guard.main())
