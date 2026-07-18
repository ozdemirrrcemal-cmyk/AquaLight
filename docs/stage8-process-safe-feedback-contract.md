# Stage 8 — Process-safe sheets and feedback contract

## Commercial decision

This stage is required before release. Android may recreate a `Fragment` or `DialogFragment`
after rotation, configuration change, background process eviction, permission changes, or task
restoration. Constructor parameters, callback properties, captured `Context` values, and raw
`BottomSheetDialog` instances cannot be reconstructed reliably by the framework.

## Main-branch audit

### Already compliant

- `CapabilityPermissionBottomSheet` uses arguments and Fragment Result.
- `PhotoSourceBottomSheet` uses arguments and Fragment Result.
- `CountryPickerBottomSheet` uses arguments and Fragment Result.
- `CareTaskTypeBottomSheetFragment` uses arguments and Fragment Result.
- `NotificationsBottomSheet` is not present on current `main`; the old checklist item is obsolete.

### Migrated in the first Stage 8 batch

- `ThemeBottomSheet` no longer stores callback fields. It publishes the selected mode through
  Fragment Result and has a duplicate-safe `show` entry point.
- Device removal confirmation now uses the shared `FeedbackBottomSheet`.
- The device-specific callback-based confirm wrapper and tone enum were removed.
- Instrumentation checks assert no-argument construction and recreatable argument bundles.
- `process_safe_feedback_guard.py` prevents new callback-based sheets, raw sheet dialogs,
  direct Snackbar creation, and additional Toast debt.

### Remaining migration debt

- `SettingsContentBottomSheet` and the tank settings helper sheets still build raw dialogs and
  inject callback-bound views. They must be replaced by one Fragment-based action sheet family.
- `DialogManager` must be folded into the shared info/warning/error contract.
- Existing Toast calls are temporarily allowlisted and must be migrated before Stage 8 closes.
- Global loading is centralized by owner key, but it is still rendered as an Activity-owned
  `Dialog`; rendering must move to a lifecycle-safe overlay driven by observable UI state.
- Programmatic spacing and typography values in `CareTaskTypeBottomSheetFragment` and the
  Snackbar implementation must move to resources.
- Full rotation and process-death scenario tests must be added after the remaining raw dialogs
  are converted.

## Mandatory rules

1. Every `DialogFragment` and `BottomSheetDialogFragment` has a public no-argument constructor.
2. Reconstructable input belongs in `arguments`; pending workflow state belongs in a ViewModel,
   `SavedStateHandle`, or another durable owner.
3. A sheet returns user actions only with Fragment Result or a `SavedStateHandle` result.
4. A sheet never stores a Fragment, Activity, View, Context, lambda callback, repository, or
   ViewModel in constructor parameters or mutable callback fields.
5. Confirmation, warning, error, and informational decisions use the shared feedback sheet.
6. Snackbar creation remains behind one renderer boundary. Toast is reserved for platform-level
   events where no screen anchor exists; it is not a normal feature feedback mechanism.
7. Loading is state, not an imperative dialog lifetime. The screen or Activity observes owners
   and renders one common blocking overlay.
8. User-facing dimensions, text sizes, radii, margins, colors, and copy come from resources.
9. Duplicate sheets use stable tags and refuse a second instance while the first is restored or
   visible.
10. Every new sheet ships with recreation coverage and is included in the architecture guard.

## Feedback channel selection

- **Inline field error:** validation that the user can fix in the current form.
- **Snackbar:** non-blocking operation result with an available screen anchor.
- **Feedback bottom sheet:** destructive confirmation, important warning, recoverable blocking
  error, or decision that requires an explicit user action.
- **Dialog:** reserved for platform-owned or accessibility-critical cases that cannot use the
  shared sheet.
- **Toast:** platform-level notification only when the app has no suitable visual owner.

## Stage completion gate

Stage 8 is complete only when no raw feature-owned dialog/sheet is callback-bound, the common
loading overlay survives rotation without leaking a window, all remaining Toast debt is removed
or explicitly justified, programmatic visual constants are resource-backed, and emulator tests
cover rotation plus process recreation for the shared sheet flows.
