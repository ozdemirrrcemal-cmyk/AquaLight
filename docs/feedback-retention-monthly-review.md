# Monthly feedback retention review

Status: **approved by the AquaLight operator**

This procedure applies only to the Cloud Firestore collection `feedback_items`. AquaLight feedback is text-only and does not use Cloud Storage attachments.

## Schedule

Run this review once during the first seven calendar days of every month. A review with zero matching records must still be logged.

## Retention rule

Delete feedback documents whose `createdAt` timestamp is older than 12 months, unless a documented legal hold applies.

## Monthly procedure

1. Open Firebase Console and select the production AquaLight project.
2. Open **Firestore Database → Data → feedback_items**.
3. Calculate the cutoff timestamp as the review date minus 12 months.
4. Filter or inspect records by `createdAt` and identify documents older than the cutoff.
5. Confirm that no documented legal hold applies to each document selected for deletion.
6. Delete the expired documents.
7. Confirm that the expired documents no longer appear in `feedback_items`.
8. Save evidence that shows the project, collection, cutoff, result count and completion time. Do not expose or copy feedback message content into the evidence or log.
9. Complete one review record below.

## Review record template

- Review month:
- Review date and time:
- Cutoff timestamp:
- Operator:
- Production project:
- Collection: `feedback_items`
- Documents considered:
- Documents deleted:
- Legal-hold exceptions:
- Result: `PASS` / `FAIL`
- Evidence reference:
- Notes:

## Failure handling

A review is `FAIL` when expired documents cannot be identified, deleted or verified. Record the error without copying feedback content, correct the problem promptly and repeat the review. A missed monthly review is an operations incident and must be completed as soon as it is discovered.

## Approval

By approving this procedure, the operator confirms that:

- the 12-month retention limit will be reviewed monthly;
- zero-result months will still be recorded;
- feedback message content will not be copied into operational logs;
- legal holds will be exceptional and documented;
- failures and missed reviews will be corrected and evidenced.

- Approval status: `APPROVED`
- Approved by: Cemal Özdemir — AquaLight project owner/operator
- Approval date: 2026-07-21
- Approval statement: “Cemal Özdemir olarak aylık feedback saklama ve temizleme prosedürünü onaylıyorum.”
- Approved commit: recorded in the repository history of this approval update
