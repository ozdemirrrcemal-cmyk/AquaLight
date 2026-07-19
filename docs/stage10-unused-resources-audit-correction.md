# Stage 10 — Unused Resources Audit Correction

This addendum is authoritative for the three palette rows below and supersedes their earlier **removed — unreachable** classification in `stage10-unused-resources-audit.md`.

The first real Android resource-link pass (`mergeDebugResources`) proved that these primitives are referenced from `values-night` qualifiers. They must remain because deleting them breaks resource linking before lint can complete.

| Resource | Final classification | Proof | Exact value preserved |
|---|---|---|---|
| `R.color.aqua_palette_hex_cc0a192f` | retained — qualifier reference | `values-night/colors.xml` aliases `colorSurfaceTransparent`; removal fails `mergeDebugResources`. | `#CC0A192F` |
| `R.color.aqua_palette_hex_dde8f5` | retained — qualifier reference | `values-night/button_colors.xml` aliases `aqua_button_outline_content`; removal fails `mergeDebugResources`. | `#DDE8F5` |
| `R.color.aqua_palette_hex_ff3b30` | retained — qualifier reference | `values-night/colors.xml` aliases `aqua_button_red`; removal fails `mergeDebugResources`. | `#FF3B30` |

## Corrected totals

- Removed as genuinely unreachable: **374**
- Retained with explicit dynamic uCrop proof: **2**
- Retained with qualifier-link proof: **3**
- Total classified: **379**

This correction does not alter the semantic-token migration or rendered colors. It restores only the three primitive definitions required by night-qualified semantic aliases.
