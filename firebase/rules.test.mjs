import fs from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
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

function textFeedback(ownerUid, message = 'A reproducible commercial feedback issue.') {
  return {
    category: 'Bug',
    email: null,
    message,
    platform: 'android',
    appVersion: '1.0.0',
    locale: 'tr-TR',
    status: 'new',
    userId: ownerUid,
    createdAt: serverTimestamp(),
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
      doc(anonymousDb, 'feedback_items', 'oversized-message'),
      textFeedback('anonymous', 'x'.repeat(4001)),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'unexpected-field'),
      { ...textFeedback(OWNER), unexpected: true },
    ),
  );

  console.log('Feedback Firestore rules tests passed.');
} finally {
  await testEnvironment.cleanup();
}
