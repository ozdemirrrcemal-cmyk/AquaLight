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
import {
  deleteObject,
  getBytes,
  ref,
  uploadBytes,
} from 'firebase/storage';

const PROJECT_ID = 'demo-aqualight-feedback-text';
const OWNER = 'owner-a';
const OTHER_OWNER = 'owner-b';

const testEnvironment = await initializeTestEnvironment({
  projectId: PROJECT_ID,
  firestore: {
    rules: fs.readFileSync('firestore.rules', 'utf8'),
  },
  storage: {
    rules: fs.readFileSync('storage.rules', 'utf8'),
  },
});

function textFeedback(userId, message = 'A reproducible commercial feedback issue.') {
  return {
    category: 'Bug',
    email: null,
    message,
    platform: 'android',
    appVersion: '1.0.0',
    locale: 'tr-TR',
    status: 'new',
    userId,
    createdAt: serverTimestamp(),
  };
}

try {
  await testEnvironment.clearFirestore();
  await testEnvironment.clearStorage();

  const ownerDb = testEnvironment.authenticatedContext(OWNER).firestore();
  const otherDb = testEnvironment.authenticatedContext(OTHER_OWNER).firestore();
  const anonymousDb = testEnvironment.unauthenticatedContext().firestore();

  const ownerRef = doc(ownerDb, 'feedback_items', 'owner-text');
  await assertSucceeds(setDoc(ownerRef, textFeedback(OWNER)));
  await assertSucceeds(getDoc(ownerRef));
  await assertFails(getDoc(doc(otherDb, 'feedback_items', 'owner-text')));
  await assertFails(deleteDoc(doc(otherDb, 'feedback_items', 'owner-text')));
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
      textFeedback(OWNER),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'unexpected-media-field'),
      {
        ...textFeedback(OWNER),
        screenshotUrl: 'https://example.invalid/retired.jpg',
      },
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'unexpected-transaction-field'),
      {
        ...textFeedback(OWNER),
        mediaTransactionState: 'committed',
      },
    ),
  );

  await assertSucceeds(
    setDoc(
      doc(anonymousDb, 'feedback_items', 'anonymous-text'),
      textFeedback('anonymous', 'Anonymous text feedback is accepted.'),
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
      doc(anonymousDb, 'feedback_items', 'short-message'),
      textFeedback('anonymous', 'too short'),
    ),
  );
  await assertFails(
    setDoc(
      doc(anonymousDb, 'feedback_items', 'oversized-message'),
      textFeedback('anonymous', 'x'.repeat(4001)),
    ),
  );

  await assertSucceeds(deleteDoc(ownerRef));

  const retiredPath = 'retired-media/existing.jpg';
  await testEnvironment.withSecurityRulesDisabled(async context => {
    await uploadBytes(
      ref(context.storage(), retiredPath),
      new Uint8Array([0xff, 0xd8, 0xff, 0xd9]),
      { contentType: 'image/jpeg' },
    );
  });

  const ownerStorage = testEnvironment.authenticatedContext(OWNER).storage();
  const anonymousStorage = testEnvironment.unauthenticatedContext().storage();
  await assertFails(getBytes(ref(ownerStorage, retiredPath)));
  await assertFails(deleteObject(ref(ownerStorage, retiredPath)));
  await assertFails(
    uploadBytes(
      ref(ownerStorage, 'retired-media/new.jpg'),
      new Uint8Array([0xff, 0xd8, 0xff, 0xd9]),
      { contentType: 'image/jpeg' },
    ),
  );
  await assertFails(
    uploadBytes(
      ref(anonymousStorage, 'retired-media/anonymous.jpg'),
      new Uint8Array([0xff, 0xd8, 0xff, 0xd9]),
      { contentType: 'image/jpeg' },
    ),
  );

  console.log('Text-only feedback and Storage decommission rules tests passed.');
} finally {
  await testEnvironment.cleanup();
}
