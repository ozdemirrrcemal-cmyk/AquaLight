# Stage 9 — Firestore Feedback Production Activation

## Security policy

- Feedback is text-only and is stored in `feedback_items`.
- Documents are field-allowlisted to category, optional email, message, platform, app version,
  locale, status, owner identity and server timestamp.
- Authenticated writers may use only their own UID. Unauthenticated writers must use the explicit
  `anonymous` identity.
- Category, message, email, app-version and locale lengths are bounded by Firestore rules.
- Existing documents cannot be listed, updated or deleted through the feedback client path.
- An authenticated owner may read only their own document; cross-owner reads are rejected.
- No Firebase Storage product or TTL policy is required by feedback submission.

## Source-controlled deployment inputs

- `firebase.json`
- `firestore.rules`
- `firestore.indexes.json`
- `firebase/rules.test.mjs`
- `.github/workflows/firebase_rules.yml`

The validation workflow starts an isolated Firestore emulator and verifies owner writes,
cross-owner denial, anonymous policy, input bounds and rejection of unrecognized fields.

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
3. A production smoke test confirms authenticated and anonymous text submission plus cross-owner
   access denial.
