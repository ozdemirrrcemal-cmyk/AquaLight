#!/usr/bin/env python3
"""Generate the quick-setup light index from the packaged plant catalog."""
import argparse
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "app/src/main/assets/aqualight_plant_catalog.json"
OUTPUT = ROOT / "app/src/main/java/com/aqua/aqualight/application/aquarium/AquariumPlantLightCatalog.kt"
MINIMUM = {
    "LOW": "LOW", "LOW_MEDIUM": "LOW", "LOW_HIGH": "LOW",
    "MEDIUM": "MEDIUM", "MEDIUM_HIGH": "MEDIUM", "HIGH": "HIGH",
}


def render():
    raw = ASSET.read_text(encoding="utf-8")
    catalog = json.loads(raw)
    records = catalog["records"]
    assert catalog["recordCount"] == len(records) == 271
    assert not re.search(r"https?://|www\.|sourceUrl|sourceOrganization|selectedRecordEvidence", raw, re.I)
    ids = ["plant:" + row["recordId"].removeprefix("plant-").replace("-", "_") for row in records]
    assert len(set(ids)) == len(records)
    assert all(row["placement"] and row["lightRequirement"] in MINIMUM for row in records)
    assert Counter(row["healthDataStatus"] for row in records) == {"VERIFIED": 182, "PARTIAL": 89}

    lines = [
        "package com.aqua.aqualight.application.aquarium", "",
        "enum class AquariumPlantLightDemand { LOW, MEDIUM, HIGH }", "",
        "data class AquariumPlantLightCatalogRecord(",
        "    val catalogId: String,", "    val lightDemand: AquariumPlantLightDemand,",
        "    val lightRequirement: String,",
        "    val catalogRevision: Int = AquariumPlantLightCatalog.CATALOG_REVISION", ")", "",
        "/** Exact catalog identities. A range uses its lowest supported light as the minimum demand. */",
        '@Suppress("LargeClass") // Static catalog entries intentionally share one record table.',
        "object AquariumPlantLightCatalog {", "    const val CATALOG_REVISION: Int = 2",
        "    const val EXPECTED_RECORD_COUNT: Int = 271", "",
        "    val records: List<AquariumPlantLightCatalogRecord> = listOf("
    ]
    for i, (row, identity) in enumerate(zip(records, ids)):
        demand = MINIMUM[row["lightRequirement"]]
        comma = "," if i < len(records) - 1 else ""
        lines.extend([
            "        AquariumPlantLightCatalogRecord(",
            f'            catalogId = "{identity}",',
            f"            lightDemand = AquariumPlantLightDemand.{demand},",
            f'            lightRequirement = "{row["lightRequirement"]}"',
            f"        ){comma}",
        ])
    lines.extend([
        "    )", "", "    private val byCatalogId = records.associateBy(AquariumPlantLightCatalogRecord::catalogId)",
        "    val catalogIds: Set<String> get() = byCatalogId.keys", "", "    init {",
        "        check(records.size == EXPECTED_RECORD_COUNT)",
        "        check(byCatalogId.size == records.size)", "    }", "",
        "    fun record(catalogId: String): AquariumPlantLightCatalogRecord? = byCatalogId[catalogId]",
        "    fun requireRecord(catalogId: String): AquariumPlantLightCatalogRecord =",
        '        requireNotNull(record(catalogId)) { "Missing plant-light record for $catalogId" }',
        "    fun resolve(catalogId: String): AquariumPlantLightDemand = requireRecord(catalogId).lightDemand",
        "}", ""
    ])
    return "\n".join(lines)


if __name__ == "__main__":
    cli = argparse.ArgumentParser()
    cli.add_argument("--check", action="store_true", help="Fail if generated Kotlin differs")
    args = cli.parse_args()
    generated = render()
    if args.check:
        if OUTPUT.read_text(encoding="utf-8") != generated:
            cli.error("plant light index is out of date; regenerate it")
        print("Plant catalog and light index match (271 records).")
    else:
        OUTPUT.write_text(generated, encoding="utf-8")
