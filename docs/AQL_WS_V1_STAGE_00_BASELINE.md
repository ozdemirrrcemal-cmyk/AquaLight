# AquaLight WS v1 — Stage 00 Baseline Kaydı

Branch: `test/ws-00-runtime-baseline`  
Base: `integration/aql-ws-v1-commercial`  
Amaç: Çalışan provisioning, discovery, WebSocket ve online/offline davranışlarını değiştirmeden koruma altına almak.

## Durum

- Aşama: **00 — Runtime Baseline**
- Durum: **IN PROGRESS**
- Runtime davranış değişikliği: **YOK**
- Sonraki aşamaya geçiş: Yalnız bütün otomatik kontroller ve fiziksel smoke testleri PASS olursa.

## Otomatik koruma kapsamı

- [x] Global cleartext kapalı.
- [x] Yalnız `*.device.aql.local` WebSocket cleartext istisnası korunuyor.
- [x] Runtime credential encrypted, owner-scoped storage içinde tutuluyor.
- [x] Credential `stage → verify/connect → commit` akışı korunuyor.
- [x] Provisioning rollback ve staged-token rollback akışı korunuyor.
- [x] Private IPv4 → sentetik hostname → özel DNS sınırı korunuyor.
- [x] WebSocket handshake timeout ve binary-frame reddi korunuyor.
- [x] Authenticated metadata bootstrap korunuyor.
- [x] Local-network loss sırasında runtime session kapatma korunuyor.
- [x] Network restore sırasında device-scoped reconnect korunuyor.
- [x] Authenticated `network.status.get` liveness probe korunuyor.
- [x] Foreground revalidation ve stale-state koruması korunuyor.
- [x] Credential/provisioning recovery test dosyaları mevcut.
- [x] Golden WebSocket ve private-LAN endpoint testleri mevcut.

Otomatik guard:

```text
python3 tools/ws_runtime_baseline_guard.py
python3 tools/provisioning_commit_recovery_guard.py
python3 tools/ws_protocol_guard.py
```

## CI kanıtı

- [ ] Android CI PASS
- [ ] Unit/golden/protocol guard PASS
- [ ] Lint/detekt/coverage PASS
- [ ] Emulator API 27 PASS
- [ ] Emulator API 36 PASS

CI sonucu PR açıldıktan sonra bu bölüme commit/run bilgisiyle kaydedilecektir.

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
