# Stage 14 Signed Candidate Acceptance

Stage 14 uses a build-once, finalize-without-rebuild contract. The `candidate`
phase first creates the production-signed, minified APK and AAB. Physical testing
is performed against that APK. The `finalize` phase accepts only the same
candidate workflow run and the same artifact digests.

This contract is deliberately limited to the Stage 14 technical closure. Full
TalkBack review, final Privacy Policy/Terms approval, Google Play declarations
and store rollout happen after the device menus, UI and data flows are complete.
They are not simulated or prematurely approved here.

## Required physical checks

Test the APK downloaded from the successful
`AquaLight-Candidate-vMAJOR.MINOR.PATCH` workflow artifact:

1. Clean install and first launch complete without crash or ANR.
2. Login, logout and account switching preserve owner isolation.
3. Force-stop, relaunch and a physical phone reboot recover safely.
4. Permission denial and a real connectivity interruption do not crash or leak
   stale state.
5. The currently implemented critical path completes end to end on the signed
   APK.

Record immutable evidence for each check. The evidence may be an immutable HTTPS
URL or an AquaLight evidence URN, and `evidenceSha256` must be the lowercase
SHA-256 digest of that record.

## Bind acceptance to the candidate

Start from
`config/commercial/stage14-manual-acceptance.example.json`. Copy the following
values from `CANDIDATE.json` and the selected workflow run:

- `releaseTag`
- `releaseCommit`
- `candidateApproval.workflowRunId`
- `candidateApproval.signingCertificateSha256`
- the AAB, APK and mapping digests in `candidateApproval`
- SHA-256 of the complete `CANDIDATE.json` file as
  `candidateApproval.manifestSha256`

Do not edit, rebuild or re-sign the downloaded candidate. If any artifact changes,
run the candidate phase again and repeat physical acceptance against the new run.

After all five checks pass, the release manager records package approval later
than every gate execution time. Validate the completed source file with:

```bash
python3 tools/verify_manual_acceptance.py \
  --acceptance stage14-manual-acceptance.json \
  --release-tag vMAJOR.MINOR.PATCH \
  --commit 40_CHARACTER_LOWERCASE_GIT_SHA \
  --candidate-manifest CANDIDATE.json \
  --summary stage14-manual-acceptance-summary.json
```

Base64-encode the validated source JSON without line wrapping and store it as
`AQL_STAGE14_MANUAL_ACCEPTANCE_BASE64` in the protected
`production-release` GitHub environment. Never commit a completed acceptance
file.

The `finalize` phase downloads the immutable candidate artifact by workflow run
ID, rehashes every signed deliverable, validates this acceptance against
`CANDIDATE.json`, and creates the final GitHub Actions archive without invoking
Gradle or any signing task. Missing evidence, a different run, tag, commit,
certificate or artifact digest stops finalization.
