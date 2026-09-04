#!/usr/bin/env python3
"""Run the reviewed interoperability guard against the final firmware architecture revision."""

from __future__ import annotations

import firmware_interoperability_guard_core as guard


guard.FIRMWARE_COMMIT = "2e3688f266d7ed34a6773badafcd62af73cf4aac"
guard.COMMAND_NAMES_BLOB = "4a7b548008235c3cd3e07d760327a477bbf2c6d4"
guard.EVENT_CONTRACT_BLOB = "5e87fd043c0efaa849ed3ba8e5be9ce6f2727e24"
guard.REQUEST_CONTRACT_BLOBS = {
    "src/api/v1/commands/AqlDeviceCommands.hpp": "a78d6355555afea780fdb62809bc9107d7122698",
    "src/api/v1/commands/AqlNetworkCommands.hpp": "529a3b341e81a48d33b9036343dbb0b9f2844fb6",
    "src/api/v1/commands/AqlSecurityCommands.hpp": "1c16c3e7c6d1456b1802f494d91c104347ad09aa",
    "src/api/v1/commands/AqlTimeCommands.hpp": "ee6e87ab0e1152ffd3d9004fe8b5c7e380488a4f",
    "src/api/v1/commands/AqlLightCommands.hpp": "40f4a888232b8630a07f7bde51d555ccf24961ed",
    "src/api/v1/commands/AqlLightTemperatureProtectionCommands.hpp": "e3f04116d2f6ae77e0a1adfd7112773a3e1d06fe",
    "src/api/v1/commands/AqlLightThermalCommands.hpp": "10f5e03237b16ad4bf0a26b64c2715e0985d094e",
    "src/api/v1/commands/AqlCoolingCommands.hpp": "4df542d5106fce2810e64559667685c1c2ecfd69",
    "src/api/v1/commands/AqlTimerCommands.hpp": "5951757f9eeebf81fce647ba9e94b35fe01c13a5",
    "src/api/v1/commands/AqlDosingCommands.hpp": "34cfc3287485f6a46a73b47f061a5456cde9aa9d",
    "src/api/v1/commands/AqlDosingProgressCommands.hpp": "8700e785bdd2e747abea3b09eff97755e2addad0",
    "src/api/v1/commands/AqlFirmwareCommands.hpp": "8b1107d159ca3ff026754c8a06bd1e75fb608c37",
    "src/modules/timer/AqlTimerService.hpp": "3b852245ccfe84ddb28d4bc664e8e125fd5f11ca",
    "src/security/AqlSecurityService.hpp": "484906dbdd833d6ad7505ae1755748d239fc0805",
}

# Dosing keeps its separately reviewed feature pin; it now declares the final core revision.
guard.DOSING_FIRMWARE_COMMIT = "fa147211749c2dcb2f56e15a617a00010e071984"

guard.PRODUCT_CATALOG_EXPORT_COMMIT = "2e3688f266d7ed34a6773badafcd62af73cf4aac"
guard.EXPECTED_FIXTURES["aql_product_catalog_v1.json"] = (
    "5eb7c027ecff23c5fa939ee0a16f62804737b0c9c7b0d9a3ea4b479c4d604a59",
    None,
    False,
)


if __name__ == "__main__":
    raise SystemExit(guard.main())
