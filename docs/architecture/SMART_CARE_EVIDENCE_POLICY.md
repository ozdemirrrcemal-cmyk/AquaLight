# Smart Care Evidence Policy

## Purpose

Smart Care converts saved aquarium facts into reminders and conservative lighting guidance. It is not a diagnosis engine, a replacement for product labels, or an authority for fixture channel output.

Every automatic rule must have stable evidence IDs from `SmartCareEvidenceCatalog`. Evidence is reviewed independently from localized notification text so future Quick Setup and care surfaces can share the same decisions without depending on data-layer models.

## Implemented freshwater policy

| Phase or signal | Automated behavior | Evidence |
| --- | --- | --- |
| Days 1–21, planted with light | Recommend a stable six-hour photoperiod | Tropica Growing-in and Quick Guide |
| Days 22–90, planted with light | Increase gradually only when growth is stable and algae is controlled, capped at eight hours | Tropica Growing-in and Quick Guide |
| Nature Aquarium with active soil, days 1–14 | Daily early water-change reminder | ADA Starting from Zero |
| Other planted startup, days 1–28 | Water-change reminder every three days within the published 25–50% range | Tropica Growing-in |
| Before livestock | Require stable water and undetectable ammonia/nitrite rather than relying only on tank age | ADA Starting from Zero and UF/IFAS |
| Tropica fertilizer, days 1–28 | Withhold or limit; never invent a fractional dose | Tropica Growing-in plus the selected product guide |
| Tropica Specialised with observed algae | Store the official halve-dose and increased-water-change response for a future observation input | Tropica Specialised Nutrition |
| ADA Green Brighty Nitrogen | Require observed/measured need; no unconditional dose | ADA liquid fertilizer guide |
| ADA Green Brighty Iron, days 1–60 | Defer until the two-month stage | ADA liquid fertilizer guide |
| Mature planted aquarium | Continue product-frequency fertilizer checks, weekly water changes, and monthly light review | Tropica and ADA official guides |

The Tropica and ADA methods stay separate through tank style, setup day, materials, and product identity. A Nature Aquarium rule is not silently applied to every freshwater tank. Marine and reef-specific advice requires a separate evidence set and must not inherit planted-freshwater schedules.

## Lighting boundary

`SmartCareLightingAdvisor` returns photoperiod, phase, adjustment direction, confidence, and evidence IDs. It deliberately does not return RGB percentages or intensity. Quick Setup may consume this recommendation later, but device output requires fixture identity and output calibration or PAR measurements first.

## Fertilizer boundary

Gross dimensions are converted to an explicitly labeled estimated net water volume. Calculated milliliters are a product-label baseline, not a command. Unknown or ambiguous products produce a label-verification reminder instead of selecting a product from brand name alone.

Only products carrying reviewed evidence IDs are eligible for an automatic milliliter estimate. Older catalog entries without reviewed sources remain selectable data, but resolve to label verification until their official product guidance is added and reviewed.

## Fish-health boundary

Health automation is limited to prevention and triage: observe and record behavior, appetite, respiration and visible signs; check water quality first; quarantine new arrivals; and refer suspected disease to an aquatic-animal professional. Symptoms alone must never trigger a diagnosis or medication recommendation. Veterinary evidence entries are marked `requiresProfessionalDiagnosis` for future consumers.

## Change requirements

- Add or update an authoritative source before changing a medical, dosing, water-quality, or lighting threshold.
- Keep exact source URLs and review dates in `SmartCareEvidenceCatalog`.
- Add deterministic tests for evidence coverage, tank gating, schedule cadence, and boundary behavior.
- Revalidate product guidance when labels change; a source review date is not a claim that guidance never changes.
