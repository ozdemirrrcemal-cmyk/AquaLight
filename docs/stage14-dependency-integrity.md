# Stage 13 / Madde 14 — Dependency Locking ve Verification Metadata

Bu aşama, AquaLight Gradle bağımlılık grafiğinin sürüm ve içerik bütünlüğünü kaynak kontrolünde sabitler.

## Kilitleme politikası

- Tüm proje konfigürasyonlarında Gradle dependency locking etkinleştirilir.
- `LockMode.STRICT` kullanılır; çözümlenebilir bir konfigürasyonun lock state'i yoksa build başarısız olur.
- `app/gradle.lockfile`, Debug, staging, production release, release-smoke, unit test, instrumentation ve Detekt konfigürasyonlarını kapsar.
- Güncel lock state 454 modülü ve 193 konfigürasyonu kaydeder.
- Dynamic (`+`) ve changing (`SNAPSHOT`) sürümler politika denetleyicisi tarafından reddedilir.

## Artifact doğrulama politikası

- Global doğrulama dosyası `gradle/verification-metadata.xml` konumundadır.
- Repository metadata doğrulaması açıktır: `verify-metadata=true`.
- Artifact ve metadata girdileri yalnız SHA-256 checksum ile doğrulanır.
- Android Gradle Plugin, AAPT2, Android Lint, IntelliJ/UAST, Kotlin Gradle Plugin, Google Services, Protobuf, Navigation Safe Args ve JaCoCo build artifact'ları metadata kapsamındadır.
- AGP, Kotlin, Google Services, Protobuf ve Navigation Safe Args için beklenen aktif sürümler `settings.gradle` içindeki `pluginManagement.plugins` bloğundan fail-closed türetilir; Python denetleyicisinde ikinci bir sürüm sabiti tutulmaz.
- AAPT2 ve Android Lint'in temiz Gradle çözümlemesinden alınan kesin sürümleri `config/dependency-integrity/resolved-build-tools.json` manifestinde tutulur. Manifestteki AGP sürümü `settings.gradle` ile eşleşmek zorundadır ve her iki kesin artifact kimliği verification metadata içinde bulunmalıdır; belgelenmemiş sürüm dönüşümü tahmin edilmez.
- Bu aşamada PGP imza doğrulaması etkin değildir; checksum doğrulaması fail-closed uygulanır.
- `trusted-artifacts` istisnalarına ve MD5/SHA-1 gibi zayıf checksum'lara izin verilmez.

## CI kapısı

`Dependency Integrity` workflow'u aşağıdaki kontrolleri yapar:

1. Lockfile ve verification metadata dosyalarının mevcut ve boş olmadığını doğrular.
2. Gradle bağımlılık raporunu, JaCoCo unit-test coverage yolunu, gerçek Debug APK build yolunu ve Android Lint yolunu strict dependency-verification modunda çalıştırır.
3. `tools/verify_dependency_integrity.py --settings settings.gradle --build-tools-manifest config/dependency-integrity/resolved-build-tools.json` ile plugin manifestini, çözülmüş build-tool kimliklerini, lock ve metadata şemasını birlikte denetler.
4. Zorunlu build/test konfigürasyonlarının lockfile'da bulunduğunu doğrular.
5. Zorunlu build plugin, AAPT2 ve Android Lint bileşenlerinin SHA-256 metadata kapsamında olduğunu doğrular.
6. Makine tarafından okunabilir bir JSON özetini workflow artifact'ına ekler.

Dependency verification global olduğundan Android CI, CodeQL, emulator testleri ve release workflow'larındaki sonraki tüm Gradle çözümlemeleri de checksum politikasını otomatik olarak uygular.

## Dependabot sıralaması ve otomatik merge sınırı

Canonical `dependabot/gradle/*` PR'larında pahalı kontroller ilk committe çalıştırılmaz. Sıra fail-closed olarak şöyledir:

1. Yalnız `Dependabot Gradle Trust Refresh` Dependabot kimliğini, imzalı metadata'yı, PR dosya kapsamını ve Gradle Wrapper bütünlüğünü doğrular.
2. Lockfile ile SHA-256 verification metadata yeniden üretilir; çalışma ağacında bu iki dosya dışında üretilmiş değişiklik reddedilir.
3. Ayrı, yetkili Apply workflow'u artifact ve canlı PR/base/head SHA'larını yeniden doğrular ve trust state'i Dependabot dalına yazar.
4. Android CI, Dependency Integrity, API 27/36 emulator entegrasyonu ve CodeQL doğrulanmış final SHA üzerinde `workflow_dispatch` ile başlatılır. Her workflow PR, base SHA ve kaynak Trust run'ını build başlamadan yeniden doğrular.
5. Trust dispatch'lerinde gerçek Firebase repository girdileri yerine deterministik, production olmayan fixture'lar kullanılır.

Kontrollü otomatik merge yalnız `config/dependabot/gradle-auto-merge-policy.json` allowlist'indeki `testImplementation` veya `androidTestImplementation` bağımlılıklarının kesin `MAJOR.MINOR.PATCH` patch/minor yükseltmeleri için mümkündür. Manifest farkı, Dependabot metadata'sı ve final SHA birebir eşleşmelidir; dört tam workflow başarılı olmadan merge yapılmaz.

Firebase/BoM, Google Services, Kotlin, AGP, Gradle Wrapper, üretim runtime bağımlılıkları, Protobuf/Navigation build pluginleri, JaCoCo ve Detekt manuel incelemede kalır. Firebase BoM minor/major güncellemeleri, Kotlin–AGP koordineli yükseltmesi tamamlanana kadar `.github/dependabot.yml` içinde pinlenmiştir.

`main` repository kuralı Android CI, Dependency Integrity, Emulator API 27, Emulator API 36 ve CodeQL sonuçlarını merge öncesi zorunlu tutmalıdır. Bu GitHub repository ayarı workflow kaynak kodundan ayrı bir yönetim katmanıdır.

## Güncelleme prosedürü

Bağımlılık veya plugin sürümü bilinçli olarak değiştirildiğinde bağımlılık, build aracı ve lint yolları yenilenir:

```bash
./gradlew :app:dependencies \
  --write-locks \
  --write-verification-metadata sha256

./gradlew :app:assembleDebug :app:assembleReleaseSmoke \
  --write-locks \
  --write-verification-metadata sha256

./gradlew :app:createDebugUnitTestCoverageReport \
  --write-locks \
  --write-verification-metadata sha256

./gradlew :app:lintDebug \
  --write-locks \
  --write-verification-metadata sha256
```

Üretilen `app/gradle.lockfile` ve `gradle/verification-metadata.xml` değişiklikleri commit edilmeden önce incelenmelidir. Bootstrap işlemi o anda repository'lerden gelen artifact'lara güvenir; otomatik üretim, bağımsız güvenlik incelemesinin yerine geçmez.
