# KVKK / GDPR compliance matrix

Status: **legal and operational verification required before commercial release**

This document is a technical compliance register. It is not legal advice and must be reviewed by a qualified privacy professional.

## 1. Scope decision

| Question | Current assessment | Required evidence / action |
| --- | --- | --- |
| Is the operator established in Türkiye or processing data in Türkiye? | Türkiye is stated in the current public policy. KVKK is therefore treated as applicable. | Verify the legal controller's registered name, address, tax/company details where applicable, and authorized contact. |
| Is the service offered to individuals in the EEA or is their behaviour monitored? | Not established by the repository. | Record supported markets, Play distribution countries, marketing targets, and monitoring practices. Determine whether GDPR Article 3 applies. |
| Is special-category data intentionally requested? | No special-category field is required. Free-text feedback could nevertheless contain sensitive information entered by a user. | Keep the pre-submit warning, minimize access, and define deletion/escalation handling for accidentally submitted sensitive content. |
| Is large-scale systematic monitoring performed? | No. Analytics, Crashlytics, Performance Monitoring, and advertising SDKs are absent. | Maintain dependency and code guards; repeat assessment if telemetry is proposed. |

## 2. Current data architecture

- Firebase Authentication processes account and security data.
- Cloud Firestore stores text feedback and related metadata in `europe-west1`.
- The feedback form does not accept attachments.
- Cloud Storage for Firebase is not included in the Android build and no Storage bucket is required for the product.
- Aquarium, tank, device, schedule, reminder, profile-image, and tank-image data remain in app-controlled local Android storage under the current architecture.

## 3. KVKK obligations

| Obligation | AquaLight requirement | Status |
| --- | --- | --- |
| Controller identity | Public notice must state the controller's complete legal identity and contact details. | **Blocked — repository identifies “My AquaLight” and a support email, but complete legal identity is not verified.** |
| Aydınlatma / notice | At collection, explain controller identity, purposes, recipients, collection method, legal basis, and data-subject rights. | Partially implemented in the Privacy Policy and feedback pre-submit notice; legal review required. |
| Processing condition | Map each data category to a valid KVKK processing condition; do not use blanket consent where another condition applies. | **Blocked — legal-basis register requires counsel approval.** |
| Data minimization and purpose limitation | Collect only account data needed for authentication and text voluntarily submitted for support. Keep aquarium/device records local. | Implemented by current architecture; verify each release. |
| Security measures | Apply owner-scoped Firestore authorization, app-private or encrypted storage, transport security, least privilege, deletion, and incident procedures. | Technical controls exist; operational access-control and incident evidence required. |
| Retention | Define and enforce maximum periods. | Twelve-month text-feedback limit documented; recurring operational deletion process still requires approval and evidence. |
| Data-subject rights | Provide a verified request channel and procedures for access, correction, deletion, and other rights under KVKK Article 11. | Support email exists; identity verification, response workflow, and request log are not yet evidenced. |
| Breach response | Assess incidents, contain exposure, preserve minimum evidence, and make required notifications without undue delay. | **Blocked — incident-response runbook and named owner required.** |
| VERBIS assessment | Determine whether the controller is required to register or qualifies for an exemption. | **Blocked — depends on controller identity, headcount, financial data, and processing profile.** |

## 4. International transfers under KVKK

Firebase Authentication may process data in the United States. Cloud Firestore is configured in `europe-west1`; contractual support, security, and subprocessor operations may still involve international processing.

Before production processing:

1. Identify the Turkish data exporter and applicable Google/Firebase processor entity.
2. Identify data categories, data subjects, purposes, transfer frequency, retention, recipients, security controls, and onward-transfer terms.
3. Determine the valid KVKK Article 9 transfer mechanism.
4. Where a KVKK standard contract is used, select the correct module, obtain valid signatures, and complete the required Authority notification within the statutory period.
5. Preserve the signed agreement, authority documents, Turkish text, notification evidence, and current subprocessor list.
6. Do not rely on a guessed adequacy decision.

Status: **blocked pending legal review and contractual evidence.**

## 5. GDPR obligations when applicable

| GDPR area | AquaLight requirement | Status |
| --- | --- | --- |
| Principles and accountability | Document lawfulness, fairness, transparency, minimization, accuracy, retention, security, and accountability. | Partial technical evidence; formal record required. |
| Legal basis | Map each purpose to Article 6 and document legitimate-interest assessments where used. | Blocked pending scope and counsel review. |
| Articles 12–14 transparency | Provide controller identity, purposes, legal bases, recipients, transfers, retention, rights, complaint route, and source of data. | Partial; controller identity and final operational evidence remain blocked. |
| Articles 15–22 rights | Provide access, rectification, erasure, restriction, portability, objection, and applicable automated-decision workflows. | Deletion path exists; full request operations not evidenced. |
| Processor contracts | Maintain Article 28-compliant terms with processors. | Firebase/Google terms must be archived and reviewed. |
| Security | Apply Article 32-appropriate controls and testing. | Technical controls exist; operational review required. |
| Breach notification | Establish Articles 33–34 assessment and notification workflow when applicable. | Blocked — runbook required. |
| International transfers | Determine Chapter V mechanism and supplementary measures where required. | Blocked pending market scope and contract review. |
| DPIA screening | Document whether processing is likely to create high risk; repeat when telemetry, cloud aquarium sync, or new sensors are added. | Screening record required. |
| EU representative / DPO | Assess only after confirming GDPR territorial scope and statutory thresholds. | Not determined. |

## 6. Release evidence required

- Verified legal controller identity and postal address.
- Approved English and Turkish Privacy Policy and Terms.
- Data inventory and legal-basis register.
- Verified Firebase Authentication and Firestore transfer map.
- Executed transfer mechanism and notification evidence where required.
- Firestore `europe-west1` evidence.
- Text-feedback retention/deletion procedure and first verification record.
- Production account-deletion test evidence.
- Data-subject request procedure and test case.
- Incident-response and breach-notification runbook.
- Google Play Data Safety declaration mapped field-by-field to current code.
- Qualified legal reviewer name, review date, reviewed commit SHA, and written approval.

## 7. Change control

Any future analytics, crash reporting, performance monitoring, cloud aquarium synchronization, binary feedback attachment, advertising, profiling, location tracking, or third-party SDK requires a new privacy, pricing, security, and legal assessment before code is merged or collection begins.
