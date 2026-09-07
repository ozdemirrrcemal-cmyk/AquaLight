# Firmware golden fixture envanteri ve Android eşleme raporu

## Referans

- Firmware repository: `ozdemirrrcemal-cmyk/AquaLight-Firmware`
- Firmware branch: `feature/cooling-contract-v1`
- Golden commit: `980b03f0d83cdeb997698fc6b207064aa709cec8`
- Android repository: `ozdemirrrcemal-cmyk/AquaLight`
- Android branch: `feature/cooling-fan-asset-clean`
- WebSocket şeması: `aql.ws.v1`
- Public komut: `0`
- Authenticated komut: `50`
- Declared event: `13`

Bu belge önce golden firmware yüzeyini, sonra Android eşlemesini kaydeder. UI,
ViewModel ve ekran-state bağlantısı bu değişikliğin kapsamında değildir; yeni
Cooling V1 ve WRGB thermal veri sözleşmeleri bu bağlantı için ayrı, strict bir
firmware boundary olarak hazırlanmıştır.

## 1. Firmware katman envanteri

| Katman | Firmware kaynakları | Otorite |
|---|---|---|
| Ürün ve feature kataloğu | `src/product/AqlProductCatalog.hpp`, `AqlProductProfile.hpp`, `AqlProductFeatureTokens.hpp` | SKU, family, capability, limit ve screen/feature sahipliği |
| Sabit donanım profili | `src/hardware/AqlHardwareProfile.hpp`, `AqlHardwarePins.hpp`, `channels/*` | GPIO, LEDC, fan/sensör/relay/pump mapping; Android tarafından değiştirilemez |
| Provisioning | `src/modules/provisioning/AqlBleProvisioningContract.hpp`, `AqlBleProvisioningCrypto.hpp`, `AqlBleProvisioningPayload.hpp`, `AqlBleProvisioningService.hpp` | QR, BLE secure session, Wi-Fi credential ve runtime-token teslimi |
| Discovery | `src/contracts/AqlDiscoveryContract.hpp`, `src/network/AqlDiscoveryService.hpp` | UDP `aql.discovery.v1`, port `10888`, announce/refresh |
| Runtime transport | `src/contracts/AqlWebSocketContract.hpp`, `src/protocol/ws/*`, `src/server/AqlRealtimeServer.*` | HMAC auth, Base64URL data, MAC, sequence, response ve event envelope |
| Merkezi komut kaydı | `src/api/v1/commands/AqlCommandNames.hpp`, `AqlCommandRegistry.hpp`, `AqlCommandDispatcher.hpp`, `AqlCoreCommandBootstrap.*` | Exact module/action, auth ve ürün-gated registration |
| Device / Network / Security | `AqlDeviceCommands.hpp`, `AqlNetworkCommands.hpp`, `AqlSecurityCommands.hpp` | Kimlik, capability, status, ad, ağ ve ownership durumu |
| Time | `AqlTimeCommands.hpp`, `src/modules/time/*`, `src/storage/AqlTimeRuntimeStateStore.hpp` | RTC/NTP/telefon sync, timezone ve scheduler clock authority |
| Light | `AqlLightCommands.hpp`, `AqlLightTemperatureProtectionCommands.hpp`, `src/modules/light/AqlLightService.hpp` | LED kanalları, manual/program ve ışık sıcaklık koruması |
| WRGB thermal | `AqlLightThermalCommands.hpp`, `AqlLightThermalTelemetry.hpp`, `AqlLightThermalContractV1.hpp`, `AqlLightThermalService.hpp` | WRGB iki fan + fixture DS18B20; config/status/telemetry ve fail-safe |
| Cooling | `AqlCoolingCommands.hpp`, `AqlCoolingTelemetry.hpp`, `src/modules/cooling/*` | Cool Pro 1F config/manual/program/history, control kararı, sensör, alarm ve güç |
| Timer | `AqlTimerCommands.hpp`, `src/modules/timer/AqlTimerService.hpp` | Relay Pro channel/schedule ve compensating transaction |
| Dosing | `AqlDosingCommands.hpp`, `AqlDosingProgressCommands.hpp`, `src/modules/dosing/*` | Dose Pro program, kalibrasyon, reservoir, runtime/accounting ve progress |
| OTA | `AqlFirmwareCommands.hpp`, `src/modules/firmware/AqlOtaService.hpp`, `AqlOtaSafeModeTransaction.hpp` | OTA status/start/clear, safe-mode ve exact-state restore |
| Kalıcılık | `src/storage/AqlConfigStorage.hpp`, `AqlRuntimeConfigStore.hpp`, `AqlEepromStore.hpp`, `AqlFileSystem.hpp` | Şema doğrulama, revision ve atomik/rollback persistence |
| Event bus | `src/protocol/ws/AqlWsEventBus.hpp`, module telemetry publishers | Signed command-result wrapper ve direct telemetry eventleri |

## 2. Transport ve provisioning özeti

### Provisioning

- QR contract `v=1`; required alanlar:
  `v,brand,uid,sn,productId,model,name,hw,sku,pid,claim,ble`.
- BLE service `FFF0`; preferred MTU `517`; timeout `300000 ms`.
- GATT characteristic’leri: DeviceInfo, StartSession, WifiCredentials,
  ProvisioningStatus, RuntimeEndpoint, FinalizeSetup.
- Security version `2`; QR claim yolu HMAC-SHA256, physical-reset yolu P-256
  ECDH kullanır.
- WifiCredentials, RuntimeEndpoint ve FinalizeSetup AES-256-GCM envelope’dur;
  app→device ve device→app sequence sayaçları ayrıdır.
- RuntimeEndpoint exact alanları `deviceUid,ip,webSocketPort,path,token`.
- Runtime token yalnız encrypted BLE ile teslim edilir; WebSocket credential key
  `SHA-256(UTF8(trim(token)))` olarak türetilir.

### Runtime

- UDP discovery: `aql.discovery.v1`, port `10888`, paket sınırı `768` byte.
- WebSocket: `/aql/v1/ws`, port `80`, `aql.ws.v1`, HMAC-SHA256.
- Post-auth `data`: compact JSON object → standard Base64 → unpadded Base64URL.
- Message limiti `8192`, decoded data limiti `4096`, sequence üst sınırı
  `9007199254740991`.
- Tüm uygulama komutları authenticated’dır; whitespace/case/alias normalizasyonu
  yapılmaz.

## 3. Tam 50 komut matrisi

`{}` tam boş request anlamına gelir. “Canonical fields” dışındaki alanlar,
donanım alanları ve legacy alias’lar yeni Cooling/WRGB contract’larında
fail-closed reddedilir.

| # | Komut | Canonical request |
|---:|---|---|
| 1 | `device.identity.get` | `{}` |
| 2 | `device.status.get` | `{}` |
| 3 | `device.capabilities.get` | `{}` |
| 4 | `device.name.set` | `customName,save` |
| 5 | `security.status.get` | `{}` |
| 6 | `security.pair` | `{}` |
| 7 | `security.unpair` | `{}` |
| 8 | `security.reset` | `{}` |
| 9 | `network.status.get` | `{}` |
| 10 | `time.status.get` | `{}` |
| 11 | `time.config.apply` | `ntpEnabled,gadgetSyncEnabled,timezoneId,posixTimeZone,utcOffsetMinutes,ntpServerPrimary,ntpServerSecondary,save` |
| 12 | `time.phone.sync` | `epochMillis` + optional time config/save |
| 13 | `time.ntp.sync` | `{}` |
| 14 | `time.rtc.set` | `epochMillis` veya `parts{year,month,day,weekday,hour,minute,second}` + optional time config/save |
| 15 | `firmware.status.get` | `{}` |
| 16 | `firmware.ota.status` | `{}` |
| 17 | `firmware.ota.start` | `url,version,sha256,expectedSize,applyNow,productKey,productId,model,hardwareRevision,allowInsecureHttp` |
| 18 | `firmware.ota.clear` | `{}` |
| 19 | `light.status.get` | `{}` |
| 20 | `light.manual.set` | `clear,durationMs,channels[{channelKey,percent|value}]` |
| 21 | `light.channel.regime.set` | `channelKey,regime,save` |
| 22 | `light.program.apply` | `channelKey,points[{timeMs,percent}],save,programIndex?` |
| 23 | `light.program.delete` | `programIndex,save` |
| 24 | `light.temperature-protection.status.get` | `{}` |
| 25 | `light.temperature-protection.set` | `thresholdC,save` |
| 26 | `light.thermal.status.get` | `{}` |
| 27 | `light.thermal.config.apply` | `mode,minTemperatureC,maxTemperatureC,save` |
| 28 | `cooling.status.get` | `{}` |
| 29 | `cooling.config.apply` | `expectedConfigRevision` + en az biri: `controlMode,startTemperatureC,fullSpeedTemperatureC,silentModeEnabled` |
| 30 | `cooling.manual.apply` | `expectedConfigRevision,fanKey,targetPercent` |
| 31 | `cooling.program.get` | `{}` |
| 32 | `cooling.program.apply` | `expectedProgramRevision,slots[{startMinute,endMinute,fanOnTemperatureC,fanPercent}]` |
| 33 | `cooling.history.get` | `range` (`24h|7d|30d`) |
| 34 | `timer.status.get` | `{}` |
| 35 | `timer.config.apply` | `channels[{channelKey,displayName,regime}],schedules[{enabled,name,channelKey,weekdays,startTimeMs,intervalOnMs,intervalOffMs,repeatCount,amountMl}],save` |
| 36 | `timer.channel.set` | `channelKey,regime,save` |
| 37 | `dosing.status.get` | `{}` veya `channelKey` |
| 38 | `dosing.progress.get` | exact `channelKey` |
| 39 | `dosing.config.apply` | `channelKey,expectedRevision,displayName,reservoir` |
| 40 | `dosing.program.apply` | `channelKey,expectedRevision,program` |
| 41 | `dosing.channel.reset` | `channelKey,expectedRevision` |
| 42 | `dosing.prime.start` | `channelKey` |
| 43 | `dosing.prime.stop` | `channelKey` |
| 44 | `dosing.calibration.start` | `channelKey,durationMs?` |
| 45 | `dosing.calibration.finish` | `channelKey,measuredMl` |
| 46 | `dosing.calibration.confirm` | `channelKey` |
| 47 | `dosing.calibration.cancel` | `channelKey` |
| 48 | `dosing.dose.now` | `channelKey,amountMl,usePendingCalibration?` |
| 49 | `dosing.dose.stop` | `channelKey` |
| 50 | `dosing.reservoir.refill` | `channelKey` |

## 4. Tam 13 event matrisi

| Event | Veri ve authoritative refresh |
|---|---|
| `device.status.changed` | Wrapper; `device.status.get`, gerekirse identity |
| `network.state.changed` | Wrapper/reserved; `network.status.get` |
| `light.status.changed` | Wrapper; `light.status.get` |
| `light.thermal.status.changed` | Wrapper; `light.thermal.status.get` |
| `light.thermal.telemetry.changed` | Direct WRGB thermal snapshot; gap/reconnect’te status re-read |
| `cooling.status.changed` | Wrapper; `cooling.status.get` |
| `cooling.telemetry.changed` | Direct Cooling V1 snapshot; gap/reconnect/revision mismatch’te status re-read |
| `timer.status.changed` | Wrapper; `timer.status.get` |
| `dosing.status.changed` | Wrapper veya direct; channel status ve gerekiyorsa progress re-read |
| `time.status.changed` | Wrapper; `time.status.get` |
| `firmware.ota.progress` | Direct OTA; gap/reconnect’te `firmware.ota.status` |
| `firmware.ota.completed` | Direct OTA; status ve reboot sonrası firmware status |
| `system.restarting` | Reconnect + bootstrap |

`temperature.changed` golden sözleşmeden kaldırılmıştır. Cooling sıcaklık ve
kontrol snapshot’ı artık `cooling.telemetry.changed`; WRGB fixture telemetry’si
`light.thermal.telemetry.changed` üzerinden gelir.

## 5. Cool Pro 1F — `aql.cooling.v1`

### Ürün ve topoloji

- Yalnız `COOLING_COOL_PRO_1F`.
- Catalog SHA-256:
  `dac61fd3b16ad1f29df59f3c7b881bb562007f9e629e7a636d607fc3d84c0531`.
- Tek fan: `fan1`; PWM duty percent; RPM yok; hardware mapping read-only.
- Sensör sırası: `water` DS18B20/one-wire, `ambient` SHT40-AD1F/I²C `0x44`.
- Capacity: 1 fan, 2 sensor slot, 8 program slot.
- Şema strict; legacy decoder, migration ve fallback yok.

### Config/manual/program policy

- Mode: `AUTOMATIC|MANUAL|PROGRAM`.
- Sıcaklık: `0..40 °C`, step `0.5`, minimum start/full-speed farkı `0.5`.
- Fabrika eğrisi: start `25.5 °C`, full speed `30.0 °C`.
- Fan: `%0..100`, step `%1`; Silent Mode Automatic/Program’da en fazla `%50`.
- Manual hedef kalıcıdır, restart ve bağlantı kesilmesinden etkilenmez; `%0`
  anında kapatır. Mode Manual’dan çıkınca hedef temizlenir.
- Program: en çok 8 slot; 5 dakika hizası; minimum 15 dakika;
  `[start,end)`; `endMinute=1440` geçerli; cross-midnight ve overlap geçersiz.
- Program saati firmware `AqlTimeService` otoritesidir; trusted clock yoksa
  Android yerel karar üretmez.

### Status ve telemetry veri katmanı

`cooling.status.get` kökü:

```text
schema/schemaVersion/uptimeMs/contract/topology/config/program/control/policy
telemetry/alarms/healthSummary/history
```

- `contract`: catalog version/SHA, product key, config/program revisions.
- `config`: revision, mode, manual target, start/full-speed sıcaklık, silent mode.
- `program`: persisted/evaluated revision, slot count, clock ve active slot.
- `control`: immutable decision sequence/time/sample/time-generation, mode/state,
  reason, target ve manual-active.
- `telemetry`: iki sensör, `fan1`, estimated power.
- `alarms` ve `healthSummary` firmware-authoritative’dır; Android severity veya
  alarm sayısını yeniden hesaplamaz.
- `history`: yalnız water sensor; 30 dakikalık persistent capture; chart source
  `24h/7d=SAMPLES`, `30d=DAILY_AVERAGE`.

Direct telemetry root alan sayısı `23`:

```text
schema,schemaVersion,catalogSha256,configRevision,programRevision,uptimeMs,
decisionSequence,evaluatedAtMs,inputSampleSequence,timeGeneration,controlMode,
operatingState,controlReason,manualActive,manualTargetPercent,clockReady,
currentMinuteOfDay,activeProgramSlotIndex,sensors,fan,power,alarms,healthSummary
```

Fan `pwmOutputHealth=OK|FAULT`, physical `health=UNVERIFIED|HARDWARE_FAULT`,
`rpm=null`. Güç yalnız `ESTIMATED`; başarılı output için
`powerWatts=0.5*percent/100`, aksi durumda output/power alanları null’dır.

### Cooling safety

- Water sensor `MISSING|CRC_ERROR|OUT_OF_RANGE|STALE|WARMING_UP` ise
  Automatic/Program output `%0` ve `WATER_SENSOR_FAULT/CRITICAL`.
- Persisted Manual hedef water fault sırasında çalışabilir; kritik alarm kalır.
- Ambient fault warning’dir ve water control’ü durdurmaz.
- PWM write başarısında config/manual başarı cevabı veya persistence üretilmez.
- Program persistence failure’da eski program transaction rollback ile korunur.

## 6. WRGB Pro Elite — `aql.light-thermal.v1`

- Yalnız `LIGHT_WRGB_PRO_ELITE`; feature/screen `LIGHT_FAN_CONTROL`.
- İki sabit fan: `fan1,fan2`; tek `fixture` DS18B20.
- Mode: exact `Auto|On|Off`.
- `light.thermal.status.get` exact `{}`.
- Config yalnız `mode,minTemperatureC,maxTemperatureC,save`; en az bir config
  alanı gerekir.
- Auto’da iki fan aynı lineer eğriyi kullanır.
- Fixture sample yalnız CRC-valid, index 0, health OK ve en çok 10 saniye
  yaşındayken güvenilirdir.
- Sensör güvenilmezse iki fan `%100` fail-safe’e gider;
  `sensorFailSafeActive=true`; eski duty restore edilmez.
- Aynı fault’ta Light correction factor `0` olur. Güvenilir sensör geri gelince
  persisted config ve normal thermal protection yeniden uygulanır.
- PWM write failure config’i persist etmez; firmware `HARDWARE_ERROR` döndürür.
- RPM/current feedback yoktur; fiziksel çalışma `OK` olarak uydurulmaz.

## 7. Timer, Dosing, Time ve OTA değişmezleri

- Relay Timer üç komuttur; Dosing internal timer ile karıştırılmaz.
- `timer.config.apply` tüm channel adaylarını ve schedule’ları doğrular. Physical
  write failure’da ters-sıralı compensation yapılır; compensation da başarısızsa
  scheduler `lockLoop=true` ile kapalı kalır.
- Dosing schema `aqualight.dosing.v1`; toplam 14 komut. Revision, occurrence,
  progress, reservoir/accounting certainty ve runtime reason firmware’e aittir.
- Dosing completion/calibration/OTA quiesce physical pump OFF kanıtı olmadan
  ilerlemez.
- Time `timeSet=true` yalnız authoritative RTC sağlıklı, readback doğrulanmış ve
  scheduler gate açıkken true’dur. Calendar aralığı 2000..2099.
- OTA start HTTPS + SHA-256 + size + exact product identity ister;
  `allowInsecureHttp=false`.
- OTA safe-mode exact-state transaction’dır. Restore write başarısızsa runtime
  fail-closed paused kalır ve `lastErrorField=safeModeRestore` bildirilir.

## 8. Golden fixture ve source pinleri

| Fixture | SHA-256 | Firmware blob |
|---|---|---|
| `aql_ws_v1_golden.json` | `508bd588c118a0c41b66c838c579c45fefcfa5f54b1a608c26b2c9b1ef8984fb` | `a16e32d73a2b8aabd5989fc400df36fd9f6b5347` |
| `aql_cooling_contract_v1.json` | `dac61fd3b16ad1f29df59f3c7b881bb562007f9e629e7a636d607fc3d84c0531` | `0ade050f48fca0eaae9e491156c9b996d0dabbd9` |
| `aql_cooling_telemetry_v1.json` | `8c0ecc54eff1a05f3d72b9b740e6d986dbc3a7cc69c61647608aed360b621b85` | `7ec000ec24e2ef48cd54beff3bad81b58d7cd4c4` |
| `aql_light_thermal_contract_v1.json` | `f1c8bac58740c3250a5c2e7a172f3d49604bf0ae0a0ba628f88f156c1842d7a6` | `acbe344c29f8fe5569ffcf3b5b1d0fda2a6b07f7` |

Ana source blob matrisi `protocol/fixtures/aql_firmware_interoperability_v1.json`
içinde command names, event contract, her command handler, Timer service ve
Security service için pinlenmiştir.

## 9. Android eşleme sonucu

| Alan | Android sonucu |
|---|---|
| Firmware pin | Ana interoperability, ürün kataloğu, OTA fixture ve Dosing core pin `980b03f…` ile eşlendi |
| Komut registry | `44 → 50`; Cooling +4 ve WRGB thermal +2 eklendi |
| Event registry | `11 → 13`; legacy `temperature.changed` kaldırıldı, üç direct thermal/cooling event eklendi |
| Golden fixture | Dört firmware fixture byte-identical kopyalandı ve SHA/blob ile guard edildi |
| Cooling request katmanı | Revision-aware config/manual/program/history serializer’ları ve firmware policy validation eklendi |
| Cooling response katmanı | Strict status/config/manual/program/history/telemetry parser’ları eklendi |
| Cooling command repository | Altı golden komut correlated gateway üzerinden ayrı V1 boundary’ye eklendi |
| WRGB thermal request/response | Strict config serializer; status/config/telemetry DTO/parser eklendi |
| WRGB thermal repository | İki golden thermal komut correlated gateway üzerinden eklendi |
| Timer/Dosing/Time/OTA | Mevcut wire alanları korundu; source/pin ve global command/event matrisi golden commit ile eşlendi |
| UI/data connection | Bilinçli olarak yapılmadı; legacy Cooling presentation reducer direct V1 telemetry’yi state’e uygulamıyor |

Tüm yeni request serializer’ları hardware-owned alanlardan ayrıdır. GPIO, I²C,
LEDC, PWM, mapping, calibration ve factory identity Android mutation yüzeyine
eklenmemiştir.
