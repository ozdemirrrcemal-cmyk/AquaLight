# Feedback and local media contract

## Feedback submission

The feedback form accepts a category, an optional email address and a message. Form state survives
view recreation, but an interrupted submission is never replayed automatically.

The UI delegates submission to `FeedbackSubmissionUseCase`. `FirebaseFeedbackRepository` is the
only component that talks to Firebase Auth and Firestore. It requires an authenticated owner,
propagates coroutine cancellation and maps persistence failures to typed results.

Each `feedback_items` document contains exactly:

- `category`
- `email`
- `message`
- `platform`
- `appVersion`
- `locale`
- `status`
- `userId`
- `createdAt`

Firestore accepts a create only when `request.auth.uid` matches `userId`. Owners may query, read
and delete their own records; cross-owner access and client updates are denied. Category, email,
message and metadata limits are enforced both before submission and by the server rules where
applicable.

## Profile and aquarium photos

Profile and aquarium photos use a separate local-media boundary:

1. `BoundedImageProcessor` performs bounded, cancellable image preparation off the main thread.
2. `MediaFlowCoordinatorViewModel` owns camera/crop state across configuration and process
   recreation.
3. `AppMediaStorage` records new candidates until a repository durably commits the owning profile
   or aquarium record.
4. Replaced files are deleted only after the new domain state commits.
5. Recovery is scoped to the immutable owner identity and never removes another owner's files.
6. Feature Fragments do not decode bitmaps, write files or construct platform dependencies.

These records protect only current transactions from interruption.

## Release gates

- feedback use-case, repository and ViewModel unit tests;
- authenticated Firestore rule tests with strict field allowlisting;
- bounded image, camera/crop recreation and owner-isolation instrumentation tests;
- architecture guards, lint, Debug and minified Release builds;
- API 27 and current-API emulator coverage;
- CodeQL and focused physical-device verification.
