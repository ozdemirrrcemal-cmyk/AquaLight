#!/usr/bin/env python3
"""Protect the Plant Health UI flow until the analysis engine is connected."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
RES = ROOT / "app/src/main/res"

DETAIL = APP / "ui/tabs/aquarium/detail/health/PlantHealthDetailFragment.kt"
DETAIL_BINDER = APP / "ui/tabs/aquarium/detail/health/PlantHealthDetailUiBinder.kt"
OBSERVATION = APP / "ui/tabs/aquarium/detail/health/PlantHealthObservationFragment.kt"
HISTORY = APP / "ui/tabs/aquarium/detail/health/PlantHealthHistoryFragment.kt"
ANALYSIS_RESULT = APP / "ui/tabs/aquarium/detail/health/PlantHealthAnalysisResultFragment.kt"
ALGAE_DETECTION = APP / "ui/tabs/aquarium/detail/health/PlantHealthAlgaeDetectionFragment.kt"
ALGAE_CONTROL = APP / "ui/tabs/aquarium/detail/health/PlantHealthAlgaeControlFragment.kt"
CATALOG_UI = APP / "ui/tabs/aquarium/detail/health/PlantHealthCatalogUi.kt"
TANK_PLANTS = APP / "ui/tabs/aquarium/detail/TankDetailPlantsFragment.kt"

TANK_PLANTS_LAYOUT = RES / "layout/fragment_tank_detail_plants.xml"
PLANT_CARD_LAYOUT = RES / "layout/item_tank_plant_photo.xml"
DETAIL_LAYOUT = RES / "layout/fragment_plant_health_detail.xml"
OBSERVATION_LAYOUT = RES / "layout/fragment_plant_health_observation.xml"
HISTORY_LAYOUT = RES / "layout/fragment_plant_health_history.xml"
ANALYSIS_LAYOUT = RES / "layout/fragment_plant_health_analysis_result.xml"
ALGAE_DETECTION_LAYOUT = RES / "layout/fragment_plant_health_algae_detection.xml"
ALGAE_CONTROL_LAYOUT = RES / "layout/fragment_plant_health_algae_control.xml"
PLANT_STRINGS = RES / "values/plant_health_strings.xml"
PLANT_STRINGS_TR = RES / "values-tr/plant_health_strings.xml"
ENTITY_STRINGS = RES / "values/entity_health_strings.xml"
ENTITY_STRINGS_TR = RES / "values-tr/entity_health_strings.xml"
NAVIGATION = RES / "navigation/nav_aquarium.xml"

required = (
    DETAIL,
    DETAIL_BINDER,
    OBSERVATION,
    HISTORY,
    ANALYSIS_RESULT,
    ALGAE_DETECTION,
    ALGAE_CONTROL,
    CATALOG_UI,
    TANK_PLANTS,
    TANK_PLANTS_LAYOUT,
    PLANT_CARD_LAYOUT,
    DETAIL_LAYOUT,
    OBSERVATION_LAYOUT,
    HISTORY_LAYOUT,
    ANALYSIS_LAYOUT,
    ALGAE_DETECTION_LAYOUT,
    ALGAE_CONTROL_LAYOUT,
    PLANT_STRINGS,
    PLANT_STRINGS_TR,
    ENTITY_STRINGS,
    ENTITY_STRINGS_TR,
    NAVIGATION,
)

errors: list[str] = []


def read(path: Path) -> str:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required Plant Health UI file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


for path in required:
    read(path)

for legacy_file in (
    APP / "ui/tabs/aquarium/detail/health/PlantHealthFragment.kt",
    RES / "layout/fragment_plant_health.xml",
    RES / "layout/item_plant_health_plant.xml",
):
    if legacy_file.exists():
        errors.append(f"{legacy_file.relative_to(ROOT)}: duplicate assigned-plant list must stay removed")

detail = read(DETAIL)
for token in (
    "PlantHealthDetailTab.OVERVIEW",
    "PlantHealthDetailTab.OBSERVATIONS",
    "PlantHealthDetailTab.CARE",
    "PlantHealthDetailTab.NOTES",
    "actionPlantHealthDetailFragmentToPlantHealthObservationFragment",
    "actionPlantHealthDetailFragmentToPlantHealthHistoryFragment",
):
    if token not in detail:
        errors.append(f"{DETAIL.relative_to(ROOT)}: plant detail flow missing: {token}")

detail_layout = read(DETAIL_LAYOUT)
for token in (
    "@+id/tabOverview",
    "@+id/tabObservations",
    "@+id/tabCare",
    "@+id/tabNotes",
    "@+id/overviewContainer",
    "@+id/observationsContainer",
    "@+id/careContainer",
    "@+id/notesContainer",
    "@+id/btnNewObservation",
):
    if token not in detail_layout:
        errors.append(f"{DETAIL_LAYOUT.relative_to(ROOT)}: detail UI contract missing: {token}")

observation = read(OBSERVATION)
if "PlantPickerFragment" in observation:
    errors.append(f"{OBSERVATION.relative_to(ROOT)}: observation must use the selected tank plant")
if "actionPlantHealthObservationFragmentToPlantObservationRecordFragment" not in observation:
    errors.append(
        f"{OBSERVATION.relative_to(ROOT)}: saved observation must open its real record"
    )

observation_layout = read(OBSERVATION_LAYOUT)
for token in (
    "@+id/symptomGridContainer",
    "@+id/etNote",
    "@+id/btnSaveObservation",
    "@+id/photoContainer",
):
    if token not in observation_layout:
        errors.append(f"{OBSERVATION_LAYOUT.relative_to(ROOT)}: observation UI contract missing: {token}")

navigation = read(NAVIGATION)
for token in (
    'android:id="@+id/plantHealthDetailFragment"',
    'android:id="@+id/plantHealthObservationFragment"',
    'android:id="@+id/plantHealthHistoryFragment"',
    "action_tankDetailFragment_to_plantHealthDetailFragment",
    "action_plantHealthDetailFragment_to_plantHealthObservationFragment",
    "action_plantHealthDetailFragment_to_plantHealthHistoryFragment",
):
    if token not in navigation:
        errors.append(f"{NAVIGATION.relative_to(ROOT)}: Plant Health navigation missing: {token}")
for forbidden in (
    '@+id/plantHealthFragment',
    "action_tankDetailFragment_to_plantHealthFragment",
    "plantHealthAnalysisResultFragment",
    "plantHealthAlgaeDetectionFragment",
):
    if forbidden in navigation:
        errors.append(
            f"{NAVIGATION.relative_to(ROOT)}: removed or deferred navigation remains: {forbidden}"
        )

for token in (
    "plantObservationRecordFragment",
    "plantHealthAlgaeControlFragment",
    "action_plantHealthObservationFragment_to_plantObservationRecordFragment",
    'app:popUpToInclusive="true"',
):
    if token not in navigation:
        errors.append(f"Plant observation persistence navigation missing: {token}")
if "btnStartAnalysis" in observation_layout or "plant_health_analysis_start" in observation_layout:
    errors.append("Observation form must save observations without pretending to run an analysis")
if "algaeGridContainer" in read(ALGAE_CONTROL_LAYOUT):
    errors.append("Algae control must remain an empty shared-header destination in this stage")

android = "{http://schemas.android.com/apk/res/android}"
app = "{http://schemas.android.com/apk/res-auto}"
try:
    graph = ET.fromstring(navigation)
    tank = next(node for node in graph.iter("fragment")
                if node.get(android + "id") == "@+id/tankDetailFragment")
    action = next(node for node in tank.findall("action")
                  if node.get(android + "id") ==
                  "@+id/action_tankDetailFragment_to_plantHealthDetailFragment")
    if action.get(app + "destination") != "@id/plantHealthDetailFragment":
        errors.append("Plant card must navigate directly from tank detail to plant detail")
    arguments = {node.get(android + "name"): node.get(app + "argType")
                 for node in action.findall("argument")}
    if arguments != {"tankId": "long", "plantId": "long"}:
        errors.append("Direct plant detail action must carry both tankId and plantId as longs")
except (ET.ParseError, StopIteration):
    errors.append("Direct tank-to-plant-detail navigation contract is missing or malformed")

for source in (DETAIL, OBSERVATION, HISTORY, ANALYSIS_RESULT, ALGAE_DETECTION, ALGAE_CONTROL):
    text = read(source)
    if "setupAquaHeader" not in text:
        errors.append(f"{source.relative_to(ROOT)}: shared AquaHeader is required")

tank_plants = read(TANK_PLANTS)
for forbidden in (
    "plantHealthEntry",
    "PlantPickerFragment",
    "PlantCatalog.resolve",
    "R.string.plant_health_entry_summary",
    "R.string.plant_health_entry_last_check",
    "btnPlantPhoto",
):
    if forbidden in tank_plants:
        errors.append(
            f"{TANK_PLANTS.relative_to(ROOT)}: duplicate/fake health entry or catalog selection remains: {forbidden}"
        )
for token in (
    "tanks.firstOrNull { it.id == tankId }",
    "renderPlants(tank.plants)",
    "actionTankDetailFragmentToPlantHealthDetailFragment",
    "tankId = tankId",
    "plantId = plant.id",
    "TankDetailFragment.KEY_SELECTED_TAB",
    "TankDetailTabArgs.PLANTS",
    "isNavigating || photoTarget.isInProgress || photoActionsBlocked",
    "item.plantCard.setOnClickListener",
    "openPlantDetail(plant)",
    "item.plantPhotoFrame.setOnClickListener",
    "showPlantPhotoSource(plant)",
    "R.string.plant_health_detail_action_description",
    "item.plantPhotoFrame.contentDescription",
    "R.string.aquarium_plant_photo_action_description",
):
    if token not in tank_plants:
        errors.append(f"{TANK_PLANTS.relative_to(ROOT)}: direct plant/card photo flow missing: {token}")

if "plantHealthEntry" in read(TANK_PLANTS_LAYOUT):
    errors.append(f"{TANK_PLANTS_LAYOUT.relative_to(ROOT)}: duplicate health-list entry remains")
try:
    card = ET.fromstring(read(PLANT_CARD_LAYOUT))
    photo = next(node for node in card.iter()
                 if node.get(android + "id") == "@+id/plantPhotoFrame")
    for dimension in ("layout_width", "layout_height"):
        if photo.get(android + dimension) != "@dimen/aqua_size_56":
            errors.append("Plant photo frame must retain its accessible 56dp touch target")
    for attribute in ("clickable", "focusable"):
        if photo.get(android + attribute) != "true":
            errors.append(f"Plant photo frame must be {attribute}")
    if any(node.get(android + "id") == "@+id/btnPlantPhoto" for node in card.iter()):
        errors.append("Redundant plant camera button must stay removed")
except (ET.ParseError, StopIteration):
    errors.append("Clickable plant photo frame is missing or malformed")

for strings_file in (ENTITY_STRINGS, ENTITY_STRINGS_TR):
    text = read(strings_file)
    for forbidden_name in (
        "plant_health_entry_summary",
        "plant_health_entry_last_check",
    ):
        if f'name="{forbidden_name}"' in text:
            errors.append(
                f"{strings_file.relative_to(ROOT)}: legacy fake resource remains: {forbidden_name}"
            )

for strings_file in (PLANT_STRINGS, PLANT_STRINGS_TR):
    text = read(strings_file)
    for token in (
        'name="plant_health_detail_action_description"',
        'name="plant_health_status_no_observation"',
        'name="plant_health_tab_overview"',
        'name="plant_health_tab_observations"',
        'name="plant_health_tab_care"',
        'name="plant_health_tab_notes"',
        'name="plant_health_analysis_deferred"',
    ):
        if token not in text:
            errors.append(f"{strings_file.relative_to(ROOT)}: Plant Health copy missing: {token}")

if errors:
    print("Plant Health UI architecture guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print(
    "Plant Health UI architecture guard passed: tank plant cards open detail directly, "
    "photo actions use plant thumbnails, saved records and algae routing are present, and analysis is deferred."
)
