# AquaLight WS v1 — Stage 00 Baseline Kaydı

Branch: `test/ws-00-runtime-baseline`  
Base: `integration/aql-ws-v1-commercial`  
PR: `#181`  
Amaç: Çalışan provisioning, discovery, WebSocket ve online/offline davranışlarını değiştirmeden mevcut test sistemiyle doğrulamak.

## Durum

- Aşama: **00 — Existing-system baseline**
- Durum: **IN PROGRESS**
- Runtime davranış değişikliği: **YOK**
- Provisioning davranış değişikliği: **YOK**
- Yeni architecture/runtime guard: **YOK**
- Sonraki aşamaya geçiş: Yalnız mevcut otomatik kontroller ve fiziksel smoke testleri PASS olursa.

## Düzeltme kaydı

İlk CI çalışması gerçek Android derlemesine ulaşmadan, Stage 00 için yanlışlıkla eklenen özel guard testinde durdu. Bu sonuç ürün veya Gradle derleme hatası olarak kabul edilmez.

Kaldırılan dosyalar:

```text
tools/ws_runtime_baseline_guard.py
tools/tests/test_ws_runtime_baseline_guard.py
```

Stage 00 bundan sonra yalnız repository içinde daha önce mevcut olan CI, unit, protocol, lint, coverage ve emulator kontrollerini kullanır.

## Mevcut otomatik test kapıları

- [ ] Android CI PASS
- [ ] Existing commercial policy/architecture guards PASS
- [ ] Existing WebSocket golden/protocol tests PASS
- [ ] Unit tests PASS
- [ ] Android lint/detekt PASS
- [ ] Coverage gates PASS
- [ ] Debug APK build PASS
- [ ] Emulator API 27 PASS
- [ ] Emulator API 36 PASS
- [ ] CodeQL PASS

Gerçek bir hata oluşursa şu bilgiler kaydedilir:

```text
Workflow / job / step
İlk gerçek hata mesajı
Kök neden
Değiştirilen dosyalar
Provisioning/runtime etkisi
Düzeltme commit'i
Rerun sonucu
```

## Fiziksel cihaz smoke testi

### Provisioning

- [ ] QR kimliği ile BLE cihaz kimliği eşleşiyor.
- [ ] Secure provisioning session açılıyor.
- [ ] Wi-Fi credential gönderiliyor.
- [ ] Cihaz Wi-Fi ağına bağlanıyor.
- [ ] Runtime endpoint ve token alınıyor.
- [ ] Token güvenli storage'a yazılıyor.
- [ ] UDP discovery cihazı buluyor.
- [ ] WebSocket HMAC authentication tamamlanıyor.
- [ ] Identity/capabilities/status metadata bootstrap tamamlanıyor.

### Online / Offline

- [ ] Cihaz açılışı: online.
- [ ] Cihaz elektriği kesilince: offline.
- [ ] Cihaz tekrar açılınca: yeniden online.
- [ ] Router kapanınca: local-network offline.
- [ ] Router açılınca: discovery + WebSocket reconnect.
- [ ] Telefon Wi-Fi kapanınca: offline.
- [ ] Telefon Wi-Fi açılınca: reconnect.
- [ ] Uygulama background → foreground: state yeniden doğrulanıyor.
- [ ] Uygulama process restart: kayıtlı cihaz ve token ile reconnect.

### Lifecycle / Güvenlik

- [ ] Endpoint IP değişince temiz reconnect yapılıyor.
- [ ] Cihaz silinince session kapanıyor ve credential temizleniyor.
- [ ] Authentication hatasında raw token loglanmıyor.
- [ ] Provisioning başarısızlığında eski cihaz/token durumu geri yükleniyor.
- [ ] Loglarda Wi-Fi şifresi, claim veya runtime token bulunmuyor.

## Stage 00 çıkış kararı

- [ ] Otomatik testlerin tamamı PASS.
- [ ] Fiziksel testlerin tamamı PASS.
- [ ] Provisioning regresyonu yok.
- [ ] Online/offline regresyonu yok.
- [ ] Reconnect regresyonu yok.
- [ ] PR kanıtları kaydedildi.
- [ ] Stage sonucu: **PASSED**

Stage 00 tamamlanınca sıradaki branch:

```text
chore/ws-01-contract-parity
```
