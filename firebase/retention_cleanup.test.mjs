import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import {
  positiveInteger,
  retentionCutoff,
  validateFeedbackPath,
} from './retention_cleanup.mjs';

test('retention cutoff uses twelve calendar months', () => {
  assert.equal(
    retentionCutoff(new Date('2026-07-22T12:00:00.000Z')).toISOString(),
    '2025-07-22T12:00:00.000Z',
  );
});

test('maintenance limits accept only positive integers', () => {
  assert.equal(positiveInteger(undefined, 250, 'batch'), 250);
  assert.equal(positiveInteger('100', 250, 'batch'), 100);
  assert.throws(() => positiveInteger('0', 250, 'batch'));
  assert.throws(() => positiveInteger('not-a-number', 250, 'batch'));
});

test('feedback path and owner must agree', () => {
  const validDocument = {
    ref: { path: 'feedback_items/owner-a/submissions/submission-a' },
    data: () => ({ userId: 'owner-a' }),
  };
  assert.doesNotThrow(() => validateFeedbackPath(validDocument));

  assert.throws(() => validateFeedbackPath({
    ...validDocument,
    data: () => ({ userId: 'owner-b' }),
  }));
  assert.throws(() => validateFeedbackPath({
    ...validDocument,
    ref: { path: 'unrelated/owner-a/submissions/submission-a' },
  }));
});

test('deployed indexes support the ordered collection-group retention query', () => {
  const indexPath = fileURLToPath(
    new URL('../firestore.indexes.json', import.meta.url),
  );
  const configuration = JSON.parse(readFileSync(indexPath, 'utf8'));
  const createdAtOverride = configuration.fieldOverrides.find(
    (override) => override.collectionGroup === 'submissions' &&
      override.fieldPath === 'createdAt',
  );

  assert.ok(createdAtOverride, 'submissions.createdAt field override is required');
  assert.ok(
    createdAtOverride.indexes.some(
      (index) => index.order === 'ASCENDING' &&
        index.queryScope === 'COLLECTION_GROUP',
    ),
    'ascending collection-group index is required',
  );
});
