# AquaLight — Aşama 13 Release, CI ve Supply-Chain Geçiş Planı

**Durum:** Uygulama başladı  
**Öncelik:** Ticari yayın öncesi zorunlu  
**Hedef dal:** `main`  
**Çalışma dalı:** `agent/stage-13-release-ci-supply-chain-20260723`  
**Başlangıç:** 23 Temmuz 2026

## 1. Yönetici özeti

Bu geçiş, AquaLight Android yayın sürecini geliştirici bilgisayarına veya `main` dalına yapılan sıradan bir push işlemine bağlı olmaktan çıkararak tekrarlanabilir, denetlenebilir ve ticari yayına uygun bir modele taşır.

Hedef modelde üretim paketi yalnız doğrulanmış bir SemVer etiketi (`vMAJOR.MINOR.PATCH`) veya yetkili manuel çalıştırma üzerinden oluşturulur. Play Store için birincil teslimat formatı imzalı AAB'dir. APK yalnız açıkça talep edilen dağıtım senaryolarında ek çıktı olarak üretilir. Release adayı; guard, tam Android lint, birim testi, enstrümantasyon/release-smoke ve imzalı release build kapılarını sırasıyla geçmeden yayımlanamaz.

Geçiş, her biri ölçülebilir kabul kriterine ve rollback noktasına sahip kontrollü fazlarla yürütülür.

## 2. Mevcut durum ve ticari riskler

| Alan | Mevcut durum | Ticari risk |
|---|---|---|
| Release tetikleme | `main` push release workflow'unu başlatıyor | İncelenmemiş değişiklik üretim artifact'ına dönüşebilir |
| Sürümleme | `versionCode=1`, `versionName=1.0.0` sabit | Play Store güncellemesi ve izlenebilirlik bozulur |
| Dağıtım formatı | İmzalı APK üretiliyor | Play Store AAB teslimat modeli karşılanmıyor |
| CI sırası | Build, lint ve test sırası release güvenliğini garanti etmiyor | Hatalı artifact testlerden önce üretilebilir |
| Android lint | Yalnız `UnusedResources` kontrol ediliyor | Standart Android hata sınıfları görünmez kalır |
| Supply chain | Actions hareketli sürüm etiketleri kullanıyor | Üçüncü taraf action içeriği sonradan değişebilir |
| Artifact bütünlüğü | Checksum, SBOM ve provenance yok | Binary kaynağı ve bütünlüğü yeterince kanıtlanamaz |
| Bağımlılıklar | Otomatik güncelleme, locking ve verification eksik | Açıklar ve transitif değişiklikler geç fark edilir |
| Ortamlar | Debug, staging ve production Firebase ayrımı tamamlanmamış | Test ve üretim verisi karışabilir |
| Signing | Secret ve keystore kontrolleri sınırlı | Yanlış veya eksik signing materyali release'i bozabilir |

## 3. Hedef işletim modeli

### 3.1 Release kimliği

- `versionName`, tag veya yetkili manuel girdiden türetilen kararlı SemVer'dir: `MAJOR.MINOR.PATCH`.
- `versionCode = MAJOR × 1.000.000 + MINOR × 1.000 + PATCH`.
- Minor ve patch bileşenleri `0..999`; sonuç `1..2.100.000.000` aralığındadır.
- Aynı SemVer aynı versionCode'u, daha yüksek SemVer monoton artan versionCode'u üretir.
- İlk ticari tag öncesinde Play Console'daki en yüksek versionCode doğrulanır; gerekiyorsa belgeli başlangıç ofseti eklenir.

### 3.2 Kontrollü release

- `main` push release yayımlamaz.
- Üretim release'i yalnız `vMAJOR.MINOR.PATCH` etiketi veya `main` üzerinden yetkili manuel tetikleme ile başlar.
- Tag, `main` geçmişinde bulunmayan bir commit'e işaret ediyorsa release durur.
- Tag release'i imzalı AAB, checksum ve release manifesti üretir; mevcut GitHub Release üzerine yazılmaz.
- `production` GitHub Environment üzerinde zorunlu onaylayıcı, sınırlı deployment politikası ve environment-scoped secrets kullanılır.

### 3.3 Zorunlu CI sırası

1. **Guard ve metadata:** Mimari/politika guard'ları, Firebase runtime politikası, SemVer ve versionCode doğrulaması.
2. **Lint:** Tam `lintRelease`; `checkOnly(UnusedResources)` kısıtı yoktur.
3. **Unit test:** `testReleaseUnitTest`, coverage raporu ve eşik kontrolü.
4. **Instrumentation:** Desteklenen API matrisi ve minified release-smoke.
5. **Release build:** İmzalı AAB, isteğe bağlı APK, mapping, checksum, SBOM/provenance ve yayın.

Bir kapı başarısız olduğunda sonraki kapılar çalışmaz.

## 4. Fazlı uygulama planı

### Faz 0 — Baseline ve izolasyon

- [x] `main` başından bağımsız çalışma dalı açıldı.
- [x] Mevcut versioning, signing, lint ve release workflow yapısı incelendi.
- [ ] Başlangıç CI sonuçları ve bilinen lint/test borcu PR açıklamasına kaydedilecek.
- [ ] Release sorumlusu ve production onaylayıcısı atanacak.

**Çıkış kriteri:** Kapsam, sorumlular, rollback noktası ve başlangıç SHA'sı kayıtlıdır.

### Faz 1 — Release kontrolü ve artifact güvenliği

- [x] SemVer'den versionName ve deterministik versionCode türetme altyapısı.
- [x] `main` push yerine kontrollü tag/manuel release modeli.
- [x] İmzalı AAB birincil artifact; APK yalnız manuel talepte.
- [x] Signing secret, keystore dosyası ve alias ön kontrolleri.
- [x] SHA-256 checksum ve release manifesti.
- [x] Değiştirilen release action'larının tam commit SHA ile sabitlenmesi.
- [x] Dependabot için Gradle ve GitHub Actions politikası.
- [ ] Diğer tüm workflow action referanslarının SHA ile sabitlenmesi.
- [ ] `production` environment ve required reviewer ayarları.
- [ ] Kontrollü `workflow_dispatch` kuru koşusu.

**Çıkış kriteri:** `main` push release üretmez; geçerli sürüm girdisi olmadan release başlamaz; imzalı AAB ve doğrulanabilir checksum oluşur.

### Faz 2 — Statik kalite ve coverage

- [x] Android lint'in yalnız `UnusedResources` ile sınırlandırılmasının kaldırılması.
- [ ] Tam lint borcunun blocker/düzeltme/belgeli istisna olarak sınıflandırılması.
- [ ] Kotlin statik analizi için Detekt; formatlama için ktlint değerlendirmesi.
- [ ] JaCoCo veya Kover ile XML/HTML coverage raporu.
- [ ] Mevcut coverage baseline ölçümü.
- [ ] Kritik domain/security/protocol paketleri için minimum eşik.
- [ ] Coverage düşüşünün CI'ı durdurması.

**Başlangıç önerisi:** Kritik paketlerde en az `%80` line coverage; proje genelinde ölçülen baseline'ın altına düşmeme. Nihai eşik ilk rapor sonrasında teknik karar kaydıyla kesinleştirilir.

### Faz 3 — Dependency ve supply-chain sertleştirme

- [ ] Gradle dependency locking ve repoya alınmış lock dosyaları.
- [ ] Gradle dependency verification metadata; kontrollü şekilde strict moda geçiş.
- [ ] Repodaki tüm Actions referanslarının tam commit SHA ile sabitlenmesi.
- [x] Dependabot yapılandırması.
- [ ] CycloneDX veya eşdeğer SBOM.
- [ ] GitHub artifact attestation/provenance.
- [ ] AAB, APK, mapping, SBOM, provenance ve manifest için bütünlük doğrulaması.

**Çıkış kriteri:** CI doğrulanmamış dependency veya değiştirilmiş artifact ile release oluşturamaz.

### Faz 4 — Firebase ve ortam ayrımı

- [ ] `debug`, `staging`, `production` product flavor tasarımı.
- [ ] Ortam bazlı `applicationIdSuffix`, uygulama adı, endpoint ve Firebase projesi.
- [ ] Flavor bazlı `google-services.json` yönetimi.
- [ ] Auth, Firestore rules/indexes ve yapılandırmaların ortam bazında ayrılması.
- [ ] Staging release-smoke'un production verisine erişememesi.
- [ ] Production binary'de debug/staging Firebase kimliği bulunmasını engelleyen CI guard.

**Çıkış kriteri:** Her variant yalnız kendi Firebase ortamına bağlanır; yanlış ortam kimliği release'i durdurur.

### Faz 5 — Ticari cutover

- [ ] Branch protection required checks'in yeni job adlarına geçirilmesi.
- [ ] Production secrets'ın GitHub Environment kapsamına taşınması.
- [ ] Release candidate etiketiyle uçtan uca prova.
- [ ] Play Console internal testing kanalında AAB, imza ve versionCode doğrulaması.
- [ ] Rollback provası ve release runbook onayı.
- [ ] İlk ticari tag'in yalnız go/no-go onayı sonrasında oluşturulması.

**Çıkış kriteri:** Play Console artifact'ı, GitHub Release, commit, tag, checksum ve onay kaydı uçtan uca izlenebilir durumdadır.

## 5. GitHub yapılandırma gereksinimleri

### Production Environment

- Environment adı: `production`
- En az bir required reviewer
- Self-approval kapalı
- Yalnız korumalı SemVer tag'leri ve yetkili manuel çalıştırmalar
- Environment secrets:
  - `RELEASE_KEYSTORE_BASE64`
  - `RELEASE_KEYSTORE_PASSWORD`
  - `RELEASE_KEY_ALIAS`
  - `RELEASE_KEY_PASSWORD`
  - `AQL_OTA_MANIFEST_PUBLIC_KEY_PEM`
  - `AQL_OTA_MANIFEST_KEY_ID`

### Main branch protection

- Doğrudan push kapalı
- PR ve en az bir onay zorunlu
- Guard/policy, lint, unit/coverage, instrumentation/release-smoke ve dependency verification required check
- Release job'u normal PR check'i değildir; yalnız kontrollü tag/manuel süreçte production onayıyla çalışır

## 6. Risk ve kontrol matrisi

| Risk | Etki | Olasılık | Kontrol | Rollback |
|---|---:|---:|---|---|
| Tam lint mevcut borç nedeniyle CI'ı durdurur | Orta | Yüksek | Borcu sınıflandır; gerekçeli ve süreli baseline kullan | Eski `checkOnly` ayarına dönmeden son güvenli baseline'a dön |
| versionCode Play Console değerinden düşük kalır | Yüksek | Orta | İlk tag öncesi Play Console kontrolü; gerekirse belgeli ofset | Tag oluşturma; stratejiyi düzelt |
| Signing secret/keystore hatalıdır | Yüksek | Orta | Boş secret, dosya, izin ve alias doğrulaması | Artifact yayımlanmaz; secret rotasyonu |
| Release süresi aşırı uzar | Orta | Orta | Job bağımlılıkları, cache ve kontrollü API matrisi | Instrumentation matrisini geçici olarak desteklenen tek API'ye indir |
| Ortam ayrımı uygulama davranışını bozar | Yüksek | Orta | Ayrı PR, staging provası ve project-ID guard | İlgili flavor commit'ini revert et |
| Locking güncelleme akışını engeller | Orta | Orta | Lock yenileme runbook'u ve Dependabot PR doğrulaması | İlgili lock değişikliğini geri al |

## 7. Rollback ilkeleri

- Değişiklikler `main` üzerinde doğrudan yapılmaz; draft PR üzerinden ilerler.
- Her faz ayrı commit veya ayrı PR sınırı olarak tutulur.
- Rollback, eski `main push` release davranışını yeniden açmaz; son güvenli kontrollü workflow commit'ine döner.
- Başarısız veya şüpheli artifact yayımlanmaz.
- Tag yeniden kullanılmaz; düzeltme yeni patch sürümüyle yapılır.
- Keystore/secret sızıntısı şüphesinde release durur, anahtar rotasyonu ve Play App Signing prosedürü işletilir.

## 8. Ticari go/no-go kontrol listesi

- [ ] SemVer ve versionCode Play Console ile uyumlu
- [ ] Guard, lint, unit, coverage ve instrumentation başarılı
- [ ] AAB imza sertifikası beklenen sertifika ile eşleşiyor
- [ ] APK yalnız belgeli ihtiyaç varsa üretildi
- [ ] SHA-256 checksum doğrulandı
- [ ] SBOM ve provenance mevcut veya belgeli risk kabulü var
- [ ] Production Firebase kimliği doğrulandı
- [ ] Production environment onayı kayıtlı
- [ ] Release notes, mapping ve rollback sahibi hazır
- [ ] GitHub Release ile Play Console artifact'ı aynı commit/tag'e bağlı

## 9. İlk PR kapsamı

İlk PR güvenli başlangıç temelini kurar:

1. Kontrollü tag/manuel release workflow'u.
2. SemVer tabanlı versionName/versionCode.
3. İmzalı AAB, isteğe bağlı APK, checksum ve release manifesti.
4. Tam Android lint'in yeniden etkinleştirilmesi.
5. Signing ön kontrolleri.
6. Değiştirilen action'ların SHA-pinning'i.
7. Dependabot başlangıç politikası.
8. Bu fazlı geçiş ve rollback planı.

Detekt/ktlint, coverage eşikleri, dependency locking/verification, SBOM/provenance ve Firebase flavor ayrımı ayrı, ölçülebilir takip PR'larına bölünür. Bu ayrım ticari yayın kapılarını gevşetmez; değişiklik riskini ve inceleme yüzeyini kontrol eder.
