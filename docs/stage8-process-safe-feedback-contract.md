# Stage 8 — Process-safe sheets, dialogs and feedback

## Commercial decision

Stage 8 is a release requirement. Android may recreate a `Fragment` or `DialogFragment` after
rotation, configuration change, background process eviction, permission changes, or task
restoration. Constructor parameters, mutable callback properties, captured `Context` values and
feature-owned raw dialogs cannot be reconstructed safely by the framework.

## Delivered architecture

- Every app-owned sheet and dialog is a no-argument `DialogFragment` or
  `BottomSheetDialogFragment`.
- Reconstructable input is stored in `arguments`; transient UI selections that must survive a
  recreation are stored in saved instance state or in the owning ViewModel.
- Results are returned only with Fragment Result. A sheet or dialog does not retain its caller,
  Activity, Fragment, View, repository, ViewModel or callback lambda.
- `ConfirmDialogFragment` is the common screen-centered binary confirmation contract. It reuses
  the shared Aqua confirm typography, buttons, icon surface, colors, dimensions and the existing
  `ic_info`, `ic_success`, `ic_warning` and `ic_error` assets.
- `FeedbackBottomSheet` is the common information, success, warning and error acknowledgement
  contract when a bottom-sheet presentation is appropriate.
- `GlobalActionBottomSheet` is the common multi-action/detail contract.
- `SingleChoiceBottomSheet`, `TextInputBottomSheet`, `TankSettingsEditorBottomSheet` and
  `CareProfileBottomSheet` cover the remaining reusable sheet forms.
- `AppDatePickerDialogFragment` and `AppTimePickerDialogFragment` own compact framework date/time
  picker flows. `AquaTimePickerBottomSheet` owns visually prominent 24-hour wall-clock selection
  for product schedules and uses the same reconstructable argument/result contract.
- `DialogManager` is a compatibility façade for information feedback only and delegates to the
  process-safe feedback sheet. Callback and legacy confirm APIs remain removed.
- `LoadingOverlayDialogFragment` is owned by FragmentManager. `BaseActivity` stores loading owner
  keys in saved state and renders exactly one restored overlay.
- Pending loading overlays are tracked until their asynchronous Fragment transaction is attached,
  so a rapid `show` then `hide` cannot leave a stale full-screen overlay visible.
- Snackbar creation remains behind `BaseActivity`; feature-owned Toast usage was removed.
- Programmatic Stage 8 dimensions are backed by resources.

## Migrated feature flows

The following flows use reconstructable arguments and Fragment Result contracts:

- theme selection and permission sheets;
- device removal and multi-device deletion;
- aquarium deletion, duplication and missing-record warnings;
- tank basic settings editors, care-profile actions and livestock forms;
- maintenance history actions, task completion/deletion, water-change percentage, aquarium
  selection, due date and due time;
- completed tank activity actions and date changes;
- custom material creation;
- logout, account deletion, re-authentication, password reset and email verification feedback;
- device provisioning, create-tank and OTA test feedback through the shared Snackbar renderer;
- screen-centered destructive confirmations that explicitly opt into `ConfirmDialogFragment`.

`NotificationsBottomSheet` is not present in the current product tree, so the original checklist
entry for removing its constructor parameter is obsolete rather than pending.

## Mandatory rules

1. Every `DialogFragment` and `BottomSheetDialogFragment` has a public no-argument constructor.
2. Reconstructable input belongs in `arguments`; pending business state belongs in a ViewModel,
   `SavedStateHandle`, or another durable owner.
3. User actions return only with Fragment Result or a `SavedStateHandle` result.
4. A sheet or dialog never stores a Fragment, Activity, View, Context, callback lambda, repository
   or ViewModel in constructor parameters or mutable fields.
5. Binary confirmation that must interrupt the current decision uses the shared
   `ConfirmDialogFragment`; action lists use the global action sheet; acknowledgement feedback uses
   the shared feedback sheet.
6. Snackbar creation remains behind one renderer boundary. Toast is not a feature feedback
   mechanism.
7. Loading is owner state rendered by one FragmentManager-owned overlay, not an Activity-owned
   imperative `Dialog` lifetime. Pending show transactions must remain cancelable before attach.
8. User-facing dimensions, text sizes, radii, margins and copy come from resources.
9. Duplicate sheets and dialogs use stable tags and reject a second instance while the first is
   visible or being restored.
10. Every new sheet or dialog is added to the recreation instrumentation suite and the architecture
    guard.

## Feedback channel selection

- **Inline field error:** validation the user can correct in the current form.
- **Snackbar:** non-blocking operation result with an available screen anchor.
- **Confirm dialog:** focused binary confirmation, especially destructive or irreversible actions
  where leaving the current screen context is unnecessary.
- **Feedback bottom sheet:** information, warning, error or success acknowledgement where a sheet
  presentation is appropriate and no focused binary confirmation is required.
- **Global action sheet:** multiple actions plus contextual details.
- **Aqua time-picker sheet:** prominent schedule time-of-day selection in the product visual system.
- **Platform dialog fragment:** compact date/time or another platform-owned picker.
- **Toast:** prohibited for product feature feedback.

## Automated gates

`process_safe_feedback_guard.py` fails CI when production code introduces any of the following:

- callback fields or non-empty constructors on Fragment dialogs;
- raw feature-owned `BottomSheetDialog`, `AlertDialog`, `DatePickerDialog`, or `TimePickerDialog`;
- Material confirmation-dialog construction outside `ui/common/dialog`;
- direct Toast creation;
- Snackbar construction outside the central renderer;
- callback-based `DialogManager` APIs;
- removal of a required Stage 8 component, test host, or instrumentation contract;
- reintroduction of deleted legacy sheet classes;
- hard-coded programmatic dimensions in the Stage 8 renderer components;
- replacement of the central confirm dialog's shared Aqua layout binding, icons, colors or
  dimensions with feature-owned visual resources.

Instrumentation verifies no-argument reconstruction, argument Bundle recreation, Parcel
round-trip across a simulated process boundary, real Activity recreation with visible feedback and
confirm components, and rapid loading-overlay show/hide cancellation. The API 27 and API 36
emulator jobs execute these tests.

## Completion gate

Stage 8 is complete when architecture guard, lint, unit tests, debug build, CodeQL, API 27
instrumentation, API 36 instrumentation and minified release-smoke validation all pass on the
same branch head. The branch remains draft until that gate is green.

The validation result must belong to the final connector-authored branch head; intermediate patch
staging commits and automation-authored assembly commits are not accepted as the release gate.
