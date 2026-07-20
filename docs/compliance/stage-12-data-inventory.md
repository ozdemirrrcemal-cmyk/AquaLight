# AquaLight Stage 12 — Personal Data Inventory and Compliance Baseline

**Document status:** Engineering baseline; legal validation required before commercial release  
**Baseline date:** 2026-07-21  
**Baseline branch:** `agent/stage-12-firebase-privacy-legal`  
**Baseline source commit:** `ce6800a68e3ef838dab7deb4c6ba03690740d591`  
**Scope owner:** AquaLight product and engineering  
**Legal owner:** To be assigned before release

## 1. Purpose

This document is the single engineering source of truth for Stage 12 privacy, Firebase, telemetry, data-retention, account-deletion, and Google Play declarations.

It records:

- personal and device-related data observed in the current Android implementation;
- where each data category originates, is processed, and is stored;
- whether processing is local, remote, optional, or required for the requested service;
- current deletion and retention behavior;
- third-party services and SDKs that may process data;
- unresolved legal, infrastructure, and product decisions that block commercial release.

This document is not a legal opinion. Candidate legal bases and compliance conclusions must be reviewed against the final company structure, target markets, Firebase configuration, contracts, and production behavior.

## 2. Stage 12 governance rules

1. The shipped application, Firebase configuration, legal notices, Google Play Data safety declarations, support procedures, and account-deletion behavior must describe the same system.
2. A dependency being present does not by itself prove intentional product use. Automatic SDK collection must still be treated as a potential data flow until explicitly disabled or removed.
3. Android permissions do not by themselves prove that data is stored or transferred. Collection, storage, transmission, and purpose are recorded separately.
4. No personal data field may be added to remote diagnostics without updating this inventory, the telemetry allowlist, tests, and the applicable notice.
5. Unknown retention periods are release blockers. Arbitrary periods must not be inserted merely to complete a document.
6. Privacy notice presentation is not treated as consent. Optional diagnostics consent, where used, must remain separate from account creation and core service access.
7. Account deletion is complete only when both authoritative cloud data and owner-scoped local data are deleted or a clearly disclosed lawful retention rule applies.
8. Every Stage 12 checklist item is implemented and reviewed in a separate commit.

## 3. Confirmed product decisions

The following product decisions are approved for the current Stage 12 scope:

- The login and registration layouts will not receive a legal checkbox, age declaration, acceptance sentence, or permanent Privacy/Terms link.
- AquaLight will not request an “I am over 18” declaration by default.
- The public Google Play listing will contain the Privacy Policy URL and accurate Data safety declarations.
- Privacy Policy and Terms of Use will remain continuously accessible inside the authenticated application under Settings / About / Legal.
- The adequacy of the selected notice timing for KVKK and any target-market rules remains an explicit external legal-review gate.
- Firebase Analytics will not be part of the approved production architecture.
- Local usage counters are a device-local usage summary, not remote analytics.
- Crashlytics and Performance Monitoring may remain only as optional diagnostics, default off, user-controlled, data-minimised, and independent from account creation.
- Firebase Authentication, Cloud Firestore, and Cloud Storage remain candidate required services, subject to the service-architecture and regional review in later Stage 12 items.

## 4. System boundaries

### 4.1 In scope

- AquaLight Android application package `com.aqua.aqualight`;
- Firebase Authentication;
- Cloud Firestore;
- Cloud Storage for Firebase;
- Firebase Crashlytics and Firebase Performance Monitoring;
- device provisioning through camera/QR, Bluetooth Low Energy, Wi-Fi, and local networking;
- owner-scoped device registration and assignment;
- aquarium, tank, care, reminder, and local profile data;
- feedback messages and optional feedback screenshots;
- local media, preferences, caches, diagnostics preferences, and pending-operation journals;
- account deletion and support/deletion request handling;
- Google Play Privacy Policy and Data safety declarations.

### 4.2 Outside the current baseline

The following require separate confirmation before they can be treated as implemented production controls:

- Firebase Console resource locations;
- Firebase project member and IAM review;
- a production Cloud Functions or equivalent trusted backend deployment;
- public Privacy Policy hosting;
- public account-deletion request hosting;
- support mailbox and data-subject request workflow;
- company/legal-entity details;
- executed Google/Firebase contractual and international-transfer documentation;
- Play Console declarations;
- final retention schedule approved by the legal owner;
- legal review for Türkiye, the EEA, the United Kingdom, or any other target market.

## 5. Data inventory

### Status legend

- **Observed:** Confirmed in the Android repository or Firebase rules.
- **Potential:** An SDK/dependency or permission can create the flow, but the production behavior is not yet explicitly controlled or verified.
- **Required validation:** The value exists outside the repository or requires legal/business confirmation.
- **Blocked:** A known technical defect or missing control prevents a reliable commercial claim.

| ID | Data category | Representative fields | Source / trigger | Processing and storage | Service purpose | Required or optional | Current retention / deletion | Third party or transfer | Baseline status and action |
|---|---|---|---|---|---|---|---|---|---|
| DI-01 | Authentication identifiers | Firebase UID, email address, authentication provider, authentication state | Email registration/login or Google sign-in | Firebase Authentication; current-user projection and limited cache may exist locally | Create and secure an account, authenticate requests, isolate owners | Required for account-based cloud features | Active until account deletion; authoritative deletion flow must be verified | Google Firebase; processing location and transfer mechanism require validation | **Observed.** Keep in inventory; document region/processor details and verify authoritative deletion |
| DI-02 | Local user profile and contact data | Username, display/photo URL, full name, first/last name, city, address, postcode, phone, country | User profile/address entry and authenticated session | Proto DataStore in app-private storage; per-user cache and active-session projection | Display and manage the local profile and optional contact/address details | Mostly optional; UID/session fields required | Local owner-scoped cleanup exists but complete coverage must be tested | No remote profile sync confirmed by this baseline; photo URL origin requires verification | **Observed.** Minimise optional fields, verify media origin, test account switch and deletion |
| DI-03 | App preferences and security settings | Theme, language, login-alert flag, 2FA flag, auto-update flag | User settings | Proto DataStore in app-private storage | User-selected application behavior | Optional except operational defaults | Cleared or reset according to logout/account-deletion policy; cross-user leakage must be prevented | No remote transfer confirmed | **Observed.** Verify whether displayed security settings are actually enforced and accurately described |
| DI-04 | Local usage summary | Weekly/today automation counts, alert count, manual-action count, last event time/description, day/week keys | Local user actions and app events | Proto DataStore in app-private storage | Show a personal on-device activity summary | Optional local feature | Must be owner-scoped and removed on account deletion; normal period rollover applies | No remote transfer observed | **Observed.** Rename and describe as local-only; do not present as anonymous remote analytics |
| DI-05 | Device identity and ownership | Device serial/identifier, owner UID, setup/registration state, ownership record | QR/BLE scan, registration, reset/reconfiguration | Local repositories and Firebase-backed ownership records; exact collection paths to be documented | Register AquaLight hardware, enforce owner isolation, prevent duplicate ownership | Required for connected-device service | Keep while device is owned; release ownership on device/account deletion according to verified workflow | Firebase/Google for remote ownership records | **Observed.** Map exact Firestore collections, fields, indexes, and deletion paths in the security phase |
| DI-06 | Device-to-tank assignment | Device identifier, owner UID, tank identifier/reference, assignment state | User assigns/removes a device | Owner-scoped local stores and any confirmed remote assignment store | Associate a device with the intended aquarium/tank | Required only when assignment feature is used | Remove on device deletion, tank deletion, reassignment, and account deletion | Remote transfer only if confirmed by repository mapping | **Observed.** Verify local/remote source of truth and prevent ghost or cross-owner assignments |
| DI-07 | Provisioning and network data | Nearby device metadata, BLE addresses where exposed, Wi-Fi SSID, Wi-Fi credentials, provisioning payload, connection state | Device setup and reconfiguration | Transient memory and approved protected local stores only; credentials must not enter logs, feedback, analytics, or diagnostics | Connect the physical device to the user-selected network | Required during setup | Retain only what is operationally necessary; securely remove on device/account deletion and failed provisioning cleanup | Device/local network; any cloud transmission is prohibited unless separately designed and disclosed | **Observed/Potential.** Complete field-level code review, storage-security review, and log redaction before release |
| DI-08 | Aquarium and tank records | Names, volume, dates, notes, measurements, settings and related local identifiers | User creates or edits aquarium/tank data | Owner-scoped local DataStore/repositories observed | Provide aquarium management functions | Optional feature data | Retain until user deletes the item or account; clear on owner change/account deletion | No remote storage confirmed in the current baseline | **Observed.** Legal documents must not claim remote storage unless a real remote flow is added |
| DI-09 | Care tasks, reminders and schedules | Task names/descriptions, dates/times, completion state, notification schedule | User configures care activities | Local storage, Android alarm/notification scheduling | Deliver aquarium care reminders | Optional | Remove when task/account is deleted; cancel alarms and boot-restored schedules | Android OS components; no remote transfer confirmed | **Observed.** Verify cancellation and owner isolation after logout, account switch, and deletion |
| DI-10 | User-selected aquarium/profile media | Local image content, app-private file path/URI, crop output, metadata retained by the app | User selects or captures media and confirms use | App-private local media/cache unless a specific remote flow is confirmed | Display user-selected imagery in the application | Optional | Delete replacements, failed-operation leftovers, orphan files, and all owner media on account deletion | Image loading/cropping libraries; remote source possible for a profile URL and must be mapped | **Observed/Potential.** Confirm every upload destination and prohibit undisclosed remote storage |
| DI-11 | Feedback content | Category, message, optional email, locale, platform, app version, status, Firebase UID | User submits feedback | Cloud Firestore feedback document | Receive, triage, and respond to support/product feedback | Optional; basic service must remain available without feedback | No approved maximum retention period yet; account-deletion cleanup currently requires repair | Google Firebase; authorised support personnel | **Observed/Blocked.** Add contextual notice, approve retention, and implement trusted deletion |
| DI-12 | Feedback screenshot | Image bytes, storage object path, download URL/reference, owner UID association | User explicitly attaches a screenshot | Cloud Storage plus reference in feedback record; local pending/rollback files may exist | Supply visual context for a feedback request | Optional and must not be preselected | No approved retention period yet; remove on rollback, expiry, feedback deletion, and account deletion | Google Firebase Storage; authorised support personnel | **Observed/Blocked.** Enforce opt-in attachment, size/type limits, orphan cleanup, and trusted deletion |
| DI-13 | Feedback operation journal | Pending entry identifier, local file/reference, retry/rollback state | Interrupted or pending feedback upload | SharedPreferences/app-private local storage | Safely complete or roll back feedback operations | Operationally required only while an operation is pending | Remove on success, rollback, expiry, logout/account deletion as appropriate | No remote transfer by the journal itself | **Observed/Blocked.** Add explicit owner-aware cleanup and tests |
| DI-14 | Crash diagnostics | Stack trace, exception type, app/OS/device version, process/thread state, SDK-generated identifiers and any approved custom keys | Application crash or recorded non-fatal error | Firebase Crashlytics when enabled; unsent reports may be held locally | Diagnose software defects and improve stability | Optional diagnostics under the approved product policy | Provider defaults and local unsent-report lifecycle require configuration and disclosure | Google Firebase Crashlytics; international processing details require validation | **Potential.** SDK is present; disable by default, delete unsent reports on refusal, and enforce a strict data allowlist |
| DI-15 | Performance diagnostics | App start, screen/network trace metrics, duration, device/app metadata and SDK-generated identifiers | SDK automatic/custom instrumentation when enabled | Firebase Performance Monitoring | Diagnose performance regressions | Optional diagnostics under the approved product policy | Provider retention and collection lifecycle require verification | Google Firebase Performance Monitoring; international processing details require validation | **Potential.** SDK is present; disable by default and review automatic network traces for URLs/parameters |
| DI-16 | Analytics events and identifiers | Automatically collected app/device events and analytics identifiers | Firebase Analytics SDK initialisation | Google Analytics for Firebase if dependency remains active | No approved AquaLight purpose | Not approved | Not applicable after removal; historical Console data, if any, requires review | Google Analytics for Firebase | **Potential/Rejected architecture.** Dependency exists and must be removed; verify Console history and Data safety impact |
| DI-17 | Network and service metadata | IP address, request time, user agent/client metadata, security and abuse signals | Any request to Firebase, hosting, update, or support endpoints | Service-provider infrastructure and operational logs | Deliver, secure, and troubleshoot the service | Inherent to remote service use | Provider/contract-specific; AquaLight-controlled copies must have an approved schedule | Google/Firebase and any future hosting/support provider | **Required validation.** Document providers, locations, contractual roles, and retention |
| DI-18 | Camera/QR scan data | Camera frames and decoded provisioning payload | User opens QR scanner and grants camera access | CameraX and barcode processing; frames should remain transient unless user explicitly captures media | Read device setup codes | Optional method; alternative setup path should be considered where applicable | Frames should not be retained; decoded payload only as required for setup | ML Kit barcode component and Android camera stack; confirm on-device model behavior for shipped dependency | **Observed.** Verify no frame capture, logging, analytics, or diagnostics leakage |
| DI-19 | Bluetooth and nearby-device scan data | Scan result, device name/advertising data, signal strength, connection state | User starts nearby scan and grants required permission | Android BLE stack and transient application state; selected device data may enter the device repository | Discover and connect AquaLight hardware | Optional method but required for BLE setup | Non-selected scan results should be transient; selected device record follows DI-05 | Android platform and physical device | **Observed.** Verify API-level permission behavior and prohibit location inference use |
| DI-20 | Location-related permission result and Wi-Fi context | Fine-location permission state on API 27–30, connected Wi-Fi SSID where needed | User starts setup on relevant Android versions | Android permission/system APIs; business storage not yet confirmed | Enable legacy BLE/Wi-Fi setup behavior | Conditional by Android version and setup path | Permission state managed by Android; do not retain precise location because AquaLight has no approved location purpose | Android platform | **Observed.** Privacy text must distinguish permission requirement from collection of precise location |
| DI-21 | Notifications and exact-alarm state | Notification permission state, reminder identifiers/schedules, boot-restore state | User enables reminders and Android grants permissions | Local application/Android scheduling | Deliver user-requested care reminders | Optional | Remove/cancel with task/account deletion; no marketing-notification purpose approved | Android platform | **Observed.** Do not claim promotional messaging and do not conflate with Firebase Cloud Messaging |
| DI-22 | Update and network-security metadata | App version, manifest/key identifier, update request metadata | App/update checks and normal network operations | Local BuildConfig plus remote update endpoint behavior where configured | Secure application/device update workflow | Operational | Endpoint logs and retention require mapping if enabled in production | Future/current update hosting provider | **Potential.** Map production endpoints and logs; never include account/device secrets in update telemetry |
| DI-23 | Account-deletion operation data | UID, deletion step/status, retry/error state, minimal audit reference | User requests account deletion | Android coordinator, trusted backend to be implemented, Firebase services, minimal local state | Execute and evidence account/data deletion safely | Required user-rights function | Operation state only as long as necessary; any audit record must be minimal, protected, and separately scheduled | Firebase/Google and future trusted backend | **Blocked.** Current client-side feedback deletion conflicts with deployed rule posture; implement authoritative idempotent deletion |
| DI-24 | Support and rights-request records | Requester contact, account identifier, request type, verification evidence, response history | User contacts support or uses external deletion/privacy request page | Future support mailbox/ticketing/hosting system | Respond to account deletion, access, correction, objection, and privacy requests | Optional unless user submits a request | Retention schedule and legal hold procedure require legal approval | Future support/hosting provider | **Required validation.** Select provider, define access controls, verification, SLA, retention, and processor terms |

## 6. Current Firebase and SDK baseline

### 6.1 Firebase dependencies present in the Android module

The current Android build declares:

- Firebase Analytics;
- Firebase Authentication;
- Cloud Firestore;
- Realtime Database;
- Cloud Storage;
- Firebase Cloud Messaging;
- Remote Config;
- Firebase Performance Monitoring;
- Firebase Crashlytics.

Code review performed for this baseline found intentional product flows for Authentication, Firestore, and Storage. No intentional AquaLight feature use was identified for Analytics, Realtime Database, Cloud Messaging, or Remote Config. Crashlytics and Performance are present but are not yet controlled by the approved default-off diagnostics policy.

Later Stage 12 commits will remove unused modules and configure retained diagnostics explicitly. This baseline commit does not alter runtime behavior.

### 6.2 Non-Firebase libraries relevant to the inventory

The application also uses libraries including CameraX, ML Kit barcode scanning, OkHttp, Coil, uCrop, WorkManager, AndroidX, and Material components. A library is not automatically a data processor. It becomes relevant to a disclosure when it receives, stores, or transmits personal data in the shipped configuration.

The following must be checked during implementation:

- whether the selected ML Kit barcode dependency performs all barcode processing on-device in the shipped build;
- which URLs Coil and OkHttp contact and whether request/response logging exists;
- whether image metadata is retained after crop/copy operations;
- whether WorkManager inputs or logs contain personal data;
- whether release builds expose sensitive values through logs, traces, exceptions, or backups.

## 7. Android permission-to-purpose register

| Permission/capability | Approved purpose | Explicit non-purpose | Required control |
|---|---|---|---|
| Internet / network state / Wi-Fi state | Authentication, approved Firebase operations, device connectivity/setup, secure update operations | Advertising profiling or undisclosed analytics | TLS-only production traffic, endpoint inventory, no secret logging |
| Fine location on API 27–30 | Legacy Android requirement for BLE scanning/connected Wi-Fi context during setup | Collection or tracking of the user’s physical location | Version-aware explanation, request only at point of use, no location storage |
| Camera | Scan AquaLight provisioning QR codes; user-confirmed media action where separately presented | Background capture or diagnostics attachment | Point-of-use permission, transient frames, no automatic upload |
| Bluetooth scan/connect | Discover and connect AquaLight hardware | General nearby-device tracking | Point-of-use permission, transient non-selected results, owner-safe selected record |
| Notifications | Deliver user-requested care reminders and operational notices | Marketing messages | Separate runtime permission, user controls, no FCM dependency without an approved purpose |
| Exact alarm / boot completed | Restore and deliver precise user-created reminders | Continuous background monitoring | Restore only valid owner-scoped schedules and cancel deleted-account tasks |
| FileProvider/media access | Securely share or crop user-selected app media | Broad filesystem access | Narrow URI grants, app-private files, cleanup and revocation |

## 8. Preliminary purpose and legal-basis worksheet

The following entries are drafting inputs only. They must not be copied directly into a legal notice without legal-owner approval.

| Processing group | Candidate purpose | Candidate legal analysis | Required validation |
|---|---|---|---|
| Account authentication and owner isolation | Create an account, secure access, associate cloud records with the correct user | May be necessary to establish/perform the requested account-based service and to protect service security | Legal entity, terms model, target markets, exact Firebase fields and transfers |
| Device registration and provisioning | Connect and manage the user’s AquaLight hardware | May be necessary to perform the connected-device service requested by the user | Credential handling, device ownership model, cloud fields, security controls |
| Local aquarium, care and preference data | Provide user-requested local application functions | Primarily local processing; applicable legal analysis depends on controller access and any later remote sync | Confirm no undisclosed remote backup/sync and final deletion behavior |
| Feedback and optional screenshot | Receive and resolve user-initiated support/product feedback | May rely on handling the user’s request; optional email/screenshot and future secondary uses require separate analysis | Retention, recipients, support access, content warning, account-deletion treatment |
| Optional crash and performance diagnostics | Diagnose stability and performance defects | Approved product policy requires a separate, revocable, default-off user choice; final legal characterisation requires counsel | Exact SDK data, provider terms, region/transfer, retention, withdrawal behavior |
| Security and abuse prevention metadata | Protect accounts, infrastructure, devices, and users | May be necessary for legitimate security interests and legal obligations depending on jurisdiction | Necessity/proportionality, fields, retention, access and incident process |
| Rights and deletion requests | Verify and fulfil privacy/account requests | May be necessary for legal obligations and request defence | Verification method, minimum audit data, retention, support provider |

## 9. Retention and deletion baseline

No final retention periods are approved in this baseline.

### 9.1 Current technical position

- Owner-scoped local cleanup covers substantial application data, but complete cleanup must include all local media, caches, pending jobs, feedback journals, alarms, runtime scopes, credentials, and diagnostics state.
- Authentication deletion alone does not prove Firestore or Storage deletion.
- Current feedback cleanup attempts client-side query/delete operations that are incompatible with the restrictive rule posture identified during review. This is a known account-deletion blocker.
- Feedback documents and screenshots do not yet have an approved automatic expiry policy.
- Crashlytics and Performance provider retention and local unsent-report behavior require explicit configuration and documentation.
- Google Play requires an in-app account-deletion path and an external web resource for apps that support account creation; the external resource is not yet implemented.

### 9.2 Required deletion outcomes

For each owner, a verified account-deletion operation must address:

1. Firebase Authentication identity;
2. every owner-scoped Firestore document and nested/related record;
3. every owner-scoped Storage object, including feedback screenshots;
4. device ownership and assignment release;
5. local profiles, aquariums, tanks, care data, preferences, usage summary and media;
6. provisioning credentials and device runtime state;
7. pending feedback journal entries and orphan files;
8. alarms, notifications, WorkManager tasks, sockets, coroutines, caches and session state;
9. optional diagnostics state and unsent crash reports where applicable;
10. any support/deletion record that has no approved continuing retention basis.

The operation must be owner-safe, idempotent, retryable, observable without leaking sensitive data, and unable to delete another user’s records.

## 10. Preliminary Google Play Data safety mapping

This table is a preparation aid, not a completed Play Console declaration.

| Play data family | AquaLight candidate data | Collection status | Shared/processed by service provider | Required declaration work |
|---|---|---|---|---|
| Personal info | Email, optional name/contact/address, optional feedback email | Authentication email is remotely collected; other fields depend on actual storage path | Firebase and any future support provider as applicable | Confirm each field, required/optional status, purpose, retention and deletion |
| App activity | Local usage summary; potential automatic analytics/performance activity | Local summary is not remotely collected; Analytics dependency is currently a risk; Performance is optional by policy | Google only for enabled retained SDKs | Remove Analytics, verify Performance payloads, distinguish local-only data |
| App info and performance | Crash and performance diagnostics | Potential until explicitly default-off; optional after control implementation | Firebase Crashlytics/Performance | Record consent state, purposes, data fields, retention and deletion behavior |
| Device or other identifiers | Firebase SDK identifiers, device serial/identifier, BLE metadata | Varies by flow | Firebase and physical AquaLight device as applicable | Separate hardware identity from advertising/analytics identifiers and map exact transmission |
| Photos and videos | Optional aquarium/profile media and feedback screenshot | Local media varies; feedback screenshot is remote only on explicit attachment | Firebase Storage for feedback screenshot | Confirm every media destination, optionality, retention, deletion and access |
| Location | No approved precise-location collection purpose; legacy permission supports setup | Permission use is not automatically data collection | Android platform; no approved remote location recipient | Verify code and declare only actual collected/transmitted data, not permission name alone |
| Messages/other user-generated content | Feedback message, aquarium notes | Feedback is remote; aquarium notes currently appear local | Firebase Firestore for feedback | Define purpose, access, retention and account-deletion treatment |

A final declaration must be produced from the release build, retained SDK list, production Firebase configuration, and published notices—not from this table alone.

## 11. Security and privacy controls already visible

The current manifest baseline includes:

- cleartext traffic disabled;
- application backup disabled;
- app-private `FileProvider` with URI grants;
- non-exported main activity and reminder receiver;
- Android-version-scoped legacy Bluetooth permissions;
- `neverForLocation` on modern Bluetooth scanning;
- explicit optional hardware features for camera and BLE.

These are positive controls but do not replace:

- Firebase Security Rules and Emulator tests;
- App Check/Play Integrity;
- server-authoritative deletion;
- diagnostics default-off configuration;
- sensitive-log and trace redaction;
- processor/region/retention documentation;
- Play Console and legal-document accuracy.

## 12. Known gaps and commercial release blockers

| Blocker | Owner | Closure evidence |
|---|---|---|
| Unused Firebase modules remain in the production dependency graph | Android engineering | Dependency removal commit, release dependency report, smoke tests |
| Crashlytics and Performance are not yet governed by a default-off user preference | Android engineering | Manifest/runtime controls, preference UI, instrumentation tests, Console verification |
| Diagnostics field minimisation is not centrally enforced | Android engineering | Allowlisted wrapper, redaction tests, release log review |
| Firebase resource locations are not recorded | Firebase/project owner | Console screenshots/export recorded outside secrets, approved architecture record |
| International-transfer and processor documentation is not approved | Legal/business owner | Executed contract review and legal sign-off |
| Firestore/Storage collection and field map is incomplete | Android/Firebase engineering | Collection schema, rules mapping, Emulator tests |
| Feedback retention is undefined | Product/legal/support | Approved schedule and automatic cleanup implementation |
| Feedback cloud deletion is not authoritative | Backend/Firebase engineering | Trusted backend deletion, integration tests, owner-isolation tests |
| Full local deletion does not yet cover every journal/runtime residue | Android engineering | Idempotent cleanup implementation and destructive-path tests |
| External account-deletion resource is absent | Product/web/backend | Public HTTPS page/form and Play Console entry |
| Privacy and Terms assets do not yet match the final real data flows and supported languages | Product/legal/Android | Reviewed TR/EN documents, versioning, secure in-app viewer |
| Google Play Privacy URL and Data safety submission are not prepared from the release build | Release owner | Public URL, completed evidence matrix, Play Console review |
| Login/registration notice timing decision lacks external legal confirmation | Legal owner | Written counsel decision; product change only if counsel requires it |
| Data-controller identity, contact channel, target markets, and rights-request process are not final | Business/legal owner | Final company information and operational procedure |
| Final retention schedule and deletion-audit policy are not approved | Legal/security owner | Approved schedule, implementation tests, documented exceptions |

## 13. Evidence reviewed for this baseline

The baseline was created from the Stage 12 branch and review of, at minimum:

- `app/build.gradle` — Firebase and Android dependency surface;
- `app/src/main/AndroidManifest.xml` — permissions, exported components, backup and network posture;
- `app/src/main/proto/user_prefs.proto` — local profile, preferences and usage-summary fields;
- Firebase Authentication, Firestore and Storage repository usage found during the Stage 12 code review;
- feedback submission fields and screenshot upload flow;
- `CloudUserDataCleaner`, `AccountDeletionManager` and `UserDataCleaner` behavior;
- Firestore and Storage Security Rules;
- aquarium/tank owner-scoped local storage;
- current Privacy Policy and Terms of Use assets and their WebView presentation;
- feedback pending-operation journal and local media cleanup behavior.

Where a repository fact cannot prove production infrastructure configuration, the item is marked **Required validation** rather than assumed.

## 14. Change-control requirements

A future change must update this inventory in the same pull request when it:

- adds or removes a Firebase/third-party SDK;
- adds a remote endpoint or background transmission;
- changes a Firestore collection, Storage path, user identifier, or ownership rule;
- adds a new profile, device, aquarium, feedback, media, log, or diagnostics field;
- changes retention, deletion, backup, export, support, or access behavior;
- changes a permission purpose;
- introduces advertising, attribution, analytics, marketing notifications, or profiling;
- changes target countries or legal entities;
- changes the Privacy Policy, Terms, Google Play Data safety form, or account-deletion page.

## 15. Stage 12 baseline acceptance criteria

This item is complete when:

- the inventory exists in version control and is reviewable by engineering, product, release, and legal owners;
- verified facts and unresolved assumptions are visibly separated;
- all known local, Firebase, feedback, diagnostics, permission, media, and deletion flows are represented;
- every material unknown has an owner and a closure artifact;
- the document makes no claim of completed legal compliance;
- later Stage 12 commits can trace their implementation and tests to stable inventory IDs.

The next Stage 12 item is the approved Firebase service architecture record. It will classify each currently declared Firebase module as required, optional diagnostics, or prohibited/unused before dependency removal changes runtime code.