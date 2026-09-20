# Light V1 firmware–Android veri sözleşmesi

## Otorite ve kapsam

- Firmware repository: `ozdemirrrcemal-cmyk/AquaLight-Firmware`
- Firmware branch: `feature/smart-light-automation-plan-v2`
- Firmware commit: `99aca74d3c2ae99e85584893822c0a63fe50bcd8`
- Firmware tree: `4b188f9b20a088bb3f38efd425ff3a6a72f17980`
- Android branch: `feat/smart-light-automation-plan-v2-parity` (base: `feat/custom-program-device-apply`)
- Schema: `aqualight.light.v1`, storage version `1`
- Kapsam: firmware, Android veri katmanı ve Light cihaz menüsünün authoritative
  giriş kapısı. Dashboard'ın görsel bağlaması bu değişikliğin kapsamı dışındadır.

Android tek bir ürün-bağımsız Light V1 veri kaynağı kullanır. Ürün ayrımı
`productKey`, strict `features`, kanal descriptor'ları ve authoritative status
üzerinden yapılır. Eski generic Light DTO/komut yolu kaldırılmıştır.

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
| Ortak Light V1 komutları | 18 | 18 |
| Acclimation komutları | 3 | 0 |
| Thermal/temperature-protection komutları | 4 | 0 |
| Toplam Light komutu | 25 | 18 |
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
| `light.custom.clear` | `expectedRevision` | ✓ | ✓ |
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

`schema,storageVersion,storageGeneration,productKey,channelScale,channels,features,mode,`
`outputActive,outputReason,requested,effective,scales,electricalDesign,power,color,preview,`
`manual,policy,scheduler,auto,custom,acclimation,runtime`.

AUTO summary artık `scheduleSource,planRevision,planInstalled,planId,activePlanPhaseIndex,`
`planRuntimeState,planTransitionPermille,nextPlanTransitionEpochDay` alanlarını da exact
olarak taşır. Managed plan yüklüyken AUTO scheduling authority `MANAGED_PLAN` olur;
user-authored AUTO program kayıtları korunur. Bu genişleme yeni bir V2 oluşturmaz:
schema `aqualight.light.v1`, storage version `1` olarak kalır.

RGB'de opsiyonel yüzeyler silinmez: aynı status şekli korunur; ilgili
`available/supported` alanları `false`, değerler sözleşmenin öngördüğü şekilde
`null` olur. Böylece Android aynı ekran/veri modeli üzerinde capability ile
uyarlanabilir.

Firmware error envelope Android'de kayıpsız alan modeliyle tutulur:
`statusCode,code,field,message,data`. Light V1 structured `data.reason` kümesi:

- `STALE_REVISION`
- `STALE_STORAGE_GENERATION`
- `AUTO_CAPACITY_REACHED`
- `AUTO_PROGRAM_OVERLAP`
- `AUTO_PROGRAM_NOT_FOUND`
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
- `OUTPUT_TRANSACTION_FAILED`
- `STORAGE_COMMIT_FAILED`

Reason'a bağlı ek alanlar strict olarak doğrulanır:

| Reason | Ek `data` alanları |
|---|---|
| `STALE_REVISION`, `AUTO_PROGRAM_NOT_FOUND` | `actualRevision` |
| `STALE_STORAGE_GENERATION` | `actualStorageGeneration` |
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
| `aql_ws_v1_golden.json` | `71e537ecd0be42833c3daa32d8abf2b8f053895c55e0cf6cfea75378ee378b74` | `568832d8999bc6208cec2cedc7dab33c1fb8adf2` |
| `aql_light_contract_v1.json` | `836fe5cd41a2d777db7c559dc7599cbd88a63fc9e51ce0b291c3cbb27b7b6b82` | `2f454296d12c4225d23cfb775e84295a4b935f7f` |
| `aql_light_rgb_pro_slim_contract_v1.json` | `b067ccd11749e26b862ce99df1ccd63e91ce04ddcaf580f53bb2d1fe84fe7e96` | `e2d878b503681507f89660ad68713b464d84f9f9` |
| `aql_light_manual_control_v1.json` | `2f523a82eed615543bf1a3d645069627a41a45b1a62f2d1e9910914c19a4afd9` | `ded90c6fe8b2b014c8d75356dbf99f1b3d7112fb` |
| `aql_light_graph_contract_v1.json` | `2ea04e333b95f01b8a27c2c80969b2fa121754821e2f9bef377cca19daaae2f7` | `686bce0c41df8749887cd7ff2b4c0fd1ffe3ed3b` |
| `aql_light_thermal_contract_v1.json` | `1eba62b3b80101e5f799c35c2e1af4d69e1961cf331e5d6f139b5a3aab30a3cf` | `7a6cebbddeab45802bc60ce8201b410d8c2ef851` |

Bu dosyalar Android repository'sine firmware'den byte-identical kopyalanır ve
interoperability guard SHA-256 + firmware Git blob SHA üzerinden sapmayı reddeder.
Ürün kataloğu firmware export commit'ine pinlidir; hem katalog hem parser
`LIGHT_MOONLIGHT` özelliğini iki ürün için de reddeder.
