# Dependabot CI Firebase fixture contract

Dependabot pull-request workflows do not receive the repository's normal GitHub Actions secrets. AquaLight therefore provisions deterministic, non-secret Firebase configuration fixtures only for trusted Dependabot pull requests.

## Trust boundary

The local composite action at `.github/actions/provision-dependabot-firebase` refuses to run unless all of the following are true:

- GitHub Actions is the execution environment.
- The event is `pull_request`.
- The actor is exactly `dependabot[bot]`.
- The repository is exactly `ozdemirrrcemal-cmyk/AquaLight`.
- The pull-request head ref starts with `dependabot/`.

The action preserves any protected input that is already present and fills only missing non-production values.

## Allowed fixtures

The fixture action may populate only:

- `AQL_FIREBASE_DEBUG_CONFIG_BASE64`
- `AQL_FIREBASE_STAGING_CONFIG_BASE64`
- `AQL_FIREBASE_RELEASE_SMOKE_CONFIG_BASE64`

Each generated configuration has a distinct package name, project ID, project number and mobile SDK app ID so that the existing Firebase isolation checks remain effective.

`AQL_FIREBASE_PRODUCTION_CONFIG_BASE64` is never generated. Production release and protected-environment workflows continue to require the real protected Firebase input.

## Security properties

The fixture values are format-valid test data and are not connected to a Firebase project. They are written to `GITHUB_ENV` only after repository checkout and only inside the trusted Dependabot context. The action is local to the reviewed commit, and all external actions remain pinned to immutable commit SHAs.

The unit contract is covered by `tools/tests/test_dependabot_firebase_provision.py`.
