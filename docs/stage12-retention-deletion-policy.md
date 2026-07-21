# Data retention and deletion policy

Status: **commercial release blocker until the operational retention process and production account deletion are verified**

## Principles

- Keep personal data only for a defined product, support, security, or legal purpose.
- Prefer local storage for aquarium, device, schedule, reminder, and usage-counter data.
- The feedback form is text-only and does not accept attachments.
- Do not retain feedback indefinitely.
- Delete owner-scoped feedback when the authenticated user completes account deletion, except where a documented legal obligation requires limited retention.
- A legal hold must be exceptional, documented, access-restricted, and reviewed periodically.

## Retention schedule

| Data category | Storage | Maximum retention | Deletion trigger |
| --- | --- | --- | --- |
| Firebase Authentication account | Firebase Authentication | While the account is active | In-app account deletion or verified data-subject request |
| Local profile and address data | Encrypted or app-private Android storage | Until user deletion, account deletion, app-data clear, or uninstall | User action or account-deletion cleanup |
| Aquarium, tank, schedule, reminder, device, assignment, provisioning, and credential records | App-private Android storage | Until user deletion, account deletion, app-data clear, or uninstall | User action or account-deletion cleanup |
| Local usage counters | Encrypted app-private Android storage | Until account deletion, app-data clear, or uninstall | Account-deletion cleanup or user action |
| Text feedback message and metadata | Cloud Firestore | **12 months from submission**, unless a shorter support need applies or a documented legal hold is active | Scheduled operational review, account deletion, or verified deletion request |
| Security or legal record retained by exception | Restricted service storage | Minimum period required by applicable law or active dispute | Expiry of the documented legal requirement or hold |

## Text-feedback deletion process

Cloud Firestore automatic TTL is not required by the current product and is not used as a release dependency. Until a separately approved automated deletion service exists, the operator must run and evidence a recurring retention review at least monthly:

1. Identify `feedback_items` documents whose `createdAt` value is older than 12 months.
2. Confirm that no documented legal hold applies.
3. Delete the expired documents using an owner-authorized administrative process.
4. Record the review date, operator, query criteria, number of records considered, number deleted, exceptions, and evidence reference.
5. Do not copy feedback message content into the operational log.

The first production review procedure and evidence template must be approved before commercial release. A missed review is a release and operations incident that must be corrected promptly.

## Account deletion

1. The application queries `feedback_items` by the authenticated Firebase UID.
2. Matching documents are deleted in bounded batches.
3. Local cleanup covers aquarium data, care tasks, device assignments, provisioning state, known devices, credentials, profile and tank media, feedback form state, and owner preferences.
4. Deletion operations must be idempotent and safe to retry.
5. Production verification evidence must record the test UID, document counts before and after deletion, local-state result, timestamps, app build, and verifier.

## User requests

- Privacy and deletion requests are received at `support@myaqualight.com`.
- Identity must be verified proportionately before disclosing or deleting account data.
- Requests and outcomes must be logged without copying unnecessary aquarium, feedback, or credential content into the request log.

## Legal review

The durations, legal-hold wording, and operational review cadence require review by a qualified privacy professional before publication. Technical implementation does not replace legal advice.
