#!/usr/bin/env python3
"""Protect the reference-driven Cooling Compose hero architecture."""
from __future__ import annotations

import base64
import binascii
import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COOLING_ROOT = Path("app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling")
FRAGMENT = COOLING_ROOT / "DeviceCoolingRootFragment.kt"
VIEW_MODEL = COOLING_ROOT / "DeviceCoolingRootViewModel.kt"
DESIGN = COOLING_ROOT / "design/CoolingHeroDesign.kt"
ROOT_SCREEN = COOLING_ROOT / "presentation/root/DeviceCoolingRootScreen.kt"
HERO_ROOT = COOLING_ROOT / "presentation/hero"
HERO_SECTION = HERO_ROOT / "CoolingHeroSection.kt"
ARTWORK_LOADER = HERO_ROOT / "CoolingHeroArtwork.kt"
WATER_LAYER = HERO_ROOT / "CoolingWaterLayer.kt"
ROTOR_LAYER = HERO_ROOT / "CoolingRotorLayer.kt"
LAYOUT = Path("app/src/main/res/layout/fragment_device_cooling_root.xml")
ARTWORK_PARTS = tuple(
    Path(f"app/src/main/res/raw/cooling_hero_reference_{index}.b64")
    for index in range(1, 6)
)
ARTWORK_SHA256 = "ba9127bca87db19a6ac0e6579ac4cf26f59d1b3b43d277602ebc53ad4c99996e"

RAW_COMPOSE_DP = re.compile(r"(?<![\w.])(?:-?\d+(?:\.\d+)?|[A-Za-z_][A-Za-z0-9_]*)\.dp\b")
RAW_KOTLIN_ARGB = re.compile(r"\b0x[0-9A-Fa-f]{6,8}\b")
FORBIDDEN_PROCEDURAL_FILES = (
    HERO_ROOT / "CoolingFanLayer.kt",
    HERO_ROOT / "CoolingTankLayer.kt",
    HERO_ROOT / "CoolingAirflowLayer.kt",
    HERO_ROOT / "CoolingHeroSceneGeometry.kt",
)


def _read(repository_root: Path, relative_path: Path, errors: list[str]) -> str:
    path = repository_root / relative_path
    if not path.is_file():
        errors.append(f"{relative_path}: required Cooling hero file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def _require(path: Path, source: str, errors: list[str], token: str, reason: str) -> None:
    if token not in source:
        errors.append(f"{path}: {reason}: {token}")


def validate_cooling_source(relative_path: Path, source: str) -> list[str]:
    errors: list[str] = []
    if relative_path != DESIGN:
        if RAW_COMPOSE_DP.search(source):
            errors.append(f"{relative_path}: Cooling Compose dimensions must come from CoolingHeroDesign")
        if RAW_KOTLIN_ARGB.search(source):
            errors.append(f"{relative_path}: Cooling Compose colors must come from CoolingHeroDesign")
    return errors


def validate_artwork(repository_root: Path) -> list[str]:
    encoded_parts: list[str] = []
    for relative_path in ARTWORK_PARTS:
        path = repository_root / relative_path
        if not path.is_file():
            return [f"{relative_path}: approved Cooling hero artwork part is missing"]
        encoded_parts.append(path.read_text(encoding="utf-8").strip())
    try:
        artwork = base64.b64decode("".join(encoded_parts), validate=True)
    except (ValueError, binascii.Error) as error:
        return [f"Cooling hero artwork Base64 is invalid: {error}"]
    digest = hashlib.sha256(artwork).hexdigest()
    if digest != ARTWORK_SHA256:
        return [f"Cooling hero artwork hash changed: {digest}"]
    return []


def validate_repository(repository_root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    fragment = _read(repository_root, FRAGMENT, errors)
    view_model = _read(repository_root, VIEW_MODEL, errors)
    design = _read(repository_root, DESIGN, errors)
    root_screen = _read(repository_root, ROOT_SCREEN, errors)
    hero_section = _read(repository_root, HERO_SECTION, errors)
    artwork_loader = _read(repository_root, ARTWORK_LOADER, errors)
    water_layer = _read(repository_root, WATER_LAYER, errors)
    rotor_layer = _read(repository_root, ROTOR_LAYER, errors)
    layout = _read(repository_root, LAYOUT, errors)

    errors.extend(validate_artwork(repository_root))

    for forbidden in FORBIDDEN_PROCEDURAL_FILES:
        if (repository_root / forbidden).exists():
            errors.append(
                f"{forbidden}: product geometry must not be independently redrawn; use approved artwork"
            )

    for token, reason in (
        ("ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed", "Compose host must follow Fragment view lifecycle"),
        ("binding.coolingContentCompose.apply", "Cooling Fragment must host the canonical Compose surface"),
        ("collectAsStateWithLifecycle()", "Cooling Compose must collect state lifecycle-safely"),
        ("DeviceCoolingRootScreen(state = state)", "Fragment must delegate rendering to Cooling root Compose"),
    ):
        _require(FRAGMENT, fragment, errors, token, reason)

    _require(
        VIEW_MODEL,
        view_model,
        errors,
        "val fanSpeedPercent: Int? = null",
        "fan telemetry must stay unavailable until a Cooling application boundary supplies it",
    )

    for token, reason in (
        ("object CoolingHeroGeometry", "hero geometry must have one family-owned contract"),
        ("object CoolingHeroMotion", "hero motion must have one family-owned contract"),
        ("object CoolingHeroMotionPalette", "hero motion colors must have one family-owned contract"),
    ):
        _require(DESIGN, design, errors, token, reason)

    _require(ROOT_SCREEN, root_screen, errors, "CoolingHeroSection(", "root screen must render canonical hero")
    _require(ROOT_SCREEN, root_screen, errors, "state.fanSpeedPercent", "root must preserve authoritative telemetry hook")

    for token, reason in (
        ("rememberCoolingHeroArtwork()", "hero must render the approved reviewed artwork"),
        ("Image(", "approved artwork must be the visual base layer"),
        ("contentScale = ContentScale.FillBounds", "artwork must map exactly to the reviewed hero bounds"),
        ("CoolingWaterLayer(", "hero must add water motion over the approved artwork"),
        ("CoolingRotorLayer(", "hero must preserve a rotor motion hook without replacing product geometry"),
    ):
        _require(HERO_SECTION, hero_section, errors, token, reason)

    for token, reason in (
        ("R.raw.cooling_hero_reference_1", "artwork loader must own the ordered reference parts"),
        ("Base64.decode", "artwork loader must reconstruct the reviewed WebP bytes"),
        ("BitmapFactory.decodeByteArray", "artwork loader must decode the reviewed bytes directly"),
    ):
        _require(ARTWORK_LOADER, artwork_loader, errors, token, reason)

    for token, reason in (
        ("RuntimeShader(", "water must use continuous GPU refraction instead of image slices"),
        ("setInputShader(", "water shader must resample the approved artwork"),
        (
            "Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU",
            "runtime water shader must remain API-gated with a safe static fallback",
        ),
        (
            "CoolingHeroMotion.waterImpactDisplacementPx",
            "fan-driven disturbance must remain localized and centrally tuned",
        ),
        (
            "CoolingHeroGeometry.waterImpactXRatio",
            "water disturbance must use the canonical fan-impact position",
        ),
    ):
        _require(WATER_LAYER, water_layer, errors, token, reason)

    for token, reason in (
        ("drawArc(", "rotor must use motion-blur arcs rather than rotating a perspective-warped bitmap"),
        ("scale(", "rotor motion must be projected into the reviewed intake ellipse"),
        ("clipPath(clip)", "rotor motion must stay inside the reviewed intake region"),
        ("restoreRotorHubFromArtwork(", "the reviewed hub must stay visually stable while blades spin"),
        ("minimumRunningFraction", "rotor must not animate without a running presentation state"),
    ):
        _require(ROTOR_LAYER, rotor_layer, errors, token, reason)

    for token, reason in (
        ("androidx.compose.ui.platform.ComposeView", "root layout must host Compose below AquaHeader"),
        ('android:id="@+id/coolingContentCompose"', "Compose host id must remain canonical"),
        ('app:layout_constraintTop_toBottomOf="@id/appHeader"', "Compose content must remain below AquaHeader"),
    ):
        _require(LAYOUT, layout, errors, token, reason)

    cooling_root = repository_root / COOLING_ROOT
    if cooling_root.is_dir():
        for path in sorted(cooling_root.rglob("*.kt")):
            relative_path = path.relative_to(repository_root)
            source = path.read_text(encoding="utf-8", errors="ignore")
            errors.extend(validate_cooling_source(relative_path, source))

    return errors


def main() -> int:
    errors = validate_repository()
    if errors:
        print("Cooling Compose architecture guard failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1
    print("Cooling Compose architecture guard passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
