# Stage 12 — Firebase provider and data-region register

Status: **incomplete — commercial release blocked until Firestore and Storage locations are verified from the production project**

Firebase project observed in the Android configuration: `aqualight-58aa2`  
Default Storage bucket observed in the Android configuration: `aqualight-58aa2.firebasestorage.app`

## Active Firebase services

| Service | Product purpose | Data categories | Provider role | Region status |
| --- | --- | --- | --- | --- |
| Firebase Authentication | Account creation, email/password sign-in, Google sign-in, session and account security | Email, Firebase UID, authentication provider data, account status, security metadata such as IP address and user agent | Google entity identified by the applicable Firebase/Google terms generally acts as processor/service provider for customer data | **United States processing** according to Firebase's service privacy documentation |
| Cloud Firestore | Feedback message, category, optional reply email, app version, locale, UID/anonymous marker, media transaction state | Feedback and support data | Google Cloud/Firebase processor or service provider | **Unverified. Must be read from Firebase Console → Firestore → Data / database location** |
| Cloud Storage for Firebase | Optional feedback screenshots | User-selected screenshot and owner-scoped object path | Google Cloud/Firebase processor or service provider | **Unverified. Must be read from Firebase Console → Storage → Files / bucket details** |

## Firebase services intentionally absent

The commercial Android build must not include:

- Firebase Analytics
- Firebase Crashlytics
- Firebase Performance Monitoring
- Firebase Realtime Database
- Firebase Cloud Messaging
- Firebase Remote Config

Any future addition requires a separate privacy assessment, dependency review, public-policy update, Google Play Data Safety review, and explicit product approval before collection begins.

## Required production verification

Record the following using an authenticated project owner/editor account:

1. Firestore database ID and exact location.
2. Storage bucket name and exact location.
3. Whether either resource uses a regional or multi-region location.
4. Firestore TTL state for `feedback_items.mediaTransactionExpiresAt`.
5. Storage lifecycle state for the `feedback_screenshots/` prefix.
6. Applicable Google/Firebase data-processing terms and subprocessor list revision date.
7. Verification date, verifier identity, and evidence reference.

## Verification record

| Field | Required value |
| --- | --- |
| Firestore database ID | Not yet verified |
| Firestore location | Not yet verified |
| Storage bucket | `aqualight-58aa2.firebasestorage.app` |
| Storage location | Not yet verified |
| Firestore TTL enabled | Not yet verified |
| Storage lifecycle enabled | Not yet verified |
| Verified by | Not yet verified |
| Verification date | Not yet verified |
| Evidence | Not yet verified |

## Publication rule

- Do not publish a guessed region.
- Do not mark Stage 12 complete while any production-region field remains unverified.
- After verification, update this register and the English and Turkish Privacy Policy documents in the same reviewed change.
- Firebase Authentication's United States processing and any Firestore/Storage processing outside Türkiye must be reflected in the international-transfer assessment and public notice.
