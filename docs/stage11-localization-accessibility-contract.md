# Stage 11 — Localization and Accessibility Contract

## Production locale policy

- English (`en`) is the only production-enabled locale until a complete reviewed translation catalog exists.
- Turkish, German, French, Russian and Chinese resource folders are translation staging areas only.
- A staging locale must not be added to `SupportedLocaleRegistry` or `locales_config.xml` until all translatable strings and plurals are present.
- The registry and Android locale configuration must contain the same language tags.
- Legacy or unsupported saved language codes are normalized to English at startup and when settings are persisted.

## Translation activation procedure

1. Fill the target `values-xx` catalog completely; do not copy English as a fake translation.
2. Preserve every indexed format placeholder, including type (`%1$s`, `%2$d`, and similar values).
3. Run `python3 tools/localization_accessibility_guard.py`.
4. Complete product-language review and accessibility review.
5. Add the language tag to both `SupportedLocaleRegistry` and `res/xml/locales_config.xml` in the same change.
6. Run unit, lint, release-smoke and emulator CI before merging.

The guard permits empty or partially prepared staging catalogs, but it rejects placeholder mismatches. Once a locale is enabled, the same guard requires complete base-resource coverage.

## Locale-aware presentation

User-facing date, time, integer, decimal and percentage formatting must use `LocaleFormatters` or an equivalent Android locale-aware API. Protocol values, persisted machine values and cryptographic payloads must remain locale-neutral.

The Stage 11 migration removes fixed English/US formatting from the usage and aquarium summary surfaces while preserving storage and device-protocol formats.

## Automated accessibility gates

The following checks run in Android, Release and CodeQL CI paths:

- locale registry and `locales_config.xml` parity;
- translation placeholder compatibility;
- incomplete enabled-locale rejection;
- translation staging-folder presence;
- icon-only control content-description checks, including runtime-managed header actions;
- explicit icon touch targets below 48dp rejection;
- dynamic Online/Offline accessibility binding checks;
- critical WCAG contrast-pair checks for light and dark semantic colors.

The minified release-smoke matrix runs on API 27 and API 35 and captures four main screens for each variant:

- Light theme;
- Dark theme;
- 200% font scale;
- RTL layout direction.

Each variant must produce four non-empty screenshots. Visible icon-only controls are checked for non-empty accessibility descriptions before capture.

## Dynamic device status contract

- Online and Offline state is always available as text, not color alone.
- Device cards expose a complete accessible summary containing name, serial and presence state.
- Presence icons use a polite accessibility live region.
- A status update uses the localized `accessibility_device_status_changed` announcement.

## Touch target contract

- New icon-only controls must expose a minimum 48dp interactive target.
- Decorative icons must use `importantForAccessibility="no"` and no spoken duplicate label.
- A visible interactive icon must never depend on a drawable name or visual color for meaning.

## Required manual TalkBack release pass

Automated semantic checks reduce regressions but do not replace a real screen-reader session. Before the Release Candidate is approved, perform this pass on at least one physical Android device with TalkBack enabled:

1. Launch and sign in using swipe navigation only.
2. Traverse Aquarium, Maintenance, Devices and Settings in logical reading order.
3. Confirm every icon-only action announces its purpose and state.
4. Confirm Online/Offline changes are announced without moving focus unexpectedly.
5. Complete QR/device permission entry points and verify denial/settings actions are understandable.
6. Change language and theme, then verify focus is retained or restored predictably.
7. Repeat the core navigation at 200% font scale and confirm no essential action is clipped or unreachable.
8. Record device model, Android version, build SHA, tester, date and defects in the Release Candidate evidence.

A Release Candidate must not be marked commercially ready while any blocker/high TalkBack defect remains open.
