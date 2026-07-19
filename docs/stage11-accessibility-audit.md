# Stage 11 accessibility audit contract

## Automated commercial gates

A Release Candidate is rejected when any of these checks fails:

- Every XML layout is parsed. Icon-only controls require a meaningful description or an explicit decorative exclusion.
- Every XML control with an explicit fixed interactive size must be at least 48dp in both dimensions.
- Every XML layout is inflated by the minified `releaseSmoke` build on API 27 and API 35. Visible, enabled click targets are measured at runtime and must be at least 48dp.
- Kotlin-created clickable image controls are statically rejected when they do not assign a content description.
- Kotlin-created click handling may not retain the legacy 46dp interactive size.
- UI source code may not instantiate `SimpleDateFormat`, `DecimalFormat` or use `Locale.US`; presentation formatting and localized number parsing must use `LocaleFormatters` or Android locale APIs.
- The semantic contrast contract covers every declared text/status foreground role in both base and night resource overlays and enforces WCAG AA thresholds.
- Unsupported locale resources, flag assets and hidden locale staging views are forbidden.
- Empty `values-tr`, `values-de`, `values-fr`, `values-ru` and `values-zh` directories remain translation workspaces only.

## Translation acceptance status

**OPEN — not a merge blocker while English remains the only declared and selectable production locale.**

A staged language cannot be enabled until its complete catalog is translated, placeholder-compatible, linguistically reviewed and added to both `SupportedLocaleRegistry` and `locales_config.xml`.

## Physical TalkBack acceptance status

**OPEN — mandatory Release Candidate device test.**

Automated semantics do not replace the physical TalkBack pass documented in `stage11-localization-accessibility-contract.md`. The PR remains Draft until the physical test evidence is recorded and every blocker/high-severity TalkBack defect is closed.
