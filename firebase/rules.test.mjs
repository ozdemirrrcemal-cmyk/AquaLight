import fs from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';

const PROJECT_ID = 'demo-aqualight-feedback';
const OWNER = 'owner-a';
const OTHER_OWNER = 'owner-b';
const ADMIN = 'admin-a';
const NON_ADMIN = 'non-admin';
const WRONG_ROLE = 'wrong-role';
const OWNER_SUBMISSION = '123e4567-e89b-42d3-a456-426614174000';
const OWNER_EMAIL_SUBMISSION = '123e4567-e89b-42d3-a456-426614174001';
const OTHER_SUBMISSION = '123e4567-e89b-42d3-a456-426614174002';
const OLD_SUBMISSION = '123e4567-e89b-42d3-a456-426614174011';

const testEnvironment = await initializeTestEnvironment({
  projectId: PROJECT_ID,
  firestore: {
    rules: fs.readFileSync('firestore.rules', 'utf8'),
  },
});

function textFeedback(ownerUid, submissionId, overrides = {}) {
  return {
    submissionId,
    category: 'Bug',
    email: null,
    message: 'A reproducible commercial feedback issue.',
    platform: 'android',
    appVersion: '1.0.0',
    locale: 'tr-TR',
    status: 'new',
    userId: ownerUid,
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

function submissionRef(db, ownerUid, submissionId) {
  return doc(db, 'feedback_items', ownerUid, 'submissions', submissionId);
}

function storedFeedback(ownerUid, submissionId, createdAt) {
  return {
    submissionId,
    category: 'Bug',
    email: null,
    message: 'Stored feedback for admin access tests.',
    platform: 'android',
    appVersion: '1.0.0',
    locale: 'tr-TR',
    status: 'new',
    userId: ownerUid,
    createdAt,
  };
}

try {
  await testEnvironment.clearFirestore();

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, 'admin_access', ADMIN), {role: 'feedback-admin'});
    await setDoc(doc(db, 'admin_access', WRONG_ROLE), {role: 'viewer'});
    await setDoc(
      submissionRef(db, OWNER, OLD_SUBMISSION),
      storedFeedback(
        OWNER,
        OLD_SUBMISSION,
        Timestamp.fromDate(new Date('2024-01-01T00:00:00Z')),
      ),
    );
  });

  const ownerDb = testEnvironment.authenticatedContext(OWNER).firestore();
  const otherDb = testEnvironment.authenticatedContext(OTHER_OWNER).firestore();
  const adminDb = testEnvironment.authenticatedContext(ADMIN).firestore();
  const nonAdminDb = testEnvironment.authenticatedContext(NON_ADMIN).firestore();
  const wrongRoleDb = testEnvironment.authenticatedContext(WRONG_ROLE).firestore();
  const anonymousDb = testEnvironment.unauthenticatedContext().firestore();
  const ownerRef = submissionRef(ownerDb, OWNER, OWNER_SUBMISSION);
  const otherRef = submissionRef(otherDb, OTHER_OWNER, OTHER_SUBMISSION);

  await assertSucceeds(
    setDoc(ownerRef, textFeedback(OWNER, OWNER_SUBMISSION)),
  );
  await assertSucceeds(
    setDoc(
      submissionRef(ownerDb, OWNER, OWNER_EMAIL_SUBMISSION),
      textFeedback(OWNER, OWNER_EMAIL_SUBMISSION, {
        email: 'user+tag@example.com',
      }),
    ),
  );
  await assertSucceeds(
    setDoc(otherRef, textFeedback(OTHER_OWNER, OTHER_SUBMISSION)),
  );

  await assertSucceeds(getDoc(ownerRef));
  await assertFails(
    getDoc(submissionRef(otherDb, OWNER, OWNER_SUBMISSION)),
  );

  await assertFails(
    setDoc(
      submissionRef(ownerDb, OWNER, '123e4567-e89b-42d3-a456-426614174003'),
      textFeedback(
        OTHER_OWNER,
        '123e4567-e89b-42d3-a456-426614174003',
      ),
    ),
  );
  await assertFails(
    setDoc(
      submissionRef(
        anonymousDb,
        OWNER,
        '123e4567-e89b-42d3-a456-426614174004',
      ),
      textFeedback(
        OWNER,
        '123e4567-e89b-42d3-a456-426614174004',
      ),
    ),
  );
  await assertFails(
    setDoc(
      submissionRef(ownerDb, OWNER, 'not-a-uuid'),
      textFeedback(OWNER, 'not-a-uuid'),
    ),
  );
  await assertFails(
    setDoc(
      submissionRef(ownerDb, OWNER, '123e4567-e89b-42d3-a456-426614174005'),
      textFeedback(
        OWNER,
        '123e4567-e89b-42d3-a456-426614174099',
      ),
    ),
  );
  await assertFails(
    setDoc(
      submissionRef(ownerDb, OWNER, '123e4567-e89b-42d3-a456-426614174006'),
      textFeedback(OWNER, '123e4567-e89b-42d3-a456-426614174006', {
        email: 'invalid-email',
      }),
    ),
  );
  await assertFails(
    setDoc(
      submissionRef(ownerDb, OWNER, '123e4567-e89b-42d3-a456-426614174007'),
      textFeedback(OWNER, '123e4567-e89b-42d3-a456-426614174007', {
        email: 'user..name@example.com',
      }),
    ),
  );
  await assertFails(
    setDoc(
      submissionRef(ownerDb, OWNER, '123e4567-e89b-42d3-a456-426614174008'),
      textFeedback(OWNER, '123e4567-e89b-42d3-a456-426614174008', {
        email: `${'a'.repeat(65)}@example.com`,
      }),
    ),
  );
  await assertFails(
    setDoc(
      submissionRef(ownerDb, OWNER, '123e4567-e89b-42d3-a456-426614174009'),
      textFeedback(OWNER, '123e4567-e89b-42d3-a456-426614174009', {
        message: 'x'.repeat(501),
      }),
    ),
  );
  await assertFails(
    setDoc(
      submissionRef(ownerDb, OWNER, '123e4567-e89b-42d3-a456-426614174010'),
      {
        ...textFeedback(
          OWNER,
          '123e4567-e89b-42d3-a456-426614174010',
        ),
        unexpected: true,
      },
    ),
  );

  // Existing documents are immutable. Idempotent retries read and return them without writing.
  await assertFails(
    setDoc(ownerRef, textFeedback(OWNER, OWNER_SUBMISSION)),
  );

  const ownerSubmissions = collection(
    ownerDb,
    'feedback_items',
    OWNER,
    'submissions',
  );
  await assertSucceeds(getDocs(ownerSubmissions));

  const crossOwnerSubmissions = collection(
    otherDb,
    'feedback_items',
    OWNER,
    'submissions',
  );
  await assertFails(getDocs(crossOwnerSubmissions));
  await assertFails(getDocs(collection(ownerDb, 'feedback_items')));

  // Signing in is not authorization. Only a server/Console-provisioned
  // admin_access/{uid} document grants cross-owner admin operations.
  await assertSucceeds(
    getDoc(submissionRef(adminDb, OWNER, OWNER_SUBMISSION)),
  );
  await assertFails(
    getDoc(submissionRef(nonAdminDb, OWNER, OWNER_SUBMISSION)),
  );
  await assertSucceeds(getDoc(doc(adminDb, 'admin_access', ADMIN)));
  await assertSucceeds(getDoc(doc(nonAdminDb, 'admin_access', NON_ADMIN)));
  await assertFails(getDoc(doc(nonAdminDb, 'admin_access', ADMIN)));
  await assertFails(setDoc(doc(nonAdminDb, 'admin_access', NON_ADMIN), {}));
  await assertFails(deleteDoc(doc(adminDb, 'admin_access', ADMIN)));

  await assertSucceeds(getDocs(query(
    collectionGroup(adminDb, 'submissions'),
    orderBy('createdAt', 'desc'),
    limit(50),
  )));
  await assertFails(getDocs(query(
    collectionGroup(nonAdminDb, 'submissions'),
    orderBy('createdAt', 'desc'),
    limit(50),
  )));
  await assertFails(getDocs(collectionGroup(wrongRoleDb, 'submissions')));
  await assertFails(getDocs(collectionGroup(anonymousDb, 'submissions')));

  // Admin access never permits forging or editing user submissions.
  await assertFails(setDoc(
    submissionRef(adminDb, OWNER, '123e4567-e89b-42d3-a456-426614174012'),
    textFeedback(OWNER, '123e4567-e89b-42d3-a456-426614174012'),
  ));
  await assertFails(updateDoc(
    submissionRef(adminDb, OWNER, OWNER_SUBMISSION),
    {status: 'reviewed'},
  ));

  // The panel's bounded deletion and non-PII audit record succeed atomically.
  const cutoff = Timestamp.fromDate(new Date('2025-01-01T00:00:00Z'));
  const expired = await assertSucceeds(getDocs(query(
    collectionGroup(adminDb, 'submissions'),
    where('createdAt', '<', cutoff),
    orderBy('createdAt', 'asc'),
    limit(100),
  )));
  const auditRef = doc(collection(adminDb, 'retention_audits'));
  const deletionBatch = writeBatch(adminDb);
  expired.forEach((snapshot) => deletionBatch.delete(snapshot.ref));
  deletionBatch.set(auditRef, {
    actorUid: ADMIN,
    createdAt: serverTimestamp(),
    cutoff,
    deletedCount: expired.size,
    mode: 'manual-admin-panel',
    version: 1,
  });
  await assertSucceeds(deletionBatch.commit());
  await assertSucceeds(getDoc(auditRef));
  await assertSucceeds(getDocs(collection(adminDb, 'retention_audits')));
  await assertFails(getDocs(collection(nonAdminDb, 'retention_audits')));
  await assertFails(setDoc(doc(nonAdminDb, 'retention_audits', 'fake'), {
    actorUid: NON_ADMIN,
    createdAt: serverTimestamp(),
    cutoff,
    deletedCount: 1,
    mode: 'manual-admin-panel',
    version: 1,
  }));
  await assertFails(updateDoc(auditRef, {deletedCount: 99}));
  await assertFails(deleteDoc(auditRef));

  await assertFails(
    deleteDoc(submissionRef(otherDb, OWNER, OWNER_SUBMISSION)),
  );
  await assertSucceeds(deleteDoc(ownerRef));

  console.log('Spark-compatible feedback Firestore rules tests passed.');
} finally {
  await testEnvironment.cleanup();
}
