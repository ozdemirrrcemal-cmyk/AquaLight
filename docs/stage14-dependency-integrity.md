# Stage 13 / Madde 14 — Dependency Locking ve Verification Metadata

Bu aşama, AquaLight Gradle bağımlılık grafiğinin sürüm ve içerik bütünlüğünü kaynak kontrolünde sabitler.

## Kilitleme politikası

- Tüm proje konfigürasyonlarında Gradle dependency locking etkinleştirilir.
- `LockMode.STRICT` kullanılır; çözümlenebilir bir konfigürasyonun lock state'i yoksa build başarısız olur.
- `app/gradle.lockfile`, Debug, staging, production release, release-smoke, unit test, instrumentation ve Detekt konfigürasyonlarını kapsar.
- Başlangıç lock state'i 457 modülü ve 193 konfigürasyonu kaydeder.
- Dynamic (`+`) ve changing (`SNAPSHOT`) sürümler politika denetleyicisi tarafından reddedilir.

## Artifact doğrulama politikası

- Global doğrulama dosyası `gradle/verification-metadata.xml` konumundadır.
- Repository metadata doğrulaması açıktır: `verify-metadata=true`.
- Artifact ve metadata girdileri yalnız SHA-256 checksum ile doğrulanır.
- Android Gradle Plugin, AAPT2, Android Lint, IntelliJ/UAST, Kotlin Gradle Plugin, Google Services, Protobuf, Navigation Safe Args ve JaCoCo build artifact'ları metadata kapsamındadır.
- Bu aşamada PGP imza doğrulaması etkin değildir; checksum doğrulaması fail-closed uygulanır.
- `trusted-artifacts` istisnalarına ve MD5/SHA-1 gibi zayıf checksum'lara izin verilmez.

## CI kapısı

`Dependency Integrity` workflow'u aşağıdaki kontrolleri yapar:

1. Lockfile ve verification metadata dosyalarının mevcut ve boş olmadığını doğrular.
2. Gradle bağımlılık raporunu, JaCoCo unit-test coverage yolunu, gerçek Debug APK build yolunu ve Android Lint yolunu strict dependency-verification modunda çalıştırır.
3. `tools/verify_dependency_integrity.py` ile lock ve metadata şemasını denetler.
4. Zorunlu build/test konfigürasyonlarının lockfile'da bulunduğunu doğrular.
5. Zorunlu build plugin, AAPT2 ve Android Lint bileşenlerinin SHA-256 metadata kapsamında olduğunu doğrular.
6. Makine tarafından okunabilir bir JSON özetini workflow artifact'ına ekler.

Dependency verification global olduğundan Android CI, CodeQL, emulator testleri ve release workflow'larındaki sonraki tüm Gradle çözümlemeleri de checksum politikasını otomatik olarak uygular.

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
