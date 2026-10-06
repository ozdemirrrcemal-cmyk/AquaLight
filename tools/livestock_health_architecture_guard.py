#!/usr/bin/env python3
"""Protect Livestock Health application/data/UI boundaries."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
TESTS = ROOT / "app/src/test/java/com/aqua/aqualight"

APPLICATION = APP / "application/aquarium/health/LivestockHealthOperations.kt"
SCHEMA = APP / "data/store/CommercialStoreSchema.kt"
PROTO = ROOT / "app/src/main/proto/livestock_health.proto"
ADAPTER = APP / "data/aquarium/health/DefaultLivestockHealthOperations.kt"
DATA_STORE = APP / "data/aquarium/health/LivestockHealthDataStore.kt"
MANAGER = APP / "data/aquarium/health/LivestockHealthDataStoreManager.kt"
RULES = APP / "data/aquarium/health/LivestockHealthStoreRules.kt"
EVALUATION_MAPPER = APP / "data/aquarium/health/LivestockHealthEvaluationMapper.kt"
EVALUATION_RULES = APP / "data/aquarium/health/LivestockHealthEvaluationRules.kt"
EVALUATION_MUTATION = APP / "data/aquarium/health/LivestockHealthEvaluationMutation.kt"
OBSERVATION_CREATION = APP / "data/aquarium/health/LivestockObservationCreationContext.kt"
LEGACY_STORE = APP / "data/aquarium/health/LivestockHealthStore.kt"
VIEW_MODEL = APP / "ui/tabs/aquarium/detail/health/LivestockHealthViewModel.kt"
PHOTO_VIEWER = (
    APP
    / "ui/tabs/aquarium/detail/health/LivestockHealthPhotoViewerDialogFragment.kt"
)
AQUA_HEADER_CONFIG = APP / "ui/common/header/AquaHeaderConfig.kt"
AQUA_HEADER_LAYOUT = ROOT / "app/src/main/res/layout/layout_aqua_header.xml"
PAGE_INDICATOR = APP / "ui/common/pager/AquaPageIndicatorView.kt"
CHECK_SHEET = APP / "ui/tabs/aquarium/detail/health/LivestockHealthCheckBottomSheet.kt"
CHECK_PHOTO_CONTROLLER = (
    APP
    / "ui/tabs/aquarium/detail/health/LivestockHealthCheckPhotoController.kt"
)
FOLLOW_UP_FRAGMENT = APP / "ui/tabs/aquarium/detail/health/LivestockHealthFollowUpFragment.kt"
FOLLOW_UP_UI = APP / "ui/tabs/aquarium/detail/health/LivestockHealthFollowUpUi.kt"
FOLLOW_UP_CARD_BINDER = (
    APP / "ui/tabs/aquarium/detail/health/LivestockHealthFollowupCardBinder.kt"
)
UI_TEXT = APP / "ui/tabs/aquarium/detail/health/LivestockHealthUiText.kt"
UI_SESSION = APP / "ui/tabs/aquarium/detail/health/LivestockHealthUiSessionState.kt"
RECORD_UI = APP / "ui/tabs/aquarium/detail/health/LivestockHealthRecordUi.kt"
EVALUATION = APP / "ui/tabs/aquarium/detail/health/LivestockHealthEvaluationFragment.kt"
EVALUATION_SUMMARY = (
    APP / "ui/tabs/aquarium/detail/health/LivestockHealthEvaluationSummaryBinder.kt"
)
EVALUATION_SCREEN_BINDER = (
    APP / "ui/tabs/aquarium/detail/health/LivestockHealthEvaluationScreenBinder.kt"
)
EVALUATION_REQUEST_FACTORY = (
    APP / "ui/tabs/aquarium/detail/health/LivestockHealthEvaluationRequestFactory.kt"
)
EVALUATION_PERSISTENCE = (
    APP / "ui/tabs/aquarium/detail/health/LivestockEvaluationSaveResult.kt"
)
NAVIGATION = ROOT / "app/src/main/res/navigation/nav_aquarium.xml"
CHECK_LAYOUT = ROOT / "app/src/main/res/layout/content_sheet_livestock_health_check.xml"
HISTORY_LAYOUT = ROOT / "app/src/main/res/layout/item_livestock_health_history.xml"
FOLLOW_UP_LAYOUT = ROOT / "app/src/main/res/layout/fragment_livestock_health_follow_up.xml"
OBSERVATION_LAYOUT = ROOT / "app/src/main/res/layout/fragment_livestock_health_observation.xml"
EVALUATION_LAYOUT = ROOT / "app/src/main/res/layout/fragment_livestock_health_evaluation.xml"
STRINGS = ROOT / "app/src/main/res/values/entity_health_strings.xml"
STRINGS_TR = ROOT / "app/src/main/res/values-tr/entity_health_strings.xml"
UI_ROOT = APP / "ui/tabs/aquarium/detail/health"
OWNER_GRAPH = APP / "composition/OwnerDependencyGraph.kt"
OWNER_FACTORY = APP / "composition/OwnerViewModelFactory.kt"
SMOKE = ROOT / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeAppContainer.kt"
BOUNDARY_TEST = TESTS / "ui/tabs/aquarium/detail/health/LivestockHealthViewModelBoundaryTest.kt"

required = (
    APPLICATION,
    SCHEMA,
    PROTO,
    ADAPTER,
    DATA_STORE,
    MANAGER,
    RULES,
    EVALUATION_MAPPER,
    EVALUATION_RULES,
    EVALUATION_MUTATION,
    OBSERVATION_CREATION,
    VIEW_MODEL,
    PHOTO_VIEWER,
    AQUA_HEADER_CONFIG,
    AQUA_HEADER_LAYOUT,
    PAGE_INDICATOR,
    CHECK_SHEET,
    CHECK_PHOTO_CONTROLLER,
    FOLLOW_UP_FRAGMENT,
    FOLLOW_UP_UI,
    FOLLOW_UP_CARD_BINDER,
    UI_TEXT,
    UI_SESSION,
    RECORD_UI,
    EVALUATION,
    EVALUATION_SUMMARY,
    EVALUATION_SCREEN_BINDER,
    EVALUATION_REQUEST_FACTORY,
    EVALUATION_PERSISTENCE,
    NAVIGATION,
    CHECK_LAYOUT,
    HISTORY_LAYOUT,
    FOLLOW_UP_LAYOUT,
    OBSERVATION_LAYOUT,
    EVALUATION_LAYOUT,
    STRINGS,
    STRINGS_TR,
    OWNER_GRAPH,
    OWNER_FACTORY,
    SMOKE,
    BOUNDARY_TEST,
)
errors: list[str] = []


def read(path: Path) -> str:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required architecture file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


for path in required:
    read(path)

if LEGACY_STORE.exists():
    errors.append(
        f"{LEGACY_STORE.relative_to(ROOT)}: serializer, manager and validation must remain split"
    )

application = read(APPLICATION)
for token in (
    "const val LIVESTOCK_HEALTH_MAX_PHOTOS = 3",
    "val photoUris: List<String>",
):
    if token not in application:
        errors.append(
            f"{APPLICATION.relative_to(ROOT)}: multi-photo application contract missing: {token}"
        )
if "val photoUri: String?" in application:
    errors.append(
        f"{APPLICATION.relative_to(ROOT)}: singular health-check photo contract is forbidden"
    )
for token in (
    "data class LivestockEvaluationInput",
    "data class LivestockEvaluationSnapshot",
    "val latestEvaluation: LivestockEvaluationSnapshot?",
    "fun isEvaluationStale(",
    "evaluation: LivestockEvaluationInput",
    "suspend fun addEvaluation(",
):
    if token not in application:
        errors.append(
            f"{APPLICATION.relative_to(ROOT)}: evaluation application contract missing: {token}"
        )

schema = read(SCHEMA)
if "const val LIVESTOCK_HEALTH_VERSION = 3" not in schema:
    errors.append(
        f"{SCHEMA.relative_to(ROOT)}: livestock health schema must use hard-cutover version 3"
    )

proto = read(PROTO)
if "repeated string photo_uris = 5;" not in proto:
    errors.append(f"{PROTO.relative_to(ROOT)}: health checks must persist repeated photo_uris")
if "string photo_uri = 5;" in proto:
    errors.append(f"{PROTO.relative_to(ROOT)}: singular health-check photo field is forbidden")
for token in (
    "repeated StoredLivestockEvaluation evaluations = 16;",
    "message StoredLivestockEvaluation {",
    "message StoredLivestockEvaluationWaterMeasurement {",
):
    if token not in proto:
        errors.append(f"{PROTO.relative_to(ROOT)}: evaluation revision schema missing: {token}")

for forbidden in (
    "import android.",
    "import androidx.",
    "com.aqua.aqualight.data.",
    "com.aqua.aqualight.platform.",
    "com.aqua.aqualight.ui.",
    "com.aqua.aqualight.composition.",
):
    if forbidden in application:
        errors.append(f"{APPLICATION.relative_to(ROOT)}: application boundary leak: {forbidden}")

adapter = read(ADAPTER)
if "LivestockHealthOperations" not in adapter or "withCurrentOwnerScope" not in adapter:
    errors.append(f"{ADAPTER.relative_to(ROOT)}: application adapter boundary is incomplete")
if "com.aqua.aqualight.ui." in adapter:
    errors.append(f"{ADAPTER.relative_to(ROOT)}: data adapter must not depend on UI")

manager = read(MANAGER)
for token in (
    "private val tanks: AquariumTankDataStoreManager",
    "LivestockHealthDataStoreManager(",
    "removeObservations(",
    "createOrReuseLivestockObservation(",
    "input.photoUris.forEach",
):
    if token not in manager:
        errors.append(f"{MANAGER.relative_to(ROOT)}: required injected-store contract missing: {token}")

observation_creation = read(OBSERVATION_CREATION)
for token in (
    "LivestockEvaluationTrigger.INITIAL_OBSERVATION",
    "requireObservationRetryMatches(",
    ".addEvaluations(storedEvaluation)",
    "existing.photoUrisList == input.photoUris",
):
    if token not in observation_creation:
        errors.append(
            f"{OBSERVATION_CREATION.relative_to(ROOT)}: atomic initial evaluation missing: {token}"
        )
for forbidden in (
    "private val tanks = AquariumTankDataStoreManager",
    "AquariumTankDataStoreManager(appContext)",
    "com.aqua.aqualight.ui.",
):
    if forbidden in manager:
        errors.append(f"{MANAGER.relative_to(ROOT)}: hidden dependency construction/leak: {forbidden}")

data_store = read(DATA_STORE)
if "Serializer<LivestockHealthStore>" not in data_store:
    errors.append(f"{DATA_STORE.relative_to(ROOT)}: serializer responsibility is missing")
if "class LivestockHealthDataStoreManager" in data_store:
    errors.append(f"{DATA_STORE.relative_to(ROOT)}: manager responsibility leaked into serializer file")

rules = read(RULES)
for token in (
    "validateLivestockHealthStore",
    "validateStoredObservation",
    "validateStoredCheck",
    "toSnapshot",
    "check.photoUrisList",
    "LIVESTOCK_HEALTH_MAX_PHOTOS",
):
    if token not in rules:
        errors.append(f"{RULES.relative_to(ROOT)}: validation/mapping rule missing: {token}")
if "android." in rules or "androidx." in rules:
    errors.append(f"{RULES.relative_to(ROOT)}: store rules must remain Android-free")
if "evaluations = evaluationsList.map" not in rules:
    errors.append(f"{RULES.relative_to(ROOT)}: evaluation snapshots are not mapped")

evaluation_mapper = read(EVALUATION_MAPPER)
for token in (
    "buildStoredEvaluation(",
    "StoredLivestockEvaluation.toSnapshot",
    "matchesInput(",
):
    if token not in evaluation_mapper:
        errors.append(
            f"{EVALUATION_MAPPER.relative_to(ROOT)}: evaluation mapping missing: {token}"
        )

evaluation_rules = read(EVALUATION_RULES)
for token in (
    "validateStoredEvaluation(",
    "validateEvaluationInput(",
):
    if token not in evaluation_rules:
        errors.append(
            f"{EVALUATION_RULES.relative_to(ROOT)}: evaluation validation missing: {token}"
        )

evaluation_mutation = read(EVALUATION_MUTATION)
for token in (
    "LivestockHealthDataStoreManager.addEvaluation",
    "LivestockEvaluationTrigger.USER_REFRESH",
    "appendNewEvaluation(",
):
    if token not in evaluation_mutation:
        errors.append(
            f"{EVALUATION_MUTATION.relative_to(ROOT)}: append-only evaluation mutation missing: {token}"
        )

photo_viewer = read(PHOTO_VIEWER)
for token in (
    "ViewPager2.OnPageChangeCallback",
    "ARG_PHOTO_URIS",
    "LIVESTOCK_HEALTH_MAX_PHOTOS",
    "binding.appHeader.setupAquaHeader",
    "AquaHeaderTrailingText",
    "photoViewerIndicator.render",
):
    if token not in photo_viewer:
        errors.append(
            f"{PHOTO_VIEWER.relative_to(ROOT)}: photo viewer contract missing: {token}"
        )
for forbidden in (
    "btnPhotoViewerClose",
    "tvPhotoViewerCounter",
    "common_close",
):
    if forbidden in photo_viewer:
        errors.append(
            f"{PHOTO_VIEWER.relative_to(ROOT)}: legacy viewer chrome remains: {forbidden}"
        )

header_config = read(AQUA_HEADER_CONFIG)
if "AquaHeaderTrailingText" not in header_config:
    errors.append(
        f"{AQUA_HEADER_CONFIG.relative_to(ROOT)}: shared trailing-text contract is missing"
    )

header_layout = read(AQUA_HEADER_LAYOUT)
if "@+id/tvTrailingText" not in header_layout:
    errors.append(
        f"{AQUA_HEADER_LAYOUT.relative_to(ROOT)}: shared header trailing text view is missing"
    )

page_indicator = read(PAGE_INDICATOR)
for token in (
    "class AquaPageIndicatorView",
    "fun render(pageCount: Int, selectedIndex: Int)",
):
    if token not in page_indicator:
        errors.append(
            f"{PAGE_INDICATOR.relative_to(ROOT)}: shared page indicator contract missing: {token}"
        )

check_sheet = read(CHECK_SHEET)
for token in (
    "MutableList<String?>(LIVESTOCK_HEALTH_MAX_PHOTOS)",
    "photoUris = checkPhotoUris.filterNotNull()",
    "STATE_PHOTO_URIS",
):
    if token not in check_sheet:
        errors.append(
            f"{CHECK_SHEET.relative_to(ROOT)}: three-photo check contract missing: {token}"
        )
if "photoUri = photoController.selectedUri()" in check_sheet:
    errors.append(f"{CHECK_SHEET.relative_to(ROOT)}: singular check photo save is forbidden")

check_photo_controller = read(CHECK_PHOTO_CONTROLLER)
for token in (
    "activeSlotIndex: () -> Int",
    "currentPhotoUri: (Int) -> String?",
    "rollbackPendingMedia(previousUri)",
):
    if token not in check_photo_controller:
        errors.append(
            f"{CHECK_PHOTO_CONTROLLER.relative_to(ROOT)}: slot media lifecycle missing: {token}"
        )

check_layout = read(CHECK_LAYOUT)
for token in (
    "@+id/checkPhotoAddArea",
    "@+id/checkPhotoSlotOne",
    "@+id/checkPhotoSlotTwo",
    "@+id/checkPhotoSlotThree",
    "@drawable/bg_livestock_health_photo_dropzone",
    "@drawable/ic_camera_24",
    "@drawable/ic_livestock_note_24",
    "@string/livestock_health_photo_optional",
    "@string/livestock_health_note_label",
):
    if token not in check_layout:
        errors.append(
            f"{CHECK_LAYOUT.relative_to(ROOT)}: shared observation/check media UI missing: {token}"
        )
if "@string/livestock_health_check_context_value" in check_layout:
    errors.append(
        f"{CHECK_LAYOUT.relative_to(ROOT)}: obsolete water-measurement row must stay removed"
    )

follow_up_fragment = read(FOLLOW_UP_FRAGMENT)
for token in (
    "lastCompletedWaterChangeForTank(args.tankId)",
):
    if token not in follow_up_fragment:
        errors.append(
            f"{FOLLOW_UP_FRAGMENT.relative_to(ROOT)}: real tank-context binding missing: {token}"
        )
if "MaintenanceViewModel" in follow_up_fragment:
    errors.append(
        f"{FOLLOW_UP_FRAGMENT.relative_to(ROOT)}: cross-feature MaintenanceViewModel dependency is forbidden"
    )

follow_up_ui = read(FOLLOW_UP_UI)
for token in (
    "val photoUris: List<String>",
    "livestock_health_photo_more_count",
    "LivestockHealthPhotoViewerDialogFragment.show",
    "LivestockHealthUiText.observationLabel",
    "tvHistoryNote",
    "livestock_health_history_note_format",
    "renderLatestStatus(",
    "renderTankContext(",
    "tvFollowupLastCheckTime",
    "tvFollowupWaterMeasurementValue",
    "tvFollowupWaterChangeValue",
):
    if token not in follow_up_ui:
        errors.append(f"{FOLLOW_UP_UI.relative_to(ROOT)}: history contract missing: {token}")
for forbidden in (
    "entry.photoUri ?: livestock.photoUri",
    "photoUri = check.photoUri",
):
    if forbidden in follow_up_ui:
        errors.append(f"{FOLLOW_UP_UI.relative_to(ROOT)}: misleading photo fallback remains: {forbidden}")

history_layout = read(HISTORY_LAYOUT)
for token in (
    "@+id/tvHistoryPhotoCount",
    "@+id/tvHistoryNote",
):
    if token not in history_layout:
        errors.append(f"{HISTORY_LAYOUT.relative_to(ROOT)}: history UI contract missing: {token}")

follow_up_layout = read(FOLLOW_UP_LAYOUT)
for token in (
    "@+id/cardEvaluation",
    "@+id/tvFollowupLastCheckTime",
    "@+id/tvFollowupWaterMeasurementValue",
    "@+id/tvFollowupWaterChangeValue",
):
    if token not in follow_up_layout:
        errors.append(
            f"{FOLLOW_UP_LAYOUT.relative_to(ROOT)}: live follow-up binding missing: {token}"
        )
for forbidden in (
    "@string/livestock_health_issue_surface",
    "@string/livestock_health_issue_first_seen",
    "@string/livestock_health_last_check_time",
    "@string/livestock_health_tank_context_measurement_age",
    "@string/livestock_health_tank_context_change_age",
):
    if forbidden in follow_up_layout:
        errors.append(
            f"{FOLLOW_UP_LAYOUT.relative_to(ROOT)}: static demo observation content remains: {forbidden}"
        )

ui_text = read(UI_TEXT)
for token in (
    "fun observationLabel(",
    "fun resolveObservationLabel(",
    "symptomKey == SYMPTOM_OTHER",
    "otherObservation.trim()",
):
    if token not in ui_text:
        errors.append(f"{UI_TEXT.relative_to(ROOT)}: custom observation resolver missing: {token}")

ui_session = read(UI_SESSION)
if "val otherObservation: String" not in ui_session:
    errors.append(f"{UI_SESSION.relative_to(ROOT)}: custom observation state is missing")

record_ui = read(RECORD_UI)
if "otherObservation = otherObservation" not in record_ui:
    errors.append(f"{RECORD_UI.relative_to(ROOT)}: custom observation mapping is missing")

follow_up_card_binder = read(FOLLOW_UP_CARD_BINDER)
for token in (
    "LivestockHealthUiText.observationLabel",
    "otherObservation = entry.otherObservation",
):
    if token not in follow_up_card_binder:
        errors.append(
            f"{FOLLOW_UP_CARD_BINDER.relative_to(ROOT)}: custom observation card binding missing: {token}"
        )

evaluation = read(EVALUATION)
for token in (
    "WaterAnalysisViewModel",
    "lastCompletedWaterChangeForTank(args.tankId)",
    "renderEvaluationScreen(",
    "saveLivestockEvaluation(",
):
    if token not in evaluation:
        errors.append(f"{EVALUATION.relative_to(ROOT)}: evaluation flow missing: {token}")
if "MaintenanceViewModel" in evaluation:
    errors.append(
        f"{EVALUATION.relative_to(ROOT)}: cross-feature MaintenanceViewModel dependency is forbidden"
    )

evaluation_persistence = read(EVALUATION_PERSISTENCE)
for token in (
    "healthViewModel.addEvaluation(",
    "healthViewModel.create(",
    "LivestockEvaluationSaveResult.FollowUpStarted",
):
    if token not in evaluation_persistence:
        errors.append(
            f"{EVALUATION_PERSISTENCE.relative_to(ROOT)}: evaluation persistence missing: {token}"
        )

evaluation_summary = read(EVALUATION_SUMMARY)
for token in (
    "record.isEvaluationStale(latestWaterAnalysis)",
    "cardEvaluation.setOnClickListener",
):
    if token not in evaluation_summary:
        errors.append(
            f"{EVALUATION_SUMMARY.relative_to(ROOT)}: follow-up evaluation summary missing: {token}"
        )

evaluation_screen_binder = read(EVALUATION_SCREEN_BINDER)
for token in (
    "usedWaterAnalysis",
    "latestWaterAnalysis",
    "lastWaterChangeAtMillis",
    "tvEvaluationWaterChangeValue",
    "renderEvaluationAction",
):
    if token not in evaluation_screen_binder:
        errors.append(
            f"{EVALUATION_SCREEN_BINDER.relative_to(ROOT)}: evaluation screen state missing: {token}"
        )

evaluation_request_factory = read(EVALUATION_REQUEST_FACTORY)
for token in (
    "INITIAL_OBSERVATION",
    "USER_REFRESH",
):
    if token not in evaluation_request_factory:
        errors.append(
            f"{EVALUATION_REQUEST_FACTORY.relative_to(ROOT)}: idempotent evaluation request missing: {token}"
        )

observation_layout = read(OBSERVATION_LAYOUT)
if "@string/livestock_health_affected_counter_preview" in observation_layout:
    errors.append(
        f"{OBSERVATION_LAYOUT.relative_to(ROOT)}: fake affected-count preview is forbidden"
    )

evaluation_layout = read(EVALUATION_LAYOUT)
if "@+id/tvEvaluationWaterChangeValue" not in evaluation_layout:
    errors.append(
        f"{EVALUATION_LAYOUT.relative_to(ROOT)}: real water-change binding is missing"
    )
for forbidden in (
    "@string/livestock_health_last_water_measurement_value",
    "@string/livestock_health_last_water_change_value",
):
    if forbidden in evaluation_layout:
        errors.append(
            f"{EVALUATION_LAYOUT.relative_to(ROOT)}: static tank-context fallback remains: {forbidden}"
        )

legacy_demo_resources = (
    "livestock_health_history_today_surface_same",
    "livestock_health_history_issue_surface",
    "livestock_health_tank_context_measurement",
    "livestock_health_tank_context_measurement_age",
    "livestock_health_tank_context_change_age",
    "livestock_health_tank_context_change",
    "livestock_health_affected_counter_preview",
    "livestock_health_last_check_time",
    "livestock_health_check_time_value",
    "livestock_health_check_context_value",
)
for resource_file in (STRINGS, STRINGS_TR):
    resource_text = read(resource_file)
    for resource_name in legacy_demo_resources:
        if f'name="{resource_name}"' in resource_text:
            errors.append(
                f"{resource_file.relative_to(ROOT)}: legacy demo resource remains: {resource_name}"
            )

navigation = read(NAVIGATION)
for token in (
    "action_livestockHealthFollowUpFragment_to_livestockHealthEvaluationFragment",
    'android:name="observationId"',
    'app:popUpTo="@id/livestockHealthObservationFragment"',
):
    if token not in navigation:
        errors.append(f"{NAVIGATION.relative_to(ROOT)}: evaluation navigation missing: {token}")

view_model = read(VIEW_MODEL)
if "LivestockHealthOperations" not in view_model:
    errors.append(f"{VIEW_MODEL.relative_to(ROOT)}: ViewModel must depend on application operations")
for forbidden in (
    "com.aqua.aqualight.data.",
    "com.aqua.aqualight.platform.",
    "LivestockHealthDataStoreManager",
    "DefaultLivestockHealthOperations",
):
    if forbidden in view_model:
        errors.append(f"{VIEW_MODEL.relative_to(ROOT)}: ViewModel boundary leak: {forbidden}")

livestock_ui = [
    path for path in UI_ROOT.glob("LivestockHealth*.kt")
    if path.is_file()
]
for path in livestock_ui:
    text = path.read_text(encoding="utf-8", errors="ignore")
    for forbidden in (
        "import com.aqua.aqualight.data.",
        "import com.aqua.aqualight.platform.media.",
        "AppMediaStorage",
        "requireAppContainer",
        "StoredLivestock",
    ):
        if forbidden in text:
            errors.append(f"{path.relative_to(ROOT)}: livestock UI boundary leak: {forbidden}")

owner_graph = read(OWNER_GRAPH)
if "LivestockHealthDataStoreManager(appContext, aquariumTankStore)" not in owner_graph:
    errors.append(
        f"{OWNER_GRAPH.relative_to(ROOT)}: livestock store must reuse the composed tank store"
    )

owner_factory = read(OWNER_FACTORY)
for token in (
    "LivestockHealthViewModel::class.java",
    "DefaultLivestockHealthOperations(graph.livestockHealthStore)",
    "careTasks = DefaultMaintenanceOperations(",
):
    if token not in owner_factory:
        errors.append(f"{OWNER_FACTORY.relative_to(ROOT)}: livestock ViewModel binding missing: {token}")

smoke = read(SMOKE)
if "LivestockHealthDataStoreManager(appContext, tankStore)" not in smoke:
    errors.append(f"{SMOKE.relative_to(ROOT)}: release-smoke store wiring must match production")
if "careTasks = maintenanceOperations.tasks" not in smoke:
    errors.append(f"{SMOKE.relative_to(ROOT)}: release-smoke care-task projection must match production")

single_arg_construction = re.compile(
    r"LivestockHealthDataStoreManager\(\s*(?:appContext|context)\s*\)"
)
for source_root in (ROOT / "app/src/main", ROOT / "app/src/releaseSmoke"):
    for source in source_root.rglob("*.kt"):
        text = source.read_text(encoding="utf-8", errors="ignore")
        if single_arg_construction.search(text):
            errors.append(
                f"{source.relative_to(ROOT)}: livestock health store must receive the "
                "authoritative tank store from its owner"
            )

boundary_test = read(BOUNDARY_TEST)
for token in (
    "observationReadDelegatesToApplicationBoundary",
    "createCheckAndCloseDelegateTypedApplicationInputs",
    "lastWaterChangeUsesLatestCompletedTaskForSelectedTank",
    "FakeLivestockHealthOperations",
):
    if token not in boundary_test:
        errors.append(f"{BOUNDARY_TEST.relative_to(ROOT)}: boundary regression coverage missing: {token}")

if errors:
    print("Livestock Health architecture guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print(
    "Livestock Health architecture guard passed: UI/application/data boundaries are isolated, "
    "store dependencies are composed once, and persistence responsibilities are split."
)
