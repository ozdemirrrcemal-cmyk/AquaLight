# Stage 10 — Unused Resources Audit Correction

This addendum is the authoritative final classification for the resources whose reachability changed during real Android resource-link and lint validation. It supersedes the preliminary qualifier-retention conclusion in the earlier audit.

## Dynamically retained uCrop dependency chain

| Resource | Final classification | Proof | Exact value preserved |
|---|---|---|---|
| `R.layout.ucrop_activity_photobox` | retained — dynamic entry point | uCrop resolves this layout dynamically; it is declared in `aqua_dynamic_resource_keep.xml`. | unchanged layout |
| `R.string.ucrop_label_edit_photo` | retained — dynamic layout dependency | Referenced by the retained uCrop layout and declared in the keep contract. | unchanged text |
| `R.dimen.aqua_size_negative_12` | retained — dynamic transitive dependency | Referenced by the retained uCrop layout; deleting it fails Android resource linking. | `-12dp` |

The design-system guard now requires all three resources and verifies that the retained layout still references the audited dimension token.

## Qualifier-only orphan correction

The first resource-link pass temporarily suggested that the following palette primitives had to remain because `values-night` aliases referenced them:

- `R.color.aqua_palette_hex_cc0a192f`
- `R.color.aqua_palette_hex_dde8f5`
- `R.color.aqua_palette_hex_ff3b30`

Full lint validation then proved that those night aliases were themselves unreachable. The 28 qualifier-only semantic/legacy color definitions were removed, after which these three palette primitives had no remaining references and were also removed. No rendered screen used these definitions.

## Final totals for the original 379-resource inventory

- Removed as genuinely unreachable: **376**
- Retained with explicit dynamic uCrop proof: **3**
- Total classified: **379**

Follow-up lint additionally removed **28 qualifier-only orphan color definitions** created or exposed by the migration. This cleanup does not change rendered colors, dimensions, or screen appearance; it removes only definitions with no reachable consumer.
