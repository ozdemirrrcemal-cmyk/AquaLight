# Stage 9 — Feedback, Media and Heavy-Work Contract

## Commercial objective

Feedback submission and every user-photo flow must be lifecycle-safe, memory-bounded, rollback-capable and testable without Android UI ownership of business operations.

## Mandatory architecture

1. `FeedbackFragment` renders state and forwards user intent only.
2. `FeedbackViewModel` owns form state, selected media state, validation, submission state and one-shot UI events.
3. Submission runs through a suspend `SubmitFeedbackUseCase` and typed `FeedbackRepository` result contract.
4. Firebase callbacks terminate inside the data adapter and never reach a Fragment or Activity.
5. Bitmap bounds decode, sampled decode, resize, orientation handling and compression run off the main thread.
6. Streams and file descriptors are always managed with `use`/structured ownership.
7. Media processing enforces source-byte, source-pixel, decoded-dimension and output-byte limits before upload.
8. Temporary local files are unique per operation and deleted on success, failure or cancellation.
9. A successful Storage upload followed by a failed Firestore write must attempt Storage rollback before returning failure.
10. Rollback failure must be represented separately and leave enough metadata for orphan cleanup.
11. Profile photo, tank creation photo and tank settings photo use one saved-state-capable media session/coordinator contract.
12. Replacing or removing an app-owned profile/tank photo deletes the superseded file only after durable state is committed.
13. Pending camera/crop state survives configuration change and process recreation.
14. No feature Fragment performs bitmap decode, resize, compression, direct file output or Firebase orchestration.

## Required tests

- bounds-first and sampled decoding for oversized images
- maximum pixel and output-byte enforcement
- corrupt image and unknown-length URI handling
- stream/file-descriptor closure on success and failure
- Storage upload success plus Firestore failure rollback
- rollback failure reporting
- local temporary file cleanup on every terminal path
- ViewModel recreation with selected media and submission state
- camera/crop pending-state recreation
- profile/tank replacement cleanup
- API 27 and current API instrumentation coverage
- minified release-smoke validation

## Completion gate

Stage 9 is complete only when:

- all ten checklist items are implemented,
- no feedback or media heavy work remains in a Fragment,
- all shared media consumers use the same contract,
- rollback and orphan cleanup are verified,
- architecture guard, lint, unit tests, API 27/current API instrumentation, release build and CodeQL are green,
- focused physical tests pass on feedback screenshot, profile photo, tank creation photo and tank settings photo flows.

No backward-compatibility layer is required; obsolete implementations should be removed after migration.
