# Stage 9 — Firestore Feedback Production Activation

## Security policy

- Feedback is authenticated, text-only and stored in `feedback_items`.
- Documents are field-allowlisted to category, optional email, message, platform, app version,
  locale, status, owner identity and server timestamp.
- Writers may use only the UID from `request.auth`; anonymous writes are denied.
- Category is limited to 80 characters, optional email to 254 characters and message to 10–500
  characters. App-version and locale lengths are also bounded.
- The optional email must match the same conservative address shape accepted by the Android policy.
- Documents cannot be updated through the client path.
- An authenticated owner may read, query and delete only documents whose `userId` equals their UID.
  Broad list queries and cross-owner access are rejected.
- Owner-scoped query/delete permissions are required by the account-deletion transaction, which
  removes feedback documents before deleting the Firebase account.
- No Firebase Storage product, upload journal, migration or TTL policy is required by feedback.

## Source-controlled deployment inputs

- `firebase.json`
- `firestore.rules`
- `firestore.indexes.json`
- `firebase/rules.test.mjs`
- `.github/workflows/firebase_rules.yml`

The validation workflow starts an isolated Firestore emulator and verifies authenticated owner
writes, anonymous denial, spoof prevention, email/message bounds, owner-constrained query/delete,
broad-query denial, cross-owner denial and rejection of unrecognized fields.

## Production deployment

Use a Firebase CLI identity with permission to deploy Firestore rules and indexes:

```bash
firebase use <production-project-id>
firebase deploy --only firestore
```

## Release gate

Commercial release is blocked unless:

1. `Firebase Rules Validation` is green.
2. The exact committed Firestore rules and index files are deployed to production.
3. A production smoke test confirms authenticated text submission, owner-scoped account cleanup and
   anonymous/cross-owner denial.
