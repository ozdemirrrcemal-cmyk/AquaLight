# Firebase provider and data-region register

Status: **Firestore location verified; legal and operational review still required before commercial release**

Firebase project observed in the Android configuration: `aqualight-58aa2`

## Active Firebase services

| Service | Product purpose | Data categories | Provider role | Region status |
| --- | --- | --- | --- | --- |
| Firebase Authentication | Account creation, email/password sign-in, Google sign-in, session and account security | Email, Firebase UID, authentication-provider data, account status, security metadata such as IP address and user agent | Google entity identified by the applicable Firebase/Google terms generally acts as processor/service provider for customer data | United States processing may occur according to Firebase service documentation |
| Cloud Firestore | Text feedback: category, message, optional reply email, app version, locale, UID or anonymous marker | Feedback and support data | Google Cloud/Firebase processor or service provider | **Verified production location: `europe-west1`** |

## Firebase services intentionally absent

The commercial Android build must not include:

- Firebase Analytics
- Firebase Crashlytics
- Firebase Performance Monitoring
- Firebase Realtime Database
- Firebase Cloud Messaging
- Firebase Remote Config
- Firebase binary object-storage SDK

The feedback form is text-only. It does not upload attachments or other binary content. No remote object bucket, object rules, object lifecycle policy, or paid object-storage plan is required by the current product.

Any future addition requires a separate privacy assessment, dependency review, public-policy update, Google Play Data Safety review, pricing assessment, and explicit product approval before collection begins.

## Verification record

| Field | Verified value |
| --- | --- |
| Firestore database ID | `(default)` |
| Firestore edition | Standard |
| Firestore configuration | Firestore Native |
| Firestore location | `europe-west1` |
| Verification date | 2026-07-21 |
| Verified by | Project owner via Firebase Console |
| Evidence | Firebase Console database information screenshot |

## Remaining production evidence

1. Archive the applicable Google/Firebase data-processing terms and current subprocessor list.
2. Record the legal international-transfer assessment for Firebase Authentication and Firestore.
3. Complete a production account-deletion test showing that owner-linked feedback documents are removed.
4. Verify and evidence the documented text-feedback retention/deletion process.
5. Update this register in the same reviewed change whenever the Firebase service set or database location changes.
