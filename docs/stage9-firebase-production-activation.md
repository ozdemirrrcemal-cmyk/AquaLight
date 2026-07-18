# Stage 9 — Firebase production activation

## Security policy

- Authenticated feedback screenshots use the immutable path
  `feedback_screenshots/{ownerUid}/{documentId}.jpg`.
- Upload, download and rollback deletion are restricted to the authenticated path owner.
- Screenshot objects are immutable after creation and limited to non-empty JPEG files of at most
  3 MiB.
- Anonymous users may submit text-only feedback. Anonymous screenshot upload is intentionally
  rejected because an unauthenticated caller cannot be given safe object-level delete ownership.
- Firestore feedback documents are field-allowlisted. Owner identity and screenshot path cannot be
  changed after the pending marker is created.
- The only screenshot transaction transitions are `pending -> committed` and
  `pending -> aborted`. Existing committed documents cannot be overwritten by delayed writers.
- `mediaTransactionExpiresAt` is the TTL field for minimal pending/aborted transaction fences.
  Committed feedback documents remove this field and are not TTL-deleted.

## Source-controlled deployment inputs

- `firebase.json`
- `firestore.rules`
- `firestore.indexes.json`
- `storage.rules`
- `firebase/rules.test.mjs`
- `.github/workflows/firebase_rules.yml`

The validation workflow starts isolated Firestore and Storage emulators, compiles both rulesets and
verifies owner access, cross-owner denial, anonymous text-only policy, transaction transitions,
content type and the 3 MiB object limit.

## Production deployment

Use a Firebase CLI identity with permission to deploy Firestore rules/indexes and Storage rules:

```bash
firebase use <production-project-id>
firebase deploy --only firestore,storage
```

The deploy applies the TTL field override from `firestore.indexes.json`. After deployment, verify in
Firestore **Indexes / TTL** that collection group `feedback_items` uses
`mediaTransactionExpiresAt` and that the policy status is enabled. TTL deletion is asynchronous and
may occur after the expiration time; the application rollback journal remains the immediate cleanup
mechanism.

## Release gate

Commercial release is blocked unless:

1. `Firebase Rules Validation` is green.
2. The exact committed rules/index files have been deployed to the production Firebase project.
3. The TTL policy reports enabled for `feedback_items.mediaTransactionExpiresAt`.
4. A production smoke test confirms authenticated screenshot submit, Firestore failure rollback and
   cross-owner access denial.
