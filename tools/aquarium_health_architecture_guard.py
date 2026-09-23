#!/usr/bin/env python3
"""Fail CI if Aquarium Health Stage 1 boundaries regress."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"

CONTRACT = ROOT / "docs/architecture/AQUARIUM_HEALTH_CONTRACT.md"
APPLICATION = APP / "application/aquarium/health/AquariumHealthRecordOperations.kt"
POLICY = APP / "application/aquarium/health/AquariumHealthMeasurementPolicy.kt"
SYMPTOMS = APP / "application/aquarium/health/LivestockHealthSymptomCatalog.kt"
PLANT_SYMPTOMS = APP / "application/aquarium/health/PlantHealthObservationCatalog.kt"
PROTO = ROOT / "app/src/main/proto/aquarium_health.proto"
STORE_RULES = APP / "data/aquarium/health/AquariumHealthStoreRules.kt"
SERIALIZER = APP / "data/aquarium/health/AquariumHealthCommercialSerializer.kt"
STORE_ACCESS = APP / "data/aquarium/health/AquariumHealthStoreAccess.kt"
MANAGER = APP / "data/aquarium/health/AquariumHealthDataStoreManager.kt"
WATER_STORE = APP / "data/aquarium/health/AquariumWaterTestStore.kt"
LIVESTOCK_STORE = APP / "data/aquarium/health/LivestockHealthObservationStore.kt"
PLANT_STORE = APP / "data/aquarium/health/PlantHealthObservationStore.kt"
INTEGRITY_STORE = APP / "data/aquarium/health/AquariumHealthIntegrityStore.kt"
ADAPTER = APP / "data/aquarium/health/DefaultAquariumHealthRecordOperations.kt"
WATER_ADAPTER = APP / "data/aquarium/health/DefaultAquariumWaterTestOperations.kt"
LIVESTOCK_ADAPTER = APP / "data/aquarium/health/DefaultLivestockHealthObservationOperations.kt"
PLANT_ADAPTER = APP / "data/aquarium/health/DefaultPlantHealthObservationOperations.kt"
JOURNAL = (
    APP
    / "data/aquarium/health/integrity/TankHealthIntegrityJournal.kt"
)
RECOVERY = (
    APP
    / "data/aquarium/health/integrity/TankHealthIntegrityRecovery.kt"
)
TANK_CLEANER = APP / "data/aquarium/delete/OwnerTankDataCleaner.kt"
OWNER_SESSION = APP / "data/auth/OwnerSessionCoordinator.kt"
USER_CLEANER = APP / "data/user/UserDataCleaner.kt"
OWNER_GRAPH = APP / "composition/OwnerDependencyGraph.kt"
COMMERCIAL_SCHEMA = APP / "data/store/CommercialStoreSchema.kt"

REQUIRED_TESTS = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/application/aquarium/health/"
    / "AquariumHealthMeasurementPolicyTest.kt",
    ROOT
    / "app/src/test/java/com/aqua/aqualight/data/aquarium/health/"
    / "AquariumHealthStoreRulesTest.kt",
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/data/aquarium/health/"
    / "AquariumHealthDataStoreManagerInstrumentedTest.kt",
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/data/aquarium/delete/"
    / "OwnerTankDataCleanerMultiTankInstrumentedTest.kt",
    ROOT
    / "app/src/androidTest/java/com/aqua/aqualight/data/aquarium/health/integrity/"
    / "TankHealthIntegrityRecoveryInstrumentedTest.kt",
)

errors: list[str] = []


def read(path: Path) -> str:
    if not path.is_file():
        errors.append(f"missing required file: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")


contract = read(CONTRACT)
application = read(APPLICATION)
policy = read(POLICY)
symptoms = read(SYMPTOMS)
plant_symptoms = read(PLANT_SYMPTOMS)
proto = read(PROTO)
store_rules = read(STORE_RULES)
serializer = read(SERIALIZER)
store_access = read(STORE_ACCESS)
manager = read(MANAGER)
water_store = read(WATER_STORE)
livestock_store = read(LIVESTOCK_STORE)
plant_store = read(PLANT_STORE)
integrity_store = read(INTEGRITY_STORE)
adapter = read(ADAPTER)
water_adapter = read(WATER_ADAPTER)
livestock_adapter = read(LIVESTOCK_ADAPTER)
plant_adapter = read(PLANT_ADAPTER)
journal = read(JOURNAL)
recovery = read(RECOVERY)
tank_cleaner = read(TANK_CLEANER)
owner_session = read(OWNER_SESSION)
user_cleaner = read(USER_CLEANER)
owner_graph = read(OWNER_GRAPH)
commercial_schema = read(COMMERCIAL_SCHEMA)

for test_path in REQUIRED_TESTS:
    if not test_path.is_file():
        errors.append(
            f"missing required Health regression test: {test_path.relative_to(ROOT)}"
        )

for token in (
    "Health-owned authoritative data",
    "Persistence architecture",
    "Assessment architecture",
    "Automation separation",
    "Commercial completion criteria",
):
    if token not in contract:
        errors.append(f"Health architecture contract is missing section: {token}")

if "package com.aqua.aqualight.application.aquarium.health" not in application:
    errors.append("Health record contract must remain in the application layer")
for forbidden in ("com.aqua.aqualight.data.", "android."):
    if forbidden in application:
        errors.append(
            f"Health application contract imports forbidden implementation type: {forbidden}"
        )

for token in (
    "interface AquariumHealthRecordOperations",
    "data class AquariumWaterTestRecord",
    "data class LivestockHealthObservation",
    "data class PlantHealthObservation",
    "interface AquariumWaterTestOperations",
    "interface LivestockHealthObservationOperations",
    "interface PlantHealthObservationOperations",
    "enum class HealthWaterParameter",
):
    if token not in application:
        errors.append(f"Health application contract is missing: {token}")

for token in (
    "TOTAL_AMMONIA_PPM",
    "NITRITE_PPM",
    "DISSOLVED_OXYGEN_MG_L",
    "CO2_MG_L",
):
    if token not in application or token not in policy:
        errors.append(f"Health water parameter is not centrally validated: {token}")

for token in (
    'const val FISH_SURFACE_GASPING = "fish_surface_gasping"',
    "requireApplicable",
    "AquariumLivestockTaxonomy.categoryCodes",
):
    if token not in symptoms:
        errors.append(f"Health symptom identity contract is missing: {token}")

for token in (
    'const val ALGAE_PRESENCE = "plant_algae_presence"',
    'const val BLACK_BEARD_ALGAE = "black_beard_algae"',
    "requireValidSelection",
    "AquariumAlgaeCatalog",
):
    if token not in plant_symptoms:
        errors.append(f"Plant Health observation identity is missing: {token}")

for token in (
    "message AquariumHealthStore",
    "repeated StoredAquariumWaterTest water_tests",
    "repeated StoredLivestockHealthObservation livestock_observations",
    "repeated StoredPlantHealthObservation plant_observations",
    "message StoredPlantHealthObservation",
    "uint32 schema_version = 100;",
):
    if token not in proto:
        errors.append(f"Health proto contract is missing: {token}")

for forbidden in ("migration", "Migration", "DataMigration"):
    health_data_root = APP / "data/aquarium/health"
    if any(
        forbidden in path.read_text(encoding="utf-8", errors="ignore")
        for path in health_data_root.rglob("*.kt")
    ):
        errors.append(
            f"Health Stage 1 must not introduce legacy migration logic: {forbidden}"
        )

for token in (
    "AQUARIUM_HEALTH_VERSION = 1",
    "requireCurrent(",
):
    target = commercial_schema if "VERSION" in token else store_rules
    if token not in target:
        errors.append(f"Health commercial schema gate is missing: {token}")

for token in (
    "AquariumHealthStoreRules.validateStore",
    "CorruptionException",
):
    if token not in serializer:
        errors.append(f"Health serializer is not fail-closed: {token}")

for token in (
    'fileName = "aquarium_health.pb"',
    "ReplaceFileCorruptionHandler",
    "Area.AQUARIUM_HEALTH",
    "AquariumHealthStoreRules::validateStore",
):
    if token not in store_access:
        errors.append(f"Health store access boundary is missing: {token}")

for label, text_value in (
    ("water", water_store),
    ("livestock", livestock_store),
    ("plant", plant_store),
):
    for token in (
        "AquariumHealthStoreRules.nextUniqueId",
        "access.updateTank",
        "access.requireTank",
    ):
        if token not in text_value and not (
            label == "water" and token == "access.requireTank"
        ):
            errors.append(
                f"{label} Health store is missing owner/tank invariant: {token}"
            )

for token in (
    "snapshotForTank",
    "deleteRecordsForTank",
    "restoreSnapshotForIntegrity",
    "repairOrphanedRecords",
    "plantObservations",
):
    if token not in integrity_store:
        errors.append(f"Health integrity store is missing: {token}")

owner_scope_count = sum(
    text_value.count("withCurrentOwnerScope")
    for text_value in (water_adapter, livestock_adapter, plant_adapter)
)
if owner_scope_count < 9:
    errors.append("Every Health mutation must remain pinned to one owner scope")

for token in (
    "DefaultAquariumWaterTestOperations",
    "DefaultLivestockHealthObservationOperations",
    "DefaultPlantHealthObservationOperations",
):
    if token not in adapter:
        errors.append(f"Health operation composition is missing: {token}")

for token in (
    "TankHealthIntegrityTransactions",
    "processTombstones",
    "withRollbackWritesAllowed",
    "requireWritable",
):
    if token not in journal:
        errors.append(f"Health tank-deletion journal is incomplete: {token}")

for token in (
    "healthStore.integrity.restoreSnapshotForIntegrity",
    "healthStore.integrity.repairOrphanedRecords",
    "pendingForOwner",
):
    if token not in recovery:
        errors.append(f"Health owner-session recovery is incomplete: {token}")

for token in (
    "TankHealthDeletionDependencies",
    "HEALTH_RECORDS",
    "health.deleteForTank",
    "health.restoreForTank",
):
    if token not in tank_cleaner:
        errors.append(f"Tank deletion does not protect Health records: {token}")

if "TankHealthIntegrityRecovery" not in owner_session:
    errors.append("Owner activation must recover interrupted Health cleanup")

for token in (
    "AQUARIUM_HEALTH",
    "AquariumHealthDataStoreManager",
    "TankHealthIntegrityJournal.clearOwner",
):
    if token not in user_cleaner:
        errors.append(f"Account/local deletion does not clear Health state: {token}")

for token in (
    "AquariumHealthDataStoreManager.create(appContext)",
    "val aquariumHealthStore: AquariumHealthDataStoreManager",
    "val aquariumHealthRecordOperations: AquariumHealthRecordOperations",
    "DefaultAquariumHealthRecordOperations(aquariumHealthStore)",
):
    if token not in owner_graph:
        errors.append(f"Owner composition is missing Health store binding: {token}")

ui_root = APP / "ui"
for source in ui_root.rglob("*.kt"):
    text = source.read_text(encoding="utf-8", errors="ignore")
    for forbidden in (
        "AquariumHealthDataStoreManager",
        "AquariumHealthCommercialSerializer",
        "com.aqua.aqualight.data.aquarium.health",
    ):
        if forbidden in text:
            errors.append(
                f"{source.relative_to(ROOT)}: UI bypasses Health application boundary: "
                f"{forbidden}"
            )

if errors:
    print("Aquarium Health architecture guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print(
    "Aquarium Health architecture guard passed: owner scope, schema v1, "
    "fail-closed persistence, deletion integrity, and UI boundaries are intact."
)
