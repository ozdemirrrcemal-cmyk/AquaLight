# AquaLight WS v1 — Stage 00 Baseline Kaydı

Branch: `test/ws-00-runtime-baseline`  
Base: `integration/aql-ws-v1-commercial`  
PR: `#181`

## Amaç

Firmware → Android WebSocket geçişinden önce mevcut sistemin referans durumunu kayıt altına almak.

## Kapsam kararı

Bu branch üzerinde runtime, provisioning, UDP discovery, WebSocket transport/crypto, online/offline veya reconnect davranışını değiştiren hiçbir kod değişikliği yapılmamıştır.

Bu nedenle main üzerinde daha önce doğrulanmış fiziksel provisioning ve bağlantı davranışları bu dokümantasyon branch'i için yeniden koşulmaz. Fiziksel regresyon testi yalnız ilgili davranış kodu değiştiğinde veya release candidate kapısında zorunludur.

## Otomatik doğrulama — PASS

- [x] Existing commercial policy/architecture guards
- [x] Existing WebSocket golden/protocol tests
- [x] Unit tests
- [x] Android lint/Detekt
- [x] JaCoCo reports and coverage thresholds
- [x] Debug APK build and verification
- [x] Emulator API 27 instrumentation/minified release smoke
- [x] Emulator API 36 instrumentation/minified release smoke
- [x] CodeQL analysis
- [x] Zero critical/high CodeQL gate

Doğrulanan kod eşdeğer commit: `b1a87340e25e93ed2feea6299c81f922080de930`

## Düzeltme kaydı

Stage 00 için yanlışlıkla eklenen özel runtime guard ve testi gerçek Android derlemesine ulaşmadan CI'ı durdurduğu için tamamen kaldırılmıştır:

```text
tools/ws_runtime_baseline_guard.py
tools/tests/test_ws_runtime_baseline_guard.py
```

Bu dosyalar geçiş mimarisinin parçası değildir.

## Stage 00 çıkış kararı

- [x] Runtime/provisioning kod farkı yok.
- [x] Main baseline daha önce doğrulanmış.
- [x] Repository'nin mevcut otomatik test sistemi PASS.
- [x] Fiziksel yeniden test bu dokümantasyon branch'i için N/A.
- [x] Stage sonucu: **PASSED**

Sonraki branch:

```text
chore/ws-01-contract-parity
```
