# AquaLight design-system contract

## Product theme decision

AquaLight supports **Light**, **Dark**, and **Follow system** modes. Existing screens intentionally
retain their current palette in both resource configurations during this cleanup; this stage does
not redesign surfaces. The light configuration still places the system bars on a dark navy/black
surface, so status-bar and navigation-bar icons remain light in both configurations.

Any future palette redesign must be delivered separately with visual-regression approval. It must
not be mixed into resource cleanup.

## Resource layers

Resources move through three layers:

1. Primitive values contain an exact reusable value and do not express component intent.
   `aqua_palette_hex_*`, `aqua_size_*`, and the typography scale are the only places where raw
   palette colors, dp, or sp values may be declared.
2. Semantic tokens describe intent, such as surface, content, outline, success, warning, spacing,
   radius, or elevation. Light/night variants belong at this layer.
3. Component tokens and styles describe a reusable UI contract. Screens consume
   `Widget.Aqua.Button.*`, `Widget.Aqua.Input.*`, `Widget.Aqua.Card.*`,
   `Widget.Aqua.BottomSheet.*`, `Widget.Aqua.Dialog.*`, selection-row, chip, and toolbar styles.

Direct primitive use is acceptable only while preserving an existing exact visual value during
migration. New UI must introduce or reuse a semantic/component token. Compose-only component
contracts live under `ui/common` in audited `*ComposeStyle.kt` files; feature packages consume
their typed `Dp`, shape, color, brush and typography tokens and never declare raw visual values.

## Naming standard

- Strings: `<feature>_<surface>_<element>[_<state>]`, for example
  `device_menu_manual_control_title`.
- Plurals: the same convention, describing the counted noun.
- Colors: `aqua_<component-or-role>_<property>[_<state>]`; exact palette primitives use
  `aqua_palette_hex_<rrggbb-or-aarrggbb>`.
- Dimensions: `aqua_<component-or-role>_<property>`; exact migration primitives use
  `aqua_size_<dp>` with `_` as the decimal separator and `negative_` for negative values.
- Typography: `aqua_text_size_<role>`.
- Styles: `Widget.Aqua.<Component>.<Variant>` or `TextAppearance.Aqua.<Component>.<Role>`.
- Drawables: `bg_<component>_<state>` for backgrounds and `ic_<meaning>_<size>` for icons.

Resource names are lowercase snake case. Product copy belongs in string/plural resources. Official
brand names, model names, protocol tokens, and symbols may be marked `translatable="false"`.

## Kotlin boundary

Static user-facing copy is represented by `@StringRes`, `@PluralsRes`, or `AquaUiText` until an
Android `Context` resolves it. `AquaUiText.Dynamic` is reserved for user data, device/firmware data,
identifiers, and external diagnostic details. Catalog titles, descriptions, brands, and category
labels follow the same rule.

## Enforcement

`python3 tools/design_system_resource_guard.py` rejects raw XML copy, colors, dp/sp values outside
their primitive files, raw feature-owned Kotlin palette/dp/text-size values, legacy button styles,
duplicate resources in one configuration, and catalog copy that bypasses `@StringRes`. Dosing
Compose UI additionally fails when a feature file declares a local `.dp` conversion or ARGB value
instead of consuming `AquaDosingComposeStyle`.
