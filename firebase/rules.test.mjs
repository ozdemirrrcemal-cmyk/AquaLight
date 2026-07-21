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
  const otherRef = doc(otherDb, 'feedback_items', 'other-feedback');

  await assertSucceeds(setDoc(ownerRef, textFeedback(OWNER)));
  await assertSucceeds(
    setDoc(
      doc(ownerDb, 'feedback_items', 'owner-feedback-with-email'),
      textFeedback(OWNER, { email: 'user+tag@example.com' }),
    ),
  );
  await assertSucceeds(setDoc(otherRef, textFeedback(OTHER_OWNER)));

  await assertSucceeds(getDoc(ownerRef));
  await assertFails(getDoc(doc(otherDb, 'feedback_items', 'owner-feedback')));

  await assertFails(
    setDoc(
      doc(otherDb, 'feedback_items', 'spoofed-owner'),
      textFeedback(OWNER),
    ),
  );
  await assertFails(
    setDoc(
      doc(anonymousDb, 'feedback_items', 'anonymous-feedback'),
      textFeedback('anonymous'),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'invalid-email'),
      textFeedback(OWNER, { email: 'invalid-email' }),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'invalid-dotted-email'),
      textFeedback(OWNER, { email: 'user..name@example.com' }),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'oversized-email-local-part'),
      textFeedback(OWNER, { email: `${'a'.repeat(65)}@example.com` }),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'oversized-message'),
      textFeedback(OWNER, { message: 'x'.repeat(501) }),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'unexpected-field'),
      { ...textFeedback(OWNER), unexpected: true },
    ),
  );

  const ownerQuery = query(
    collection(ownerDb, 'feedback_items'),
    where('userId', '==', OWNER),
  );
  await assertSucceeds(getDocs(ownerQuery));

  const crossOwnerQuery = query(
    collection(otherDb, 'feedback_items'),
    where('userId', '==', OWNER),
  );
  await assertFails(getDocs(crossOwnerQuery));
  await assertFails(getDocs(collection(ownerDb, 'feedback_items')));

  await assertFails(deleteDoc(doc(otherDb, 'feedback_items', 'owner-feedback')));
  await assertSucceeds(deleteDoc(ownerRef));

  console.log('Feedback Firestore rules tests passed.');
} finally {
  await testEnvironment.cleanup();
}
