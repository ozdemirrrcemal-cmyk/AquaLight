# Stage 9 commercial gap closure

AquaLight is preparing its first commercial release. Feedback is authenticated, text-only and
compatible with the Firebase Spark plan. Profile/tank images use one domain-neutral media
architecture without backward-compatibility shims.

## Enforced guarantees

- Profile, tank creation and tank settings sources pass through `ImageMediaProcessor` before crop.
- Source bytes, decoded pixels, output dimensions and output bytes are bounded before persistence.
- The only staging cache and FileProvider root is `image_processing`.
- App-owned crop results are synchronously journaled with immutable owner UID before a domain store
  may reference them.
- A domain commit removes the pending journal entry; a pre-commit failure rolls the candidate back.
- Process startup reconciles pending media against the active owner's durable profile and tank
  references.
- Account cleanup discards only the departing owner's pending candidates.
- Tank creation drafts survive process death and are cleared only after commit or explicit flow
  cancellation.
- Profile and tank persistence rethrow coroutine cancellation and never delete a URI after its
  durable commit.
- Tank duplication never reuses a source file as the duplicate's owned photo.
- Feedback requires an authenticated UID and applies the same field limits in UI, use case,
  persistence tests and Firestore rules.
- Feedback uses an owner-scoped Firestore transaction. Offline attempts fail instead of entering the
  normal Firestore offline-write queue.
- A stable UUID is reused while the form remains unchanged, so timeout/retry cannot create a second
  feedback document.
- Network failure and timeout close loading, re-enable the send button and preserve the form.
- Account deletion performs a server-only owner-subcollection read and transaction-based deletes.
- Cloud Functions, App Check, Play Integrity, Firebase Storage and Blaze-plan dependencies remain
  absent from the feedback feature.
- Screenshot upload, anonymous feedback and all `FeedbackMedia*` compatibility names remain absent.

## Release validation

Commercial completion requires architecture guards, Firestore emulator tests, Debug/Release unit
and lint checks, API 27/current API instrumentation, minified release build, CodeQL and focused
physical-device offline/online feedback, retry, account deletion, camera/gallery and process-death
tests to pass.
