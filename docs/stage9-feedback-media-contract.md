# Stage 9 — Commercial Text Feedback and Shared Image Media Contract

## Release baseline

AquaLight has not been commercially released. This is the first production release, so the codebase
contains no backward-compatibility aliases, legacy cache paths, migration wrappers or support for the
removed feedback screenshot feature.

The feedback implementation must remain compatible with the Firebase Spark plan. Cloud Functions,
App Check and Play Integrity are not dependencies of this feature.

## Commercial objective

Feedback submission is authenticated and text-only. Profile and aquarium photo flows remain
lifecycle-safe, memory-bounded, owner-isolated, process-death recoverable and testable without
Android UI ownership of business or platform operations.

## Authenticated text feedback

1. `FeedbackFragment` renders form state and forwards user intent only.
2. `FeedbackViewModel` owns saved form state, validation, duplicate-submit locking and one-shot UI
   events.
3. Submission runs through `FeedbackSubmissionUseCase` and a typed `FeedbackRepository` result.
4. `FeedbackSubmissionPolicy` is the shared normalization and validation contract used before the
   persistence boundary.
5. Category is limited to 80 characters, optional email to 254 characters with a 64-character local
   part, and message to 10–500 characters.
6. The repository rejects a missing authenticated owner and never writes anonymous feedback.
7. Each unchanged form owns one UUID v4 submission identity stored in `SavedStateHandle`.
8. Firestore data is stored below `feedback_items/{ownerUid}/submissions/{submissionId}`.
9. Submission uses a Firestore transaction rather than an offline-capable direct `set()`.
10. Firestore transactions fail offline; a 15-second client timeout also terminates the loading state.
11. If an earlier transaction completes after a timeout, retrying the unchanged form reads the same
    document and returns success without creating a duplicate.
12. Editing category, email or message invalidates the UUID and starts a new logical submission.
13. Firestore rules require the path owner to match `request.auth.uid`, deny updates and enforce the
    same field limits.
14. The loading state is cleared before success/failure events are rendered. Failures preserve the
    form and re-enable the send button.
15. No Cloud Function, App Check provider, Play Integrity registration, Firebase Storage dependency,
    upload journal, migration or orphan-recovery path is part of feedback submission.

## Shared profile and aquarium photos

1. Profile photo, tank creation photo and tank settings photo use one saved-state-capable media
   coordinator and one domain-neutral `ImageMediaProcessor` API.
2. The concrete implementation is `AndroidImageMediaProcessor`; no `FeedbackMedia*` alias, wrapper
   or composition property exists.
3. Selected sources are copied into the `image_processing` app cache before decode. There is no
   legacy `feedback_media` FileProvider path or legacy filename support.
4. Declared and unknown source lengths use the same byte limit.
5. Bitmap bounds decode, sampled decode, orientation, resize and compression run off the main
   thread.
6. Streams, cursors, file descriptors and output streams use structured ownership. Temporary files
   are removed on success, failure, cancellation and memory exhaustion.
7. Source bytes, source pixels, decoded dimensions and output bytes are bounded before persistence.
8. App-owned candidates are journaled with immutable owner identity until a domain store commits or
   rolls them back.
9. Replacing or removing an app-owned profile or tank photo deletes the superseded file only after
   durable state is committed.
10. Pending camera and crop state survives configuration change and process recreation.
11. Process and owner-session startup reconcile only the active owner's pending media against
    durable profile and tank references.
12. No feature Fragment performs bitmap processing, direct file output or concrete platform
    dependency construction.

## Account deletion

The Android account-deletion path reads only the authenticated owner's
`feedback_items/{ownerUid}/submissions` collection from the Firestore server. Documents are deleted
in bounded Firestore transactions before the Firebase account is deleted. Server-only reads prevent
stale local cache data from being treated as authoritative; transaction deletes are not queued as
offline writes.

## Required tests

- text form restoration without automatic submission replay
- shared policy normalization, UUID validation, email constraints and 10–500 message enforcement
- synchronous duplicate-submit protection and immutable validated request snapshots
- network failure and timeout terminate loading, preserve the form and reuse the same UUID
- editing after a failed attempt creates a new UUID
- authenticated Firestore transaction persistence and missing-session rejection
- typed validation, authentication, network, persistence failure and cancellation propagation
- Firestore field allowlist, anonymous denial, owner-path isolation, immutable documents and delete
- server-only account cleanup with transaction deletion
- bounds-first and sampled decoding for oversized images
- source closure and temporary-file cleanup on every terminal path
- camera/crop state recreation, commit and rollback cleanup
- owner-isolated pending-media reconciliation
- architecture guard proving Cloud Functions/App Check and all media compatibility shims are absent
- API 27 and current API instrumentation coverage
- minified release-smoke validation

## Completion gate

Stage 9 is complete only when architecture guards, Firestore rules tests, lint, Debug/Release unit
tests, API 27/current API instrumentation, minified release build and CodeQL are green. Focused
physical tests cover offline/online feedback, unchanged-form retry, account deletion and profile/tank
camera/gallery flows.
