# Stage 12 — KVKK / GDPR compliance matrix

Status: **legal and operational verification required before commercial release**

This document is a technical compliance register. It is not legal advice and must be reviewed by a qualified privacy professional.

## 1. Scope decision

| Question | Current assessment | Required evidence / action |
| --- | --- | --- |
| Is the operator established in Türkiye or processing data in Türkiye? | Türkiye is stated in the current public policy. KVKK is therefore treated as applicable. | Verify the legal controller's registered name, address, tax/company details where applicable, and authorized contact. |
| Is the service offered to individuals in the EEA or is their behaviour monitored? | Not established by the repository. | Record supported markets, Play distribution countries, marketing targets, and monitoring practices. Determine whether GDPR Article 3 applies. |
| Is special-category data intentionally requested? | No special-category field is required by the product. Free-text feedback and screenshots could nevertheless contain sensitive data entered by a user. | Add user warning, minimize access, and define deletion/escalation handling for accidentally submitted sensitive content. |
| Is large-scale systematic monitoring performed? | No. Analytics, Crashlytics, and Performance Monitoring are removed. | Maintain dependency and code guards; repeat assessment if telemetry is proposed. |

## 2. KVKK obligations

| Obligation | AquaLight requirement | Status |
| --- | --- | --- |
| Controller identity | Public notice must state the controller's complete legal identity and contact details. | **Blocked — repository only identifies “My AquaLight” and support email.** |
| Aydınlatma / notice | At collection, explain controller identity, purposes, recipients, collection method, legal basis, and data-subject rights. | Partially implemented in Privacy Policy and feedback pre-submit notice; legal review required. |
| Processing condition | Map each data category to a valid KVKK processing condition; do not use blanket consent where another condition applies. | **Blocked — legal-basis register requires counsel approval.** |
| Data minimization and purpose limitation | Collect only account data needed for authentication and support data voluntarily submitted by the user. Keep aquarium/device records local. | Implemented by current architecture; verify each release. |
| Security measures | Apply owner-scoped authorization, Firebase rules, app-private/encrypted storage, transport security, least privilege, deletion and incident procedures. | Technical controls exist; operational access-control and incident evidence required. |
| Retention | Define and enforce maximum periods. | Source-controlled policy added; Firestore committed-record expiry and Storage lifecycle still require production enforcement. |
| Data-subject rights | Provide a verified request channel and procedures for access, correction, deletion and other rights under KVKK Article 11. | Support email exists; identity verification, response workflow and request log are not yet evidenced. |
| Breach response | Assess incidents, preserve minimum evidence, contain exposure, and make required notifications without undue delay. | **Blocked — incident-response runbook and named owner required.** |
| VERBIS assessment | Determine whether the controller is required to register or qualifies for an exemption. | **Blocked — depends on controller identity, headcount, financial data and processing profile.** |

## 3. International transfers under KVKK

Firebase Authentication processes data in the United States. Firestore and Storage locations are not yet verified and may also involve processing outside Türkiye.

Before production processing:

1. Identify the Turkish data exporter and the Google/Firebase data importer or processor entity under the applicable contract.
2. Identify data categories, data subjects, purposes, transfer frequency, retention, recipients, security controls and onward-transfer terms.
3. Determine the valid KVKK Article 9 transfer mechanism.
4. Where the KVKK standard contract is used, select the correct controller-to-processor or other module, obtain valid signatures from authorized parties, and complete the required notification to the Authority within the statutory period.
5. Preserve the signed agreement, authority documents, Turkish text, notification evidence and current subprocessor list.
6. Do not rely on a guessed adequacy decision; the Authority's current guidance states that no adequate-country determination has yet been announced.

Status: **blocked pending legal review and contractual evidence.**

## 4. GDPR obligations when applicable

| GDPR area | AquaLight requirement | Status |
| --- | --- | --- |
| Principles and accountability | Document lawfulness, fairness, transparency, minimization, accuracy, retention, security and accountability. | Partial technical evidence; formal record required. |
| Legal basis | Map each purpose to Article 6 and document legitimate-interest assessments where used. | Blocked pending scope and counsel review. |
| Articles 12–14 transparency | Provide clear controller identity, purposes, legal bases, recipients, transfers, retention, rights, complaint route and source of data. | Partial; controller identity, exact locations and final retention enforcement remain blocked. |
| Articles 15–22 rights | Provide access, rectification, erasure, restriction, portability, objection and applicable automated-decision rights workflows. | Deletion path exists; full request operations not evidenced. |
| Processor contracts | Maintain Article 28-compliant terms with processors. | Firebase/Google terms must be archived and reviewed. |
| Security | Apply Article 32-appropriate controls and testing. | Technical controls exist; operational review required. |
| Breach notification | Establish Articles 33–34 assessment and notification workflow when applicable. | Blocked — runbook required. |
| International transfers | Determine Chapter V mechanism and supplementary measures where required. | Blocked pending market scope, resource locations and contract review. |
| DPIA screening | Document whether processing is likely to create high risk; repeat when telemetry, cloud aquarium sync or new sensors are added. | Screening record required. |
| EU representative / DPO | Assess only after confirming GDPR territorial scope and statutory thresholds. | Not determined. |

## 5. Release evidence required

- Verified legal controller identity and postal address.
- Approved EN/TR Privacy Policy and Terms versions.
- Data inventory and legal-basis register.
- Verified Firebase Auth, Firestore and Storage transfer map.
- Executed transfer mechanism and notification evidence where required.
- Production Firestore and Storage region evidence.
- Retention enforcement evidence.
- Data-subject request procedure and test case.
- Incident-response and breach-notification runbook.
- Google Play Data Safety declaration mapped field-by-field to current code.
- Qualified legal reviewer name, review date, reviewed commit SHA and written approval.

## 6. Change control

Any future analytics, crash reporting, performance monitoring, cloud aquarium synchronization, advertising, profiling, location tracking, or third-party SDK requires a new privacy assessment before code is merged or data collection begins.
