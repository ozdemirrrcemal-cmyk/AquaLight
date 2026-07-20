# AquaLight Stage 12 — Minimal Firebase Service Architecture

**Decision type:** Architecture, privacy and commercial-scope decision  
**Status:** Approved for implementation  
**Decision date:** 2026-07-21  
**Applies to:** AquaLight Android application `com.aqua.aqualight` and its production Firebase project  
**Related baseline:** `docs/compliance/stage-12-data-inventory.md`

## 1. Product principle

AquaLight is a connected aquarium-lighting and aquarium-management application. It is not an advertising, behavioural analytics, audience-segmentation, remote experimentation or marketing-notification product.

The production application therefore uses the smallest Firebase surface that is required by current, implemented product flows. A Firebase service is not retained merely because it may be useful later.

This decision supersedes the preliminary optional-diagnostics position recorded in the Stage 12 baseline. Crashlytics and Performance Monitoring are removed rather than placed behind a new consent/preference system. The baseline remains evidence of the pre-removal dependency state; this document is the controlling architecture decision for the release configuration.

## 2. Final service matrix

### Retained Firebase services

1. **Firebase Authentication**
   - Account registration and login.
   - Email/password and Google authentication.
   - Password and email security operations.
   - Current authenticated UID for owner isolation.
   - Reauthentication and account deletion.

2. **Cloud Firestore**
   - Scope-limited remote storage for the existing feedback document/transaction flow.
   - Future trusted account-deletion and retention coordination only when separately implemented.
   - Not a general remote mirror for aquariums, tanks, care records, preferences or local usage summaries.

3. **Cloud Storage for Firebase**
   - Scope-limited storage for an optional feedback screenshot explicitly selected by the user.
   - No automatic upload of aquarium media, profile media, QR frames, logs, provisioning data or credentials.

### Removed Firebase services

- Google Analytics for Firebase.
- Firebase Realtime Database.
- Firebase Cloud Messaging.
- Firebase Remote Config.
- Firebase Crashlytics.
- Firebase Performance Monitoring.

### Planned controls that are not current Android SDK dependencies

- Firebase App Check with Play Integrity, subject to a separate staged implementation and enforcement decision.
- Cloud Functions for Firebase or an equivalent trusted backend for authoritative account-data deletion and retention cleanup.
- Firebase Hosting or another HTTPS host for the public Privacy Policy and external account-deletion resource.

## 3. Why each removed service is unnecessary

### 3.1 Analytics

AquaLight has no approved remote behavioural-analytics, attribution, advertising, audience or profiling purpose. Existing usage counters are stored locally and are intended only to show the user an on-device usage summary.

Keeping Analytics would create an additional collection surface and Google Play declaration burden without a current product requirement.

### 3.2 Realtime Database

Current repository flows use Firestore where remote documents are required. No implemented AquaLight feature uses Realtime Database. A second database SDK would increase dependency and security-review scope without providing a product function.

### 3.3 Cloud Messaging

Care reminders and notifications use the Android local alarm, notification and WorkManager architecture. AquaLight has no approved remote-push backend, marketing notification purpose, FCM token lifecycle or message handler.

Local notifications must remain technically and legally separate from remote push messaging.

### 3.4 Remote Config

AquaLight has no implemented remote feature-flag, A/B testing, experiment or emergency configuration service. Product behaviour is controlled by the shipped application and its verified local configuration.

A future Remote Config proposal requires a new architecture decision, safe defaults, rollback design, ownership, security and privacy review.

### 3.5 Crashlytics

Crashlytics can be useful for production diagnostics, but it is not required for AquaLight's current user functions. Retaining it would require:

- an explicit collection policy;
- default-off startup controls;
- a user preference and withdrawal behaviour;
- unsent-report lifecycle handling;
- strict field allowlisting and redaction;
- provider-access and retention procedures;
- Privacy Policy and Google Play Data safety treatment.

The current product priority is a small, understandable and auditable architecture. Crashlytics is therefore removed. Application stability will be protected through automated tests, emulator tests, release smoke tests, defensive error handling and user-initiated feedback.

### 3.6 Performance Monitoring

Performance Monitoring is not required for authentication, device provisioning, local aquarium management or feedback. Its automatic traces would create a separate technical-data flow and would require review of URLs, attributes, identifiers, retention and user controls.

It is removed. Performance will be evaluated through local profiling, benchmark/test tooling and controlled engineering diagnostics that are not shipped as an automatic Firebase collection service.

## 4. Android dependency policy

The Android application may retain only:

```text
Firebase BoM
firebase-auth
firebase-firestore
firebase-storage
```

The `com.google.gms.google-services` Gradle plugin remains because the retained Firebase Android services require generated Firebase configuration resources.

The `com.google.firebase.crashlytics` Gradle plugin is removed from:

- the application module plugin block;
- root plugin management.

No runtime or build dependency for Analytics, Realtime Database, Messaging, Remote Config, Crashlytics or Performance Monitoring may remain in the release dependency graph.

## 5. Approved data boundaries

### 5.1 Authentication

Approved data is limited to what is required by the implemented authentication flow, including Firebase UID, email address, authentication provider and Firebase-managed account metadata.

The application must not:

- store user passwords;
- log passwords or Google tokens;
- expose UID as a public profile identifier;
- copy authentication data into unrelated stores without a documented purpose;
- attach authentication identifiers to feedback or logs unless the specific field is required and documented.

### 5.2 Firestore

Firestore is approved for explicitly mapped collections only. Current confirmed use includes feedback records and their media-transaction state.

The following must not be silently added to Firestore:

- aquariums and tanks;
- care tasks and reminders;
- local profile and address data;
- local preferences;
- local usage summary;
- Wi-Fi credentials or provisioning payloads;
- device logs.

Any future remote-sync feature requires an inventory update, Security Rules, deletion mapping, retention decision, Privacy update, Google Play review and tests before implementation.

### 5.3 Storage

Storage upload is allowed only after an explicit user action in an approved feature. The current case is an optional feedback screenshot.

Required controls include:

- owner-scoped and transaction-bound object paths;
- file type and size validation;
- rollback and orphan cleanup;
- feedback-expiry cleanup;
- account-deletion cleanup;
- no download URL or object path in general logs;
- no automatic upload from camera, QR scanner, aquarium media or profile media.

## 6. Privacy and legal effect

Removing six Firebase services reduces, but does not eliminate, AquaLight's privacy and legal obligations.

The release documentation must still accurately cover:

- account identifiers processed through Firebase Authentication;
- feedback content stored in Firestore;
- optional feedback screenshots stored in Storage;
- Firebase/Google service-provider and international-transfer assessment;
- resource locations and access controls;
- retention and account deletion;
- Google Play Privacy Policy and Data safety declarations.

The release does not require a Firebase diagnostics consent or diagnostics preference because no Firebase crash, performance or analytics SDK is shipped under this architecture.

Privacy notice presentation must not be described as consent. The previously approved product decision remains unchanged: no legal checkbox, age declaration or permanent Privacy/Terms link is added to the login or registration layouts. Privacy Policy and Terms remain available in the authenticated application and through the Google Play/public publication process, subject to final legal review.

## 7. Google Play effect

The final Data safety declaration must be based on the release artifact and production behaviour.

Under this architecture:

- Firebase Analytics collection is not an AquaLight data flow.
- Firebase Crashlytics data is not an AquaLight data flow.
- Firebase Performance Monitoring data is not an AquaLight data flow.
- FCM registration tokens and remote messages are not an AquaLight data flow.
- Authentication data remains a remote account flow.
- Feedback text remains optional user-provided remote content.
- Feedback screenshots remain optional user-selected remote media.

Historical Firebase Console data, if any, must be reviewed separately. Removing an SDK does not by itself delete data previously collected by a provider.

## 8. Security and deletion obligations that remain

The simplified SDK list does not weaken the following requirements:

- strict owner isolation in Firestore and Storage Rules;
- Emulator tests for permitted and forbidden operations;
- trusted server-side account-data deletion;
- feedback retention and automatic cleanup;
- Storage orphan cleanup;
- complete local account cleanup;
- App Check evaluation;
- least-privilege Firebase project access;
- documented Firestore and Storage regions;
- public account-deletion resource;
- Turkish and English legal documents;
- release-artifact verification.

## 9. Reintroduction rule

A removed Firebase service may not be reintroduced as a convenience change.

A reintroduction requires, before code is merged:

1. a concrete product requirement;
2. a data-field and automatic-collection inventory;
3. necessity and proportionality review;
4. regional, provider and retention review;
5. user-choice design where applicable;
6. account-deletion mapping;
7. Google Play Data safety update;
8. Privacy/Terms impact review;
9. tests, rollout and rollback plan;
10. a dedicated architecture decision.

## 10. Acceptance criteria

This decision is implemented when:

- `app/build.gradle` retains only Auth, Firestore and Storage Firebase product dependencies;
- the Crashlytics application plugin is removed;
- the Crashlytics plugin declaration is removed from `settings.gradle`;
- repository search finds no application use of the six removed Firebase products;
- authentication and feedback code still compile against retained services;
- debug and release dependency reports contain no removed Firebase product SDKs;
- merged-manifest inspection shows no generated components from removed products;
- automated build, unit, lint and release checks pass.

## 11. Next Stage 12 work

The next compliance step records and verifies only the infrastructure that remains relevant:

1. Firebase Authentication processing characteristics;
2. Firestore database region and access ownership;
3. Storage bucket region and access ownership;
4. future trusted-backend region and IAM design;
5. Firebase/Google contractual and international-transfer documentation.

No region, retention period or legal conclusion will be guessed from Android source code.
