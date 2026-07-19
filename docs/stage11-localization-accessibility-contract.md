# Stage 11 — Localization and Accessibility Commercial Contract

## Scope decision

Stage 11 is a commercial release gate. The product must not advertise a language until every translatable resource for that language has been completed, reviewed and verified.

The current application has a complete English default resource set in `res/values`. English is therefore the only supported locale for this stage. Duplicating the same strings into `values-en` would create two English sources of truth and allow drift, so the default `values` directory remains the authoritative English pack.

The previously visible Turkish, German, French, Russian and Chinese choices did not have complete translation packs. They remain in the existing layout for future activation, but are hidden at runtime and cannot be persisted as supported choices. Existing stored selections from those legacy choices migrate safely to English.

## Implemented release controls

- `SupportedLocaleRegistry` is the single source of truth for commercially supported locales.
- Android `localeConfig` and the registry must contain the same locale set.
- A future locale cannot pass CI without a complete `values-xx` or BCP-47 resource directory.
- String, plural and string-array placeholder signatures must match the English source.
- User-visible number, decimal, percentage, date, time and date-time formatting has a locale-aware boundary.
- Fixed English and fixed US formatting in audited aquarium and usage surfaces has been removed.
- Legacy untranslated language selections are migrated to English instead of crashing or displaying a mixed-language UI.
- Visible icon-only controls in release smoke screens must expose a non-empty content description.
- Online and Offline device states must remain present in icon and row accessibility descriptions.
- Clickable controls smaller than 48dp receive a 48dp `TouchDelegate` hit area without changing rendered dimensions, padding, color, shape or spacing.
- Release smoke captures Light, Dark, 200% font Light/Dark and forced RTL Light/Dark screenshots.
- Load-bearing semantic text/background pairs are checked against WCAG AA 4.5:1 contrast.
- The localization/accessibility guard runs in Debug CI, Release CI and CodeQL validation.

## Visual invariant

Stage 11 must not modify product layout dimensions, component styles, color resources, drawable resources or typography tokens merely to satisfy touch-target requirements. The minimum target is implemented as an invisible interaction-area expansion. Screenshot artifacts are the regression evidence for layout preservation.

## Adding a language later

A language may be enabled only after all of the following are true:

1. Add the complete locale resource directory.
2. Preserve every formatting placeholder and plural contract.
3. Add the language tag to `SupportedLocaleRegistry`.
4. Add the same tag to `res/xml/locales_config.xml`.
5. Pass unit, lint, localization guard and all visual smoke profiles.
6. Complete native-language copy review and manual TalkBack review.

Partial packs, machine-generated placeholders and mixed English/localized screens are release blockers.

## Manual pre-release evidence

Automation supplements but does not replace assistive-technology review. Before store publication, QA must complete these flows with Android TalkBack enabled:

- Sign-in and account recovery.
- Aquarium creation and aquarium detail navigation.
- Device QR/Nearby setup, Online/Offline state and device removal.
- Maintenance task creation, completion and deletion.
- Settings, language and theme navigation.
- Permission denial, repeated denial and settings redirection.

For each flow, record focus order, spoken label, role/state announcement, error announcement, keyboard/switch reachability and absence of focus traps. Any unlabeled icon, silent dynamic state, clipped 200% text, inaccessible dialog or contrast failure blocks release.
