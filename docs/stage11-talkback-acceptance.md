# Stage 11 — Physical TalkBack Acceptance

## Test configuration

- Install the Stage 11 APK as a clean installation.
- Use a physical Android device with TalkBack enabled.
- Test with English, the only currently published locale.
- Run once in Light and once in Dark mode.
- Repeat the marked layout checks at 200% system font size.
- Record device model, Android version, APK commit and result for every scenario.

A scenario passes only when every required action can be completed without sight, focus does not become trapped, and no control is announced with an ambiguous label such as “button”, “image” or “unlabelled”.

## Acceptance scenarios

### Authentication

- Launch AquaLight and move through the sign-in screen in reading order.
- Verify email and password fields announce their purpose and current error state.
- Verify Sign in, Google sign-in, registration, password reset and Back actions are distinct.
- Submit an empty/invalid form and confirm the error is discoverable without searching visually.

### Bottom navigation

- Move through Aquarium, Maintenance, Devices and Settings.
- Confirm every destination announces its label and selected state.
- Confirm one action produces one focus stop; icon and text must not be read separately.

### Aquarium creation and detail

- Start aquarium creation and navigate each form field.
- Verify required fields, selected options and validation errors are announced.
- Select a photo source, open and dismiss the crop flow.
- Open a saved aquarium and navigate summary, life, plants, devices, activity and settings.
- At 200% font size, confirm content remains reachable by scrolling and no essential action disappears.

### Maintenance

- Open the maintenance list and distinguish date headers from task cards.
- Add a task, choose aquarium and task type, set a date and save.
- Open task detail, complete/reopen/delete the task and confirm confirmation sheets are announced.
- Confirm completed, overdue, today and upcoming states are conveyed by words, not color alone.

### Devices and provisioning

- Open Devices and navigate each device card.
- Confirm one device card announces name, serial/UID, aquarium assignment when present and Online/Offline state.
- Change the physical device between Online and Offline and confirm the state is discoverable without reading the colored dot.
- Open Add device, QR and manual BLE paths.
- Verify permission rationale, denied state, settings action, Wi-Fi form and provisioning progress are announced in logical order.
- Confirm Back, flashlight, scan-again and close icon actions have explicit labels.

### Settings, theme and language

- Open App settings and navigate notification, auto-update, theme, language and About cards.
- Open theme selection and confirm Light, Dark and System announce selected/not-selected state.
- Open Language and confirm only published languages appear.
- Confirm the language card is one focus stop and is announced as “English language, Selected”.
- Confirm decorative flag and radio artwork do not create duplicate focus stops.

### Dialogs and bottom sheets

- Trigger an information dialog, warning confirmation, text-input sheet and single-choice sheet.
- Confirm focus moves into the surface, title is read first, actions are distinguishable and Back/dismiss returns focus sensibly.
- Confirm hidden content behind the modal cannot be activated.

## Blocking defects

Any of the following blocks Stage 11 approval:

- an actionable icon without a meaningful label,
- duplicate focus stops for one logical action,
- focus trapped or lost after navigation/modal dismissal,
- Online/Offline or selected state conveyed only by color,
- an essential control unreachable at 200% font size,
- overlapping/clipped text that removes meaning,
- a touch target that cannot be reliably activated,
- RTL or pseudolocale layout that reverses content incorrectly,
- placeholder or untranslated fallback presented as a published language.

## Result record

| Field | Value |
|---|---|
| Commit | |
| APK workflow/run | |
| Device model | |
| Android version | |
| TalkBack version | |
| Light result | Pending |
| Dark result | Pending |
| 200% result | Pending |
| Provisioning result | Pending |
| Blocking defects | None recorded |
| Tester approval | Pending |
