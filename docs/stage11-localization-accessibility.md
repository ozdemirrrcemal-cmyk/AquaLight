# Stage 11 — Localization and Accessibility Contract

## Product locale policy

AquaLight has not been released. Stage 11 therefore establishes a clean locale contract without migration or backward-compatibility code.

| Language tag | Native label | Status | User selectable | Resource directory |
|---|---|---|---|---|
| `en` | English | `PUBLISHED` | yes | base `values/` |
| `tr` | Türkçe | `PLANNED` | no | not present until complete |
| `de` | Deutsch | `PLANNED` | no | not present until complete |
| `fr` | Français | `PLANNED` | no | not present until complete |

Russian and Chinese are not part of the product locale contract.

A locale may be promoted from `PLANNED` to `PUBLISHED` only when:

1. Its complete `values-xx` resource pack exists.
2. Every translatable base string, plural and string-array has a translation.
3. Format-placeholder indices and types match the base resource.
4. `SupportedLocaleRegistry` and `locales_config.xml` contain the same published tag.
5. Unit, lint, emulator, pseudolocale and screenshot checks pass.
6. The translated application receives a manual linguistic review.

Empty locale directories and English copies are prohibited because Android fallback would make an incomplete language appear published.

## Locale boundaries

`SupportedLocaleRegistry` is the only source of truth for application locales. Settings, startup locale application, durable preferences and Android `LocaleConfig` must agree with it.

`AppLocaleFormatter` is the UI-facing boundary for:

- dates,
- user-visible times,
- decimal and grouped numbers,
- percentages.

Machine contracts must remain fixed and must not use the UI locale formatter. This includes BLE payloads, QR payloads, WebSocket protocol fields, persistence keys, cryptographic values and other firmware-facing formats.

## Language-screen visual contract

The approved language-screen appearance is preserved:

- existing page background,
- existing Aqua card style,
- existing card padding and spacing,
- 32dp flag artwork,
- native language label in the centre,
- existing radio style on the trailing edge,
- existing typography and semantic colors.

Availability changes visibility and selection only; it must not redesign the row.

Each visible language card is one accessibility node. The decorative flag and radio indicator do not create duplicate TalkBack stops. Selection is exposed through a state description.

## Accessibility contract

### Text resizing

Critical flows must preserve content and functionality at a system font scale of 200%. Fixed visual height must not clip essential text; affected content must wrap or remain scrollable.

### Touch targets

Clickable controls must provide an effective touch target of at least 48dp. Compact approved visuals use the centralized `BaseActivity` touch-delegate layer so their measured appearance does not change.

### Icon-only controls

An actionable icon requires a meaningful action description. Decorative imagery uses no accessibility description and does not create an extra focus stop.

### Dynamic device state

Online and Offline are not conveyed by color alone. Device cards expose the device identity, assignment context and current presence as speakable content plus a state description.

### RTL

Production layouts use start/end semantics. `ar-XB` pseudolocale screenshots are mandatory even while no RTL language is published.

### Contrast

Declared semantic Light/Dark text and UI contracts must satisfy WCAG AA thresholds. The automated contrast guard resolves color aliases and alpha before calculating ratios.

## Automated release gates

`tools/localization_accessibility_guard.py` is the authoritative Stage 11 guard and composes:

- registry and `LocaleConfig` parity,
- translation completeness,
- placeholder compatibility,
- language-screen visual invariants,
- icon-description checks,
- RTL-safe attribute checks,
- centralized 48dp touch-target protection,
- WCAG Light/Dark contrast checks.

The guard runs in Android CI, Release CI, CodeQL and emulator workflows.

The emulator matrix includes:

- API 27: English Light and Dark at 100%,
- API 35: English Light and Dark at 100%,
- API 35: English Light and Dark at 200%,
- API 35: `en-XA` Light at 200%,
- API 35: `ar-XB` Light and Dark at 100%,
- full-root Espresso accessibility checks on the primary application screens.

## Manual acceptance boundary

Automated checks do not replace a real screen-reader session. Stage 11 is not ready for merge until the TalkBack checklist in `stage11-talkback-acceptance.md` is completed on a physical Android device and no blocking issue remains.
