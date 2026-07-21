# Stage 12 — Data retention and deletion policy

Status: **commercial release blocker until every enforcement check below is verified in production**

## Principles

- Keep personal data only for a defined product, support, security, or legal purpose.
- Prefer local storage for aquarium, device, schedule, reminder, and usage-counter data.
- Do not retain optional feedback content indefinitely.
- Delete all owner-scoped data when the authenticated user completes account deletion, except where a documented legal obligation requires limited retention.
- A legal hold must be exceptional, documented, access-restricted, and reviewed periodically.

## Retention schedule

| Data category | Storage | Maximum retention | Deletion trigger |
| --- | --- | --- | --- |
| Firebase Authentication account | Firebase Authentication | While the account is active | In-app account deletion or verified data-subject request |
| Local profile and address data | Encrypted/app-private Android storage | Until user deletion, account deletion, app-data clear, or uninstall | User action or account-deletion cleanup |
| Aquarium, tank, schedule, reminder, device, assignment, provisioning, and credential records | App-private Android storage | Until user deletion, account deletion, app-data clear, or uninstall | User action or account-deletion cleanup |
| Local usage counters | Encrypted app-private Android storage | Until account deletion, app-data clear, or uninstall | Account-deletion cleanup or user action |
| Committed feedback message and metadata | Cloud Firestore | **12 months from submission** | Automatic retention job, account deletion, or verified deletion request |
| Committed feedback screenshot | Cloud Storage for Firebase | **12 months from upload** | Bucket lifecycle rule, account deletion, or verified deletion request |
| Pending or aborted feedback transaction marker | Cloud Firestore | **7 days** | Firestore TTL on `mediaTransactionExpiresAt` |
| Local feedback screenshot candidate and recovery journal | App-private cache/preferences | Until submission completes, rollback completes, or startup recovery removes it | Immediate transaction cleanup or startup reconciliation |
| Security or legal record retained by exception | Restricted service storage | Minimum period required by applicable law or active dispute | Expiry of the documented legal requirement or hold |

## Required enforcement before commercial release

1. Firestore pending/aborted TTL must be enabled for `feedback_items.mediaTransactionExpiresAt`.
2. Committed feedback documents must receive an immutable retention-expiry value and be deleted no later than 12 months after submission.
3. The Storage bucket must have a lifecycle rule that deletes `feedback_screenshots/` objects no later than 12 months after creation.
4. Deleting an account must delete both indexed feedback records and orphaned objects under `feedback_screenshots/{uid}/`.
5. Local cleanup must cover aquarium data, care tasks, device assignments, provisioning state, known devices, credentials, profile/tank media, feedback cache, feedback recovery journal, and owner preferences.
6. Deletion operations must be idempotent and safe to retry.
7. Production verification evidence must record the Firestore location, Storage location, TTL state, bucket lifecycle state, test user UID, deletion timestamps, and result.

## User requests

- Privacy and deletion requests are received at `support@myaqualight.com`.
- Identity must be verified proportionately before disclosing or deleting account data.
- Requests and outcomes must be logged without copying unnecessary aquarium, feedback, or credential content into the request log.

## Documentation rule

The public Privacy Policy may state the 12-month feedback retention limit only after the Firestore and Storage enforcement controls above are deployed and verified. Until then, the release remains blocked.

## Legal review

The durations and legal-hold wording require review by a qualified privacy professional before production publication. Technical implementation does not replace legal advice.
