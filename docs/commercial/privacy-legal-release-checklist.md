# Privacy and legal commercial release checklist

This file is a Google Play production-publication gate, not a claim of legal compliance.

`assembleRelease`, signed release APK/AAB generation, CI, and internal release-build testing remain available while items below are open. An artifact built for testing is **not approved for Google Play production publication** until every applicable unchecked item is closed, evidenced, and signed off. Run `python3 tools/google_play_publication_guard.py` immediately before store submission; this publication-only command is deliberately not wired into `assembleRelease` or the normal Android release CI.

## Technical controls

- [x] Production Firebase runtime limited to Authentication and Cloud Firestore.
- [x] Firebase Analytics, Crashlytics, Performance, Realtime Database, Remote Config and Messaging dependencies/plugins removed.
- [x] CI guard prevents forbidden Firebase/telemetry SDK reintroduction.
- [x] Cloud Firestore client configured for memory-only cache before first use.
- [x] Feedback data fields and absence of screenshots are disclosed in the Privacy/KVKK notice, which is linked and accessible from the feedback submission point.
- [x] Privacy and Terms provided in Turkish and English and selected from the active app language.
- [x] Privacy/Terms/licenses use a shared locked-down `WebViewAssetLoader` origin; JavaScript, DOM storage, file/content access, mixed content and network loads are disabled.
- [x] Email registration and Google Sign-In have no Terms-acceptance gate; Privacy and Terms remain available in Settings → Privacy & Legal, are not duplicated on the About screen, and are not added to the home screen. AquaLight has no numeric age gate, age declaration or age-data collection, and Google Sign-In authenticates an existing provider account without creating a Google account inside AquaLight.
- [x] Local usage copy states that counters stay on-device and are not analytics.
- [x] Account deletion covers the defined cloud/local boundary and has restartable recovery state.
- [x] Twelve-month feedback cleanup is implemented as a dry-run-first, bounded, allow-listed manual admin-panel control with an 11-month operational cutoff.

## Required production configuration

- [ ] Confirm the final legal controller/provider identity and insert it into both Turkish and English Privacy/Terms assets and the hosted Privacy Policy.
- [ ] Insert the official, publishable postal address into both Turkish and English Privacy/Terms assets and the hosted Privacy Policy.
- [ ] Insert the monitored privacy/support email into both Turkish and English Privacy/Terms assets, the hosted Privacy Policy, and `PUBLICATION_SUPPORT_EMAIL` in the secure WebView allowlist; document request ownership and response SLA.
- [ ] Remove every `AQL_GOOGLE_PLAY_PUBLICATION_PENDING` marker and every empty `data-aql-publication-field` after the final identity/contact values are inserted.
- [x] Enable Google Authentication for the admin panel and provision the intended Firebase UID only through an immutable `admin_access/{uid}` Firestore document with `role = feedback-admin`; keep the UID out of source control. (Validated 2026-07-22: Google sign-in succeeded, the role-less UID was denied, and access succeeded only after the immutable role document was provisioned.)
- [x] Deploy the hardened admin panel and revised Firestore Rules, then verify signed-out and non-admin accounts cannot read, list, delete or grant access. (Validated 2026-07-22: the deployed panel enforced the sign-in gate and denied the authenticated UID before `feedback-admin` provisioning.)
- [x] Deploy `firestore.indexes.json` to the production project and wait until the `submissions.createdAt` collection-group index is enabled before activating retention deletion. (Validated 2026-07-22: the index became active and feedback submissions loaded in the admin panel without an index error.)
- [x] Run the admin-panel dry-run, review the count, execute a bounded deletion against controlled expired fixtures, verify the atomic audit record, assign the monthly operator/reminder, and retain destruction evidence for at least three years. (Validated 2026-07-22: the controlled expired fixture was selected and deleted, current records were preserved, and the `retention_audits` record was created. Monthly operator: Cemal Özdemir. Reminder: first day of each month at 10:00 Europe/Istanbul. Destruction evidence remains in `retention_audits` for at least three years.)
- [x] Verify the production Firebase project's Firestore location again in Console before release; it matches `europe-west1` (evidence: `docs/commercial/firebase-production-evidence.md`, 2026-07-22).
- [x] Verify Firebase project access, MFA, least privilege, audit ownership, incident contact and offboarding. (Validated 2026-07-22: the sole human Firebase project member is Cemal Özdemir as Owner; 2-Step Verification and Google Authenticator are enabled; Google-managed service accounts were left unchanged; `admin_access` contains only the intended `feedback-admin` UID. Security/audit, incident-response and offboarding ownership: Cemal Özdemir. Offboarding order: revoke `admin_access`, remove Firebase/Google Cloud IAM and GitHub/deploy access, revoke any service-account keys, and record the action.)
- [x] Execute the complete account-deletion test matrix on a physical/emulated release build, including process death after each durable stage (automated evidence: `docs/commercial/account-deletion-process-death-matrix.md`; API 27/35 minified release-smoke CI).
- [ ] Align Google Play Data Safety, account-deletion URL/form, privacy-policy URL and in-app disclosures with `data-inventory-and-retention.md`.

## Mandatory legal review blockers

- [ ] Turkish counsel confirms the KVKK Article 5 processing conditions for every row in the inventory and documents any legitimate-interest balancing test.
- [ ] Select and complete the lawful KVKK Article 9 transfer mechanism for Firebase Authentication/Google access. If a KVKK standard contract is used, execute the correct controller-to-processor form and complete the required notification within the statutory period.
- [ ] Confirm the Google/Firebase Data Processing and Security Terms, subprocessor list, controller/processor roles, and supplementary transfer/security assessment.
- [ ] EU/EEA counsel confirms GDPR territorial scope, controller identity, lawful bases, Article 13 notice, processor contract/SCC/DPF reliance and transfer-impact assessment.
- [ ] Assess and, where required, appoint/list an EU representative under GDPR Article 27. The app must not be released in affected EU markets until the assessment and any appointment are complete.
- [ ] Assess DPO requirements, VERBIS registration/exemption, data-controller application procedure, records of processing, incident/breach process and response deadlines.
- [ ] Review the no-numeric-age-gate product decision and legal-capacity clause against consumer law, and complete target-audience and store age-rating declarations for every target country.
- [ ] Consumer-law counsel reviews warranty, liability, governing-law, device-safety and termination clauses for Türkiye and target European markets.
- [ ] A qualified legal reviewer approves the final Turkish and English Privacy/KVKK and Terms texts in writing. Record reviewer, version/hash, date, approved markets and required changes.

## Release sign-off

- [ ] Record every required release sign-off and its evidence/reference in the table below.

| Role | Name | Date | Evidence/reference |
|---|---|---|---|
| Engineering |  |  |  |
| Security/privacy engineering |  |  |  |
| Product owner |  |  |  |
| Turkish legal counsel |  |  |  |
| EU/EEA legal counsel |  |  |  |

Do not label AquaLight “KVKK compliant”, “GDPR compliant”, or “legally ready for publication” until every applicable blocker above is closed and evidenced.
