# Stage 10 visual validation

## Static no-delta verification

The pre-cleanup branch snapshot and the final working tree were compared after recursively resolving color aliases, dimension aliases, and Aqua style attributes. The comparison covered every modified XML resource that exists in both trees under `layout`, `drawable`, `color`, and `anim`.

- Modified visual XML resources compared: **144**
- Resolved visual contracts equivalent: **144**
- Resolved visual-contract differences: **0**

This verifies that the semantic-token and centralized-style migration retains the same primitive color values, dimensions, and effective view attributes.

## Runtime Light/Dark smoke contract

The minified `releaseSmoke` activity now opens the four primary screens in both Light and Dark mode:

- Aquarium
- Maintenance
- Devices
- Settings

For each theme it validates the real Fragment lifecycle and render bounds, then writes four non-empty PNG captures. The emulator workflow requires exactly four Light and four Dark captures per API level and uploads them in `release-smoke-screens/**` for review.

Commercial approval is granted only after Android CI, CodeQL, and both emulator matrix jobs complete without blockers and the eight visual artifacts per API level are present.
