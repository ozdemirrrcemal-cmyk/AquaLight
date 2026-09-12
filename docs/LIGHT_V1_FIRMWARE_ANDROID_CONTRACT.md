# Light V1 firmware–Android veri sözleşmesi

## Otorite ve kapsam

- Firmware repository: `ozdemirrrcemal-cmyk/AquaLight-Firmware`
- Firmware branch: `main`
- Firmware commit: `7df97ce807ebb1e90ff63cc36206d6ce479a62fc`
- Firmware tree: `5df1ba11e2d0d5c65e3c6fbb1e4aba5d47bd6c69`
- Android branch: `agent/timer-ui-control-surface`
- Schema: `aqualight.light.v1`, storage version `1`
- Kapsam: firmware, Android veri katmanı ve Light cihaz menüsünün authoritative
  giriş kapısı. Dashboard'ın görsel bağlaması bu değişikliğin kapsamı dışındadır.

Android tek bir ürün-bağımsız Light V1 veri kaynağı kullanır. Ürün ayrımı
`productKey`, strict `features`, kanal descriptor'ları ve authoritative status
üzerinden yapılır. Eski generic Light DTO/komut yolu kaldırılmıştır.

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
| Ortak Light V1 komutları | 14 | 14 |
| Acclimation komutları | 3 | 0 |
| Thermal/temperature-protection komutları | 4 | 0 |
| Toplam Light komutu | 21 | 14 |
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

`light.status.get.data` her iki üründe aynı 23 root alanını taşır:

`schema,storageVersion,productKey,channelScale,channels,features,mode,outputActive,`
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
| `aql_ws_v1_golden.json` | `4d9f2b406800656dc19f08350fd0a3badac659d6fe230d9e3df66f92f728845d` | `e7dc2d3d5567f4246f818659dc2ae0a779021d58` |
| `aql_light_contract_v1.json` | `1260eb5c50852bcd6652cea648e38d06ec06c88422ce1bd169103fc65a52edb0` | `1b6fd1285af4caee02a72dadc572c2113d5c0192` |
| `aql_light_rgb_pro_slim_contract_v1.json` | `c56863cc016ca6f5ca75ed56e58ae2e65c8f7d4432d639a31fdb9ebe7849466a` | `f136b629dde5e2905ac7399ce28a306e9261db34` |
| `aql_light_manual_control_v1.json` | `84d9d61fc9ea233c72d4ae51a5c3ed9bc0b57b1ecac4f60359731a7161904869` | `7d06c67aa4db70bb53b83ff0def2feb11b896781` |
| `aql_light_graph_contract_v1.json` | `49aa0c4e2e543e9e74421b94ad9b0906e460366c0edd11d6b380c0ca5ab5bcf5` | `375064486ab3236b09bae8f1e7508a1eb204581f` |
| `aql_light_thermal_contract_v1.json` | `1eba62b3b80101e5f799c35c2e1af4d69e1961cf331e5d6f139b5a3aab30a3cf` | `7a6cebbddeab45802bc60ce8201b410d8c2ef851` |

Bu dosyalar Android repository'sine firmware'den byte-identical kopyalanır ve
interoperability guard SHA-256 + firmware Git blob SHA üzerinden sapmayı reddeder.
Ürün kataloğu firmware export commit'ine pinlidir; hem katalog hem parser
`LIGHT_MOONLIGHT` özelliğini iki ürün için de reddeder.
