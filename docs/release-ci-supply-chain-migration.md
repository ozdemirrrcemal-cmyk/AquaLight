# AquaLight — Release, CI ve Supply-Chain Geçiş Planı (Madde 13)

**Belge tarihi:** 23 Temmuz 2026  
**Belge sahibi:** AquaLight Engineering  
**Hedef dal:** `main`  
**Uygulama dalı:** `agent/release-ci-supply-chain-gates`  
**Durum:** Uygulama başlatıldı — ticari yayın öncesi zorunlu  
**Risk sınıfı:** Yüksek / yayın güvenliği / ürün bütünlüğü

## 1. Yönetici özeti

Bu geçişin amacı, `main` dalına yapılan her push ile yayın paketi üretme modelini sonlandırmak ve AquaLight Android sürümlerini kontrollü, izlenebilir, doğrulanabilir ve tekrar üretilebilir bir ticari yayın hattına taşımaktır.

Hedef işletim modelinde bir sürüm; mimari guard, standart Android lint, Kotlin statik analiz, birim test, kritik kod coverage eşiği, emülatör/instrumentation testleri, imza doğrulaması ve supply-chain kontrollerinden geçmeden yayımlanamaz. Ticari çıktı olarak imzalı AAB zorunludur; operasyonel ihtiyaç varsa ayrıca APK üretilir. Her yayın checksum, SBOM ve desteklenen GitHub planlarında provenance/SBOM attestation ile ilişkilendirilir.

Geçiş fail-closed prensibiyle yürütülür. Sürüm imzalama bilgileri, dependency trust state, production Firebase konfigürasyonu veya ortam onayı eksikse workflow paket yayımlamaz.

## 2. İş hedefleri

1. Yanlışlıkla veya yetkisiz ticari yayın riskini kaldırmak.
2. Google Play sürümleme ve AAB gereksinimlerini sürdürülebilir hâle getirmek.
3. Kod kalitesi ve test kanıtlarını yayın kararının zorunlu girdisi yapmak.
4. Bağımlılık ve GitHub Action tedarik-zinciri risklerini azaltmak.
5. İmzalama ve Firebase ortam sırlarını yalnız korumalı production bağlamında kullanmak.
6. Her yayın için denetlenebilir artifact, checksum, SBOM ve provenance kanıtı oluşturmak.

## 3. Mevcut durum ve ticari riskler

| Alan | Mevcut durum | Ticari risk |
|---|---|---|
| Release tetikleyicisi | `main` push release workflow'unu çalıştırıyor | Kontrolsüz/yanlışlıkla yayın |
| Android sürümü | `versionCode` ve `versionName` sabit | Play Store sürüm çakışması, izlenebilirlik kaybı |
| Paket tipi | Release APK odaklı | Play Store AAB standardı karşılanmıyor |
| CI sırası | Release build kalite kapılarından önce üretilebiliyor | Hatalı paketin erken oluşması |
| Android lint | Kontroller `UnusedResources` ile sınırlandırılmış | Standart Android hata seti görünmüyor |
| Kotlin statik analiz | Detekt/ktlint yok | Kod kokusu ve bakım riski ölçülmüyor |
| Coverage | Rapor ve eşik yok | Kritik iş kuralları için regresyon riski ölçülmüyor |
| Dependency trust | Lockfile ve verification metadata yok | Çözümlenen dependency'nin değişmesi/tahrifi riski |
| Güncelleme otomasyonu | Dependabot/Renovate yok | Güvenlik güncellemelerinin gecikmesi |
| GitHub Actions | Mutable major tag kullanımı var | Action tag hareketi/supply-chain riski |
| Firebase | Production yapılandırması ortamdan bağımsız | Yanlış projeye bağlanan build riski |
| İmzalama | Secret ve keystore preflight sınırlı | Eksik/yanlış signing girdisiyle build riski |

## 4. Hedef yayın sözleşmesi

### 4.1 Sürümleme

- Kabul edilen etiket biçimi: `vMAJOR.MINOR.PATCH`.
- Ön sürüm biçimi: `vMAJOR.MINOR.PATCH-alpha.N`, `-beta.N` veya `-rc.N`.
- `versionName`, etiketin başındaki `v` kaldırılarak üretilir.
- `versionCode`, `AQL_VERSION_CODE_BASE + GITHUB_RUN_NUMBER` formülüyle üretilir.
- `AQL_VERSION_CODE_BASE`, Play Console'da daha önce kullanılmış en yüksek `versionCode` değerinden düşük olamaz.
- Aynı workflow run yeniden çalıştırıldığında aynı `versionCode` elde edilir; yeni Play yüklemesi için yeni run gerekir.

### 4.2 Yayın tetikleme modeli

- `main` push ticari release üretmez.
- Release yalnız geçerli bir sürüm etiketi veya kontrollü manuel dispatch ile başlar.
- Ticari publish, korumalı `production` GitHub Environment onayından sonra yapılır.
- Etiket değiştirilemez kabul edilir; hatalı etiket için yeni patch/RC etiketi oluşturulur.

### 4.3 Zorunlu kapı sırası

```text
guard → lint + detekt → unit test + coverage → instrumentation → signed release build
```

Build sonrasında:

```text
signature verification → SHA-256 checksums → SBOM → provenance/SBOM attestation → GitHub Release
```

### 4.4 Zorunlu çıktılar

- İmzalı Android App Bundle (`.aab`)
- Gerektiğinde imzalı Android Package (`.apk`)
- R8/ProGuard mapping dosyası
- `SHA256SUMS`
- CycloneDX JSON SBOM
- Desteklenen GitHub planı/izinlerinde build provenance ve SBOM attestation
- Lint, Detekt, unit test, coverage ve instrumentation raporları

## 5. Uygulama kapsamı ve dalgalar

### Dalga 1 — Release kontrolü ve kalite temeli

**Bu branch'te başlatılan çalışmalar:**

- Tag tabanlı `versionName` ve otomatik artan `versionCode` sözleşmesi
- Sürümleme aracının birim testleri
- `main` push release tetikleyicisinin kaldırılması
- İmzalı AAB ve opsiyonel APK üretim hattı
- Signing secret/keystore preflight ve alias doğrulaması
- Standart Android lint'in yeniden etkinleştirilmesi
- Detekt entegrasyonu; ilk aşamada raporlama modu
- JaCoCo raporu ve kritik sınıflarda minimum line coverage kapısı
- CycloneDX SBOM, SHA-256 checksum ve attestation adımları
- Dependabot yapılandırması
- Gradle dependency locking'in etkinleştirilmesi
- Ortama özel Firebase config materialization aracı
- GitHub Action referanslarını tam commit SHA'ya sabitleme
- Mutable Action referansını yeniden eklemeyi engelleyen supply-chain guard

**Çıkış kriteri:** Release yalnız kontrollü tag/manual süreçte çalışır; gerekli production girdileri veya dependency trust state eksikse publish gerçekleşmez.

### Dalga 2 — Dependency trust state ve statik analiz sertleştirmesi

- Güvenilir ortamda dependency lock state üretimi
- SHA-256 Gradle verification metadata üretimi
- Artifact origin/checksum incelemesi
- Lockfile ve metadata diff'inin bağımsız review edilmesi
- Detekt raporunun incelenmesi ve gerçek hataların düzeltilmesi
- Kabul edilen mevcut borç için baseline oluşturulması
- `ignoreFailures=false` ile Detekt'in blocking hâle getirilmesi
- Dependabot Action SHA güncellemelerinin supply-chain review sürecine alınması

**Çıkış kriteri:** Strict supply-chain gate başarılıdır ve Detekt yeni ihlalleri bloke eder.

### Dalga 3 — Firebase ortam ayrımı

| Ortam | Önerilen applicationId | GitHub secret |
|---|---|---|
| Debug | `com.aqua.aqualight.debug` | `FIREBASE_DEBUG_GOOGLE_SERVICES_JSON_BASE64` |
| Staging | `com.aqua.aqualight.staging` | `FIREBASE_STAGING_GOOGLE_SERVICES_JSON_BASE64` |
| Production | `com.aqua.aqualight` | `FIREBASE_PRODUCTION_GOOGLE_SERVICES_JSON_BASE64` |

- Ayrı Firebase projeleri ve Android uygulama kayıtları oluşturulur.
- Debug/staging/production product flavor veya source-set yapısı eklenir.
- Her ortamın Auth, Firestore ve kural politikası bağımsız doğrulanır.
- CI yalnız ilgili environment secret'ından `google-services.json` üretir.
- Paket adı ile Firebase client eşleşmesi build-time doğrulanır.
- Production dosyasının repo geçmişindeki varlığı için credential hygiene incelemesi yapılır.

**Çıkış kriteri:** Hiçbir ticari build repodaki sabit Firebase dosyasına güvenmez ve üç ortam bağımsız test edilir.

### Dalga 4 — Release rehearsal ve Play Store kabulü

- `vX.Y.Z-rc.1` etiketiyle kontrollü rehearsal yapılır.
- AAB imzası, package name ve `versionCode` doğrulanır.
- Google Play internal testing track'e yükleme yapılır.
- Install, update, rollback ve minimum API senaryoları doğrulanır.
- Checksum, SBOM ve attestation kanıtları incelenir.
- Production environment reviewer akışı kanıtlanır.
- Başarılı RC sonrasında stabil `vX.Y.Z` etiketi yayımlanır.

**Çıkış kriteri:** Internal track rehearsal başarılıdır ve yayın kanıtları saklanmıştır.

## 6. GitHub yönetim ayarları

### 6.1 Repository variable

- `AQL_VERSION_CODE_BASE`: Play Console'daki mevcut en yüksek `versionCode` geçmişi kontrol edilerek belirlenir; tahmin edilmez.

### 6.2 Production environment secrets

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`
- `FIREBASE_PRODUCTION_GOOGLE_SERVICES_JSON_BASE64`
- `AQL_OTA_MANIFEST_PUBLIC_KEY_PEM`
- `AQL_OTA_MANIFEST_KEY_ID`

### 6.3 Production environment protection

- En az bir zorunlu reviewer
- Self-approval kapalı
- Deployment yalnız release tag'lerinden
- Secret erişimi yalnız `production` environment job'unda
- Mümkünse release tag oluşturma yetkisi release yöneticileriyle sınırlı

### 6.4 Main branch protection

Zorunlu kontroller:

- Android CI — guard/lint/unit/coverage
- Android instrumentation
- CodeQL
- Firebase rules (ilgili path değiştiğinde)
- En az bir PR onayı
- Doğrudan push kapalı
- Force-push ve branch silme kapalı

## 7. Coverage politikası

İlk blocking eşik, mevcut testlerle doğrudan kapsanan ticari-kritik sınıflarda `%60` line coverage olarak uygulanır:

- `AppSessionCoordinator*`
- `CareTaskStoreRules*`
- `UserDataScope*`

Bu oran geçiş tabanıdır; kalıcı kalite hedefi değildir. İki başarılı release sonrasında ölçüm bazlı artırılır. Hedef, kritik iş kurallarında en az `%80` line coverage'dır. Eşik düşürme yalnız gerekçeli PR ve engineering/product onayıyla yapılabilir.

## 8. Detekt geçiş politikası

İlk dalgada Detekt raporlama modundadır; mevcut teknik borcun toplu olarak ilk geçişi bloke etmesi önlenir. Dalga 2'de:

1. Gerçek hatalar düzeltilir.
2. Kabul edilen mevcut borç baseline'a alınır.
3. `ignoreFailures=false` yapılır.
4. Yeni ihlal release'i bloke eder.

Detekt'in kalıcı olarak raporlama modunda bırakılması ticari hazır kabul edilmez.

## 9. Dependency locking ve verification prosedürü

Güvenilir bir geliştirme makinesi veya yetkili CI ortamında:

```bash
./gradlew dependencies --write-locks
./gradlew help --write-verification-metadata sha256
```

Ardından:

- Yeni veya beklenmeyen repository origin'leri incelenir.
- Beklenmeyen checksum/artifact artışı incelenir.
- Lockfile ve verification metadata diff'i bağımsız reviewer tarafından onaylanır.
- Release guard strict modda çalıştırılır.

Verification metadata'nın otomatik ve incelemesiz kabul edilmesi yasaktır.

## 10. Release runbook

1. `main` zorunlu kontrollerinin yeşil olduğunu doğrula.
2. Release notlarını ve semver kararını onayla.
3. `AQL_VERSION_CODE_BASE` değerini Play Console geçmişine karşı doğrula.
4. İmzalı/annotated `vX.Y.Z` veya `vX.Y.Z-rc.N` etiketi oluştur.
5. Etiketi push et.
6. `production` environment onayını ver.
7. Bütün kapıların başarıyla tamamlanmasını doğrula.
8. GitHub Release üzerinde AAB, gerekiyorsa APK, mapping, SBOM ve checksum dosyalarını doğrula.
9. GitHub attestation doğrulamasını tamamla.
10. AAB'yi Play Console internal track'e yükle; otomatik Play upload ayrı yetkilendirilmiş değişiklik olarak değerlendirilir.

## 11. Rollback ve olay yönetimi

- Başarısız workflow GitHub Release oluşturamaz.
- Hatalı tag silinmez veya yeniden hedeflenmez; yeni patch/RC tag'i kullanılır.
- Play Store'a yüklenmiş `versionCode` tekrar kullanılmaz.
- Signing secret şüphesinde release durdurulur, environment secrets rotate edilir ve olay kaydı açılır.
- Yanlış Firebase ortamı tespitinde release geri çekilir; secret ve environment mapping gözden geçirilir.
- Rollback eski binary'yi yeniden imzalamak yerine Play Console release yönetimi ve yeni düzeltme sürümü üzerinden yapılır.

## 12. Roller ve karar hakları

| Rol | Sorumluluk |
|---|---|
| Engineering owner | Teknik tasarım, workflow ve Gradle değişiklikleri |
| Release manager | Sürüm/tag kararı, production environment onayı |
| Security reviewer | Dependency verification, Action SHA ve signing kontrolleri |
| QA owner | Instrumentation, internal track ve regresyon kabulü |
| Product owner | Ticari yayın go/no-go kararı |

Aynı kişi teknik değişikliği yazabilir; ancak production publish ve dependency trust state onayı tek kişide birleşmemelidir.

## 13. Tamamlanma tanımı

Madde 13 aşağıdaki koşulların tamamı sağlandığında kapatılır:

- [ ] `main` push release üretmiyor.
- [ ] Tag sözleşmesi ve otomatik `versionCode` production rehearsal'da doğrulandı.
- [ ] İmzalı AAB ve gerektiğinde APK oluşturuluyor.
- [ ] Guard → lint → unit/coverage → instrumentation → release build sırası zorunlu.
- [ ] Standart Android lint blocking.
- [ ] Detekt blocking ve onaylı baseline mevcut.
- [ ] Kritik sınıflarda minimum coverage blocking.
- [ ] Dependency lockfiles ve verification metadata review edilip commit edildi.
- [ ] Dependabot aktif.
- [ ] Tüm GitHub Actions tam commit SHA'ya sabit.
- [ ] SHA-256 checksum, CycloneDX SBOM ve provenance/SBOM attestation release'e bağlı.
- [ ] Debug, staging ve production Firebase projeleri ayrılmış.
- [ ] Production signing secrets yalnız korumalı environment'ta erişilebilir.
- [ ] RC internal-track rehearsal başarılı ve kanıtları saklanmış.
