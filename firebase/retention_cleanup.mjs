import process from 'node:process';
import { pathToFileURL } from 'node:url';
import { Firestore, Timestamp } from '@google-cloud/firestore';

const RETENTION_MONTHS = 12;
const DEFAULT_BATCH_SIZE = 250;
const DEFAULT_MAX_DOCUMENTS = 5000;

export function positiveInteger(value, fallback, name) {
  if (value === undefined || value === '') return fallback;
  const parsed = Number.parseInt(value, 10);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer.`);
  }
  return parsed;
}

export function retentionCutoff(now = new Date()) {
  const cutoff = new Date(now.getTime());
  cutoff.setUTCMonth(cutoff.getUTCMonth() - RETENTION_MONTHS);
  return cutoff;
}

export function validateFeedbackPath(document) {
  const segments = document.ref.path.split('/');
  if (
    segments.length !== 4 ||
    segments[0] !== 'feedback_items' ||
    segments[2] !== 'submissions'
  ) {
    throw new Error('Retention query returned a document outside the feedback boundary.');
  }
  const ownerUid = segments[1];
  const data = document.data();
  if (typeof data.userId !== 'string' || data.userId !== ownerUid) {
    throw new Error('Retention query returned feedback with an owner mismatch.');
  }
}

async function main() {
  const execute = process.argv.includes('--execute');
  const unexpectedArgs = process.argv.slice(2).filter((arg) => arg !== '--execute');
  if (unexpectedArgs.length > 0) {
    throw new Error(`Unknown arguments: ${unexpectedArgs.join(', ')}`);
  }

  const projectId = process.env.AQL_FIREBASE_PROJECT_ID?.trim();
  if (!projectId) {
    throw new Error('AQL_FIREBASE_PROJECT_ID is required.');
  }

  const batchSize = positiveInteger(
    process.env.AQL_RETENTION_BATCH_SIZE,
    DEFAULT_BATCH_SIZE,
    'AQL_RETENTION_BATCH_SIZE',
  );
  const maxDocuments = positiveInteger(
    process.env.AQL_RETENTION_MAX_DOCUMENTS,
    DEFAULT_MAX_DOCUMENTS,
    'AQL_RETENTION_MAX_DOCUMENTS',
  );
  if (batchSize > 500) {
    throw new Error('AQL_RETENTION_BATCH_SIZE cannot exceed Firestore batch limit 500.');
  }

  const firestore = new Firestore({ projectId });
  const cutoffDate = retentionCutoff();
  const cutoff = Timestamp.fromDate(cutoffDate);
  let scanned = 0;
  let deleted = 0;
  let lastDocument = null;

  while (scanned < maxDocuments) {
    const pageSize = Math.min(batchSize, maxDocuments - scanned);
    let query = firestore.collectionGroup('submissions')
      .where('createdAt', '<', cutoff)
      .orderBy('createdAt', 'asc')
      .limit(pageSize);
    if (lastDocument !== null) query = query.startAfter(lastDocument);

    const snapshot = await query.get();
    if (snapshot.empty) break;
    snapshot.docs.forEach(validateFeedbackPath);
    scanned += snapshot.size;

    if (execute) {
      const batch = firestore.batch();
      snapshot.docs.forEach((document) => batch.delete(document.ref));
      await batch.commit();
      deleted += snapshot.size;
    }

    lastDocument = snapshot.docs.at(-1);
    if (snapshot.size < pageSize) break;
  }

  const limitReached = scanned === maxDocuments;
  console.log(JSON.stringify({
    mode: execute ? 'execute' : 'dry-run',
    cutoffUtc: cutoffDate.toISOString(),
    eligibleDocuments: scanned,
    deletedDocuments: deleted,
    safetyLimitReached: limitReached,
  }));

  if (limitReached) {
    throw new Error(
      'Retention safety limit reached; review volume and rerun before declaring completion.',
    );
  }
}

const isCommandLineEntry = process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(process.argv[1]).href;

if (isCommandLineEntry) {
  main().catch((error) => {
    console.error(`Feedback retention cleanup failed: ${error.message}`);
    process.exitCode = 1;
  });
}
