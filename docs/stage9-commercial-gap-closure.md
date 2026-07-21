# Stage 9 commercial gap closure

This branch closes the remaining shared-media commercial risks identified after the initial Stage 9 migration. Feedback submission is now an independent text-only flow.

## Enforced guarantees

- Feedback contains no attachment selector, media state, upload, journal or Storage dependency.
- Firestore accepts only the strict text-feedback schema and Firebase Storage is globally denied.
- Profile, tank creation and tank settings sources pass through the same bounded IO media processor before crop.
- Source bytes, decoded pixels, output dimensions and output bytes are bounded before user media is persisted.
- App-owned crop results are synchronously journaled with the immutable owner UID before a domain store may reference them.
- A domain commit removes the pending journal entry; a pre-commit failure rolls the candidate back.
- Process startup reconciles pending media against the active owner's durable profile and tank references.
- Account cleanup discards only the departing owner's pending candidates.
- Tank creation drafts survive process death and are cleared only after commit or explicit flow cancellation.
- Profile and tank persistence rethrow coroutine cancellation and never delete a URI after its durable commit.
- Tank duplication never reuses a source file as the duplicate's owned photo.

## Release validation

Commercial completion still requires the branch CI matrix, Firebase emulator rules, API 27/current API instrumentation, minified release build, a physical text-feedback submission and focused physical-device camera/gallery/process-death tests to pass.
