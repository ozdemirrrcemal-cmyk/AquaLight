import fs from 'node:fs';
import assert from 'node:assert/strict';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  runTransaction,
  serverTimestamp,
  setDoc,
  updateDoc,
} from 'firebase/firestore';
import {
  deleteObject,
  getBytes,
  ref,
  uploadBytes,
} from 'firebase/storage';

const PROJECT_ID = 'demo-aqualight-stage12';
const OWNER = 'owner-a';
const OTHER_OWNER = 'owner-b';
const MAX_BYTES = 3 * 1024 * 1024;
const DAY_MILLIS = 24 * 60 * 60 * 1000;

const testEnvironment = await initializeTestEnvironment({
  projectId: PROJECT_ID,
  firestore: {
    rules: fs.readFileSync('firestore.rules', 'utf8'),
  },
  storage: {
    rules: fs.readFileSync('storage.rules', 'utf8'),
  },
});

function marker(
  ownerUid,
  documentId,
  state = 'pending',
  status = 'media_pending',
  expiresAt = Timestamp.fromMillis(Date.now() + 7 * DAY_MILLIS),
) {
  return {
    userId: ownerUid,
    mediaTransactionState: state,
    status,
    screenshotPath: `feedback_screenshots/${ownerUid}/${documentId}.jpg`,
    createdAt: serverTimestamp(),
    mediaTransactionExpiresAt: expiresAt,
  };
}

function textFeedback(ownerUid = OWNER, overrides = {}) {
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
    mediaTransactionState: 'committed',
    ...overrides,
  };
}

function committedMediaFeedback(ownerUid, documentId, overrides = {}) {
  return {
    ...textFeedback(ownerUid),
    screenshotUrl: 'https://firebasestorage.googleapis.com/example.jpg',
    screenshotPath: `feedback_screenshots/${ownerUid}/${documentId}.jpg`,
    ...overrides,
  };
}

try {
  await testEnvironment.clearFirestore();

  const ownerContext = testEnvironment.authenticatedContext(OWNER);
  const otherContext = testEnvironment.authenticatedContext(OTHER_OWNER);
  const anonymousContext = testEnvironment.unauthenticatedContext();
  const ownerDb = ownerContext.firestore();
  const otherDb = otherContext.firestore();
  const anonymousDb = anonymousContext.firestore();

  // Authenticated text feedback is owner-scoped and immutable from the mobile client.
  const textId = 'owner-text-feedback';
  const textRef = doc(ownerDb, 'feedback_items', textId);
  await assertSucceeds(setDoc(textRef, textFeedback()));
  await assertSucceeds(getDoc(textRef));
  await assertFails(getDoc(doc(otherDb, 'feedback_items', textId)));
  await assertFails(getDoc(doc(anonymousDb, 'feedback_items', textId)));
  await assertFails(getDocs(collection(ownerDb, 'feedback_items')));
  await assertFails(updateDoc(textRef, { message: 'Attempted client edit.' }));
  await assertFails(deleteDoc(textRef));

  // Unauthenticated and spoofed-owner submissions are rejected.
  await assertFails(
    setDoc(
      doc(anonymousDb, 'feedback_items', 'anonymous-text'),
      textFeedback('anonymous'),
    ),
  );
  await assertFails(
    setDoc(
      doc(otherDb, 'feedback_items', 'spoofed-text-owner'),
      textFeedback(OWNER),
    ),
  );

  // Firestore accepts only the documented field set and value types.
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'unknown-field'),
      textFeedback(OWNER, { unexpected: true }),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'wrong-category-type'),
      textFeedback(OWNER, { category: 42 }),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'empty-category'),
      textFeedback(OWNER, { category: '' }),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'short-message'),
      textFeedback(OWNER, { message: 'short' }),
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
      doc(ownerDb, 'feedback_items', 'empty-version'),
      textFeedback(OWNER, { appVersion: '' }),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'empty-locale'),
      textFeedback(OWNER, { locale: '' }),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'oversized-email'),
      textFeedback(OWNER, { email: `${'a'.repeat(310)}@example.com` }),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'client-created-time'),
      textFeedback(
        OWNER,
        { createdAt: Timestamp.fromMillis(Date.now() - DAY_MILLIS) },
      ),
    ),
  );

  // Media feedback must reserve an owner-scoped pending fence before it can commit.
  const mediaId = 'feedback-media-committed';
  const mediaRef = doc(ownerDb, 'feedback_items', mediaId);
  await assertSucceeds(
    runTransaction(ownerDb, async transaction => {
      const snapshot = await transaction.get(mediaRef);
      assert.equal(snapshot.exists(), false);
      transaction.set(mediaRef, marker(OWNER, mediaId));
    }),
  );
  await assertFails(getDoc(doc(otherDb, 'feedback_items', mediaId)));
  await assertSucceeds(
    runTransaction(ownerDb, async transaction => {
      const snapshot = await transaction.get(mediaRef);
      assert.equal(snapshot.data().mediaTransactionState, 'pending');
      transaction.set(mediaRef, committedMediaFeedback(OWNER, mediaId));
    }),
  );
  await assertSucceeds(getDoc(mediaRef));
  await assertFails(
    setDoc(
      doc(otherDb, 'feedback_items', mediaId),
      committedMediaFeedback(OTHER_OWNER, mediaId),
    ),
  );
  await assertFails(updateDoc(mediaRef, { message: 'Committed feedback is immutable.' }));

  // Direct committed-media creation and malformed transaction fences are rejected.
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'direct-media-commit'),
      committedMediaFeedback(OWNER, 'direct-media-commit'),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'marker-extra-field'),
      { ...marker(OWNER, 'marker-extra-field'), unexpected: true },
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'marker-wrong-path'),
      {
        ...marker(OWNER, 'marker-wrong-path'),
        screenshotPath: `feedback_screenshots/${OTHER_OWNER}/marker-wrong-path.jpg`,
      },
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'marker-expired'),
      marker(
        OWNER,
        'marker-expired',
        'pending',
        'media_pending',
        Timestamp.fromMillis(Date.now() - DAY_MILLIS),
      ),
    ),
  );
  await assertFails(
    setDoc(
      doc(ownerDb, 'feedback_items', 'marker-too-long'),
      marker(
        OWNER,
        'marker-too-long',
        'pending',
        'media_pending',
        Timestamp.fromMillis(Date.now() + 9 * DAY_MILLIS),
      ),
    ),
  );

  // Cleanup may create or transition only the owner's exact aborted marker.
  const abortedId = 'feedback-media-aborted';
  const abortedRef = doc(ownerDb, 'feedback_items', abortedId);
  await assertSucceeds(
    setDoc(
      abortedRef,
      marker(OWNER, abortedId, 'aborted', 'media_aborted'),
    ),
  );
  await assertFails(
    setDoc(abortedRef, committedMediaFeedback(OWNER, abortedId)),
  );

  const pendingAbortId = 'feedback-pending-abort';
  const pendingAbortRef = doc(ownerDb, 'feedback_items', pendingAbortId);
  await assertSucceeds(setDoc(pendingAbortRef, marker(OWNER, pendingAbortId)));
  await assertSucceeds(
    updateDoc(pendingAbortRef, {
      mediaTransactionState: 'aborted',
      status: 'media_aborted',
      mediaTransactionExpiresAt: Timestamp.fromMillis(Date.now() + 7 * DAY_MILLIS),
    }),
  );

  // Storage objects are private, owner-scoped, immutable and JPEG-only.
  const ownerStorage = ownerContext.storage();
  const otherStorage = otherContext.storage();
  const anonymousStorage = anonymousContext.storage();
  const validPath = `feedback_screenshots/${OWNER}/valid-feedback-id.jpg`;
  const validRef = ref(ownerStorage, validPath);
  const jpegBytes = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);

  await assertSucceeds(
    uploadBytes(validRef, jpegBytes, { contentType: 'image/jpeg' }),
  );
  await assertSucceeds(getBytes(validRef));
  await assertFails(getBytes(ref(otherStorage, validPath)));
  await assertFails(getBytes(ref(anonymousStorage, validPath)));
  await assertFails(deleteObject(ref(otherStorage, validPath)));
  await assertFails(
    uploadBytes(validRef, jpegBytes, { contentType: 'image/jpeg' }),
  );

  await assertFails(
    uploadBytes(
      ref(ownerStorage, `feedback_screenshots/${OTHER_OWNER}/spoofed-owner.jpg`),
      jpegBytes,
      { contentType: 'image/jpeg' },
    ),
  );
  await assertFails(
    uploadBytes(
      ref(ownerStorage, `feedback_screenshots/${OWNER}/wrong-extension.png`),
      jpegBytes,
      { contentType: 'image/jpeg' },
    ),
  );
  await assertFails(
    uploadBytes(
      ref(ownerStorage, `feedback_screenshots/${OWNER}/wrong-type.jpg`),
      new Uint8Array([1, 2, 3]),
      { contentType: 'text/plain' },
    ),
  );
  await assertFails(
    uploadBytes(
      ref(ownerStorage, `feedback_screenshots/${OWNER}/empty.jpg`),
      new Uint8Array(),
      { contentType: 'image/jpeg' },
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
      ref(ownerStorage, `feedback_screenshots/${OWNER}/nested/file.jpg`),
      jpegBytes,
      { contentType: 'image/jpeg' },
    ),
  );
  await assertFails(
    uploadBytes(
      ref(anonymousStorage, 'feedback_screenshots/anonymous/anonymous.jpg'),
      jpegBytes,
      { contentType: 'image/jpeg' },
    ),
  );

  await assertSucceeds(deleteObject(validRef));
  console.log('Stage 12 Firebase ownership and schema tests passed.');
} finally {
  await testEnvironment.cleanup();
}
