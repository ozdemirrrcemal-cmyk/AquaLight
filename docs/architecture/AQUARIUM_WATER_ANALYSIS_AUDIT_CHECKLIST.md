# AquaLight — Su Analizi Sözleşmesi İncelemesi ve Uygulama Checklist’i

İnceleme tarihi: 26 Eylül 2026, Europe/Istanbul.

Repo: `ozdemirrrcemal-cmyk/AquaLight` · Branch: `feat/cooler-hardware-catalog`.

İncelenen güncel branch snapshot’ı: `ca996f241470117a5a797733a13e4c69ffab176c`.

İlk incelemedeki ana belge: `docs/architecture/AQUARIUM_WATER_ANALYSIS_CONTRACT.md` — 47 bölüm, 1566 satır. Sonraki kabul edilen kararlar aynı sözleşmeye eklenir.

Bu dosya, mevcut sözleşmenin kodla karşılaştırılmış incelemesi ve önerilen uygulama ekidir. İlk inceleme sırasında repo dosyaları değiştirilmedi. Checklist, kullanıcının isteğiyle `feat/water-analysis-foundation` çalışma branch'ine eklenmiştir. Karar kaydında açıkça kabul edildiği belirtilen kapsamın dışındaki modeller ve dosya adları öneridir; onaylanmış karar veya uygulanmış kod kabul edilmemelidir. Açık checklist maddeleri geliştirme sırasında test/kanıt ile kapatılmalıdır.

## Birlikte karar verme ve uygulama kuralı

- Maddeler sırayla, kullanıcıyla birlikte ele alınır. Her maddede neyin neden yapılacağı, seçenekler, öneri ve önemli etkiler sunulur; kullanıcı karar vermeden o maddenin çözümü uygulanmaz.
- Araştırma veya veri toplama gereken maddelerde kaynakların güvenilirliği, kapsamı, tarihi, birimleri ve birbiriyle tutarlılığı doğrulanır. Doğrulanamayan bilgi açıkça belirsiz bırakılır.
- Bir madde birden fazla bağımsız karar içeriyorsa kararlar tek tek alınır. Özellikle W0.1 altındaki K01–K18 topluca onaylanmış sayılmaz.
- Verilen karar ilgili sözleşme bölümüne işlenir; uygulama ve gerekli doğrulama tamamlandıktan sonra checklist maddesi kapatılır. Bir sonraki bağımsız karar kullanıcıya sunulur.
- Bu dosyanın branch'e eklenmesi mimari seçeneklerin veya bilimsel eşiklerin onaylandığı anlamına gelmez. Kabul edilen kararlar aşağıdaki kayıt üzerinden izlenir.

## Karar kaydı

| Madde | Durum / tarih | Kabul edilen kapsam | Sözleşme karşılığı |
| --- | --- | --- | --- |
| W0.1 / K01 | Kullanıcıyla kararlaştırıldı — 26.09.2026 | Mevcut application/data/UI/composition mimarisi korunacak. Ortak bağlam ve saf su analiz motoru application altında, veri/sensör/store implementasyonları data altında yer alacak. Bitki okuyucusunun hedefi data/aquarium/catalog/plant olacak. Mevcut sağlık UI'ı ve composition kullanılacak; yeni domain kökü veya Gradle modülü açılmayacak. | Ana sözleşme §4.1 |
| W0.1 / K02 | Kullanıcıyla kararlaştırıldı — 26.09.2026 | AquariumWaterParameter ve AquariumWaterSnapshot kontrollü genişletilecek. UI'da bulunan NO2/amonyak alanlarının veri modeli desteği eklenecek; kesin kimyasal anlam ve birimler K03'te belirlenecek. Canlı karşılaştırması mevcut evaluator'da kalacak; analiz kaydı, assessment ve provenance ayrı modellenecek. | Ana sözleşme §6.4 ve §45 |
| W0.1 / K03.0 | Kullanıcıyla kararlaştırıldı — 26.09.2026 | Temel ölçümler + tank türüne göre alanlar + ek ölçümler yaklaşımı kabul edildi. Tank oluştururken seçilen mevcut 9 tür kullanılacak; tür başına varsayılan ve ek testler sözleşmeye işlendi. Mevcut UI tasarımı korunacak; anlaşılır Türkçe adlar kullanılacak. Ana amonyak alanı toplam amonyak olacak; kesin raporlama temeli/birimleri henüz kabul edilmedi. | Ana sözleşme §6, §25.1–25.3 ve §28.2 |
| W0.1 / K03.1 | Kullanıcıyla kararlaştırıldı — 26.09.2026 | Nitrat mg/L olarak NO3, nitrit mg/L olarak NO2, ortofosfat/reaktif fosfat sonucu mg/L olarak PO4 kaydedilip değerlendirilecek. Aynı anlam/birimdeki kaynak sonuç sayısal olarak değişmez. Toplam fosfor ortofosfat sayılmaz; bilinmeyen kaynak anlamı tahmin edilmez. | Ana sözleşme §6.1–6.2 |
| W0.1 / K03.2 | Kullanıcıyla kararlaştırıldı — 26.09.2026 | Test/cihaz seçimi parametre bazlı tutulacak ve hatırlanacak. Doğrulanmış marka+model/yöntem profili ölçüm türünü, raporlama temelini, kaynak birimini ve sonuç yeteneklerini belirleyecek; kullanıcı sonraki girişlerde yalnız sonucu girecek. Ürün katalogda yoksa kontrollü ölçüm türü–raporlama temeli–birim listesiyle rehberli seçim yapılacak. Çözülemeyen alan normal analiz kaydına commit edilmeyecek; taslak/form durumunda kalabilecek ve diğer doğrulanmış ölçümler kaydedilebilecek. Ham değer + kaynak semantiği + profil/revision + normalize değer ayrı izlenecek; kaynak değişikliği girilmiş sayıyı sessizce yeniden anlamlandırmayacak. Çok-sonuçlu kaynakların concurrent/mutually-exclusive UI cardinality davranışı K03.4 ile kesinleştirildi. | Ana sözleşme §6.2, §6.5, §7, §28.1 ve §43.4 |
| W0.1 / K03.3 | Kullanıcıyla kararlaştırıldı — 26.09.2026 | Kanonik toplam amonyak `TOTAL_AMMONIA_NITROGEN (TAN)` ve `mg/L as N`; doğrudan ölçülen serbest amonyak `FREE_AMMONIA_NH3` ve `mg/L as NH3` olacak. Ham test sonucu ve kaynak birimi/raporlama temeli korunacak; yalnız doğrulanmış profil veya typed source semantics ile desteklenen dönüşüm yapılacak. TAN, doğrudan serbest NH3, NH4-only ve gelecekteki hesaplanmış NH3 birbirinin yerine kullanılmayacak. UI'da ana etiket `Toplam amonyak` kalacak; seçili testin kaynak birimi gösterilecek, kullanıcıya TAN dönüşümü yaptırılmayacak. | Ana sözleşme §6.3, §6.5, §28.1, §43.4 ve §46 |
| W0.1 / K03.4 | Kullanıcıyla kararlaştırıldı — 26.09.2026 | Aynı test/cihaz aynı ölçüm olayında birden fazla bağımsız sonucu üretebiliyorsa sonuçlar tek mode alanına sıkıştırılmayacak. TAN ve doğrudan serbest NH3 aynı `WaterAnalysisRecord` içinde ayrı measured metric olarak birlikte saklanabilecek; Add Analysis UI'da ayrı `Toplam amonyak` ve `Serbest amonyak (NH3)` alanları gösterilecek. Her alan kendi raw value/unit/result identity/provenance bilgisini koruyacak. Mode selector yalnız kaynağın gerçekten mutually-exclusive modları varsa kullanılacak. Bir değer diğerini silmeyecek, üretmeyecek veya türetilmiş saymayacak. | Ana sözleşme §6.6, §28.1, §43.4 ve §46 |
| W0.1 / K03.5 | Profesyonel güvenlik standardı olarak kararlaştırıldı — 26.09.2026 | Deniz/resif için PSS-78 Practical Salinity, Specific Gravity, conductivity, absolute/mass salinity ve vendor `ppt` tek sayı ailesi sayılmayacak. SG ayrı metric olacak ve reference/calibration sıcaklığı ile gerekli sample sıcaklığı provenance'ta tutulacak. Generic `ppt` tek başına kanonik anlam olmayacak. Conductivity→PSS-78 yalnız standart/versioned algoritma ve gerekli girdilerle yapılacak. SG↔salinity kör dönüşümü yasak. Aynı sensörün türettiği salinity+SG iki bağımsız kanıt sayılmayacak. Eşleşen semantik veya doğrulanmış dönüşüm yoksa motor `INSUFFICIENT_DATA` üretecek; tahmini normal/tehlikeli sonuç vermeyecek. | Ana sözleşme §6.7, §25.1, §28.1, §43.4 ve §46 |
| W0.1 / K03.6 | Kullanıcıyla kararlaştırıldı — 26.09.2026 | Marine/reef için kanonik metric `TOTAL_ALKALINITY`, canonical unit `meq/L`. Doğrulanmış aynı-semantic kaynaklar dKH, meq/L veya mg/L as CaCO3 verebilir; uygulama desteklenen dönüşümü yapar ve raw sonucu korur. `1 dKH = 17.86 mg/L as CaCO3 = 0.358 meq/L`, `1 meq/L = 50 mg/L as CaCO3` yalnız source semantic `TOTAL_ALKALINITY` olarak doğrulanmışsa kullanılır. Marine UI'da tek `Alkalinite` alanı olur; ayrıca ikinci `KH` alanı açılmaz. Freshwater'ta `TOTAL_ALKALINITY`, `CARBONATE_HARDNESS` ve `GENERAL_HARDNESS` ayrı semantiktir; `KH` etiketi domain semantiğini tek başına belirlemez. Aynı alkalinite sonucunun dKH/meq/L/CaCO3 gösterimleri tek measurement/evidence sayılır. | Ana sözleşme §6.8, §25.1, §43.4 ve §46 |
| W0.1 / K03.7 | Kullanıcıyla kararlaştırıldı — 26.09.2026 | `DISSOLVED_OXYGEN_CONCENTRATION` kanonik olarak `mg/L O2`; `DISSOLVED_OXYGEN_SATURATION_PERCENT` ayrı `% air saturation` metric/representation olacak. Aynı probe observation'dan gelen mg/L + % iki bağımsız kanıt sayılmayacak. Dönüşüm yalnız aynı ölçüm olayına ait sıcaklık, pressure/barometric reference ve yöntem gerektiriyorsa salinity/specific conductance ile versioned/testli algoritma üzerinden yapılacak. Geçmiş ölçüm için bugünkü çevresel context kullanılmayacak. Cihaz compensation davranışı/provenance korunacak; double compensation yasak. Prerequisite eksikse source-native değer saklanacak, diğer representation üretilmeyecek ve gerekirse `INSUFFICIENT_DATA` dönecek. `%100` otomatik sağlık/normal sonucu değildir. | Ana sözleşme §6.9, §25.2, §43.4 ve §46 |
| W0.1 / K03.8 | Profesyonel güvenlik standardı olarak kararlaştırıldı — 26.09.2026 | `FREE_CHLORINE_AS_CL2` ve `TOTAL_CHLORINE_AS_CL2` ayrı measured metric, canonical unit `mg/L as Cl2`. `COMBINED_CHLORINE_AS_CL2 = total - free` yalnız aynı sample/event ve method-compatible pair için **derived** sonuç olabilir; monokloramin diye etiketlenmez. Direct `MONOCHLORAMINE_AS_CL2` yalnız onu spesifik ölçen verified yöntemle kabul edilir. `RAW_SOURCE_WATER`, `CONDITIONED_SOURCE_WATER`, `TANK_WATER` sample context'leri ayrıdır; cross-context subtraction/overwrite yasak. Total < free ise negatif/clamped combined üretme, pair inconsistent/retest. Method matrix/interference uygun değilse hard assessment yok. | Ana sözleşme §6.10, §25.2, §43.4 ve §46 |
| W0.1 / K03.9 | Profesyonel güvenlik standardı olarak kararlaştırıldı — 26.09.2026 | Marine/reef kalsiyum `CALCIUM_CONCENTRATION` canonical `mg/L as Ca2+`; magnezyum `MAGNESIUM_CONCENTRATION` canonical `mg/L as Mg2+`. Generic `ppm` yalnız verified profile elemental basis'i doğrularsa normalize edilir. `CALCIUM_HARDNESS_AS_CACO3`, `MAGNESIUM_HARDNESS_AS_CACO3`, `GENERAL_HARDNESS` ayrı semantiktir; reef Ca/Mg yerine doğrudan kullanılamaz. Hardness→elemental dönüşüm yalnız explicit method/basis/matrix/interference/precision politikasıyla yapılır. Difference-derived magnesium direct elemental ölçüm gibi sunulmaz. Marine profile ayrı `Kalsiyum` ve `Magnezyum` alanları taşır; biri diğerinden/GH/alkalinity/salinity'den tahmin edilmez. | Ana sözleşme §6.11, §25.2, §43.4 ve §46 |
| W0.1 / K03.10 | Kullanıcı deneyimi + güvenlik standardı olarak kararlaştırıldı — 26.09.2026 | `ELECTRICAL_CONDUCTIVITY` canonical `µS/cm`; source mS/cm güvenli scale normalization ile µS/cm'e çevrilebilir. Conductivity temperature/reference/ATC basis provenance'ta tutulur ve double compensation yapılmaz. TDS UI'da sade `TDS [ppm]`; kullanıcı cihazdaki ppm'i aynen girer, factor sorulmaz. `TDS_REPORTED_PPM` source-native değeri factor bilinmese de saklanır. EC↔TDS dönüşümü yalnız verified profile factor/scale + temperature basis biliniyorsa yapılır; universal 0.5/0.64/0.7 varsayımı yok. Aynı meter observation'dan EC+TDS double-count edilmez. Unknown-scale catalog TDS range hard assessment üretmez. | Ana sözleşme §6.12, §25.2, §43.4 ve §46 |
| W0.1 / K03.11 | Kullanıcıyla kararlaştırıldı — 26.09.2026 | Direct `DISSOLVED_CO2_CONCENTRATION` canonical `mg/L as CO2`. Doğrudan CO2 testi/cihazı varsa kullanıcı gösterilen sonucu girer. Direct ölçüm yoksa AquaLight compatible same-event pH + KH üzerinden `CALCULATED_DISSOLVED_CO2_CONCENTRATION` üretebilir; UI bunu açıkça `Hesaplanan CO2` olarak gösterir. pH tek başına CO2 üretmez; KH semantic'i çözülmüş ve accepted calculation method ile uyumlu olmalıdır. Direct ve calculated aynı olayda birlikte saklanabilir, direct primary kalır. Drop-checker color v1'de ppm'e çevrilmez; sadece ileride qualitative indicator olabilir. Equipment presence CO2 değeri değildir. | Ana sözleşme §6.13, §25.2, §43.4 ve §46 |
| W0.1 / K03.12 | Profesyonel ürün standardı olarak kararlaştırıldı — 26.09.2026 | `POTASSIUM_CONCENTRATION` canonical `mg/L as K`. `IRON_CONCENTRATION` canonical `mg/L as Fe` fakat verified analytical scope zorunlu: en az `TOTAL_IRON`, `DISSOLVED_IRON`, `FERROUS_IRON_FE2`, `METHOD_DEFINED_IRON`. UI sade `Demir (Fe)` / `Potasyum (K)` kalır; bilinen test scope'u profile'dan otomatik çözer, kullanıcıya laboratuvar seçimi yaptırılmaz. Fe scope'ları birbirinin yerine geçmez. K2O/compound basis veya unidentified ppm elemental K sayılmaz. Bare Fe/K sonucu otomatik gübre doz miktarı üretmez. | Ana sözleşme §6.14, §25.2, §43.4 ve §46 |

Bu kayıt kabul edilen kararları belgeler; paketlerin/kodun uygulanmış olduğunu göstermez. W0.1, K03'ün kalan kararları ve K04–K18 açık olduğu için kapanmaz. **K03.0–K03.12 ölçüm kapsamı, kaynak semantiği, normalizasyon ve desteklenen ek parametrelerin ana kimyasal anlamlarını kapatır.** Sıradaki K03 kararı: **W0.1 / K03.13 — Genel Sertlik (GH) için kanonik metric/birim, dGH ↔ mg/L as CaCO3 gösterimi ve total-hardness semantiğinin kesinleştirilmesi**. Tam ticari test kataloğu içerikleri, kanıtlı dönüşüm katsayıları/hassasiyetleri ve diğer ek ölçümlerin semantiği kendi maddelerinde tamamlanacaktır. Ek ölçümleri açma ve yardım etkileşimi ayrıca kararlaştırılacak; kapsam onayı yeni görsel düzen onayı değildir.

K03.1'in doğrulanan kaynakları ile kabul edilen K03.2 kaynak-seçim/normalizasyon, K03.3 amonyak kanonik, K03.4 çok-sonuçlu kayıt/UI, K03.5 deniz salinity/SG, K03.6 alkalinite/KH, K03.7 çözünmüş oksijen, K03.8 klor/kloramin, K03.9 Ca/Mg, K03.10 conductivity/TDS, K03.11 CO2 ve K03.12 Fe/K güvenlik davranışı: [Konsantrasyon birimleri araştırma notu](research/WATER_ANALYSIS_CONCENTRATION_UNITS_K03.md).

### Önerilerin sözleşme ve checklist karşılıkları — 26.09.2026

Bu tablo kullanıcının tekrar paylaştığı önerilerin unutulmaması için izlenebilirlik kaydıdır. **Belgede kayıtlı olmak, kararı alınmış veya kodu tamamlanmış olmak değildir.** K03.0'da kabul edilen kapsam korunur; aşağıdaki açık seçimler sırayla birlikte kararlaştırılır.

| Öneri | Sözleşme / checklist karşılığı | Durum |
| --- | --- | --- |
| Toplam amonyak; “Amonyak ve amonyum toplamı” açıklaması | §6.3; W0.3, W8.1, W8.20 | **K03.3 kabul edildi:** kanonik değer TAN, `mg/L as N`; UI ana etiketi `Toplam amonyak` kalır. Açıklamanın yardım kontrolündeki kesin yerleşimi açık |
| Test/cihaz seçimiyle ölçüm türü, raporlama temeli ve birimi belirleme; seçimleri parametre bazlı hatırlama | §6.2, §6.5–6.6, §7, §28.1; W0.3, W1.4, W8.21 | **K03.2 + K03.4 kabul edildi:** doğrulanmış profil otomatik belirler; katalog dışı ürün rehberli typed seçimle girilebilir; concurrent çıktılar ayrı alan, gerçekten mutually-exclusive modlar selector ile gösterilir; kullanıcıya manuel dönüşüm yaptırılmaz |
| Doğrudan serbest NH3 sonucunu toplamdan ayrı kaydetme | §6.3–6.6; W0.3, W1.2, W8.21 | **K03.3–K03.4 kabul edildi:** doğrudan serbest NH3 `mg/L as NH3`; TAN veya hesaplanan NH3 yerine kullanılmaz. Aynı analiz olayında TAN + direct NH3 ayrı measured metric ve ayrı UI alanı olarak birlikte saklanabilir |
| Toplamdan NH3 hesabında aynı ölçüm olayının pH/sıcaklığı, denizde tuzluluk; “Hesaplanan değer” etiketi | §6.5; W4.10 | Koşullu öneri açıkça kayıtlı; özellik/formül/zaman toleransı karar bekliyor |
| Deniz/resif için tuzluluk, alkalinite, Ca/Mg, NO3/PO4; GH'nin bunların yerine geçmemesi | §25.1; W0.5–W0.6, W1.1, W8.1 | Tuzluluk K03.5, alkalinite K03.6, Ca/Mg K03.9 ile kesinleşti. Ca=`mg/L as Ca2+`, Mg=`mg/L as Mg2+`; GH/hardness-as-CaCO3 bunların yerine geçmez |
| Çözünmüş oksijen, serbest/toplam klor desteği ve ölçülmeyen değeri türetmeme | §25.2; W0.2, W0.6, W4.6 | **K03.7–K03.8 kesinleşti:** DO mg/L O2 kanonik / % saturation ayrı; free+total chlorine ayrı mg/L as Cl2 metric; combined yalnız same-sample derived, direct monochloramine ayrı verified metric; sample context zorunlu |
| İletkenlik/TDS, CO2, demir ve potasyum ek ölçümleri | §25.2–25.3; W0.2, W0.6, W8.1 | Conductivity/TDS K03.10, CO2 K03.11, Fe/K K03.12 ile kesinleşti. K=`mg/L as K`; Fe=`mg/L as Fe` + verified analytical scope. UI sade kalır; Fe/K ölçümü tek başına gübre doz miktarı üretmez |
| Tüm kutuları zorunlu tutmama; boş = Ölçülmedi, ölçülmüş 0 ayrı | §24, §28.1; W0.2, W1.3, W8.2 | İlke kayıtlı; tamamen boş kayıt/minimum girdi K11'de açık |
| Kısmi değerlendirme; eksik ölçümün puanı iyileştirmemesi | §14, §28.1; W0.7, W0.13, W8.17 | İlke kayıtlı; şiddet/tamlık ve skor politikası açık |
| Yalnız geçerli/güncel sensör sıcaklığını doldurma; yeni formda diğer alanların boş başlaması | §7, §27, §28.1; W3.1–W3.9, W8.1, W8.4 | İlke kayıtlı; sensör tazelik politikası ayrıca kesinleşecek |
| Her alanda Nedir / Nasıl ölçülür? yardımı | §25.3; W8.20 | Öneri kayıtlı; tasarımı koruyan yerleşim/etkileşim karar bekliyor |

## 1. Sonuç

**Sözleşmenin mimari yönü doğru; mevcut hali uygulamaya başlamak için bütün kararları dondurmuş değil.** Bölüm 45’teki temel sıra korunmalı; önüne kararların kesinleştirildiği bir hazırlık aşaması eklenmeli. Testler yalnız son aşamada yazılmamalı, her aşamanın kabul şartı olmalı.

Korunacak ilkeler: onaylı Su Kalitesi tasarımı; uygulama sınırları; owner/tank izolasyonu; deterministik motor; mevcut canlı karşılaştırıcısının yeniden kullanılması; doğrulanmış bitki verisi; eksik veriyi normal saymama; tarihsel assessment snapshot’ı; tank/hesap silme bütünlüğü; ortak bağlam ve ayrı sağlık motorları.

**Uygulama sırası:** kararlar → ortak modeller/katalog sınırları → bağlam ve sensör → saf analiz motoru → kalıcılık → silme/hesap/restore bütünlüğü → composition → gerçek UI verisi ve fixture temizliği → kabul testleri → Yosun Kontrolü → Bitki Sağlığı → Canlı Sağlığı.

“Hiç eksik yok” onayı ancak bu maddeler uygulanıp doğrulandıktan sonra verilebilir. Bu inceleme, belirtilen commit’in kaynak kodu ve sözleşmesi içindir; cihazdaki davranışın tamamının doğrulandığı anlamına gelmez.

## 2. Doğrulanan mevcut durum

| Alan | Kodda doğrulanan durum | Anlamı |
| --- | --- | --- |
| Bitki kataloğu | 271 kayıt; 182 VERIFIED/ready, 89 PARTIAL; 240 kayıtta verifiedCareFields; 124 kayıt %100, 185 kayıt en az %90 tamamlanmış | Sözleşmedeki sayılar doğru. Alan doluluğu tek başına bilimsel doğrulama değildir. |
| Canlı kataloğu | 687 benzersiz kayıt; sıcaklık 687, pH 687, NO3 686, GH 343, KH 167, PO4 147, TDS 34, SG 348, alkalinite 280, Ca 157, Mg 157, PAR 113, akış 121 | Kapsam sayıları doğru; eksik alanlar motor sonucunda korunmalı. |
| Canlı güven bilgisi | 687 kaydın tamamı SOFT; confidence: 660 MEDIUM, 27 LOW–MEDIUM | Katalog aralıklarını otomatik HARD/CRITICAL sınıflandırmaya çevirmek için tanımlı politika yok. |
| Mevcut modeller | AquariumWaterParameter, AquariumWaterSnapshot, LivestockWaterRequirements ve LivestockWaterCompatibilityEvaluator var | İkinci bağımsız parametre/range sistemi kurulması önlenmeli. NO2 ve ammonia mevcut enum/snapshot’ta yok. |
| Yeni altyapı | WaterAnalysisOperations, WaterQualityAssessmentEngine, AquariumHealthContext, PlantCareCatalogOperations, TankWaterTemperatureOperations isimleri main/test kaynaklarında bulunmadı | Sözleşme hedefi henüz çalışan altyapı değil. |
| Ekleme | Save listener boş; başlangıç tarihi 26.09.2026 14:10; sensör modu başlangıçta seçili | Gerçek kalıcılık ve sensör kaynağı henüz bağlanmamış. |
| Geçmiş/detay | 12 fixture kayıt; detay rotasında yalnız tankId; silme onayı sadece geri gidiyor | analysisId ve gerçek store sorgusu şart. |
| Diğer sağlık ekranları | PlantHealthFragment ve LivestockHealthFragment placeholder | Sonraki tasarım aşamaları için mevcut giriş noktaları var. |

Kaynaklar: [S1], [S2], [S3], [S4], [S5], [S6], [S7], [S8].

## 3. Sözleşmeye eklenmesi veya kesinleştirilmesi gereken noktalar

Burada “kritik”, ilgili katmanı uygulamadan önce kararı verilmesi gereken konu anlamındadır.

| No | Öncelik | Bulgu / açık karar | Gerekli ek |
| --- | --- | --- | --- |
| K01 | Karar alındı | Mevcut mimari ve temel paket sahipliği kullanıcı tarafından kabul edildi; uygulama aşaması bekliyor. | Ana sözleşme §4.1 ve karar kaydı. Yeni domain kökü/Gradle modülü açılmayacak. |
| K02 | Karar alındı | Mevcut AquariumWaterParameter/AquariumWaterSnapshot modellerinin kontrollü genişletilmesi kullanıcı tarafından kabul edildi; uygulama ve K03 semantiği bekliyor. | Ana sözleşme §6.4 ve §45. Canlı evaluator'ı korunacak; kayıt/assessment/provenance ayrı modeller olacak. |
| K03 | Kısmen kararlaştırıldı; kalanlar kritik | K03.0–K03.11'e ek olarak K03.12 ile K=`mg/L as K`; Fe=`mg/L as Fe` + typed analytical scope; simple UI/profile-resolved scope; compound-basis guard ve no-auto-fertilizer-dosing sınırı kabul edildi. | Sıradaki K03.13: GH/total-hardness canonical semantic + dGH/CaCO3 unit policy. Sonrasında profile/precision ve kalan dönüşüm verileri tamamlanmalı. |
| K04 | Kritik | §14 yön, tehlike, çatışma ve veri eksikliğini tek örnek enum’da topluyor. | Şiddet, LOW/HIGH yönü, veri yeterliliği ve conflict birlikte temsil edilsin. CRITICAL + missingData + conflict aynı sonuçta kaybolmadan saklansın. |
| K05 | Kritik | Canlı parser’ı `<` ile `≤`, `>` ile `≥` ayrımını kaybediyor; contains sınırları dahil sayıyor. Yaklaşık tek değer de eşitlik aralığına dönüşüyor. | Mevcut parser/evaluator üzerinde sınır semantiği ve approximate/SOFT politikası kesinleşsin. Örneğin `<20` sınırında 20’nin mevcut sonuçta uyumlu sayılması testle yakalansın. Ayrı evaluator yazılmasın. |
| K06 | Kritik | AquariumPlantCatalog JSON okuyucusu ui/.../catalog/plant altında. | Parser/cache data/aquarium/catalog/plant altına taşınsın; picker ve motor aynı katalog kaynağını kullansın; application/data → UI import’u oluşmasın. |
| K07 | Kritik | Advisor çıktısı bütün gereksinim aralıklarını sağlamıyor; ölçüm yokken conflict hesaplanmalı. | Mevcut LivestockCatalogOperations üzerinden typed requirements ve sürüm çözümlemesi bağlamda sağlansın; comparison aynı evaluator’da kalsın. Catalog I/O saf engine dışında olsun. |
| K08 | Kritik | §7/§27 tazeliği istiyor; kart özeti sample zamanı taşımıyor. Ayrı telemetri uptime, sequence ve timeGeneration taşıyor. | Saat alanı, cihaz/runtime kimliği, tazelik süresi, yeniden başlama, bağlantı ve atama doğrulaması; birden çok sensör seçimi; kaydetme anında yeniden kontrol tanımlansın. |
| K09 | Kritik | observedAt/createdAt ayrımı var; geriye dönük girişin sensör ve bağlam anlamı yok. | Bugünün sensörü geçmiş ölçüme eklenmesin. Geçmiş bağlam yoksa bugünkü bağlamla değerlendirme açıkça işaretlensin; tarihsel bağlam varmış gibi davranılmasın. |
| K10 | Kritik | §21 silme snapshot/rollback içeriyor; yeni store’un eşzamanlı create/delete bariyeri tarif edilmiyor. | Water Analysis yazmaları mevcut bütünlük journal/guard mekanizmasına katılsın; tank kontrolü ile commit arasındaki yarış kapatılsın. |
| K11 | Kritik | §24 zorunlu alanları, giriş hassasiyetini ve zaman politikasını kesinleştirmiyor. | Boş analiz, yalnız bazı ölçümler, gelecek tarih, timezone, precision, çok büyük değer, ölçüm limiti altı sonuçlar için açık politika. Boş ile ölçülmüş sıfır farklı olmalı. |
| K12 | Yüksek | §28 ağırlıkla ölçüm kartlarını kapsıyor; ana ekran ve tank giriş kartındaki başka fixture’lar kapsam dışı kalıyor. | Bakım yaşı/durumu, cihaz adı/bağlantısı, sistem özeti, canlı sayısı, geçmiş sayısı, tüm durum renkleri, giriş kartındaki 82 skoru dahil edilsin. |
| K13 | Yüksek | §28 “approved empty state” diyor; mevcut sağlık akışında buna karşılık gelen dinamik veri durumu akışı yok. | Loading, NoAnalysis, Partial, Error, NotFound, SensorUnavailable durumları onaylı bileşenlerle tanımlansın; hata boş listeye dönüştürülmesin. |
| K14 | Yüksek | Tek ViewModel’in edit/history/detail/dashboard kapsamı, state restorasyonu ve write retry semantiği açık değil. | Route/owner kapsamı, SavedStateHandle, tekrar tıklama, commit sonrası cevap kaybı, iptal ve süreç ölümü davranışı belirlenmeli. |
| K15 | Yüksek | Ayrı proto öneriliyor; büyüyen geçmiş ve rollback snapshot kapasitesi tanımlanmıyor. | Kayıt/byte sınırları ve performans hedefleri; geçmiş sorgulama; retention; büyük snapshot’ların mevcut SharedPreferences journal’a etkisi için karar ve test. |
| K16 | Yüksek | §19 snapshot deniyor; silinen canlı/bitkinin açıklaması için saklanacak minimum alan seti açık değil. | Yerel varlık kimliği, catalogId, tarihsel ad/özellik, kullanılan aralık, kanıt/sürüm, bağlamın alınma zamanı ve değerlendirme zamanı sabitlensin. |
| K17 | Yüksek | §23 backup dahil/haricini açık bırakıyor; duplicate davranışı yok. | Kullanıcı arşivi kararı depolama öncesinde verilsin; Android otomatik backup politikası ayrı kalsın. Tank duplicate v1’de analiz geçmişini kopyalamasın önerisi açıkça kayda geçsin. |
| K18 | Yüksek | §11 çoğunlukla canlı-canlı çatışması; §12 yalnız bitki uygunluğu. | Bitki-bitki ve bitki-canlı uyumsuzluğu, açık uçlar, eksik alanlar ve bağlama özgü kuralların önceliği de tanımlansın. |

Kaynaklar: [S1], [S2], [S5], [S9], [S10], [S11], [S12], [S13], [S14], [S15].

## 4. Klasörler ve katman sahipliği

Aşağıdaki yollar `app/src/main/java/com/aqua/aqualight/` tabanına göredir. Temel application/data/UI/composition yerleşimi K01 ile onaylanmıştır. Ayrıntılı alt paket ve sınıf adları ilgili maddelerde kesinleştirilecek hedef önerileridir. Mevcut Fragment ve XML dosyaları sırf klasör düzeni için taşınmayacaktır.

| Paket / konum | Sahibi olduğu iş | Örnek dosyalar |
| --- | --- | --- |
| application/aquarium/health/context/ | Dört motorun ortak, immutable bağlamı; tipli çözümleme sonuçları | AquariumHealthContext, AquariumHealthContextProvider, ResolvedPlantCare, ResolvedLivestockCare, ContextCompleteness |
| application/aquarium/health/water/model/ | Kanonik giriş, ölçüm, sonuç ve provenance | WaterAnalysisInput, WaterAnalysisRecord, WaterQualityAssessment, MeasurementProvenance, WaterAnalysisFailure |
| application/aquarium/health/water/policy/ | Birim, girdi, tarih, şiddet, freshness ve kanıt politikaları | WaterInputPolicy, WaterUnitPolicy, AssessmentAggregationPolicy, SensorFreshnessPolicy |
| application/aquarium/health/water/engine/ | I/O yapmayan deterministik hesaplama | WaterQualityAssessmentEngine, RequirementIntersection, WaterRecommendationPolicy |
| application/aquarium/health/water/ | UI ve diğer motorların tükettiği operations | WaterAnalysisOperations |
| application/aquarium/health/temperature/ | Tanka ait sıcaklık okumasının tipli sınırı | TankWaterTemperatureOperations, TankTemperatureState |
| application/aquarium/catalog/plant/ | Android/JSON içermeyen bitki bakım katalog sınırı | PlantCareCatalogOperations |
| data/aquarium/catalog/plant/ | Taşınan tek parser/cache; katalog revision üretimi | DefaultPlantCareCatalogOperations, PlantCatalogParser |
| data/aquarium/catalog/livestock/ | Var olan katalog/advisor implementasyonları ve gerekli küçük düzeltmeler | DefaultLivestockWaterAdvisor, LivestockWaterRequirementParser; mevcut dosyalar korunur |
| data/aquarium/health/context/ | Tank, katalog ve donanım girdilerinden tek snapshot oluşturma | DefaultAquariumHealthContextProvider |
| data/aquarium/health/temperature/ | Atama + Cooling telemetri adaptasyonu | DefaultTankWaterTemperatureOperations |
| data/aquarium/health/water/ | Owner kapsamı, validasyon, değerlendirme ve commit orkestrasyonu | DefaultWaterAnalysisOperations |
| data/aquarium/health/water/store/ | Dedicated storage, serializer, mapping, sorgu ve write guard | WaterAnalysisStore, WaterAnalysisDataStoreManager, WaterAnalysesSerializer, WaterAnalysisStoreRules, WaterAnalysisProtoMapper |
| data/aquarium/health/water/rules/ | Kanıtlı kural kataloğunu yükleme/doğrulama; engine’e typed kurallar verme | PackagedWaterChemistryRuleCatalog |
| ui/tabs/aquarium/detail/health/presentation/water/ | Route state, ViewModel, input/UI mapper | WaterAnalysisViewModel, WaterAnalysisUiState, WaterAnalysisUiMapper, WaterAnalysisInputParser |
| ui/tabs/aquarium/detail/health/ | Mevcut ekran ve adapter’ların gerçek UiState render etmesi | TankHealthFragment, TankHealthContentAdapter, TankHealthAnalysis* |
| composition/ | Üretim bağımlılıklarını oluşturma ve committed owner graph’a bağlama | OwnerDependencyGraph, OwnerViewModelFactory, gerekli AppContainer bağlantıları |
| data/aquarium/delete/ ve data/care/integrity/ | Var olan deletion/journal/recovery’nin genişletilmesi | OwnerTankDataCleaner, TankCareIntegrityJournal, TankCareIntegrityRecovery, write guard |
| data/user/ ve data/user/archive/ | Hesap temizleme, kullanıcı arşivi ve restore | UserDataCleaner, mevcut archive/restore sınıfları |

Diğer dosyalar:

- `app/src/main/proto/water_analyses.proto`: storage modeli; opsiyonel sayıların presence bilgisini korur.
- `app/src/main/assets/water_chemistry_rules.json`: kaynak/versiyon içeren kural verisi seçilirse hedef dosya; sayılar UI’a yazılmaz.
- `app/src/test/java/com/aqua/aqualight/...`: model/engine/policy/store/adapter/ViewModel paketlerine paralel testler.
- `app/src/androidTest/...`: süreç ölümü, persistence, owner değişimi ve UI entegrasyonu.
- `app/src/releaseSmoke/...`: üretimle aynı application binding davranışı.
- `tools/water_analysis_boundary_guard.py`: yeni sınırları denetleyen kontrol önerisi; CI zincirine eklenir.
- `docs/architecture/AQUARIUM_WATER_ANALYSIS_CONTRACT.md`: aşağıdaki kesin kararlar ana sözleşmeye işlenir.

**Bağımlılık kuralları:** UI yalnız application modelleri/operations kullanır. application; Android, JSON parser, data, Firebase, UI ve kaynak R sınıfını bilmez. data; application sınırlarını uygular. composition somut bağımlılıkları kurar. Engine’e hazır ölçüm, bağlam, kurallar ve açık zaman girdisi verilir; engine owner çözmez, store okumaz, sensör sorgulamaz.

WaterAnalysisRecord içindeki kalıcı owner kimliğinin UI’a açılması gerekmez. UI DTO’su ownerUid almaz/göndermez. Paket değişikliği mevcut `AquariumPlantCare`, `AquariumWaterSnapshot` veya evaluator’ın gereksiz yere kopyalanması anlamına gelmez.

## 5. Sıralı uygulama checklist’i

### W0 — Sözleşme kararlarını kapat

- [ ] W0.1 K01–K18 kararlarını ilgili sözleşme bölümlerine işle; “recommended”, “may choose”, “exact names later” kalan üretim davranışlarını kesinleştir.
- [ ] W0.2 §25'teki varsayılan ve ek parametrelerin tamamı için canonical unit, kimyasal temel, nullable/required, hassasiyet ve fiziksel giriş sınırı tablosunu tamamla. Görünür alan ile zorunlu kayıt alanını ayır; tehlikeli ama fiziksel olarak geçerli ölçümleri kabul et.
- [ ] W0.3 K03.2–K03.4 kararlarını uygula: parametre bazlı doğrulanmış test/cihaz profili veya guided typed source semantics kullanılacak; seçim hatırlanacak; ham + normalize + profil/revision provenance korunacak; kaynak değişimi girilmiş sayıyı sessizce yeniden anlamlandırmayacak; çözülemeyen alan commit edilmeyecek. Toplam amonyak TAN=`mg/L as N`, doğrudan serbest NH3=`mg/L as NH3` olacak ve birbirinin yerine kullanılmayacak. Kaynak ikisini aynı olayda üretebiliyorsa ayrı measured metric + ayrı input olarak birlikte saklanacak; selector yalnız mutually-exclusive source mode için kullanılacak.
- [ ] W0.4 Kanıtlı chemistry rule kataloğuna kural kimliği, kaynak, kapsam, sayı/birim, sınır dahil/harici bilgisi, önkoşul ve revision ekle. Bu rapor herhangi bir sayısal güvenlik eşiğini bilimsel olarak onaylamaz.
- [ ] W0.5 Kabul edilen §25.1 matrisini AquariumTankTaxonomy'nin 9 sabitine ve snapshot.tankType'a bağla; application görünürlük politikasını tanımla. Other/boş/bilinmeyen kodda profil uydurma; tank sınıfı ve kayıtlı canlıların waterGroup bilgisi çelişirse açık incompatibility üret.
- [ ] W0.6 K03.5–K03.12 politikalarını uygula: önceki marine/DO/chlorine/Ca-Mg/conductivity-TDS/CO2 kurallarına ek olarak K=`mg/L as K`; Fe=`mg/L as Fe` + typed analytical scope; known profile scope'u otomatik çözer; Fe scope alias yok; K2O/compound basis elemental K sayılmaz; bare Fe/K sonucu otomatik gübre dozu üretmez. Ardından GH canonical semantic/unit kararını kesinleştir.
- [ ] W0.7 Severity, direction, completeness, conflict öncelik matrisini dondur. Eksik ölçüm kritik sonucu gizleyemesin; tüm bilgiler eksikken OPTIMAL çıkamasın.
- [ ] W0.8 VERIFIED + ready bitkiler hard değerlendirmeye uygun; PARTIAL v1’de informational/insufficient. VERIFIED kaydın eksik alanına da aralık uydurma.
- [ ] W0.9 SOFT/HARD/INFORMATIONAL, confidence ve approximate canlı verisinin hard conflict ve uyarıya etkisini belirle. Katalog doluluğunu bilimsel güven düzeyiyle karıştırma.
- [ ] W0.10 Canlı-canlı, bitki-bitki ve canlı-bitki aralık kesişimi; tek taraflı sınır; tam sınır teması; çatışmada etkilenen varlıklar politikası yazılsın.
- [ ] W0.11 Gelecek zaman toleransı, timezone/offset, eski tarihli giriş, latest sıralaması ve zaman aşımı politikası dondurulsun.
- [ ] W0.12 Backup dahil/haricini, tank duplicate geçmiş politikasını, storage kapasitesini ve note alanının v1 kapsamını kesinleştir. Not UI’da alınmıyorsa olmayan not üretilmesin.
- [ ] W0.13 Sağlık giriş kartındaki sayısal skor için sürümlü/kanıtlı hesaplama tanımlanmıyorsa mevcut kartta veri yok sunumu kullan; `82` veya keyfi formül gösterme.

**Geçiş ölçütü:** Kimya, model, storage ve UI davranışını değiştirecek açık karar kalmamış; her kararın örnek sonucu ve test beklentisi yazılmış.

### W1 — Modelleri ve katalog sınırlarını kur

- [ ] W1.1 K02 kararına göre mevcut AquariumWaterParameter ve AquariumWaterSnapshot modellerini kontrollü genişlet; K03.1–K03.12 anlam/birimleriyle `ELECTRICAL_CONDUCTIVITY`, `TDS_REPORTED_PPM`, direct/calculated CO2, `POTASSIUM_CONCENTRATION`, `IRON_CONCENTRATION` ve typed iron analytical-scope desteğini ekle. Direct/derived ve method-scope provenance modelde ayrı olsun.
- [ ] W1.2 Input, raw measurements, record, parameter/entity assessment, reasons, recommendations, conflicts, missingData ve typed failure modellerini oluştur. Bir `WaterAnalysisRecord` aynı `observedAt` olayında birden fazla canonical metric'i taşıyabilsin. Chlorine-family measurement'larda typed sample context (`RAW_SOURCE_WATER`, `CONDITIONED_SOURCE_WATER`, `TANK_WATER`) ve measured-vs-derived provenance zorunlu olsun.
- [ ] W1.3 `null`, ölçülmüş `0`, okunamayan sayı ve değerlendirme için uygulanamaz parametreyi birbirinden ayır. Sınır altı/üstü test sonuçları v1’de desteklenmiyorsa bunu açıkça reddet.
- [ ] W1.4 Parse/normalization öncesi anlamı koru; typed source-profile politikasını kullan. Conductivity/TDS metadata'sına ek olarak Fe için analytical scope + method/range/matrix; K için elemental/compound reporting basis korunur. Known profiles bunları kullanıcıya teknik seçim yaptırmadan çözer. Yuvarlama yalnız sunumda.
- [ ] W1.5 engineVersion, ruleRevision, plantCatalogRevision ve livestockCatalogRevision alanlarını zorunlu yap. Şema sürümünü içerik revision’ı yerine kullanma; mevcut plant JSON yalnız schema/count taşıyor.
- [ ] W1.6 Bitki parser/cache’ini data katmanına taşı; mevcut bitki seçim ekranlarını aynı application boundary’ye bağla. İkinci catalog asset/parser/cache oluşturma.
- [ ] W1.7 Bitki min/max tutarlılığı, finite değerler, sabit kimlik, verified field anahtarları ve ready/status tutarlılığını katalog yüklemede doğrula.
- [ ] W1.8 Canlı requirements için mevcut LivestockCatalogOperations’ı kullan; gereken confidence/evidence/revision bilgisini mevcut sınırda tamamla.
- [ ] W1.9 `<`, `≤`, `>`, `≥`, aralık ayıracı ve approximate parser politikası için mevcut parser/evaluator’a hedefli değişiklik ve regresyon testleri ekle. Format bozukluğunu “bilinmiyor” diye sessizce yutma.
- [ ] W1.10 Salt isCompatible kontrolü kullanma: mevcut değer `issues.isEmpty()` olduğu için sıfır karşılaştırmada da true olabilir; NO_COMPARABLE_MEASUREMENTS ve parametre bazlı coverage korunmalı.

**Geçiş ölçütü:** Mevcut canlı evaluator’ı ve katalog kimlikleri korunuyor; picker davranışı bozulmuyor; eksik ve sınırlı güvene sahip veri tiplerde taşınıyor.

### W2 — Ortak AquariumHealthContext’i oluştur

- [ ] W2.1 Tek owner/tank snapshot’ından tank türü, kurulum tarihi, boyut/hacim, malzemeler, canlılar ve bitkileri çözümle.
- [ ] W2.2 Context’e capturedAt, bağlam revision/hash politikası ve veri tamlık bilgisini ekle; create sırasında hangi snapshot’ın kullanıldığı açıklanabilsin.
- [ ] W2.3 Yerel livestockId/plantId ile catalogId ayrımını koru. Bir türün adedi range’i çoğaltmasın; kayıtların ayrı kimliği kaybolmasın.
- [ ] W2.4 Custom canlı, silinmiş katalog kimliği, PARTIAL bitki ve çözümleme hatasını ayrı sonuçlara dönüştür.
- [ ] W2.5 CO2/light/filter/fertilizer/substrate/cooler/heater bağlamını canonical category/product metadata’dan çıkar. Kayıtlı ekipman varlığı ile cihazın çalıştığı bilgisi ayrı olsun.
- [ ] W2.6 Tank yaşı bilinmiyorsa mature varsayma; hacmi boyutlardan türetiyorsan brüt/geometrik hacim olduğunu koru; net su miktarı diye kimyasal doz önerme.
- [ ] W2.7 Katalog yükleme hatası ile boş tankı ayır. Bilinmeyen taxonomy ve malzeme anahtarını generic freshwater olarak kullanma.
- [ ] W2.8 Geriye dönük analizde kullanıcının eski tank bağlamı bulunmuyorsa `contextAsOfCreation` benzeri açık semantik sakla; kayıt tarihi itibarıyla bağlam biliniyormuş izlenimi verme.
- [ ] W2.9 Bağlamı UI’ın her render’ında yeniden kurma; katalogları revision ile cache’le; tank/owner değişiminde eski sonuç taşınmasını engelle.

**Geçiş ölçütü:** Dört motorun kullanabileceği tek, immutable, açıklanabilir snapshot var; application/data katmanı UI sınıflarına bağımlı değil.

### W3 — Gerçek sıcaklık ve provenance

- [ ] W3.1 TankWaterTemperatureOperations üzerinden Fresh, NoSensor, Unavailable, Stale, Invalid ve gerekiyorsa Ambiguous sonuçlarını üret.
- [ ] W3.2 Owner, tank-device ataması, cihaz ailesi/capability, doğru WATER sensörü ve valid reading şartlarını doğrula.
- [ ] W3.3 DeviceCoolingCardSummary.waterTemperatureC’yi tek başına tazelik kanıtı sayma; mevcut Cooling telemetri sample modelini uygun adapter üzerinden kullan.
- [ ] W3.4 Device UID/sensor key, inputSampleSequence, timeGeneration, runtime/boot kimliği, cihaz uptime zamanları, alım zamanı ve freshness kararını birbirine karıştırmadan modelle.
- [ ] W3.5 Cihaz uptime’ını Unix epoch veya telefon elapsedRealtime değeri gibi kullanma. Epoch dönüşümü gerekiyorsa kanıtlı anchor ve belirsizlik politikası tanımla; alınma anını ölçüm anı diye kaydetme.
- [ ] W3.6 Merkezi freshness süresini ölçüm ve transport/session koşullarıyla belirle. Donmuş bağlantıda aynı sample tekrar yayımlandığında tazelik yenilenmesin.
- [ ] W3.7 Birden çok uygun sensörde primary/explicit seçim politikası olsun; listedeki ilk cihazı veya ortalamayı sessizce seçme.
- [ ] W3.8 Kaydetme anında atama, session generation ve freshness yeniden doğrulansın. Ekran açıldıktan sonra taşınan cihazın ölçümü eski tanka yazılmasın.
- [ ] W3.9 Geçmiş observedAt için güncel sensör okumasını kullanma. V1’de o ana ait doğrulanmış örnek yoksa manuel ölçüm gerekir; sınır açık anlatılsın.
- [ ] W3.10 Manuel giriş kullanıcının açık seçimi olsun; sensör hatası otomatik olarak MANUAL etiketli eski sensör değerine dönüşmesin.
- [ ] W3.11 Kayıtta source, measurement value ve provenance atomik saklansın; ekran dönüşü veya süreç ölümü kaynağı değiştirmesin.

**Geçiş ölçütü:** Tazelik sınırı, reboot, eski/out-of-order sample, offline, atama değişimi, yanlış aile, çoklu sensör ve geriye dönük giriş testleri geçiyor.

### W4 — Saf analiz motoru

- [ ] W4.1 Engine yalnız hazır measurements/context/rules/explicit time girdileriyle çalışsın; Android kaynakları, ağ, JSON, store ve Clock çağrısı yapmasın.
- [ ] W4.2 Önce girdi geçerliliği, sonra chemistry kuralları, canlı/bitki değerlendirmeleri, conflict ve recommendation birleşimi çalışsın.
- [ ] W4.3 Mevcut LivestockWaterCompatibilityEvaluator tek range comparison kaynağı kalsın. Advisor’ın catalog I/O’su engine’in içine taşınmasın; gerekirse hazır profile kabul eden sınır uyarlaması yap.
- [ ] W4.4 Gereksinim kesişimini ölçümden bağımsız hesapla; ölçüm yokken de uyumsuz habitat gereksinimleri görünür olsun.
- [ ] W4.5 Partial/missing gereksinimi sonsuz aralık kabul edip compatible sayma; değerlendirilmiş ve değerlendirilememiş parametreleri ayrı say.
- [ ] W4.6 §25 kapsamındaki her desteklenen parametre için measured/missing/applicability, değerlendirme yönü/şiddeti, nedenler, kullanılan aralıklar ve etkilenen varlıkları üret; sabit sekiz parametre varsayımı kullanma.
- [ ] W4.7 Chemistry tehlikesini bitki ihtiyacı, filtre varlığı veya genel ortalama iyileştirmesin. Bir kritik parametre çok sayıda normal parametreyle bastırılmasın.
- [ ] W4.8 Birden fazla profil/rule uygulanırsa öncelik ve birleşim deterministik olsun; JSON/list sırası sonucu değiştirmesin.
- [ ] W4.9 Recommendation code → reason → rule/evidence bağı kur; yinelenen önerileri sabit sırayla birleştir. Belirsizlikte yeniden ölçüm gibi tanımlı konservatif öneriler kullan.
- [ ] W4.10 §6.5'teki serbest NH3 hesabı kabul edilirse toplam amonyak + aynı ölçüm olayının pH/sıcaklığı + denizde tuzluluk önkoşullarını, onaylı formül/birim/zaman toleransını uygula. Eksikte hesap üretme; geçmiş örneğe bugünkü sensörü bağlama. Kaynakları ve hesap sürümünü koru; “Hesaplanan değer” olarak göster, doğrudan ölçüm/toplam amonyak üzerine yazma. Diğer türetilmiş hesaplarda da önkoşulları doğrula.
- [ ] W4.11 Smart Care task yaratma, donanım kontrolü veya dozlama engine yan etkisi olmasın.

**Geçiş ölçütü:** Aynı girdiler aynı structured sonucu veriyor; list sırası/adet varyasyonları gereksinim aralığını değiştirmiyor; tehlike/çatışma/eksik veri kaybolmuyor.

### W5 — Kalıcılık ve application operations

- [ ] W5.1 Sözleşmedeki ayrı store yaklaşımını uygula; büyüyen geçmiş aquarium_tanks.pb’ye eklenmesin. DataStore seçimi kapasite bütçesiyle doğrulansın; sınırsız geçmiş gerekiyorsa schema yazılmadan storage kararı tekrar değerlendirilsin.
- [ ] W5.2 Tek store instance/yaşam döngüsü, CommercialStoreSchema sürümü, serializer, rules ve mapper’ları mevcut politika ile kur.
- [ ] W5.3 Proto numeric presence ve enum UNSPECIFIED/unknown davranışını doğrula; alan yokluğu default 0/normal sonucuna dönüşmesin.
- [ ] W5.4 ownerUid/tankId/analysisId geçerliliği; owner içinde benzersiz ID; tank ownership; finite sayılar; timestamp ve provenance alanları commit öncesinde doğrulansın.
- [ ] W5.5 Raw measurement + relevant context snapshot + assessment + sürümler tek atomik commit olsun. Bir parça yazıldıktan sonra assessment üretme.
- [ ] W5.6 observeForTank, observeRecord, latestForTank, createAnalysis ve deleteAnalysis uygulansın; sorgu/mutasyon kimliği owner+tank+analysis ile sınırlandırılsın.
- [ ] W5.7 Tarih sırası observedAt DESC, createdAt DESC ve sabit ID tie-break ile dondurulsun. Yeni girilen eski tarihli kayıt son ölçümü yanlış değiştirmesin.
- [ ] W5.8 V1 dashboard tek latest record kullansın; son kayıtta eksik GH varsa önceki kaydın GH’ını kaynak/zaman belirtmeden doldurmasın.
- [ ] W5.9 Create için request/idempotency politikası olsun: çift tıklama ve commit sonrası süreç ölümü/cevap kaybı retry’ında ikinci kayıt oluşmasın.
- [ ] W5.10 Delete exact record için idempotent/NotFound semantiğine sahip olsun; son kaydın silinmesi latest’i yeniden seçsin; tek kayıt silinince NoAnalysis olsun.
- [ ] W5.11 Geçmiş açılışında yeniden değerlendirme yapma. Katalog değişimi veya bitki/canlı silinmesi tarihsel sonuç ve açıklamayı değiştirmesin.
- [ ] W5.12 Corruption, unsupported schema, disk full ve I/O error’ı NoAnalysis/başarılı kayıt gibi göstermeyen typed sonuçlar kullan; mevcut commercial recovery/cutover politikasını koru.
- [ ] W5.13 Geçmiş üst sınırı, query/list page boyutu, serileştirme süresi/belleği ve retention politikası belgelensin. Sessiz kayıt atma veya sınırsız UI listesi büyümesi olmasın.

**Geçiş ölçütü:** Round-trip veri kaybı yok; process death sonrası başarılı commit duruyor; schema/corruption politikası ve tekrar işlem davranışı testli.

### W6 — Silme, hesap değişimi ve restore bütünlüğü

- [ ] W6.1 OwnerTankDataCleaner transaction’ına Water Analysis snapshot/delete/restore adımlarını ekle; yeni store yazmalarını tank silme başlangıcında engelle.
- [ ] W6.2 Write guard’ı gerçek commit yoluna bağla. Yalnız create başında tank var mı kontrolü yapıp daha sonra korumasız commit etme.
- [ ] W6.3 Mevcut durable journal snapshot formatını sürümlendir; eski pending journal durumu için açık recovery/cutover davranışı tanımla.
- [ ] W6.4 Büyük analiz snapshot’larını mevcut SharedPreferences journal’a sınırsız gömmeyi önle. Kapasiteyle uyumlu durable snapshot/staging yaklaşımını recovery testleriyle seç.
- [ ] W6.5 Snapshot alma, dependent silme, tank commit, rollback ve journal complete aralarındaki her crash/cancellation noktası için yeniden başlama sonucu test edilsin.
- [ ] W6.6 Rollback hem care hem analysis snapshot’larını geri getirsin; rollback başarısızsa journal silinmesin ve recovery devam edebilsin.
- [ ] W6.7 Eşzamanlı save/delete, iki analiz yazması, aynı ID retry, restore/create ve owner logout/account switch senaryoları bariyerlerden geçsin.
- [ ] W6.8 Mevcut device assignment cleanup ve reminder davranışları korunmalı; analiz entegrasyonu bu adımları atlatmamalı.
- [ ] W6.9 UserDataCleaner ve account deletion checkpoint/retry akışına owner’a ait analiz store’u, draft/idempotency kayıtları ve yeni journal payload’ları katılsın.
- [ ] W6.10 Owner graph generation değişince eski collector/event/draft UI’a taşınmasın; eski oturumun işlemi yeni hesaba yazamasın. Aynı owner ile yeni oturum generation’ı da kapsansın.
- [ ] W6.11 Backup dahilse archive schema/validator/snapshot/codec/restore/deduplication/journal’da yer alsın; owner/tank/analysis kimliği ve gerekiyorsa yerel entity referansları canonical restore politikasıyla eşlensin.
- [ ] W6.12 Backup hariçse export/restore sonucu kullanıcıya açıkça belirtilsin; “tüm veri geri geldi” ifadesi eksik geçmişi gizlemesin. Android otomatik backup/data-extraction kuralları ayrı denetlensin.
- [ ] W6.13 Tank duplicate’de geçmiş kopyalanmasın önerisini sözleşme ve regresyon testinde sabitle; açıkça istenen başka davranış varsa bağımsız provenance tasarla.
- [ ] W6.14 Data inventory/retention/export belgelerini güncelle; log/analytics’e ham ölçüm, owner kimliği veya notların kontrolsüz düşmesini önle.

**Geçiş ölçütü:** Tank silindikten sonra orphan analiz oluşmuyor; silme başarısız olduğunda geçmiş korunuyor; owner ve restore izolasyonu testli.

### W7 — Composition, ViewModel ve lifecycle

- [ ] W7.1 Dependencies committed OwnerDependencyGraph/OwnerViewModelFactory üzerinden kurulsun; releaseSmoke karşılığı aynı application yolunu kullansın.
- [ ] W7.2 UI/VM’de repository, DataStore, JSON reader, device provider veya Firebase construction/lookup olmasın.
- [ ] W7.3 Mevcut tek WaterAnalysisViewModel hedefi korunacaksa add/history/detail/latest state’lerini ayrı immutable alanlarda tut ve kapsamını açık tanımla. Ayrı VM gerekirse önce sözleşme kararını güncelle; dev bir feature VM oluşmasın.
- [ ] W7.4 Route tankId/analysisId, form girdileri, seçilen ölçüm zamanı ve MANUAL/SENSOR tercihi SavedStateHandle veya proje standardı ile restore edilsin; tüm geçmiş Bundle’a konulmasın.
- [ ] W7.5 Lifecycle-aware collection, tank değişiminde eski akışın iptali, view binding temizliği ve navigation event tüketimi sağlansın.
- [ ] W7.6 Saving/Deleting durumları tekrar komutu engellesin; işlemin başarı sinyali yalnız commit sonrası gelsin; cancellation yutulmasın.
- [ ] W7.7 ValidationError, TankMissing, SensorStale, ContextUnavailable, StoreFailure ve RecordMissing sonuçları merkezi process-safe feedback’e bağlansın.
- [ ] W7.8 Katalog I/O ve yoğun değerlendirme uygun dispatcher’da yürüsün; dispatcher/clock/ID generation test edilebilir şekilde sağlansın.

**Geçiş ölçütü:** Rotation, geri dönme, tank değişimi, süreç yeniden yaratma ve owner değişimi kayıt kimliğini veya form kaynağını bozmuyor.

### W8 — Onaylı UI’ı gerçek veriyle bağla ve fixture’ları temizle

- [ ] W8.1 Form XML’lerindeki gerçek android:text ölçüm doldurmalarını kaldır; mevcut kart/grid/alan tasarımını koruyarak §25 görünürlük politikasını ve Türkçe adları bağla. Ek alan etkileşimi kararlaştırılmış olmalı; tasarım örnekleri yalnız tools:text veya test/debug fixture'ında kalsın.
- [ ] W8.2 Sabit 26.09.2026 14:10 başlangıcını kaldır; Clock ve restore edilmiş draft kullan. LocaleFormatter parsing/formatting; boş ile invalid ayrımı; alan bazlı hata gösterimi bağlansın.
- [ ] W8.3 Date picker saat kısmını, time picker tarih kısmını beklenmedik değiştirmesin; locale/12–24 saat/timezone davranışları sınansın.
- [ ] W8.4 Sensör adı, bağlantı rozeti, sıcaklık ve “sensörden okundu” etiketi gerçek source state’e bağlansın; sensör yokken varsayılan 25 değeri kalmasın.
- [ ] W8.5 Save boş listener yerine application create’i çağırıp başarı sonrası onaylı rotayı izlesin; hatada form verileri korunmalı.
- [ ] W8.6 History 12 fixture yerine owner/tank flow kullansın; toplam kayıt sayısı store’dan türesin; her satır stable analysisId taşısın.
- [ ] W8.7 History’de pH, NO3 ve sıcaklığın tüm değer/status/color alanları bind edilsin; XML’de pH/sıcaklık için kalan “Normal” etiketleri unutulmasın.
- [ ] W8.8 nav_aquarium.xml detay rotasına analysisId ekle; generated Directions ve navigateSafelyFrom kullan. Deep link/restore/yanlış ID merkezi eksik kayıt davranışıyla ele alınsın.
- [ ] W8.9 Detail tamamıyla persisted snapshot okusun; değerler, nedenler, tarih ve not alanı gerçek kayda ait olsun. Kayıt yoksa sahte detay oluşturulmasın.
- [ ] W8.10 Delete dialog tarihi seçili kayıttan locale-aware formatlansın; hard-coded 26 Sep 2026 kalksın; navigation yalnız gerçek delete başarısından sonra olsun.
- [ ] W8.11 TankHealthContentAdapter immutable fixture buildItems yerine real presentation model kullansın; default status=Normal ve yeşil renk fallback’i kaldırılmış olsun.
- [ ] W8.12 “Son analiz” tarihi, data age/stale işareti ve §25'e uygun ölçüm kartları latest record'dan beslensin; sabit sekiz kart varsayımı kalksın. Tank türü değişikliği eski kaydın ölçümlerini gizlemesin; eski ölçüm güncel canlı su durumu gibi sunulmasın.
- [ ] W8.13 Bakım bölümünü MaintenanceOperations tamamlanmış kayıtlarından bağla; completedAt ile dueAt birbirine karışmasın. Son bakım kaydı yoksa “5 gün önce/Normal” uydurulmasın; overdue/approaching için tanımlı takvim politikası kullanılsın.
- [ ] W8.14 Sistem özeti canonical material selections/assigned cihaz/context verisinden gelsin; birden fazla ürün, kayıt yokluğu ve offline durumları ayrı olsun.
- [ ] W8.15 “28 canlı” gerçek quantity toplamından gelsin; sayıyı doğrulanmamış biyolojik yük/risk skoruna dönüştürme.
- [ ] W8.16 TankDetailTankFragment giriş kartındaki sabit 82, “iyi görünüyor” ve “2 gün önce” metinleri de gerçek summary/NoAnalysis durumuna bağlansın.
- [ ] W8.17 NoAnalysis, Loading, Partial, Error, NotFound ve SensorUnavailable görünümü mevcut tasarım diliyle tamamlanmış olsun. Tasarım dondurulmuş olması sahte başarı göstermeyi gerektirmez.
- [ ] W8.18 Tüm locale resource’larında kullanımdan düşen fixture değer/tarih/model string’leri temizlensin; geçerli statik etiketler ve açık empty-state metinleri korunmalı.
- [ ] W8.19 Yosun sekmesi henüz geliştirilmemişken çalışan analiz sonucu gibi görünmesin; kapsamı açık placeholder/non-active davranışı olsun.
- [ ] W8.20 §25.3'teki her alan için “Nedir / Nasıl ölçülür?” yardım önerisinin yerleşimini kullanıcıyla kararlaştır; mevcut tasarım korunarak uygulanırsa temel ve ek alanların açıklamaları, birimleri ve varsa doğrulanmış test talimatları eşleşsin. “Amonyak ve amonyum toplamı” açıklaması da kapsansın.
- [ ] W8.21 §6.5–6.6 K03.2–K03.4 akışını onaylı tasarımı bozmadan bağla: her parametre için küçük test/cihaz kontrolü; hatırlanan seçim; doğrulanmış profilden ölçüm türü/raporlama temeli/birim/capability; katalog dışı üründe rehberli typed fallback; kutuda kaynak birimi görünür ve dönüşüm application katmanında yapılır. Ana etiket `Toplam amonyak` kalır. Profil aynı olayda direct free NH3 de üretiyorsa `Serbest amonyak (NH3)` ayrı input olarak açılır ve iki değer birlikte girilebilir; selector yalnız source gerçekten mutually-exclusive mode kullanıyorsa gösterilir. Kaynak değişimi mevcut sayıyı sessizce yeniden anlamlandırmasın. Semantiği çözülemeyen alan normal analize commit edilmesin, draft/form state'te kalabilsin; diğer doğrulanmış ölçümler kaydedilebilsin.

- [ ] W8.22 Chlorine-family UI K03.8'e uysun: aynı sample/test free+total veriyorsa ayrı `Serbest klor` ve `Toplam klor` alanları; direct monochloramine yöntemi seçilirse ayrı `Monokloramin`; raw/conditioned/tank sample context açık seçili/görünür olsun. `Total - free` UI'da ölçülmüş monokloramin diye gösterilmesin; inconsistent total<free pair re-test/validation durumuna düşsün.

**Geçiş ölçütü:** Sağlık giriş kartı dahil kullanıcıya gösterilen her dinamik değer için gerçek kaynak veya açık veri-yok/hata durumu var. Onaylı ekran düzeni korunuyor.

### W9 — Water Quality kabul kapısı

- [ ] W9.1 Her parametre için normal/low/high/critical/missing/unsupported/rule-missing ve tam sınır testleri geçsin.
- [ ] W9.2 Dokuz tank türünün alan matrisi, Other/boş/bilinmeyen kod, tank türü değişirken draft/geçmiş verisinin korunması; tür ve bitki kesişimleri, approximate/SOFT politikası, custom/missing kimlik ve conflict + critical + partial bileşimleri test edilsin.
- [ ] W9.3 K03.2–K03.12 semantic/unit/source tests geçsin: önceki chemistry testlerine ek olarak K mg/L-as-K round-trip; known ppm-K profile mapping; K2O/unknown compound-basis rejection; Fe mg/L-as-Fe with total/dissolved/ferrous/method-defined scope round-trip; scope mismatch rejection; known-profile automatic scope resolution; multi-result Fe no-overwrite; no bare-value fertilizer dose; unsupported semantic→INSUFFICIENT_DATA.
- [ ] W9.4 Save/history/detail/delete/latest round-trip; owner/tank isolation; çift işlem; veri kaybı/corruption; exact ID; eski tarihli kayıt testleri geçsin.
- [ ] W9.5 Sıcaklık freshness/reboot/reassignment/backdating ve geçmiş context açıklaması testleri geçsin.
- [ ] W9.6 Tank silme/rollback/process-death/account deletion/restore test matrisi tamamlanmış olsun; eski care/device davranışları korunmuş olsun.
- [ ] W9.7 En büyük desteklenen geçmiş/katalog bağlamı ile süre/bellek ölçümleri bütçe içinde olsun; ana thread disk okuması ve kontrolsüz collector/list büyümesi olmasın.
- [ ] W9.8 Dark/light, büyük font, TalkBack, Türkçe/İngilizce, rotation, boş ve uzun hata metni görsel/işlevsel regresyon kontrolü yapılsın; durum sadece renkle anlatılmasın.
- [ ] W9.9 Yeni water-analysis architecture guard; mevcut aquarium/care/composition/navigation/localization/feedback kontrolleri geçsin; yeni suppress/baseline veya devre dışı guard eklenmesin.
- [ ] W9.10 Debug/Release unit, lint, detekt, minified release, CodeQL, installable APK ve API 27/API 36 release-smoke/emulator akışları gereken commit üzerinde yeşil olsun.
- [ ] W9.11 Fiziksel Cooling sensör testi ile epoch/uptime, bağlantı kesilmesi ve gerçek atama davranışı doğrulansın; emulator kanıtı fiziksel sensör kanıtı yerine geçmesin.
- [ ] W9.12 Ana sözleşme §46 kabul maddelerinin her biri test adı/CI run/manuel kanıtla işaretlensin; fixture temizliği tam repo taramasıyla doğrulansın.

**Geçiş ölçütü:** Water Quality uçtan uca tamam ve doğrulanmış. Bundan sonra aşağıdaki ekranların tasarım/uygulamasına sırayla geçilir.

## 6. Sonraki ekranların sırası ve sınırları

### A — Yosun Kontrolü

- [ ] A.1 Water Quality kabul kapısı kapandıktan sonra observation/input/output sözleşmesini ve ekran akışını tasarla.
- [ ] A.2 Tank-level yosun gözlemi: gözlem tarihi, konum/yayılım ve doğrulanabilir kullanıcı bulguları; fotoğraf varsa mevcut media ownership altyapısı.
- [ ] A.3 Ortak AquariumHealthContext + WaterAnalysisOperations üzerinden uygun zaman penceresindeki gerçek analizlere bağlan; “recent/current” yaş politikasını kullan.
- [ ] A.4 Ekipman varlığıyla gerçek fotoperiyot, ışık şiddeti, CO2 kararlılığı veya doz geçmişini aynı sayma; gerekli yeni girdilerin application sınırlarını belirle.
- [ ] A.5 Ayrı AlgaeAssessmentEngine, neden/kanıt/eksik veri/öneri modeli ve gözlem-müdahale-takip geçmişi kur.
- [ ] A.6 Tür/gözlem kesin değilse kesin yosun teşhisi veya otomatik kimyasal müdahale üretme; canlı/bitki güven koşulları önerilere dahil olsun.
- [ ] A.7 Owner/tank/snapshot/silme/restore/test kurallarını uygula; ardından kabul kapısını kapat.

### P — Bitki Sağlığı

- [ ] P.1 Yosun Kontrolü tamamlandıktan sonra `tankId + plantId` seçimi ve gözlem ekranlarını tasarla; catalogId tek başına kayıt kimliği olmasın.
- [ ] P.2 VERIFIED/PARTIAL bakım profili, ortak bağlam ve zamanına uygun su analizlerini kullan.
- [ ] P.3 Yaprak/büyüme/besin bulgularının tarihli gözlem modeli ve ayrı PlantHealthAssessmentEngine olsun; uyumsuz su değerini hastalık teşhisine çevirmesin.
- [ ] P.4 Bitki üzerindeki yosun bir bulgu olarak kaydedilsin; yosun değerlendirme/müdahalesi tankın Yosun Kontrolü akışına yönlensin.
- [ ] P.5 Fotoğraf ve kayıtlı bitki silinmesi/yeniden adlandırılması durumlarında tarihsel kayıt ve media sahipliği tanımlansın; gözlem geçmişi canlı katalog adına bağımlı kalmasın.
- [ ] P.6 Kayıt/silme/restore/owner/lifecycle ve motor testleriyle kabul kapısını kapat.

### L — Canlı Sağlığı

- [ ] L.1 Bitki Sağlığı tamamlandıktan sonra `tankId + livestockId` seçimiyle davranış/stres/belirti ve geçmiş ekranlarını tasarla.
- [ ] L.2 Mevcut water compatibility sonucu, shared context ve uygun tarihli analizleri tüket; ikinci su uyumluluğu motoru oluşturma.
- [ ] L.3 Ayrı LivestockHealthAssessmentEngine ve gözlem modeli; custom/unverified canlı, çoklu adet, kaldırılmış kayıt ve belirsiz gözlem durumlarını tanımla.
- [ ] L.4 Su uyumsuzluğu ile hastalık/belirti değerlendirmesi ayrı kalsın; kesin veteriner teşhisi üretme.
- [ ] L.5 Fotoğraf/observation history, owner izolasyonu, tank/canlı silme, restore ve lifecycle testleriyle kabul kapısını kapat.

Ortak altyapı yeniden kullanılır; dört motorun iş kuralları ve geçmiş kayıt türleri ayrı kalır. WaterAnalysisRecord gelecekte tüm sağlık kayıtlarını içine dolduran genel bir modele dönüşmemelidir.

## 7. Fixture ve gerçek kaynak eşlemesi

| Görünen veri / işlem | Mevcut yer | Bağlanacak kaynak veya davranış |
| --- | --- | --- |
| Sekiz ölçüm ve durum rengi | TankHealthContentAdapter; detail XML; değer string’leri | WaterAnalysisOperations.latestForTank / observeRecord → UiMapper |
| İlk form değerleri | item_tank_health_analysis_water_parameters.xml | Boş veya açık draft; normal ölçüm varsayımı yok |
| Tarih/saat | TankHealthAnalysisAddFragment selectedCalendar; measurement_time XML | Clock + kullanıcı seçimi + restored draft |
| Sensör adı/bağlı/25 °C/kaynak | sensor_section XML + TemperatureSource | TankWaterTemperatureOperations |
| Kayıt listesi ve “12 kayıt” | HistoryFragment.allRecords; history summary string | observeForTank + gerçek sayı |
| Satırdaki pH/sıcaklık “Normal” | history_record XML | İlgili kaydın parameter assessment’ı |
| Detail kimliği | History openRecordDetail; nav_aquarium.xml | tankId + analysisId |
| Silme ve dialog tarihi | DetailFragment.setupDeleteResult; delete_message | Seçili kayıt tarihi + delete sonucu |
| Son analiz tarihi | water_quality_header XML | Latest persisted observedAt ve açık veri yaşı |
| Su değişimi/budama/filtre yaşı | maintenance_section XML | MaintenanceOperations COMPLETED olayları; overdue için ayrı due politikası |
| CO2, ışık, filtre, canlı sayısı | system_section XML | Tank snapshot + canonical context + quantity toplamı |
| Giriş kartı skor/durum/tarih | fragment_tank_detail_tank.xml | Gerçek health summary; tanımlı skor yoksa veri yok |
| “Not eklenmedi” | detail XML | Gerçek note boşsa geçerli empty metni; mock diye körlemesine kaldırılmaz |

Sahte ölçüm/başarı üreten fallback kaldırılır. Açık NoData/Error durumları, kullanıcının seçtiği manuel sıcaklık ve güvenli navigation davranışı geçerli hata yönetimidir; bunlar aynı kapsamda silinmez.

## 8. Sözleşme bölümlerinin checklist karşılığı

| Sözleşme bölümleri | Checklist |
| --- | --- |
| Status, §1–3: hedef, kapsam, envanter | İnceleme §1–2; W0; W8 |
| §4: mimari sınır | Paket haritası; W1–W3; W7 |
| §5–7: kimlik, zaman, ölçüm/birim, provenance | W0–W1; W3; W5 |
| §8–12: bağlam, canlı/bitki, kesişim | W1–W2; W4 |
| §13–16: chemistry, severity, structured output, öneri | W0; W4 |
| §17–20: store, operations, tarihsel snapshot, sürüm | W1; W5 |
| §21–23: tank/hesap silme, backup/export | W0.12; W6 |
| §24–27: validation, taxonomy, ekipman, sensör | W0–W3; W4 |
| §28–30: UI, ViewModel, composition | W7–W8 |
| §31–36: saflık, nedenler, conflict, eksik veri, kanıt, güven | W0; W2–W5 |
| §37: Smart Care sınırı | W4.11; W8.13 |
| §38–40: diğer sağlık motorları | A; P; L |
| §41–42: invariants ve process death | W3; W5–W7; W9 |
| §43–44: test ve CI | Her aşamanın geçiş ölçütü; W9 |
| §45–47: sıra, kabul, ortak mimari | W0–W9; A → P → L; paket haritası |

## 9. Bu incelemede yapılan doğrulamalar

- GitHub branch’i fetch edildi; inceleme yukarıdaki commit’e sabitlendi.
- Sözleşme; ilgili application/data/UI/proto/navigation/resources/composition/integrity/backup kodlarıyla karşılaştırıldı.
- JSON/JSONL dosyalarından katalog sayıları, alan doluluğu, plant readiness, completeness, livestock warningMode/confidence yeniden sayıldı; sonuçlar bölüm 2’de.
- `python3 tools/aquarium_application_boundary_guard.py`: geçti.
- `python3 tools/care_application_boundary_guard.py`: geçti.
- `python3 tools/composition_root_guard.py`: geçti.
- `python3 tools/navigation_guard.py`: geçti.
- İncelenen worktree’de uygulama kodu değişikliği yok.

Çalıştırılmayanlar: Gradle build/unit/lint/detekt, CodeQL, emulator, fiziksel cihaz ve sensör testleri. Bu rapor mevcut CI’ın tamamının yeşil olduğunu veya yeni motorun test edildiğini iddia etmez. Mevcut dört guard’ın geçmesi yeni Water Analysis sınırlarının hazır olduğunu kanıtlamaz.

## 10. Kod kanıtları

Aşağıdaki bağlantılar branch’in hareketli adı yerine incelenen commit’e sabitlenmiştir.

[S1]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/docs/architecture/AQUARIUM_WATER_ANALYSIS_CONTRACT.md
[S2]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/java/com/aqua/aqualight/application/aquarium/LivestockWaterRequirements.kt
[S3]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/assets/aqualight_plant_catalog.json
[S4]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/assets/livestock_catalog.jsonl
[S5]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/TankHealthAnalysisAddFragment.kt
[S6]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/TankHealthAnalysisHistoryFragment.kt
[S7]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/TankHealthAnalysisDetailFragment.kt
[S8]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/res/navigation/nav_aquarium.xml
[S9]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/java/com/aqua/aqualight/data/aquarium/catalog/livestock/LivestockWaterRequirementParser.kt
[S10]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/catalog/plant/AquariumPlantCatalog.kt
[S11]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/java/com/aqua/aqualight/application/devices/cooling/DeviceCoolingTelemetrySnapshot.kt
[S12]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/java/com/aqua/aqualight/data/aquarium/delete/OwnerTankDataCleaner.kt
[S13]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/java/com/aqua/aqualight/data/care/integrity/TankCareIntegrityJournal.kt
[S14]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/res/values/tank_health_strings.xml
[S15]: https://github.com/ozdemirrrcemal-cmyk/AquaLight/blob/ca996f241470117a5a797733a13e4c69ffab176c/app/src/main/java/com/aqua/aqualight/application/aquarium/LivestockCatalogOperations.kt
