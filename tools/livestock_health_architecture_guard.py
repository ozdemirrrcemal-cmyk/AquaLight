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
FOLLOW_UP_UI = APP / "ui/tabs/aquarium/detail/health/LivestockHealthFollowUpUi.kt"
CHECK_LAYOUT = ROOT / "app/src/main/res/layout/content_sheet_livestock_health_check.xml"
HISTORY_LAYOUT = ROOT / "app/src/main/res/layout/item_livestock_health_history.xml"
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
    VIEW_MODEL,
    PHOTO_VIEWER,
    AQUA_HEADER_CONFIG,
    AQUA_HEADER_LAYOUT,
    PAGE_INDICATOR,
    CHECK_SHEET,
    CHECK_PHOTO_CONTROLLER,
    FOLLOW_UP_UI,
    CHECK_LAYOUT,
    HISTORY_LAYOUT,
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

schema = read(SCHEMA)
if "const val LIVESTOCK_HEALTH_VERSION = 2" not in schema:
    errors.append(
        f"{SCHEMA.relative_to(ROOT)}: livestock health schema must use hard-cutover version 2"
    )

proto = read(PROTO)
if "repeated string photo_uris = 5;" not in proto:
    errors.append(f"{PROTO.relative_to(ROOT)}: health checks must persist repeated photo_uris")
if "string photo_uri = 5;" in proto:
    errors.append(f"{PROTO.relative_to(ROOT)}: singular health-check photo field is forbidden")

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
    "existing.photoUrisList == input.photoUris",
    ".addAllPhotoUris(input.photoUris)",
    "input.photoUris.forEach",
):
    if token not in manager:
        errors.append(f"{MANAGER.relative_to(ROOT)}: required injected-store contract missing: {token}")
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

follow_up_ui = read(FOLLOW_UP_UI)
for token in (
    "val photoUris: List<String>",
    "livestock_health_photo_more_count",
    "LivestockHealthPhotoViewerDialogFragment.show",
):
    if token not in follow_up_ui:
        errors.append(f"{FOLLOW_UP_UI.relative_to(ROOT)}: history gallery contract missing: {token}")
for forbidden in (
    "entry.photoUri ?: livestock.photoUri",
    "photoUri = check.photoUri",
):
    if forbidden in follow_up_ui:
        errors.append(f"{FOLLOW_UP_UI.relative_to(ROOT)}: misleading photo fallback remains: {forbidden}")

history_layout = read(HISTORY_LAYOUT)
if "@+id/tvHistoryPhotoCount" not in history_layout:
    errors.append(f"{HISTORY_LAYOUT.relative_to(ROOT)}: history +N photo badge is missing")

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
):
    if token not in owner_factory:
        errors.append(f"{OWNER_FACTORY.relative_to(ROOT)}: livestock ViewModel binding missing: {token}")

smoke = read(SMOKE)
if "LivestockHealthDataStoreManager(appContext, tankStore)" not in smoke:
    errors.append(f"{SMOKE.relative_to(ROOT)}: release-smoke store wiring must match production")

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
