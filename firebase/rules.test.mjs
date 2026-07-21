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
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';

const PROJECT_ID = 'demo-aqualight-feedback';
const OWNER = 'owner-a';
const OTHER_OWNER = 'owner-b';

const testEnvironment = await initializeTestEnvironment({
  projectId: PROJECT_ID,
  firestore: {
    rules: fs.readFileSync('firestore.rules', 'utf8'),
  },
});

function feedback(userId, overrides = {}) {
  return {
    category: 'Bug',
    email: null,
    message: 'A reproducible commercial feedback issue.',
    platform: 'android',
    appVersion: '1.0.0',
    locale: 'tr-TR',
    status: 'new',
    userId,
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

try {
  await testEnvironment.clearFirestore();

  const ownerDb = testEnvironment.authenticatedContext(OWNER).firestore();
  const otherDb = testEnvironment.authenticatedContext(OTHER_OWNER).firestore();
  const unauthenticatedDb = testEnvironment.unauthenticatedContext().firestore();

  const ownerRef = doc(ownerDb, 'feedback_items', 'owner-feedback');
  await assertSucceeds(setDoc(ownerRef, feedback(OWNER)));
  await assertSucceeds(getDoc(ownerRef));
  await assertFails(getDoc(doc(otherDb, 'feedback_items', 'owner-feedback')));
  await assertFails(deleteDoc(doc(otherDb, 'feedback_items', 'owner-feedback')));
  await assertFails(updateDoc(ownerRef, { message: 'Updates are forbidden.' }));

  await assertSucceeds(
    getDocs(
      query(
        collection(ownerDb, 'feedback_items'),
        where('userId', '==', OWNER),
      ),
    ),
  );

  await assertFails(
    setDoc(
      doc(otherDb, 'feedback_items', 'spoofed-owner'),
      feedback(OWNER),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'unexpected-field'),
      feedback(OWNER, { unexpectedField: true }),
    ),
  );
  await assertFails(
    setDoc(
      doc(unauthenticatedDb, 'feedback_items', 'unauthenticated'),
      feedback(OWNER),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'short-message'),
      feedback(OWNER, { message: 'too short' }),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'oversized-message'),
      feedback(OWNER, { message: 'x'.repeat(501) }),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'client-created-time'),
      feedback(OWNER, { createdAt: new Date() }),
    ),
  );

  await assertSucceeds(deleteDoc(ownerRef));

  console.log('Feedback Firestore rules tests passed.');
} finally {
  await testEnvironment.cleanup();
}
