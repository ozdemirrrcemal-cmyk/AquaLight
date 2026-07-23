# Stage 9 — Spark-Plan Firestore Feedback Activation

## Product and billing decision

AquaLight feedback must remain compatible with the Firebase Spark plan. The feature does not use
Cloud Functions, App Check, Play Integrity, Firebase Storage or any Blaze-only runtime dependency.

## Security policy

- Feedback is authenticated and text-only.
- Documents are stored under `feedback_items/{ownerUid}/submissions/{submissionId}`.
- The path owner and document `userId` must both equal `request.auth.uid`.
- Anonymous and cross-owner access are denied.
- Category is limited to 80 characters, optional email to 254 characters with a 64-character local
  part, and message to 10–500 characters.
- Documents cannot be updated. An unchanged retry reads the existing UUID document and returns it.
- Submission uses a Firestore transaction. Firestore transactions fail while the client is offline,
  so the feedback is not silently queued for later synchronization.
- The Android wait is bounded to 15 seconds. Timeout and network errors close loading, re-enable the
  send button and preserve the form.
- Account deletion performs a server-only read of the owner's submissions and deletes them in bounded
  Firestore transactions before deleting the Firebase Authentication account.
- No Firebase Storage product, upload journal, screenshot migration, rate-limit collection or backend
  deployment is required.

## Source-controlled deployment inputs

- `firebase.json`
- `.firebaserc`
- `firestore.rules`
- `firestore.indexes.json`
- `firebase/rules.test.mjs`
- `.github/workflows/firebase_rules.yml`

The validation workflow starts an isolated Firestore emulator and verifies authenticated owner
creation, anonymous denial, path spoof prevention, UUID/email/message bounds, immutable records,
owner-scoped list/get/delete and cross-owner denial.

## Production deployment

Only Firestore rules and indexes need deployment. This does not require switching the Firebase
project to the Blaze plan.

```bash
firebase deploy --project production --only firestore
```

There are no Functions to deploy and no App Check or Play Integrity configuration for feedback.

## Release gate

Commercial release is blocked unless:

1. `Firebase Rules Validation` is green.
2. The exact committed Firestore rules and index files are deployed to the Firebase project.
3. A physical-device test confirms:
   - online authenticated feedback succeeds,
   - offline feedback returns the localized error and loading closes,
   - unchanged retry creates no duplicate,
   - edited form creates a new submission,
   - account deletion removes the owner's feedback,
   - anonymous and cross-owner Firestore access remain denied.
