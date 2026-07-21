# Firebase feedback production activation

## Security policy

- Only authenticated AquaLight users may submit feedback.
- A submission may use only the caller's Firebase UID.
- Owners may query, read and delete only their own feedback records.
- Client updates, cross-owner access and unexpected fields are denied.
- Message length is limited to 10–500 characters and document metadata is bounded.

## Source-controlled inputs

- `firebase.json`
- `firestore.rules`
- `firestore.indexes.json`
- `firebase/rules.test.mjs`
- `.github/workflows/firebase_rules.yml`

The validation workflow starts an isolated Firestore emulator and verifies authenticated creation,
strict field allowlisting, owner-scoped account cleanup access, cross-owner denial, update denial,
timestamp integrity and field limits.

## Production deployment

Use a Firebase CLI identity authorized for the production project:

```bash
firebase use <production-project-id>
firebase deploy --only firestore
```

## Release gate

Commercial release is blocked unless:

1. `Firebase Feedback Rules Validation` is green.
2. The exact committed Firestore rules and indexes are deployed to production.
3. An authenticated feedback submission succeeds with the strict schema.
4. Unauthenticated creation, cross-owner access, client updates and unexpected fields fail.
