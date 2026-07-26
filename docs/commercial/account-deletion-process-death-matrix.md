# Account-deletion process-death evidence

This matrix verifies the restartable account-deletion transaction used by AquaLight. It is a
technical recovery proof, not a substitute for legal review or a claim that every external
Firebase outage mode can be reproduced locally.

## Release-build process-death matrix

The `releaseSmoke` variant inherits the production `release` configuration, is signed with an
ephemeral CI key, and remains minified and resource-shrunk. The only additional surface is a
CI-only Activity that is absent from the production Release APK.

For every row below, `tools/run_release_smoke.sh` performs these steps on API 27 and API 36:

1. Persist the selected checkpoint with `EncryptedAccountDeletionCheckpointStore`.
2. Record the preparation process ID and render the prepared marker.
3. Run `adb shell am force-stop com.aqua.aqualight` and verify that no app PID remains.
4. Launch the minified APK again and require a different process ID.
5. Resume through the production `AccountDeletionManager` orchestration with deterministic
   boundary fakes, verify exact call counts, and require the checkpoint to be cleared.

| Interrupted durable boundary | Auth state after restart | Cloud cleanup calls | Auth-delete calls | Local cleanup calls | Expected recovery |
|---|---|---:|---:|---:|---|
| `STARTED` | Same owner authenticated | 1 | 1 | 1 | Run the whole transaction once |
| `CLOUD_CLEARED` | Same owner authenticated | 0 | 1 | 1 | Never repeat cloud deletion |
| `AUTH_DELETE_REQUESTED` | Same owner authenticated | 0 | 1 | 1 | Retry the pending Auth deletion |
| `AUTH_DELETE_REQUESTED` after Firebase confirmed deletion | No current Firebase user | 0 | 0 | 1 | Treat absent Auth user as confirmation and continue |
| `ACCOUNT_DELETED` | No current Firebase user | 0 | 0 | 1 | Run only idempotent post-delete cleanup |

Each row also requires exactly one Google access-revocation attempt, one Firebase sign-out attempt,
successful final checkpoint removal, and an `ACCOUNT_DELETION_PROCESS_DEATH_PASS` marker.

## Deterministic failure/retry matrix

`AccountDeletionProcessDeathMatrixTest` creates a fresh manager instance for every simulated
process while retaining only the durable store and external state. It verifies:

| Failure/restart case | Required durable result |
|---|---|
| Cloud cleanup fails transiently | Stay at `STARTED`; a new process retries cloud cleanup |
| Auth deletion requires a fresh login | Stay at `AUTH_DELETE_REQUESTED`; a new process retries Auth only |
| Local cleanup fails twice | Stay at `ACCOUNT_DELETED`; a new process retries post-delete cleanup |
| A different owner is authenticated | Fail closed and run no deletion operation |
| No checkpoint exists | Return without running any deletion operation |

## Evidence location

- Workflow: `Android Emulator Integration Tests`
- Device profiles: Android API 27 and target API 36 emulators
- Build: CI-signed, minified `releaseSmoke` APK
- JVM coverage: debug and release unit-test tasks, including the failure/retry cases above
- Pull request record: AquaLight PR #65 and its head-commit workflow results

The matrix must be rerun if checkpoint stages, deletion ordering, checkpoint encryption, startup
recovery, or the release-smoke runner changes.
