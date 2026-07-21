import fs from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  collection,
} from 'firebase/firestore';

const PROJECT_ID = 'demo-aqualight-stage9';
const OWNER = 'owner-a';
const OTHER_OWNER = 'owner-b';

const testEnvironment = await initializeTestEnvironment({
  projectId: PROJECT_ID,
  firestore: {
    rules: fs.readFileSync('firestore.rules', 'utf8'),
  },
});

function textFeedback(ownerUid, overrides = {}) {
  return {
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

try {
  await testEnvironment.clearFirestore();

  const ownerDb = testEnvironment.authenticatedContext(OWNER).firestore();
  const otherDb = testEnvironment.authenticatedContext(OTHER_OWNER).firestore();
  const anonymousDb = testEnvironment.unauthenticatedContext().firestore();

  const ownerRef = doc(ownerDb, 'feedback_items', 'owner-feedback');
  await assertSucceeds(setDoc(ownerRef, textFeedback(OWNER)));
  await assertSucceeds(getDoc(ownerRef));
  await assertFails(getDoc(doc(otherDb, 'feedback_items', 'owner-feedback')));

  await assertFails(
    setDoc(
      doc(otherDb, 'feedback_items', 'spoofed-owner'),
      textFeedback(OWNER),
    ),
  );

  await assertSucceeds(
    setDoc(
      doc(anonymousDb, 'feedback_items', 'anonymous-feedback'),
      textFeedback('anonymous'),
    ),
  );

  await assertFails(
    setDoc(
      doc(anonymousDb, 'feedback_items', 'anonymous-spoof'),
      textFeedback(OWNER),
    ),
  );

  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'oversized-message'),
      textFeedback(OWNER, { message: 'x'.repeat(4001) }),
    ),
  );

  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'media-field-forbidden'),
      textFeedback(OWNER, { screenshotUrl: 'https://example.invalid/image.jpg' }),
    ),
  );

  const ownerQuery = query(
    collection(ownerDb, 'feedback_items'),
    where('userId', '==', OWNER),
  );
  await assertSucceeds(getDocs(ownerQuery));

  const unscopedOtherQuery = query(collection(otherDb, 'feedback_items'));
  await assertFails(getDocs(unscopedOtherQuery));

  await assertFails(updateDoc(ownerRef, { status: 'closed' }));
  await assertFails(deleteDoc(doc(otherDb, 'feedback_items', 'owner-feedback')));
  await assertSucceeds(deleteDoc(ownerRef));

  console.log('Firebase Firestore rules tests passed.');
} finally {
  await testEnvironment.cleanup();
}
