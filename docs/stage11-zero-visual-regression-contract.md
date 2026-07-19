# Stage 11 — Zero Visual Regression Contract

This branch starts from `main`. Nothing from rejected PR #51 is an implementation source.

## Rendered UI invariants

Unless explicitly approved in a separate visual-design change, Stage 11 must not alter:

- layout width or height,
- margins, padding, spacing or alignment,
- typography, colors, shapes, strokes or elevation,
- component position, visible icon size or visible text,
- navigation structure or animation,
- existing English copy.

Accessibility metadata such as `contentDescription`, accessibility roles, live regions and traversal order may change when they do not change rendered pixels.

## Touch-target rule

The commercial requirement is an **effective touch target of at least 48 × 48 dp**, not a requirement that every visible button be rendered at 48 dp.

Small controls must preserve their original visible geometry. Their effective target may be expanded through a non-overlapping parent target, a verified `TouchDelegate`, or an equivalent accessibility-safe mechanism. Expansions must not overlap adjacent actionable targets and must not change rendered pixels.

## Locale rule

User-facing formatting follows the active AquaLight application locale, never the phone locale by accident.

- When English is the only enabled AquaLight locale, dates, times, numbers, percentages and date-picker surfaces remain English even when the phone language is Chinese, Turkish or another locale.
- Protocol, persistence and identifiers remain locale-neutral.
- Unsupported locales are not selectable or declared.
- A new locale is enabled only after its complete catalog, placeholder compatibility and linguistic review are complete.

## Automated gates

Before this PR can leave Draft status:

1. A source-diff guard must reject visual-resource changes outside an explicit reviewed allowlist.
2. App-locale tests must cover a non-English phone locale with English selected in AquaLight.
3. Light, Dark, RTL and 200% font release screenshots must pass without unintended geometry drift.
4. Icon-only semantics, dynamic status announcements, effective touch targets and semantic contrast pairs must pass automated checks.
5. A physical-device TalkBack acceptance record must be completed.

Any conflict between a checklist implementation and the established AquaLight design language is resolved by preserving the design and implementing accessibility without visible drift.
