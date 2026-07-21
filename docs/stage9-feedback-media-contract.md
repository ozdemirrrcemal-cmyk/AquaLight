# Stage 9 — Text Feedback and Shared Media Contract

## Commercial objective

Feedback submission is text-only. Profile and aquarium photo flows remain lifecycle-safe,
memory-bounded, owner-isolated, process-death recoverable and testable without Android UI ownership
of business or platform operations.

## Text feedback

1. `FeedbackFragment` renders form state and forwards user intent only.
2. `FeedbackViewModel` owns saved form state, validation, duplicate-submit locking and one-shot UI
   events.
3. Submission runs through `FeedbackSubmissionUseCase` and a typed `FeedbackRepository` result.
4. The repository writes exactly one field-allowlisted Firestore document on an IO dispatcher.
5. Firebase callbacks terminate inside the data adapter and never reach a Fragment or Activity.
6. Authenticated submissions use the captured account UID; unauthenticated submissions use the
   explicit `anonymous` identity.
7. Cancellation propagates and is never converted into a normal persistence failure.
8. No upload, local upload journal, remote object path, transaction fence or orphan-recovery path is
   part of feedback submission.

## Shared profile and aquarium photos

1. Profile photo, tank creation photo and tank settings photo use one saved-state-capable media
   coordinator.
2. Selected sources are copied into a bounded staging file before decode. Declared and unknown
   source lengths use the same byte limit.
3. Bitmap bounds decode, sampled decode, orientation, resize and compression run off the main
   thread.
4. Streams, cursors, file descriptors and output streams use structured ownership. Temporary files
   are removed on success, failure, cancellation and memory exhaustion.
5. Source bytes, source pixels, decoded dimensions and output bytes are bounded before persistence.
6. App-owned candidates are journaled with immutable owner identity until a domain store commits or
   rolls them back.
7. Replacing or removing an app-owned profile or tank photo deletes the superseded file only after
   durable state is committed.
8. Pending camera and crop state survives configuration change and process recreation.
9. Process and owner-session startup reconcile only the active owner's pending media against durable
   profile and tank references.
10. No feature Fragment performs bitmap processing, direct file output or concrete platform
    dependency construction.

## Required tests

- text form restoration without automatic submission replay
- validation and synchronous duplicate-submit protection
- successful owner and anonymous Firestore persistence
- typed persistence failure and cancellation propagation
- Firestore field allowlist and cross-owner access denial
- bounds-first and sampled decoding for oversized images
- declared-length and unknown-length byte enforcement
- corrupt image, binary MIME and explicit non-image rejection
- source closure and temporary-file cleanup on every terminal path
- camera/crop state recreation, commit and rollback cleanup
- owner-isolated pending-media reconciliation
- API 27 and current API instrumentation coverage
- minified release-smoke validation

## Completion gate

Stage 9 is complete only when the architecture guards, Firestore rules tests, lint, Debug/Release
unit tests, API 27/current API instrumentation, minified release build and CodeQL are green. Focused
physical tests cover text feedback plus profile and aquarium camera/gallery flows.

No backward-compatibility layer is required; obsolete upload and journal implementations stay
removed.
