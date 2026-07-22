# Privacy and legal commercial release checklist

This file is a Google Play production-publication gate, not a claim of legal compliance.

`assembleRelease`, signed release APK/AAB generation, CI, and internal release-build testing remain available while items below are open. An artifact built for testing is **not approved for Google Play production publication** until every applicable unchecked item is closed, evidenced, and signed off. Run `python3 tools/google_play_publication_guard.py` immediately before store submission; this publication-only command is deliberately not wired into `assembleRelease` or the normal Android release CI.

## Technical controls

- [x] Production Firebase runtime limited to Authentication and Cloud Firestore.
- [x] Firebase Analytics, Crashlytics, Performance, Realtime Database, Remote Config and Messaging dependencies/plugins removed.
- [x] CI guard prevents forbidden Firebase/telemetry SDK reintroduction.
- [x] Cloud Firestore client configured for memory-only cache before first use.
- [x] Feedback data fields and absence of screenshots disclosed at the submission point.
- [x] Privacy and Terms provided in Turkish and English and selected from the active app language.
- [x] Privacy/Terms/licenses use a shared locked-down `WebViewAssetLoader` origin; JavaScript, DOM storage, file/content access, mixed content and network loads are disabled.
- [x] Terms acceptance and 18+ declaration are separate and required before registration/Google authentication.
- [x] Local usage copy states that counters stay on-device and are not analytics.
- [x] Account deletion covers the defined cloud/local boundary and has restartable recovery state.
- [x] Twelve-month feedback cleanup is implemented as dry-run-first, bounded, keyless scheduled maintenance.

## Required production configuration

- [ ] Confirm the final legal controller/provider identity and insert it into both Turkish and English Privacy/Terms assets and the hosted Privacy Policy.
- [ ] Insert the official, publishable postal address into both Turkish and English Privacy/Terms assets and the hosted Privacy Policy.
- [ ] Insert the monitored privacy/support email into both Turkish and English Privacy/Terms assets, the hosted Privacy Policy, and `PUBLICATION_SUPPORT_EMAIL` in the secure WebView allowlist; document request ownership and response SLA.
- [ ] Remove every `AQL_GOOGLE_PLAY_PUBLICATION_PENDING` marker and every empty `data-aql-publication-field` after the final identity/contact values are inserted.
- [ ] Configure GitHub variables `AQL_FIREBASE_PROJECT_ID`, `AQL_RETENTION_WIF_PROVIDER`, and `AQL_RETENTION_SERVICE_ACCOUNT`.
- [ ] Grant the retention service account only the minimum Firestore delete/query role needed; do not create a JSON service-account key.
- [ ] Deploy `firestore.indexes.json` to the production project and wait until the `submissions.createdAt` collection-group index is enabled before activating retention deletion.
- [ ] Run the retention workflow in dry-run, review the count, run an approved execute test against controlled expired fixtures, and retain evidence.
- [ ] Verify the production Firebase project's Firestore location again in Console before release; it must match `europe-west1`.
- [ ] Verify Firebase project access, MFA, least privilege, audit ownership, incident contact and offboarding.
- [ ] Execute the complete account-deletion test matrix on a physical/emulated release build, including process death after each durable stage.
- [ ] Align Google Play Data Safety, account-deletion URL/form, privacy-policy URL and in-app disclosures with `data-inventory-and-retention.md`.

## Mandatory legal review blockers

- [ ] Turkish counsel confirms the KVKK Article 5 processing conditions for every row in the inventory and documents any legitimate-interest balancing test.
- [ ] Select and complete the lawful KVKK Article 9 transfer mechanism for Firebase Authentication/Google access. If a KVKK standard contract is used, execute the correct controller-to-processor form and complete the required notification within the statutory period.
- [ ] Confirm the Google/Firebase Data Processing and Security Terms, subprocessor list, controller/processor roles, and supplementary transfer/security assessment.
- [ ] EU/EEA counsel confirms GDPR territorial scope, controller identity, lawful bases, Article 13 notice, processor contract/SCC/DPF reliance and transfer-impact assessment.
- [ ] Assess and, where required, appoint/list an EU representative under GDPR Article 27. The app must not be released in affected EU markets until the assessment and any appointment are complete.
- [ ] Assess DPO requirements, VERBIS registration/exemption, data-controller application procedure, records of processing, incident/breach process and response deadlines.
- [ ] Review the 18+ product decision against consumer law and store age-rating declarations in every target country.
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
