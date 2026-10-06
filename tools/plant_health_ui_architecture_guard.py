#!/usr/bin/env python3
"""Protect the Plant Health UI flow until the analysis engine is connected."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
RES = ROOT / "app/src/main/res"

HOME = APP / "ui/tabs/aquarium/detail/health/PlantHealthFragment.kt"
DETAIL = APP / "ui/tabs/aquarium/detail/health/PlantHealthDetailFragment.kt"
DETAIL_BINDER = APP / "ui/tabs/aquarium/detail/health/PlantHealthDetailUiBinder.kt"
OBSERVATION = APP / "ui/tabs/aquarium/detail/health/PlantHealthObservationFragment.kt"
HISTORY = APP / "ui/tabs/aquarium/detail/health/PlantHealthHistoryFragment.kt"
ANALYSIS_RESULT = APP / "ui/tabs/aquarium/detail/health/PlantHealthAnalysisResultFragment.kt"
ALGAE_DETECTION = APP / "ui/tabs/aquarium/detail/health/PlantHealthAlgaeDetectionFragment.kt"
ALGAE_CONTROL = APP / "ui/tabs/aquarium/detail/health/PlantHealthAlgaeControlFragment.kt"
CATALOG_UI = APP / "ui/tabs/aquarium/detail/health/PlantHealthCatalogUi.kt"
TANK_PLANTS = APP / "ui/tabs/aquarium/detail/TankDetailPlantsFragment.kt"

HOME_LAYOUT = RES / "layout/fragment_plant_health.xml"
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
    HOME,
    DETAIL,
    DETAIL_BINDER,
    OBSERVATION,
    HISTORY,
    ANALYSIS_RESULT,
    ALGAE_DETECTION,
    ALGAE_CONTROL,
    CATALOG_UI,
    TANK_PLANTS,
    HOME_LAYOUT,
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

home = read(HOME)
for token in (
    "tanks.firstOrNull",
    "?.plants",
    "PlantHealthCatalogUi.record",
    "actionPlantHealthFragmentToPlantHealthDetailFragment",
    "binding.etSearch.doAfterTextChanged",
):
    if token not in home:
        errors.append(f"{HOME.relative_to(ROOT)}: assigned-plant home flow missing: {token}")
for forbidden in (
    "PlantPickerFragment",
    "PlantCatalog.resolve",
    "actionPlantHealthFragmentToPlantPicker",
):
    if forbidden in home:
        errors.append(f"{HOME.relative_to(ROOT)}: catalog selection leaked into health flow: {forbidden}")

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
if "navigateSafelyFrom" in observation:
    errors.append(
        f"{OBSERVATION.relative_to(ROOT)}: analysis navigation must remain deferred in this stage"
    )

observation_layout = read(OBSERVATION_LAYOUT)
for token in (
    "@+id/symptomGridContainer",
    "@+id/etNote",
    "@+id/btnStartAnalysis",
    'android:enabled="false"',
):
    if token not in observation_layout:
        errors.append(f"{OBSERVATION_LAYOUT.relative_to(ROOT)}: observation UI contract missing: {token}")

navigation = read(NAVIGATION)
for token in (
    'android:id="@+id/plantHealthDetailFragment"',
    'android:id="@+id/plantHealthObservationFragment"',
    'android:id="@+id/plantHealthHistoryFragment"',
    "action_plantHealthFragment_to_plantHealthDetailFragment",
    "action_plantHealthDetailFragment_to_plantHealthObservationFragment",
    "action_plantHealthDetailFragment_to_plantHealthHistoryFragment",
):
    if token not in navigation:
        errors.append(f"{NAVIGATION.relative_to(ROOT)}: Plant Health navigation missing: {token}")
for forbidden in (
    "plantHealthAnalysisResultFragment",
    "plantHealthAlgaeDetectionFragment",
    "plantHealthAlgaeControlFragment",
    "action_plantHealthObservationFragment_to_",
):
    if forbidden in navigation:
        errors.append(
            f"{NAVIGATION.relative_to(ROOT)}: analysis/algae navigation must remain deferred: {forbidden}"
        )

for source in (HOME, DETAIL, OBSERVATION, HISTORY, ANALYSIS_RESULT, ALGAE_DETECTION, ALGAE_CONTROL):
    text = read(source)
    if "setupAquaHeader" not in text:
        errors.append(f"{source.relative_to(ROOT)}: shared AquaHeader is required")

tank_plants = read(TANK_PLANTS)
for forbidden in (
    "R.string.plant_health_entry_summary",
    "R.string.plant_health_entry_last_check",
):
    if forbidden in tank_plants:
        errors.append(
            f"{TANK_PLANTS.relative_to(ROOT)}: fake Plant Health entry copy remains: {forbidden}"
        )
for token in (
    "R.plurals.plant_health_entry_assigned_count",
    "R.string.plant_health_status_no_observation",
):
    if token not in tank_plants:
        errors.append(f"{TANK_PLANTS.relative_to(ROOT)}: truthful Plant Health entry missing: {token}")

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
    "Plant Health UI architecture guard passed: assigned plants are the source of truth, "
    "detail tabs are present, fake health copy is removed, and analysis/algae navigation is deferred."
)
