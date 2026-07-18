# Stage 9 — Feedback, Media and Heavy-Work Contract

## Commercial objective

Feedback submission and every user-photo flow must be lifecycle-safe, memory-bounded, owner-isolated, process-death recoverable, rollback-capable and testable without Android UI ownership of business operations.

## Mandatory architecture

1. `FeedbackFragment` renders state and forwards user intent only. It receives application/platform interfaces from `AppContainer` and never constructs a concrete media processor or Firebase adapter.
2. `FeedbackViewModel` owns form state, selected media state, validation, submission state and one-shot UI events.
3. Submission runs through a suspend `FeedbackSubmissionUseCase` and typed `FeedbackRepository` result contract.
4. Firebase callbacks terminate inside the data adapter and never reach a Fragment or Activity.
5. Repository orchestration and durable journal I/O run on an IO dispatcher.
6. Every selected screenshot is copied once into an app-owned bounded staging file before decode. Declared and unknown-length URI sources use the same byte limit.
7. Bitmap bounds decode, sampled decode, resize, orientation handling and compression run off the main thread from that staged source.
8. Streams, cursors, file descriptors and output streams use structured ownership; bitmap instances are recycled exactly once.
9. Media processing enforces source-byte, source-pixel, decoded-dimension and output-byte limits before upload.
10. Source staging files are deleted on success, failure, cancellation and OOM. Processed output files are unique per operation and deleted on every terminal ownership path.
11. A durable owner-aware local transaction journal is synchronously committed before any Firebase operation starts.
12. Every journal entry contains the immutable document ID, captured owner UID and exact planned Storage path. Recovery never substitutes the currently signed-in owner.
13. Firestore reserves the generated document ID with a minimal owner-scoped `pending` media marker before Storage upload. The marker contains no feedback message or email.
14. Final feedback persistence is one Firestore transaction that accepts only the same owner, Storage path and `pending` marker, then changes it to `committed` while writing the complete feedback document.
15. Recovery is one Firestore transaction that changes a matching owner/path `pending` marker—or a still-absent document—to an `aborted` fence. A delayed writer cannot overwrite that fence.
16. Storage is deleted only after the server-side owner/path fence is confirmed `aborted`. A matching `committed` fence preserves the object; owner/path conflict or unverifiable state fails safe and remains locally journaled.
17. Aborted/pending fence documents carry an expiry field for the Firebase TTL policy; they contain only transaction metadata, owner UID and the planned Storage path.
18. A successful Storage upload followed by a failed Firestore commit attempts the atomic abort-and-delete path before returning failure.
19. Rollback failure remains journaled for retry.
20. Firebase Tasks are awaited to their authoritative terminal result because the underlying Task is not cancellable. Coroutine cancellation must never be converted into an ordinary upload/persistence failure.
21. Orphan reconciliation starts at application process startup and is not dependent on opening the Feedback screen.
22. Profile photo, tank creation photo and tank settings photo use one saved-state-capable media session/coordinator contract.
23. Replacing or removing an app-owned profile/tank photo deletes the superseded file only after durable state is committed.
24. Pending camera/crop state survives configuration change and process recreation.
25. Production and minified release-smoke composition expose the same feedback media boundary.
26. No feature Fragment performs bitmap decode, resize, compression, direct file output, Firebase orchestration or concrete platform dependency construction.
27. No compatibility media implementation or parallel legacy path is retained.

## Required tests

- bounds-first and sampled decoding for oversized images
- declared-length and unknown-length source-byte enforcement
- maximum source-pixel and output-byte enforcement
- corrupt image, generic binary MIME and explicit non-image rejection
- source stream closure on success, rejection and cancellation
- cancellation propagation and staged-file cleanup
- owner-aware local journal durability across Android store recreation
- Firestore owner reservation before Storage upload
- atomic owner/path pending-to-committed transition
- Storage upload success plus Firestore failure abort and rollback
- ambiguous commit acknowledgement reconciled from the server fence
- rollback failure journaling
- process death before reservation, after reservation, after upload and after Firestore commit
- owner/path conflict and offline cleanup fail-safe behavior
- application-startup recovery wiring
- local temporary file cleanup on every terminal path
- ViewModel recreation with form and selected media preserved, without automatic submission replay
- camera/crop pending-state recreation and cancellation cleanup
- profile/tank replacement commit and rollback cleanup
- API 27 and current API instrumentation coverage
- minified release-smoke validation

## Firebase operational requirement

The Firestore field `mediaTransactionExpiresAt` must be configured as a TTL field before commercial release. TTL removes old minimal `pending`/`aborted` transaction fences after the safety window. Normal committed feedback documents do not retain this field.

Firestore and Storage rules must permit only the captured owner—or the explicitly supported anonymous feedback policy—to reserve, finalize, reconcile and delete the matching owner-scoped path. This policy is finalized with the Firebase/privacy release stage.

## Completion gate

Stage 9 is complete only when:

- all ten checklist items are implemented,
- no feedback or media heavy work remains in a Fragment,
- all shared media consumers use the same contract,
- rollback, owner isolation and process-death orphan reconciliation are verified,
- architecture guard, lint, Debug/Release unit tests, API 27/current API instrumentation, minified release build and CodeQL are green,
- focused physical tests pass on feedback screenshot, profile photo, tank creation photo and tank settings photo flows,
- the Firebase TTL field and matching production security rules above are enabled before release.

No backward-compatibility layer is required; obsolete implementations must be removed after migration.
