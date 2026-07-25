# Stage 14 Manual Acceptance Contract

The controlled release workflow intentionally cannot publish from emulator and
source-code evidence alone. The following six gates require real-world evidence:

1. Physical phone reboot
2. Real camera, BLE and notification permanent denial
3. Physical Wi-Fi and power interruption
4. Real-device TalkBack review
5. Privacy Policy and Terms legal approval
6. End-to-end signed release candidate validation on a real device

Start from
`config/commercial/stage14-manual-acceptance.example.json`. Replace the release
tag and the exact tagged commit, record each executor and role, set canonical UTC
timestamps, and attach an immutable HTTPS evidence URL or AquaLight evidence URN.
`evidenceSha256` is the lowercase SHA-256 digest of the referenced test or approval
record. Set `approved` to `true` only after that record is final.

The Privacy/Terms gate requires `legal-approver`. The TalkBack gate requires
`accessibility-reviewer` or `qa-engineer`. The final package approval must be
performed by `release-manager` after every gate execution time.

Validate the completed file before provisioning it:

```bash
python3 tools/verify_manual_acceptance.py \
  --acceptance stage14-manual-acceptance.json \
  --release-tag vMAJOR.MINOR.PATCH \
  --commit 40_CHARACTER_LOWERCASE_GIT_SHA \
  --summary stage14-manual-acceptance-summary.json
```

Base64-encode the validated source JSON without line wrapping and store the result
as `AQL_STAGE14_MANUAL_ACCEPTANCE_BASE64` in the protected
`production-release` GitHub environment. Never commit a completed acceptance file.
The workflow decodes it into runner-temporary storage, validates every field
fail-closed, publishes only the normalized summary and removes the source file.

Any missing secret, false approval, unknown field, wrong release identity,
noncanonical role, mutable evidence reference, invalid digest or incomplete gate
set stops the release before signing and publication.
