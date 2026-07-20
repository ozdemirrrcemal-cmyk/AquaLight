# AquaLight Stage 12 — Approved Firebase Service Architecture

**Decision type:** Architecture and compliance decision record  
**Status:** Approved for Stage 12 implementation; infrastructure and legal validation gates remain open  
**Decision date:** 2026-07-21  
**Applies to:** AquaLight Android application `com.aqua.aqualight` and its production Firebase project  
**Related baseline:** `docs/compliance/stage-12-data-inventory.md`

## 1. Purpose

This record defines which Firebase services AquaLight is permitted to use in production, the exact purpose and scope of each approved service, and which currently declared dependencies must be removed.

The goal is to prevent accidental expansion of data processing merely because an SDK is present. A Firebase product is not approved by dependency presence, Console availability, or convenience. It must have:

1. a documented product purpose;
2. a defined data boundary;
3. a lawful and disclosed processing model;
4. owner-isolation and deletion controls;
5. a Google Play Data safety mapping;
6. automated and manual verification appropriate to its risk.

This record is an engineering architecture decision, not a legal opinion. Final production activation remains subject to infrastructure verification, Play Console review, and external legal review.

## 2. Decision summary

AquaLight adopts a **minimum-service Firebase architecture**.

### Approved production services

- Firebase Authentication — required account and session identity service.
- Cloud Firestore — approved only for explicitly mapped remote records, currently the feedback transaction/document flow and trusted server-side deletion support.
- Cloud Storage for Firebase — approved only for explicitly user-selected remote objects, currently optional feedback screenshots.
- Firebase Crashlytics — optional diagnostics service, default off and user-controlled.
- Firebase Performance Monitoring — optional diagnostics service, default off and user-controlled.

### Rejected production services

- Google Analytics for Firebase.
- Firebase Realtime Database.
- Firebase Cloud Messaging.
- Firebase Remote Config.

Rejected services must be removed from Android dependencies in the dedicated implementation commit. Historical Console configuration or data, if any, must be reviewed separately and must not be treated as proof of an approved current purpose.

### Future services not yet approved as implemented

- Firebase App Check with Play Integrity — planned security control; requires a dedicated implementation, staged monitoring, and enforcement decision.
- Cloud Functions for Firebase or an equivalent trusted backend — planned for authoritative account-data deletion and retention jobs; region, IAM, authentication, retry, audit, and deployment controls are unresolved.
- Firebase Hosting — possible host for public Privacy Policy and external account-deletion resources; not currently configured in repository `firebase.json`.

## 3. Current repository evidence

The Android module currently declares Firebase BoM and nine Firebase product SDKs: Analytics, Authentication, Firestore, Realtime Database, Storage, Messaging, Remote Config, Performance, and Crashlytics.

Repository review confirms direct product use for:

- Firebase Authentication in account registration, email/password sign-in, Google-token sign-in, password reset, password change, email-change verification, session access, and deletion coordination;
- Cloud Firestore in the feedback document transaction flow and cloud-user cleanup code;
- Cloud Storage in optional feedback screenshot upload and cleanup;
- Firebase Authentication as the owner UID source for feedback records.

Repository code search found no direct application references to:

- `FirebaseAnalytics`;
- `FirebaseDatabase`;
- `FirebaseMessaging`;
- `FirebaseRemoteConfig`;
- `FirebaseCrashlytics`;
- `FirebasePerformance`.

The absence of direct Crashlytics or Performance API calls does not prove zero automatic collection while their SDKs are present. They remain potential automatic data flows until the default-off controls are implemented and verified.

The current root Firebase configuration deploys Firestore rules/indexes and Storage rules only. It does not declare Functions or Hosting deployment targets.

## 4. Service classification matrix

| Firebase service | Architecture status | Product necessity | Approved purpose | Approved data boundary | Default collection/state | Stage 12 action |
|---|---|---|---|---|---|---|
| Authentication | Approved — required | Required for account-based identity and owner isolation | Registration, login, session identity, password/email security operations, reauthentication and account deletion | UID, email, authentication provider and Firebase-managed authentication metadata only | Active when the user creates or uses an account | Keep; document providers and processing location; test reauthentication, account switching and deletion |
| Cloud Firestore | Approved — scope-limited | Required only for features whose mapped remote records use Firestore; currently feedback and deletion support, not every AquaLight domain | Owner-associated feedback transaction records; trusted deletion/retention coordination where implemented | Only collections and fields listed in the data inventory and rules contract | Active only when an approved feature performs a Firestore operation | Keep; harden rules; document exact collections/fields; add Emulator tests; move privileged deletion to trusted backend |
| Cloud Storage | Approved — scope-limited | Optional for core product; needed only when the user explicitly attaches a remote object such as a feedback screenshot | Upload, serve to authorised support, and delete optional feedback screenshots | User-selected file, owner UID path, transaction document ID and minimum storage metadata | No upload unless the user explicitly selects and submits a screenshot | Keep; enforce type/size/path/owner rules; implement retention, rollback, orphan and account-deletion cleanup |
| Crashlytics | Approved — optional diagnostics | Not required for login, device control, aquarium management or support submission | Stability diagnosis using minimised technical crash/non-fatal data | Only allowlisted technical metadata; no UID, email, serial, network credential, aquarium/tank name, feedback content or media | Must be default off; enabled only by an independent user preference | Keep temporarily; disable automatic collection; add preference, unsent-report deletion and data-minimisation gateway |
| Performance Monitoring | Approved — optional diagnostics | Not required for core product | Diagnose app-start, rendering and approved network/performance regressions | Minimum technical metrics; review automatic URL/network traces and prevent secrets or personal parameters | Must be default off; enabled only by the same independent diagnostics preference | Keep temporarily; disable automatic collection; verify no collection before opt-in and no sensitive trace attributes |
| Analytics | Rejected | No approved necessity | None | None | Must not initialise or collect | Remove dependency; verify merged manifest/dependency tree and review any historical Console data |
| Realtime Database | Rejected | No repository-backed product need | None | None | Must not initialise or connect | Remove dependency; verify no database URL/client code and no Play declaration based solely on prior presence |
| Cloud Messaging | Rejected | AquaLight reminders are local; no approved remote push purpose | None | None | No FCM token registration or message handling | Remove dependency; keep local notification/alarm architecture separate; verify merged manifest contains no FCM components attributable to the app |
| Remote Config | Rejected | No approved remote feature-flag/configuration purpose | None | None | Must not fetch or activate | Remove dependency; future remote configuration requires a new architecture and privacy decision |
| App Check | Planned, not implemented | Security hardening | Attest legitimate app requests to supported Firebase backends | App/device integrity token processing according to provider contract; no business-data expansion | Monitoring first; enforcement only after production evidence | Implement later in dedicated Stage 12 item; define debug/CI token handling and recovery plan |
| Cloud Functions / trusted backend | Planned, not implemented | Required for privileged account-data deletion and scheduled retention cleanup | Authoritative owner-scoped deletion, retention expiry, safe retries and minimal audit | Only identifiers and records required for the invoked job | No production function exists in current repository configuration | Design and deploy in account-deletion/retention items; region and IAM review required |
| Hosting | Candidate, not implemented | Public legal and deletion resources | Privacy Policy, Terms or external account-deletion page | Public legal content and minimum verified request data if a form is later introduced | No Hosting target exists in current repository configuration | Select hosting approach later; do not collect request data until provider, security, retention and verification are designed |

## 5. Detailed decisions

### 5.1 Firebase Authentication

#### Android role

Authentication is the only approved Firebase service that is unconditionally required by the current account architecture. It supplies the authenticated UID used to isolate user-owned operations and supports:

- email/password registration and sign-in;
- Google-token sign-in;
- password reset;
- password change;
- email change verification;
- provider inspection;
- reauthentication-sensitive operations;
- account deletion.

#### Data minimisation

The application must not copy authentication data into unrelated stores without a documented reason. In particular:

- the Firebase UID is an internal owner key, not a public profile identifier;
- passwords and Google ID tokens must never be logged, persisted by AquaLight, added to feedback, or attached to diagnostics;
- email must not be duplicated into Firestore documents unless a feature needs it and the inventory/notice covers it;
- provider metadata must be used only for account and security behavior.

#### Security and lifecycle controls

- Every owner-scoped repository must resolve the current authenticated owner through the central session architecture.
- Account change must cancel old-owner runtime scopes and prevent delayed writes/events from crossing into the new session.
- Sensitive account operations must require Firebase-supported recent authentication where applicable.
- Authentication account deletion must be coordinated with authoritative cloud-data deletion; the app must not report complete deletion while known remote data remains undeleted.

#### Legal and Play implications

Authentication requires clear disclosure of account identifiers, email/provider data, Google/Firebase processing, deletion rights, and any cross-border processing assessment. Google sign-in must not be described as transferring all Google account data; only the actual token/profile fields used by the implemented flow may be stated.

### 5.2 Cloud Firestore

#### Approved scope

Firestore is not approved as a general-purpose remote mirror for all AquaLight data. Current approved use is limited to:

- feedback documents and their transaction state;
- future trusted deletion/retention coordination that is explicitly designed and documented.

Aquarium, tank, care-task, local profile, preferences, and local usage-summary data must not silently migrate to Firestore. Such a change would require a new data inventory update, architecture decision, rules design, deletion mapping, Privacy update, Data safety review, migration plan and tests.

#### Current feedback fields

The current feedback implementation can write:

- category;
- optional email;
- message;
- platform;
- app version;
- locale;
- status;
- owner UID;
- server creation time;
- optional screenshot URL/path;
- media transaction state and expiry fields.

These fields are support data, not analytics events.

#### Security controls

- Security Rules must deny unauthenticated or cross-owner operations except for any deliberately supported anonymous-feedback flow, which requires its own abuse and privacy analysis.
- Client rules must never grant broad list/delete access merely to make account deletion work.
- Privileged bulk deletion, retention expiry and support administration belong in a trusted backend using least-privilege IAM.
- Emulator tests must cover owner creation, permitted updates, forbidden reads/listing, forbidden cross-owner writes, field/schema constraints and transaction-state invariants.

#### Retention and deletion

No Firestore record may be retained indefinitely by default. Feedback retention, pending-transaction expiry, closed-request deletion and legal-hold exceptions must be defined later. Account deletion must remove or lawfully retain every owner-associated record through a tested, idempotent server-side process.

### 5.3 Cloud Storage

#### Approved scope

Storage is approved only for objects the user explicitly chooses to upload in an approved feature. The current approved case is an optional feedback screenshot.

It is not approved as an automatic backup destination for:

- aquarium images;
- profile media;
- camera/QR frames;
- device logs;
- provisioning payloads;
- Wi-Fi credentials;
- crash attachments.

#### Required controls

- Object paths must be owner-scoped and transaction-bound.
- Rules must validate authenticated ownership, allowed MIME types, maximum object size and permitted path structure.
- Upload must start only after explicit user action.
- Failed transactions must delete uploaded objects or retain a recoverable journal entry until safe cleanup succeeds.
- Replaced, expired, orphaned, feedback-deleted and account-deleted objects must be removed.
- Download URLs must be treated as access-bearing data and not exposed in logs or diagnostics.

### 5.4 Crashlytics

Crashlytics is approved only as an optional commercial-quality stability tool. Its use is conditional on all of the following:

- automatic collection is disabled before production data can be sent;
- the user can enable or disable diagnostics independently from account creation, Terms, Privacy notice and core service use;
- disabling diagnostics deletes unsent local reports where supported and stops future collection/transmission according to verified SDK behavior;
- no Firebase UID is set as Crashlytics user ID;
- custom keys and non-fatal records pass through a central allowlist/redaction layer;
- release logs and breadcrumbs cannot contain credentials, tokens, device serials, user text, aquarium names, network SSIDs or feedback content;
- the Privacy Policy and Data safety form match the enabled behavior and optionality;
- the production team has a controlled process for access, triage, retention and deletion requests.

Until these controls are implemented and tested, Crashlytics presence is a release risk rather than an approved active data flow.

### 5.5 Performance Monitoring

Performance Monitoring is approved under the same optional diagnostics preference as Crashlytics, but the services remain separately documented because their automatic collection and data fields differ.

Required controls include:

- default-off collection before application startup can produce transmitted sessions;
- runtime state aligned with the persisted diagnostics preference;
- review of automatic HTTP/S traces, URL paths, query strings, headers and custom attributes;
- prohibition of account identifiers, serial numbers, user-entered values and secrets in trace names/attributes;
- verification that opt-out remains effective after process recreation, app update, logout and account switching.

### 5.6 Rejected services

#### Analytics

AquaLight has no approved behavioral advertising, attribution, audience segmentation or remote product-analytics purpose. Local usage counters remain local and must not be routed into Analytics. Analytics dependency and any generated collection components must be removed.

#### Realtime Database

No approved repository code depends on Realtime Database. Firestore cannot be described as justification for keeping a second unused database SDK. It must be removed.

#### Cloud Messaging

AquaLight care reminders are generated through the local Android reminder/alarm architecture. There is no approved remote-push, marketing-notification or Firebase-token lifecycle. Messaging must be removed. A future push feature would require a separate purpose, token inventory, server architecture, permission/UX policy, deletion behavior and Play disclosure.

#### Remote Config

There is no approved remote configuration or experimentation use. Remote Config must be removed. Future introduction requires controls for configuration ownership, safe defaults, cache behavior, emergency rollback and prohibition on using remote flags to bypass consent or legal settings.

## 6. Dependency and plugin policy

After the SDK-removal implementation, the Android module may retain only Firebase dependencies corresponding to approved services:

- Firebase BoM;
- Authentication;
- Firestore;
- Storage;
- Crashlytics;
- Performance Monitoring.

The Google Services Gradle plugin remains necessary for Firebase Android resource generation while approved Firebase services are used.

The Crashlytics Gradle plugin may remain only while Crashlytics is retained and its release symbol/mapping workflow is intentionally used. Its presence must not be interpreted as permission for automatic runtime collection.

No Firebase product may be reintroduced merely by adding a dependency. Reintroduction requires:

1. data inventory update;
2. architecture decision amendment;
3. privacy and legal review;
4. Security Rules/backend design where applicable;
5. Google Play Data safety update;
6. account-deletion and retention mapping;
7. automated tests and release verification.

## 7. Environment and build policy

### Production

- Only approved services may initialise.
- Optional diagnostics must default to off until user preference is applied.
- Production Firebase project identifiers must come from controlled build configuration.
- Service-account credentials must never be embedded in the Android application or repository.

### Debug and CI

- Firebase Emulator Suite should be used for Firestore/Storage rules and trusted-backend integration tests where possible.
- App Check debug tokens, when introduced, must be stored as secrets and never committed.
- CI must inspect dependency and merged-manifest outputs to detect rejected SDK reintroduction.
- Test fixtures must use synthetic users, messages, identifiers and files.

### Release verification

The release artifact, not only source declarations, must be checked for:

- rejected Firebase SDKs;
- generated services, receivers, providers and metadata;
- default diagnostics collection flags;
- accidental FCM token/message components;
- unexpected Analytics collection metadata;
- release symbol/mapping upload configuration;
- network behavior before and after diagnostics opt-in.

## 8. Privacy and legal controls

### 8.1 Purpose limitation

Each Firebase service is restricted to the approved purpose in this document. Convenience, troubleshooting, future possibility or Console availability is not a valid new purpose.

### 8.2 Consent and notice separation

- Privacy notice presentation is not consent.
- Terms acceptance is not diagnostics consent.
- Optional diagnostics must remain independent and revocable.
- Firestore feedback submission is an intentional user-requested operation and requires contextual disclosure, not a general hidden background-purpose claim.

### 8.3 Processor and transfer review

Before production release, AquaLight must verify and document:

- Firebase project ownership and authorised members;
- Authentication processing characteristics;
- Firestore and Storage resource locations;
- future Functions region;
- applicable Google/Firebase processing terms;
- international-transfer mechanism appropriate to the legal entity and target markets;
- subprocessors and operational access;
- service-specific retention/deletion capabilities.

No location or transfer conclusion may be inferred from Android source code.

### 8.4 Google Play consistency

The Data safety declaration must reflect the release configuration:

- Authentication account data is required for account features.
- Feedback content is user-provided and optional.
- Feedback screenshot upload is user-initiated and optional.
- Crash/performance data is optional only after the default-off implementation is proven.
- Rejected products must not be declared as active merely because they existed in an earlier dependency set.
- SDK-level provider collection must be included where the final SDK behavior requires it.

## 9. Security and operational ownership

Before commercial release, named owners must exist for:

- Firebase project administration and IAM review;
- Authentication provider configuration;
- Security Rules review and deployment;
- Storage lifecycle and orphan cleanup;
- trusted backend deployment and secrets;
- Crashlytics/Performance access and triage;
- incident response;
- data-subject/account-deletion requests;
- Privacy Policy and Data safety updates after architecture changes.

Access must follow least privilege. Shared personal accounts and undocumented permanent administrator access are not acceptable production controls.

## 10. Acceptance criteria for this architecture decision

This decision is considered recorded when:

- every currently declared Firebase product has an explicit approved, rejected or planned status;
- current direct use of Authentication, Firestore and Storage is identified;
- automatic-diagnostics uncertainty is explicitly preserved as a risk;
- optional and required services are not conflated;
- rejected services have a scheduled removal action;
- future App Check, Functions and Hosting work is not falsely described as implemented;
- legal, regional, processor and Play verification gates are listed;
- no Android runtime behavior is changed by this documentation commit.

## 11. Implementation sequence created by this decision

1. Verify Firebase processors and resource locations through Console/contract evidence.
2. Remove Analytics, Realtime Database, Cloud Messaging and Remote Config SDKs.
3. Reclassify the local usage screen as an on-device summary.
4. Define and implement optional diagnostics policy and preference.
5. Enforce diagnostics data minimisation in code.
6. Harden Firestore and Storage rules with Emulator tests.
7. Add contextual feedback processing disclosure and retention.
8. Implement trusted account deletion and scheduled cleanup.
9. Integrate App Check through a staged rollout.
10. Reconcile Privacy, Terms and Google Play Data safety declarations with the verified release artifact.

## 12. Change-control rule

Any future change to this service matrix requires a dedicated architecture amendment before implementation. The amendment must state:

- the new or changed service;
- business purpose;
- data fields and automatic collection;
- storage and region;
- access model;
- retention and deletion;
- user choice;
- Security Rules/backend implications;
- Privacy/Terms/Data safety impact;
- migration and rollback plan;
- test evidence.

Unreviewed Firebase service expansion is a commercial release blocker.