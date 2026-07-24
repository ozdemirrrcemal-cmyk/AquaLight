# Stage 13 / Madde 12 — JaCoCo Unit Test Coverage

Bu belge, `commercial/13-release-ci-supply-chain` dalındaki 12. geçiş maddesinin doğrulama sözleşmesini kaydeder.

- Debug unit test coverage, Android Gradle Plugin'in varyant-özel JaCoCo desteğiyle etkinleştirilir.
- Rapor üretimi `:app:createDebugUnitTestCoverageReport` görevi üzerinden yapılır.
- Zorunlu çıktılar `app/build/reports/coverage/test/debug/report.xml` ve `app/build/reports/coverage/test/debug/index.html` dosyalarıdır.
- CI, iki raporun da varlığını ve XML raporunun geçerli bir JaCoCo `report` kökü içerdiğini doğrular.
- XML, HTML ve execution-data çıktıları ayrı bir GitHub Actions artifact'i olarak yüklenir.
- Minimum coverage eşikleri bu committe uygulanmaz; 13. geçiş maddesinde kritik paketler için ayrıca tanımlanacaktır.
