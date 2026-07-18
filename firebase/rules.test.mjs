import fs from 'node:fs';
import assert from 'node:assert/strict';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  doc,
  getDoc,
  runTransaction,
  serverTimestamp,
  setDoc,
} from 'firebase/firestore';
import {
  deleteObject,
  getBytes,
  ref,
  uploadBytes,
} from 'firebase/storage';

const PROJECT_ID = 'demo-aqualight-stage9';
const OWNER = 'owner-a';
const OTHER_OWNER = 'owner-b';
const MAX_BYTES = 3 * 1024 * 1024;

const testEnvironment = await initializeTestEnvironment({
  projectId: PROJECT_ID,
  firestore: {
    rules: fs.readFileSync('firestore.rules', 'utf8'),
  },
  storage: {
    rules: fs.readFileSync('storage.rules', 'utf8'),
  },
});

function marker(ownerUid, documentId, state, status) {
  return {
    userId: ownerUid,
    mediaTransactionState: state,
    status,
    screenshotPath: `feedback_screenshots/${ownerUid}/${documentId}.jpg`,
    createdAt: serverTimestamp(),
    mediaTransactionExpiresAt: Timestamp.fromMillis(Date.now() + 7 * 24 * 60 * 60 * 1000),
  };
}

function committedFeedback(ownerUid, documentId) {
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
    screenshotUrl: 'https://example.invalid/screenshot.jpg',
    screenshotPath: `feedback_screenshots/${ownerUid}/${documentId}.jpg`,
    mediaTransactionState: 'committed',
  };
}

function anonymousTextFeedback(message = 'Anonymous text feedback is accepted.') {
  return {
    category: 'Other',
    email: null,
    message,
    platform: 'android',
    appVersion: '1.0.0',
    locale: 'tr-TR',
    status: 'new',
    userId: 'anonymous',
    createdAt: serverTimestamp(),
    mediaTransactionState: 'committed',
  };
}

try {
  await testEnvironment.clearFirestore();

  const ownerDb = testEnvironment.authenticatedContext(OWNER).firestore();
  const otherDb = testEnvironment.authenticatedContext(OTHER_OWNER).firestore();
  const anonymousDb = testEnvironment.unauthenticatedContext().firestore();

  const committedId = 'feedback-committed';
  const committedRef = doc(ownerDb, 'feedback_items', committedId);

  await assertSucceeds(
    runTransaction(ownerDb, async transaction => {
      const snapshot = await transaction.get(committedRef);
      assert.equal(snapshot.exists(), false);
      transaction.set(
        committedRef,
        marker(OWNER, committedId, 'pending', 'media_pending'),
      );
    }),
  );

  await assertFails(getDoc(doc(otherDb, 'feedback_items', committedId)));
  await assertSucceeds(getDoc(committedRef));

  await assertSucceeds(
    runTransaction(ownerDb, async transaction => {
      const snapshot = await transaction.get(committedRef);
      assert.equal(snapshot.data().mediaTransactionState, 'pending');
      transaction.set(committedRef, committedFeedback(OWNER, committedId));
    }),
  );

  const abortedId = 'feedback-aborted';
  const abortedRef = doc(ownerDb, 'feedback_items', abortedId);
  await assertSucceeds(
    runTransaction(ownerDb, async transaction => {
      const snapshot = await transaction.get(abortedRef);
      assert.equal(snapshot.exists(), false);
      transaction.set(
        abortedRef,
        marker(OWNER, abortedId, 'aborted', 'media_aborted'),
      );
    }),
  );

  await assertFails(
    setDoc(
      doc(otherDb, 'feedback_items', 'spoofed-owner'),
      marker(OWNER, 'spoofed-owner', 'pending', 'media_pending'),
    ),
  );

  await assertSucceeds(
    setDoc(
      doc(anonymousDb, 'feedback_items', 'anonymous-text'),
      anonymousTextFeedback(),
    ),
  );
  await assertFails(
    setDoc(
      doc(anonymousDb, 'feedback_items', 'anonymous-media'),
      marker('anonymous', 'anonymous-media', 'pending', 'media_pending'),
    ),
  );
  await assertFails(
    setDoc(
      doc(anonymousDb, 'feedback_items', 'oversized-message'),
      anonymousTextFeedback('x'.repeat(4001)),
    ),
  );

  const ownerStorage = testEnvironment.authenticatedContext(OWNER).storage();
  const otherStorage = testEnvironment.authenticatedContext(OTHER_OWNER).storage();
  const anonymousStorage = testEnvironment.unauthenticatedContext().storage();
  const validPath = `feedback_screenshots/${OWNER}/valid.jpg`;
  const validRef = ref(ownerStorage, validPath);

  await assertSucceeds(
    uploadBytes(validRef, new Uint8Array([0xff, 0xd8, 0xff, 0xd9]), {
      contentType: 'image/jpeg',
    }),
  );
  await assertSucceeds(getBytes(validRef));
  await assertFails(getBytes(ref(otherStorage, validPath)));
  await assertFails(deleteObject(ref(otherStorage, validPath)));

  await assertFails(
    uploadBytes(
      ref(ownerStorage, `feedback_screenshots/${OWNER}/wrong-type.jpg`),
      new Uint8Array([1, 2, 3]),
      { contentType: 'text/plain' },
    ),
  );
  await assertFails(
    uploadBytes(
      ref(ownerStorage, `feedback_screenshots/${OWNER}/too-large.jpg`),
      new Uint8Array(MAX_BYTES + 1),
      { contentType: 'image/jpeg' },
    ),
  );
  await assertFails(
    uploadBytes(
      ref(anonymousStorage, 'feedback_screenshots/anonymous/anonymous.jpg'),
      new Uint8Array([0xff, 0xd8, 0xff, 0xd9]),
      { contentType: 'image/jpeg' },
    ),
  );

  await assertSucceeds(deleteObject(validRef));
  console.log('Stage 9 Firebase rules tests passed.');
} finally {
  await testEnvironment.cleanup();
}
