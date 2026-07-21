# Text-only feedback — Firebase production activation

## Security policy

- Feedback submissions contain only category, optional email, message, platform, app version,
  locale, status, user ID and server creation time.
- Authenticated users may create, query, read and delete only their own records.
- Anonymous callers may create text feedback only with the explicit `anonymous` identity and cannot
  read or delete records.
- Client updates, cross-owner access and every unexpected field are denied.
- Firebase Storage is retired. Its ruleset denies every read and write, including access from older
  app versions.

## Source-controlled deployment inputs

- `firebase.json`
- `firestore.rules`
- `firestore.indexes.json`
- `storage.rules`
- `firebase/rules.test.mjs`
- `.github/workflows/firebase_rules.yml`

The validation workflow starts isolated Firestore and Storage emulators. It verifies authenticated
and anonymous text submission, strict field allowlisting, owner-scoped account cleanup access,
cross-owner/update denial and global Storage denial.

## Production deployment

Use a Firebase CLI identity authorized to deploy both rulesets:

```bash
firebase use <production-project-id>
firebase deploy --only firestore,storage
```

Deploying Storage is intentional even though the Android Storage dependency is removed: the
deny-all policy closes access for retired application versions. Confirm that the Firestore index
configuration no longer contains the retired media transaction TTL override.

Previously uploaded objects are not removed by a rules deployment. Inventory and deletion require a
separate, explicitly authorized administrative operation.

## Release gate

Commercial release is blocked unless:

1. `Firebase Feedback Rules Validation` is green.
2. The exact committed Firestore and deny-all Storage rules have been deployed to production.
3. Authenticated and anonymous text submissions succeed with the strict schema.
4. Cross-owner reads/deletes, all client updates, unexpected media fields and every Storage action
   fail.
5. Production data contains no active media transaction TTL override.
