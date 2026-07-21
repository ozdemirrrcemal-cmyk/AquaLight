# Stage 9 — Text feedback and shared local media contract

## Commercial objective

Feedback submission is text-only. Profile and aquarium photos remain lifecycle-safe,
memory-bounded, owner-isolated, process-death recoverable and rollback-capable through a separate
shared local-media boundary.

## Text feedback architecture

1. `FeedbackFragment` renders state and forwards form intent only.
2. `FeedbackViewModel` owns category, optional email, message, validation, submission state and
   one-shot success/failure events.
3. Submission runs through `FeedbackSubmissionUseCase.submit(request)` and the typed
   `FeedbackRepository.submit(request)` contract.
4. The feedback application contract contains no Android `File`, URI, bitmap, upload, rollback or
   orphan-cleanup API.
5. The data adapter writes one Firestore document containing only category, email, message,
   platform, app version, locale, status, user ID and server creation time.
6. An authenticated submission captures the current Firebase UID. A signed-out submission uses
   the explicit `anonymous` identity.
7. Firebase callback/task handling terminates inside the data adapter. Cancellation is propagated;
   ordinary persistence errors are returned as typed failures.
8. The Feedback screen contains no attachment selector, screenshot saved state, media progress,
   bitmap work or Storage dependency.

## Firebase security contract

- `feedback_items` create is field-allowlisted and accepts only valid text feedback.
- An authenticated caller may create only with its own UID; an unauthenticated caller may create
  only with `userId == "anonymous"`.
- Authenticated owners may query, read and delete their own feedback records so account cleanup can
  complete. Cross-owner access is denied.
- Client updates are always denied.
- Screenshot URLs, paths, media transaction markers and expiry fields are rejected as unexpected
  fields.
- Firebase Storage is decommissioned with a global deny-all ruleset. The policy remains deployed to
  block retired app versions from accessing objects.

## Shared profile and aquarium media architecture

1. Profile photo, tank creation photo and tank settings photo use one saved-state-capable media
   session/coordinator contract.
2. Selected sources pass through the neutral `BoundedImageProcessor` implementation. This shared
   infrastructure is exposed only to profile/tank photo flows, never to the Feedback screen.
3. Declared and unknown-length sources use the same byte limit. Bounds decode, sampled decode,
   orientation handling, resize and compression run off the main thread.
4. Streams, descriptors and outputs use structured ownership. Source byte, source pixel,
   decoded dimension and output byte limits are enforced.
5. Pending camera/crop state survives configuration change and process recreation.
6. App-owned candidates are synchronously journaled with immutable owner identity. Replacing or
   removing a committed profile/tank photo deletes the superseded file only after durable domain
   state commits.
7. Startup and owner-session reconciliation never delete another owner's media.
8. No feature Fragment performs bitmap decode, direct file output or concrete platform dependency
   construction.
9. No compatibility media implementation or parallel legacy path is retained.

## Required tests

### Feedback

- form state restoration without automatic submission replay;
- validation and synchronous double-submit protection;
- successful text-only request forwarding and form reset;
- persistence failure with form preservation for retry;
- authenticated owner and anonymous document mapping;
- cancellation propagation;
- Firestore owner create/read/delete policy and cross-owner/update denial;
- unexpected screenshot/media fields rejected;
- global Storage read/write/delete denial.

### Shared local media

- bounds-first and sampled decoding for oversized images;
- declared-length and unknown-length byte enforcement;
- corrupt image, generic binary MIME and explicit non-image rejection;
- stream closure and staged-file cleanup on success, rejection and cancellation;
- profile/tank camera and crop recreation;
- replacement commit, rollback and owner-isolated recovery;
- API 27 and current API instrumentation coverage;
- minified release-smoke validation.

## Operational decommission requirement

The deny-all Storage ruleset must be deployed before removing Storage configuration from the
repository. Source removal does not delete previously uploaded objects; any retained objects require
a separately authorized administrative cleanup. The retired `mediaTransactionExpiresAt` TTL field
override must not remain in the Firestore index configuration.

## Completion gate

Stage 9 remains complete only while:

- the Feedback feature is text-only from UI through Firebase rules;
- no screenshot journal, upload, rollback, TTL or orphan-cleanup path remains;
- all profile/tank consumers retain the shared bounded-media contract;
- architecture guard, Firebase emulator rules, unit tests, lint, Debug/Release builds, API 27/current
  instrumentation and CodeQL are green;
- physical text feedback, profile photo, tank creation photo and tank settings photo tests pass.

No backward-compatibility layer is required.
