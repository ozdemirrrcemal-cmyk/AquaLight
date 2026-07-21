import fs from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
} from 'firebase/firestore';

const PROJECT_ID = 'demo-aqualight-feedback';
const OWNER = 'owner-a';
const OTHER_OWNER = 'owner-b';
const OWNER_SUBMISSION = '123e4567-e89b-42d3-a456-426614174000';
const OWNER_EMAIL_SUBMISSION = '123e4567-e89b-42d3-a456-426614174001';
const OTHER_SUBMISSION = '123e4567-e89b-42d3-a456-426614174002';

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

try {
  await testEnvironment.clearFirestore();

  const ownerDb = testEnvironment.authenticatedContext(OWNER).firestore();
  const otherDb = testEnvironment.authenticatedContext(OTHER_OWNER).firestore();
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

  await assertFails(
    deleteDoc(submissionRef(otherDb, OWNER, OWNER_SUBMISSION)),
  );
  await assertSucceeds(deleteDoc(ownerRef));

  console.log('Spark-compatible feedback Firestore rules tests passed.');
} finally {
  await testEnvironment.cleanup();
}
