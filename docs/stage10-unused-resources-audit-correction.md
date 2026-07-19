# Stage 10 — Unused Resources Audit Correction

This addendum is authoritative for the four rows below and supersedes their earlier **removed — unreachable** classification in `stage10-unused-resources-audit.md`.

Real Android resource-link passes proved that three palette primitives are referenced from `values-night` qualifiers and that one dimension token is a transitive dependency of the dynamically retained uCrop layout. All four resources must remain because deleting any of them breaks resource linking before lint can complete.

| Resource | Final classification | Proof | Exact value preserved |
|---|---|---|---|
| `R.color.aqua_palette_hex_cc0a192f` | retained — qualifier reference | `values-night/colors.xml` aliases `colorSurfaceTransparent`; removal fails `mergeDebugResources`. | `#CC0A192F` |
| `R.color.aqua_palette_hex_dde8f5` | retained — qualifier reference | `values-night/button_colors.xml` aliases `aqua_button_outline_content`; removal fails `mergeDebugResources`. | `#DDE8F5` |
| `R.color.aqua_palette_hex_ff3b30` | retained — qualifier reference | `values-night/colors.xml` aliases `aqua_button_red`; removal fails `mergeDebugResources`. | `#FF3B30` |
| `R.dimen.aqua_size_negative_12` | retained — dynamic transitive dependency | `layout/ucrop_activity_photobox.xml` references the token and the layout is loaded dynamically by uCrop; removal fails resource linking. | `-12dp` |

## Corrected totals

- Removed as genuinely unreachable: **373**
- Retained with explicit dynamic uCrop proof: **3**
- Retained with qualifier-link proof: **3**
- Total classified: **379**

This correction does not alter rendered dimensions or colors. It restores only definitions proven necessary by qualifier and dynamic dependency resolution.
