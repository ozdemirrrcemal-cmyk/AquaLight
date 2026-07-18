# Stage 9 — Feedback, Media and Heavy-Work Contract

## Commercial objective

Feedback submission and every user-photo flow must be lifecycle-safe, memory-bounded, process-death recoverable, rollback-capable and testable without Android UI ownership of business operations.

## Mandatory architecture

1. `FeedbackFragment` renders state and forwards user intent only.
2. `FeedbackViewModel` owns form state, selected media state, validation, submission state and one-shot UI events.
3. Submission runs through a suspend `FeedbackSubmissionUseCase` and typed `FeedbackRepository` result contract.
4. Firebase callbacks terminate inside the data adapter and never reach a Fragment or Activity.
5. Repository orchestration and durable journal I/O run on an IO dispatcher.
6. Every selected screenshot is copied once into an app-owned bounded staging file before decode. Declared and unknown-length URI sources use the same byte limit.
7. Bitmap bounds decode, sampled decode, resize, orientation handling and compression run off the main thread from that staged source.
8. Streams, cursors, file descriptors and output streams use structured ownership; bitmap instances are recycled exactly once.
9. Media processing enforces source-byte, source-pixel, decoded-dimension and output-byte limits before upload.
10. Source staging files are deleted on success, failure, cancellation and OOM. Processed output files are unique per operation and deleted on every terminal ownership path.
11. A durable feedback transaction journal is synchronously committed before Storage upload begins.
12. A successful Storage upload followed by a failed Firestore write attempts Storage rollback before returning failure.
13. Rollback failure remains journaled for retry.
14. Startup cleanup verifies the Firestore document from the server before deleting a journaled Storage object. A matching committed document preserves the object; absent documents are cleaned; conflict or unverifiable state fails safe and remains journaled.
15. Firebase Tasks are awaited to their authoritative terminal result because the underlying Task is not cancellable. Coroutine cancellation must never be converted into an ordinary upload/persistence failure.
16. Profile photo, tank creation photo and tank settings photo use one saved-state-capable media session/coordinator contract.
17. Replacing or removing an app-owned profile/tank photo deletes the superseded file only after durable state is committed.
18. Pending camera/crop state survives configuration change and process recreation.
19. No feature Fragment performs bitmap decode, resize, compression, direct file output or Firebase orchestration.
20. No compatibility media implementation or parallel legacy path is retained.

## Required tests

- bounds-first and sampled decoding for oversized images
- declared-length and unknown-length source-byte enforcement
- maximum source-pixel and output-byte enforcement
- corrupt image, generic binary MIME and explicit non-image rejection
- source stream closure on success, rejection and cancellation
- cancellation propagation and staged-file cleanup
- Storage upload success plus Firestore failure rollback
- rollback failure journaling
- process death before upload, after upload and after Firestore commit reconciliation
- Firestore conflict/offline cleanup fail-safe behavior
- local temporary file cleanup on every terminal path
- ViewModel recreation with form and selected media preserved, without automatic submission replay
- camera/crop pending-state recreation and cancellation cleanup
- profile/tank replacement commit and rollback cleanup
- API 27 and current API instrumentation coverage
- minified release-smoke validation

## Completion gate

Stage 9 is complete only when:

- all ten checklist items are implemented,
- no feedback or media heavy work remains in a Fragment,
- all shared media consumers use the same contract,
- rollback and process-death orphan reconciliation are verified,
- architecture guard, lint, Debug/Release unit tests, API 27/current API instrumentation, minified release build and CodeQL are green,
- focused physical tests pass on feedback screenshot, profile photo, tank creation photo and tank settings photo flows.

No backward-compatibility layer is required; obsolete implementations must be removed after migration.
