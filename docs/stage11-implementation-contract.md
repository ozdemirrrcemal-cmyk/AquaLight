# Stage 11 — Localization and Accessibility Implementation Contract

This branch starts directly from `main`. No application code, migration script, guard implementation or resource workaround is copied from rejected PRs #51 or #52.

## Non-negotiable visual contract

Stage 11 must not change the rendered AquaLight design unless a separate visual-design change is explicitly approved. The following are frozen:

- visible control width and height,
- margins, padding, spacing and alignment,
- typography, text size, font, line height and truncation,
- colors, tints, shapes, strokes, elevation and imagery,
- component positions, navigation structure and existing English copy.

Accessibility metadata may change only when it does not alter rendered pixels.

## Locale contract

- `SupportedLocaleRegistry` is the only production locale source of truth.
- English remains the only enabled locale until another catalog is complete and linguistically reviewed.
- Unsupported or previously saved locale codes are normalized to English before the first rendered frame.
- User-facing date, time, number and percentage formatting follows the active AquaLight application locale, not the phone locale.
- Protocol, persistence, identifiers and machine-readable timestamps remain locale-neutral.
- The complete `values-xx` checklist item remains open until real translations are delivered. Empty or partial catalogs are not presented as complete.

## Accessibility contract

- Icon-only controls need meaningful accessible names unless explicitly decorative.
- Dynamic Online/Offline changes need localized state descriptions and polite announcements.
- The commercial target is an effective touch area of at least 48 × 48 dp; visible controls are not resized to satisfy it.
- Effective hit-area expansion must not overlap another actionable target ambiguously and must not alter rendered geometry.
- RTL, Light, Dark and 200% font tests use the existing production screens and styles.
- WCAG checks use verified foreground/background semantic pairs. Existing colors are not modified silently.
- Automated checks do not replace the required physical-device TalkBack acceptance pass.

## Completion rule

The pull request remains Draft until all automated gates pass, the physical TalkBack acceptance record is complete and the user grants commercial approval. It must not be merged based on CI alone.
