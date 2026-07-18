# Stage 10 — Design system and resource contract

## Product theme decision

AquaLight supports both **Light** and **Dark** theme modes, plus the system-following option. During Stage 10 the existing rendered appearance is treated as the visual baseline: resource extraction and style consolidation must not intentionally change layout, colors, typography, spacing, shapes, or component behavior.

The current base palette is visually dark even though some resource names contain `light`. Renaming those legacy palette entries is deferred until screenshot coverage can prove pixel-equivalent output. System-bar icon appearance must follow actual luminance, not the resource qualifier name.

## Resource boundaries

- Every user-visible string is defined in `values/strings.xml` and consumed through Android resources.
- Kotlin presentation models expose `@StringRes` identifiers or typed text abstractions instead of localized raw strings.
- Menu, bottom-navigation, catalog, skeleton-menu, accessibility, dialog, sheet, chip, button, input, and status text follow the same rule.
- Palette values are declared only in `values*/colors.xml`; screens consume semantic color resources or theme attributes.
- Spacing, margins, paddings, radii, elevations, touch sizes, component sizes, and typography sizes use semantic `dimen` tokens.
- Shared components use semantic `Widget.Aqua.*`, `TextAppearance.Aqua.*`, and `ShapeAppearance.Aqua.*` names.
- Legacy visual names such as `RedButton`, `BlackButton`, and `WhiteButton` are not allowed after migration.

## Naming standard

Resource names use lowercase snake case and begin with a stable scope:

- `common_*` for reusable user text and actions.
- `nav_*` for navigation labels.
- `<feature>_*` for screen/feature-specific text.
- `color_*` or a clearly semantic component/state prefix for palette tokens.
- `space_*`, `size_*`, `radius_*`, `elevation_*`, and `text_size_*` for dimensions.
- `Widget.Aqua.*`, `TextAppearance.Aqua.*`, and `ShapeAppearance.Aqua.*` for styles.

Names describe purpose, not a temporary color, exact pixel value, or screen implementation detail.

## Component rules

Buttons, inputs, cards, chips, bottom sheets, and dialogs must inherit from the shared Aqua style family. A screen may override content and state, but not recreate a parallel visual system with literal colors or dimensions.

## Verification gate

`tools/stage10_design_system_guard.py` runs before Gradle in the Stage 10 workflow. It rejects raw user-facing XML text, common Kotlin UI literals, palette literals outside color resources, repeated dimensional literals, legacy style names, non-resource menu titles, mismatched splash typography, and system-bar icon contrast regressions.

The final migration is accepted only when this guard, Android lint, unit tests, and a debug build all pass while existing screenshot baselines remain unchanged.
