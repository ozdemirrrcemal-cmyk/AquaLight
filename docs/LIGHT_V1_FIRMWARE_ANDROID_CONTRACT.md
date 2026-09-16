# Light V1 firmware–Android veri sözleşmesi

## Otorite ve kapsam

- Firmware repository: `ozdemirrrcemal-cmyk/AquaLight-Firmware`
- Firmware branch: `feature/smart-light-automation-plan`
- Firmware commit: `455298833668537fedc16b851067558815d2cc7b`
- Android branch: `feature/smart-light-quick-setup`
- Schema: `aqualight.light.v1`, storage version `1`
- Kapsam: firmware, Android veri katmanı ve Light cihaz menüsünün authoritative
  giriş kapısı. Dashboard'ın görsel bağlaması bu değişikliğin kapsamı dışındadır.

Android tek bir ürün-bağımsız Light V1 veri kaynağı kullanır. Ürün ayrımı
`productKey`, strict `features`, kanal descriptor'ları ve authoritative status
üzerinden yapılır. Eski generic Light DTO/komut yolu kaldırılmıştır.
Hızlı kurulum karar modeli ve managed-plan uzlaşma akışı
[`SMART_LIGHT_QUICK_SETUP_ANDROID.md`](SMART_LIGHT_QUICK_SETUP_ANDROID.md)
belgesinde tanımlanır.

## Android merkezî katman düzeni

Light, owner oturumu başına tek composition kaynağı kullanır. `OwnerDependencyGraph`
bir kez `OwnerLightOperations` oluşturur; menü hazırlığı ve Light root aynı
`controlOperations`, ortak cihaz ayarları ise aynı `protectionOperations` örneğini
kullanır. Feature facade'ları kendi runtime/repository/adapter örneklerini oluşturamaz.

Kalıcı paket sınırları şöyledir:

- Uygulama sözleşmeleri: `application/devices/light/<feature>`
- Veri adapter'ları: `data/devices/light/<feature>`
- Tek firmware-aligned state/transport çekirdeği:
  `data/devices/runtime/modules/light`
- Sunum: `ui/tabs/devices/detail/light/presentation/<destination>`

İlk merkezî yüzeyler `control` ve `protection` olarak ayrılmıştır. Sonraki
dashboard, manual, auto, custom, preview, acclimation ve system ekranları bu
paketlerin yanında kendi application/data feature sınırlarını kullanacak, ancak
tamamı aynı `OwnerLightOperations` ve merkezî Light runtime üzerinden beslenecektir.
Sunum kodu data/runtime tiplerini içe aktaramaz. Acclimation ve thermal/protection
yüzeyleri yalnız katalog capability'si bulunan üründe oluşturulur; RGB Pro Slim
için bu yüzeylere route veya komut üretilemez.

## Cihaz menüsü giriş sözleşmesi

Light, Dosing ile aynı merkezi iki aşamalı cihaz menüsü akışını kullanır:

1. Devices veya Tank girişi `DeviceMenuOpenUseCase` üzerinden güncel erişim ve
   ticari katalog doğrulamasını tamamlar.
2. `DeviceControlSurfacePreparationOperations`, owner-scope içindeki tek
   `DeviceLightControlOperations` örneğinden yeni `light.status.get` ister.
3. Yanıt yalnız aynı runtime generation tarafından authoritative kabul edilen
   exact yanıt ise kullanılabilir.
4. `productKey`, fiziksel kanal sayısı ve kanal anahtarı kümesi katalogla birebir
   eşleşmeden hazır işareti üretilmez.
5. Light hedef ekranı tek kullanımlık hazır işaretini tüketir ve merkezi
   authoritative durumu yeniden doğrular. Restore/deep-link gibi işaretsiz girişler
   aynı hazırlığı hedefte tekrar çalıştırır.

Eksik status, stale generation, ürün/kanal uyuşmazlığı veya runtime hatası ekranı
fail-closed tutar; ayarlar dahil hiçbir kontrol etkinleşmez ve ortak tipli hata ile
önceki menüye dönülür. WRGB katalog slotlarının fiziksel sırası ile firmware'in
sunum descriptor sırası farklı olabildiğinden eşleme anahtar kümesiyle yapılır;
ekranda kullanılacak sıra her zaman firmware `channels[].order` değeridir.

## Ürün matrisi

| Alan | WRGB Pro Elite | RGB Pro Slim |
|---|---:|---:|
| `productKey` | `LIGHT_WRGB_PRO_ELITE` | `LIGHT_RGB_PRO_SLIM` |
| Kanal | red, green, blue, white | red, green, blue |
| Ortak Light V1 komutları | 17 | 17 |
| Acclimation komutları | 3 | 0 |
| Thermal/temperature-protection komutları | 4 | 0 |
| Toplam Light komutu | 24 | 17 |
| Acclimation | var | yok |
| Fan / sıcaklık sensörü / thermal | var | yok |
| Estimated Power / Estimated Color | var | yok |
| Moonlight | yok | yok |

## Exact komut matrisi

| Komut | Exact request `data` | WRGB | RGB |
|---|---|:---:|:---:|
| `light.status.get` | `{}` | ✓ | ✓ |
| `light.control.set` | `mode` | ✓ | ✓ |
| `light.manual.set` | `scene` | ✓ | ✓ |
| `light.manual.off` | `{}` | ✓ | ✓ |
| `light.auto.programs.get` | `{}` | ✓ | ✓ |
| `light.auto.program.create` | `expectedRevision,enabled,weekdaysMask,startTimeMs,endTimeMs,rampDurationMs,scene` | ✓ | ✓ |
| `light.auto.program.update` | `expectedRevision,programId,weekdaysMask,startTimeMs,endTimeMs,rampDurationMs,scene` | ✓ | ✓ |
| `light.auto.program.enabled.set` | `expectedRevision,programId,enabled` | ✓ | ✓ |
| `light.auto.program.delete` | `expectedRevision,programId` | ✓ | ✓ |
| `light.auto.plan.get` | `{}` | ✓ | ✓ |
| `light.auto.plan.apply` | `expectedRevision,expectedStorageGeneration,planId,initialStartPercent,phases` | ✓ | ✓ |
| `light.auto.plan.delete` | `expectedRevision,expectedStorageGeneration,planId` | ✓ | ✓ |
| `light.custom.get` | `{}` | ✓ | ✓ |
| `light.custom.install` | `expectedRevision,weekdaysMask,points` | ✓ | ✓ |
| `light.graph.get` | `{}` | ✓ | ✓ |
| `light.preview.set` | `scene,durationMs?` veya `virtualTimeMs,durationMs?` | ✓ | ✓ |
| `light.preview.clear` | `{}` | ✓ | ✓ |
| `light.acclimation.status.get` | `{}` | ✓ | — |
| `light.acclimation.start` | `expectedRevision,startPercent,durationDays` | ✓ | — |
| `light.acclimation.stop` | `expectedRevision` | ✓ | — |
| `light.temperature-protection.status.get` | `{}` | ✓ | — |
| `light.temperature-protection.set` | `thresholdC,save?` | ✓ | — |
| `light.thermal.status.get` | `{}` | ✓ | — |
| `light.thermal.config.apply` | en az biri: `mode,minTemperatureC,maxTemperatureC`; optional `save` | ✓ | — |

`scene` WRGB için exact dört alan (`redPercent,greenPercent,bluePercent,whitePercent`),
RGB için exact üç alandır (`redPercent,greenPercent,bluePercent`). Custom ve graph
tuple genişliği de ürüne göre sırasıyla 5/4 ve 5/4'tür. Android fazladan, eksik,
yanlış tipli veya yanlış ürün genişliğindeki alanları fail-closed reddeder.

## Status ve hata sözleşmesi

`light.status.get.data` her iki üründe aynı 24 root alanını taşır:

`schema,storageVersion,storageGeneration,productKey,channelScale,channels,features,mode,outputActive,`
`outputReason,requested,effective,scales,electricalDesign,power,color,preview,manual,`
`policy,scheduler,auto,custom,acclimation,runtime`.

RGB'de opsiyonel yüzeyler silinmez: aynı status şekli korunur; ilgili
`available/supported` alanları `false`, değerler sözleşmenin öngördüğü şekilde
`null` olur. Böylece Android aynı ekran/veri modeli üzerinde capability ile
uyarlanabilir.

Firmware error envelope Android'de kayıpsız alan modeliyle tutulur:
`statusCode,code,field,message,data`. Light V1 structured `data.reason` kümesi:

- `STALE_REVISION`
- `AUTO_CAPACITY_REACHED`
- `AUTO_PROGRAM_OVERLAP`
- `AUTO_PROGRAM_NOT_FOUND`
- `INVALID_WEEKDAYS_MASK`
- `INVALID_TIME_VALUE`
- `INVALID_RAMP_VALUE`
- `RAMP_DOES_NOT_FIT`
- `INVALID_SCENE_VALUE`
- `CUSTOM_POINT_COUNT`
- `CUSTOM_POINT_ORDER`
- `CUSTOM_POINT_VALUE`
- `ACCLIMATION_START_PERCENT` (yalnız WRGB)
- `ACCLIMATION_DURATION` (yalnız WRGB)
- `RTC_NOT_READY` (yalnız WRGB acclimation yolu)
- `STALE_STORAGE_GENERATION`
- `AUTO_PLAN_ID_INVALID`
- `AUTO_PLAN_PHASE_COUNT`
- `AUTO_PLAN_INITIAL_START_PERCENT`
- `AUTO_PLAN_DATE_RANGE`
- `AUTO_PLAN_PHASE_GAP`
- `AUTO_PLAN_OVERNIGHT_UNSUPPORTED`
- `AUTO_PLAN_TRANSITION`
- `AUTO_PLAN_NOT_FOUND`
- `AUTO_PLAN_SELECTED`
- `AUTO_PLAN_INTERNAL_ERROR`
- `OUTPUT_TRANSACTION_FAILED`
- `STORAGE_COMMIT_FAILED`

Reason'a bağlı ek alanlar strict olarak doğrulanır:

| Reason | Ek `data` alanları |
|---|---|
| `STALE_REVISION`, `AUTO_PROGRAM_NOT_FOUND` | `actualRevision` |
| `AUTO_CAPACITY_REACHED` | `actualRevision,capacity,programCount` |
| `AUTO_PROGRAM_OVERLAP` | `actualRevision,conflict,additionalConflictCount` |
| `OUTPUT_TRANSACTION_FAILED`, `STORAGE_COMMIT_FAILED` | `rollbackOutputHealthy` |
| Diğer reason'lar | ek alan yok |

`conflict` exact olarak `withProgramId,occurrenceWeekdayMask,overlapStartTimeMs,`
`overlapEndTimeMs,existing,candidate` taşır; `existing` ve `candidate`
`weekdaysMask,startTimeMs,endTimeMs` alanlarından oluşur. Thermal ve temperature
protection komutlarının genel firmware hataları aynı envelope alanlarıyla aynen
korunur; bu komutlar structured Light V1 reason üretmez.

## Golden fixture pinleri

| Fixture | SHA-256 | Firmware blob |
|---|---|---|
| `aql_ws_v1_golden.json` | `1646ebe28b9b27bffda866c508b2387ff3166cf76d98c7c6b09cc684b216758b` | `8414f2ec1ef689c2b9d8a0e88034edeecf3e5b7f` |
| `aql_light_contract_v1.json` | `9e0471f4573c9b729ce6c8931fc0e0683765d7fcf352d6d6fdbf09848d582fcd` | `2ed7fec8f600b81ff4f9b6fd63365aaeb3d03bdf` |
| `aql_light_rgb_pro_slim_contract_v1.json` | `604f723ca25da598ca89b2f3cc65350baba34f70ec0c283ed5a182ea9a1ce33a` | `0ad201c06cbea972b538a757e060b128a8edb181` |
| `aql_light_manual_control_v1.json` | `84d9d61fc9ea233c72d4ae51a5c3ed9bc0b57b1ecac4f60359731a7161904869` | `7d06c67aa4db70bb53b83ff0def2feb11b896781` |
| `aql_light_graph_contract_v1.json` | `2ea04e333b95f01b8a27c2c80969b2fa121754821e2f9bef377cca19daaae2f7` | `686bce0c41df8749887cd7ff2b4c0fd1ffe3ed3b` |
| `aql_light_thermal_contract_v1.json` | `1eba62b3b80101e5f799c35c2e1af4d69e1961cf331e5d6f139b5a3aab30a3cf` | `7a6cebbddeab45802bc60ce8201b410d8c2ef851` |

Bu dosyalar Android repository'sine firmware'den byte-identical kopyalanır ve
interoperability guard SHA-256 + firmware Git blob SHA üzerinden sapmayı reddeder.
Ürün kataloğu firmware export commit'ine pinlidir; hem katalog hem parser
`LIGHT_MOONLIGHT` özelliğini iki ürün için de reddeder.
