# Stage 13 / Madde 13 — Kritik Paket Coverage Eşikleri

Bu politika, 12. maddede üretilen Debug unit-test JaCoCo XML raporunu kullanır ve ticari açıdan kritik iş mantığında coverage gerilemesini engeller.

## Seçim ilkesi

UI, generated binding ve Android framework adaptörleri eşik kapsamına alınmaz. Eşikler; akvaryum/bakım iş kuralları, cihaz provisioning ve removal, bildirim, recovery, geri bildirim ve WebSocket protokolü gibi veri bütünlüğü veya cihaz davranışını doğrudan etkileyen paketlere uygulanır.

## Başlangıç tabanı ve minimumlar

| Paket | Line tabanı | Line min. | Branch tabanı | Branch min. |
|---|---:|---:|---:|---:|
| `application.aquarium` | 93.27% | 90% | 43.75% | 40% |
| `application.care` | 97.10% | 95% | 100.00% | 90% |
| `application.devices` | 97.52% | 95% | 30.00% | 25% |
| `application.devices.provisioning` | 97.56% | 95% | 100.00% | 90% |
| `application.feedback` | 94.03% | 90% | 71.74% | 65% |
| `application.notifications` | 87.40% | 85% | 72.73% | 65% |
| `data.aquarium.delete` | 62.94% | 60% | 43.75% | 40% |
| `data.devices.remove` | 65.18% | 60% | 65.63% | 60% |
| `data.devices.runtime.ws` | 72.22% | 70% | 44.01% | 40% |
| `data.recovery` | 59.52% | 55% | 45.83% | 40% |

Başlangıç değerleri Android CI run `1774` içindeki JaCoCo artifact’ından ölçülmüştür. Minimumlar mevcut değerin altında küçük bir çalışma payı bırakır; yeni değişikliklerin mevcut test güvence seviyesini anlamlı biçimde düşürmesini engeller.

## Fail-closed davranış

`tools/verify_jacoco_coverage.py` aşağıdaki durumlarda CI’ı başarısız yapar:

- Kritik paket JaCoCo XML raporunda yoksa.
- `LINE` veya `BRANCH` counter’ı yoksa ya da toplam executable öğe sıfırsa.
- Politika şeması veya eşik değeri geçersizse.
- Ölçülen line veya branch oranı tanımlı minimumun altındaysa.

Makine tarafından okunabilir sonuç `critical-package-thresholds.json` olarak coverage artifact’ına eklenir.
