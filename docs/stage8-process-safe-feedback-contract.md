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
- Results are returned only with Fragment Result. A sheet does not retain its caller, Activity,
  Fragment, View, repository, ViewModel or callback lambda.
- `FeedbackBottomSheet` is the common confirm, information, success, warning and error contract.
- `GlobalActionBottomSheet` is the common multi-action/detail contract.
- `SingleChoiceBottomSheet`, `TextInputBottomSheet`, `TankSettingsEditorBottomSheet` and
  `CareProfileBottomSheet` cover the remaining reusable sheet forms.
- `AppDatePickerDialogFragment` and `AppTimePickerDialogFragment` own all date/time picker flows.
- `DialogManager` is a compatibility façade for information feedback only and delegates to the
  process-safe feedback sheet. Callback and confirm APIs were removed.
- `LoadingOverlayDialogFragment` is owned by FragmentManager. `BaseActivity` stores loading owner
  keys in saved state and renders exactly one restored overlay.
- Snackbar creation remains behind `BaseActivity`; feature-owned Toast usage was removed.
- Programmatic Stage 8 dimensions are backed by resources.

## Migrated feature flows

The following flows now use reconstructable arguments and Fragment Result contracts:

- theme selection and permission sheets;
- device removal and multi-device deletion;
- aquarium deletion, duplication and missing-record warnings;
- tank basic settings editors, care-profile actions and livestock forms;
- maintenance history actions, task completion/deletion, water-change percentage, aquarium
  selection, due date and due time;
- completed tank activity actions and date changes;
- custom material creation;
- logout, account deletion, re-authentication, password reset and email verification feedback;
- device provisioning, create-tank and OTA test feedback through the shared Snackbar renderer.

`NotificationsBottomSheet` is not present in the current product tree, so the original checklist
entry for removing its constructor parameter is obsolete rather than pending.

## Mandatory rules

1. Every `DialogFragment` and `BottomSheetDialogFragment` has a public no-argument constructor.
2. Reconstructable input belongs in `arguments`; pending business state belongs in a ViewModel,
   `SavedStateHandle`, or another durable owner.
3. User actions return only with Fragment Result or a `SavedStateHandle` result.
4. A sheet never stores a Fragment, Activity, View, Context, callback lambda, repository or
   ViewModel in constructor parameters or mutable fields.
5. Confirmation, warning, error, success and informational decisions use the shared feedback
   contract; action lists use the global action contract.
6. Snackbar creation remains behind one renderer boundary. Toast is not a feature feedback
   mechanism.
7. Loading is owner state rendered by one FragmentManager-owned overlay, not an Activity-owned
   imperative `Dialog` lifetime.
8. User-facing dimensions, text sizes, radii, margins and copy come from resources.
9. Duplicate sheets use stable tags and reject a second instance while the first is visible or
   being restored.
10. Every new sheet is added to the recreation instrumentation suite and the architecture guard.

## Feedback channel selection

- **Inline field error:** validation the user can correct in the current form.
- **Snackbar:** non-blocking operation result with an available screen anchor.
- **Feedback bottom sheet:** destructive confirmation, important warning, recoverable blocking
  error, success requiring acknowledgement, or a decision requiring explicit action.
- **Global action sheet:** multiple actions plus contextual details.
- **Platform dialog fragment:** date/time or another platform-owned picker.
- **Toast:** prohibited for product feature feedback.

## Automated gates

`process_safe_feedback_guard.py` fails CI when production code introduces any of the following:

- callback fields or non-empty constructors on Fragment dialogs;
- raw feature-owned `BottomSheetDialog`, `AlertDialog`, `DatePickerDialog`, or `TimePickerDialog`;
- direct Toast creation;
- Snackbar construction outside the central renderer;
- callback-based `DialogManager` APIs;
- removal of a required Stage 8 component, test host, or instrumentation contract;
- reintroduction of deleted legacy sheet classes;
- hard-coded programmatic dimensions in the Stage 8 renderer components.

Instrumentation verifies no-argument reconstruction, argument Bundle recreation, Parcel
round-trip across a simulated process boundary, and real Activity recreation with a visible
feedback sheet. The API 27 and API 35 emulator jobs execute these tests.

## Completion gate

Stage 8 is complete when architecture guard, lint, unit tests, debug build, CodeQL, API 27
instrumentation, API 35 instrumentation and minified release-smoke validation all pass on the
same branch head. The branch remains draft until that gate is green.

The validation result must belong to the final connector-authored branch head; intermediate patch
staging commits and automation-authored assembly commits are not accepted as the release gate.
