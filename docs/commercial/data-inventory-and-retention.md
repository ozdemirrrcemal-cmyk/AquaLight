# AquaLight commercial data inventory and retention record

Status: implementation baseline for the 2026 commercial release. The release checklist remains authoritative for unresolved legal and operational approvals.

## Controller publication inputs

| Field | Current value | Release control |
|---|---|---|
| Controller/trade name |  | Add the final legal identity before Google Play production publication. |
| Address |  | Add the official, publishable postal address before Google Play production publication. |
| Privacy/support channel |  | Add the monitored privacy/support mailbox before Google Play production publication. |
| Target market | Türkiye and European countries | Complete country-specific consumer/privacy review. |
| Firestore location | `europe-west1` (Belgium) | Verified from the Firebase Console screenshot supplied on 22 July 2026. |

## Authoritative data-flow inventory

| Data or operation | Fields/examples | Purpose | Storage/recipient | Legal-basis candidate | Retention/deletion |
|---|---|---|---|---|---|
| Firebase account | Email, UID, password credential, provider, IP/user-agent security data; Google basic profile when selected | Account creation, authentication, recovery, security and deletion | Firebase Authentication; service operates from US data centres. Local encrypted session/profile cache | Contract/service necessity; security legitimate interest; legal obligation | Until account deletion. Firebase publishes a few-week IP-log period and up to 180 days to remove other auth information from live/backups after deletion. |
| Text feedback | Submission UUID, category, message, optional email, UID, Android platform, app version, locale, status, server timestamp | Support, investigation, deduplication and product/security improvement | Cloud Firestore `europe-west1`, Belgium. Memory-only Android client cache | Request/contract handling and legitimate interest, subject to final balancing review | Maximum 12 months; earlier for account deletion or valid request. Weekly keyless maintenance job plus in-app account deletion. |
| Profile/address | Username, name, phone, address, postcode, city, country, profile photo | User-selected profile features | Encrypted/private local app storage only | Requested local service | Until item/account deletion, app-data clearing or uninstall. Logout alone preserves owner-scoped cache. |
| Aquarium content | Tank identity/type/dimensions, setup dates, notes, photos, plants, livestock, equipment/material and related state | Local aquarium management | Private local app storage only | Requested local service | Until item/account deletion, app-data clearing or uninstall. |
| Care and reminders | Tasks, schedule, completion state, notification preferences and delivery ledger | Local maintenance and user-requested reminders | Private local stores, Android alarms/work and notifications | Requested local service | Until task/account deletion, app-data clearing or uninstall. Notifications/work are cancelled on deletion. |
| Connected devices | Device UID, product/family/capabilities, local endpoint/status, tank assignment | Local discovery, display and control | Private local stores and local network | Requested local service | Until device/account deletion, app-data clearing or uninstall. |
| Device credentials | Device authentication token/credential | Authenticate local device control | Encrypted local preferences | Security and requested local service | Until device/account deletion or app-data clearing/uninstall. |
| Wi-Fi provisioning | SSID, Wi-Fi password, UTC offset, QR secret, BLE address and recovery metadata | Send setup data to the selected device | Encrypted local draft plus direct encrypted BLE/local transfer; no AquaLight cloud | Requested setup action | Draft and QR secret expire after 15 minutes; earlier on completion/cancellation/account deletion. Process-only BLE address cache ends with process. |
| Local usage counters | Manual/automation/alert counts, last event description/time | Render the user's Usage screen | Encrypted local preferences only | Requested local display | Until account deletion or app-data clearing/uninstall. Not Firebase Analytics and never uploaded. |
| Photos/camera | Selected or captured profile/tank images and temporary processing files | User-selected local media features | App-owned local files; source URI temporarily handled by Android | Requested local feature | Pending media expires through reconciliation; saved media remains until item/account deletion or uninstall. |
| Permissions | Camera, Bluetooth/nearby devices, Android location permission on API 27–30, notifications, exact alarms | Camera/media, nearby setup and reminders | Android permission state; no location coordinates stored by AquaLight | User-requested OS capability | Until revoked or app removed. |

## Telemetry and consent decision

- Firebase Analytics, Crashlytics, Performance Monitoring, Realtime Database, Remote Config and Cloud Messaging are not packaged.
- No advertising SDK, behavioural analytics, tracking identifier or optional telemetry is used.
- A general “KVKK consent” is not requested. The Privacy/KVKK text is a notice. Terms acceptance and the 18+ declaration are separate required controls before email registration and before Google Sign-In begins.
- If optional telemetry is proposed later, it requires a new architecture decision, data-inventory update, provider/legal review, a default-off consent control before SDK initialisation, withdrawal handling and store-declaration changes.

## Deletion verification boundary

The in-app account deletion transaction covers, in order: owner feedback in Firestore, Firebase Auth account, owner session/services, care tasks, tanks, assignments, provisioning drafts/secrets/recovery, known devices, device credentials, app-owned photos/temp files, user preferences, notification schedules/work, Google access revocation and Firebase sign-out. The commercial test suite must keep this list aligned with `UserDataCleaner.Step` and the cloud cleaner.

## Source references

- Firebase privacy and processing locations: https://firebase.google.com/support/privacy
- Cloud Firestore locations (`europe-west1` is Belgium): https://firebase.google.com/docs/firestore/locations
- Firestore offline persistence and memory cache: https://firebase.google.com/docs/firestore/manage-data/enable-offline
- Firebase data-processing terms: https://firebase.google.com/terms/data-processing-terms
- KVKK international-transfer guide: https://www.kvkk.gov.tr/Icerik/8142/Kisisel-Verilerin-Yurt-Disina-Aktarilmasi-Rehberi
- KVKK standard-contract notification requirements: https://www.kvkk.gov.tr/Icerik/8170/Yurt-Disina-Kisisel-Veri-Aktariminda-Kullanilacak-Standart-Sozlesmelerde-Dikkat-Edilmesi-Gereken-Hususlara-Iliskin-Kamuoyu-Duyurusu
- European Commission GDPR rights summary: https://commission.europa.eu/law/law-topic/data-protection/information-individuals_en
- European Commission Standard Contractual Clauses: https://commission.europa.eu/law/law-topic/data-protection/international-dimension-data-protection/standard-contractual-clauses-scc_en
