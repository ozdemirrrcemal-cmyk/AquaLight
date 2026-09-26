# K03 — Konsantrasyon birimleri ve kimyasal raporlama temeli

Araştırma tarihi: 26.09.2026 (Europe/Istanbul).

Durum: **K03.0 ölçüm kapsamı, K03.1 NO3/NO2/PO4 kanonik kayıt anlamı/birimleri, K03.2 test/cihaz seçimi + kaynak semantiği çözümleme + normalizasyon akışı, K03.3 amonyak kanonik temelleri, K03.4 concurrent multi-result kayıt/UI politikası, K03.5 deniz salinity/SG dönüşüm güvenliği, K03.6 alkalinite/KH semantik-birim politikası, K03.7 çözünmüş oksijen konsantrasyon/doygunluk politikası, K03.8 klor/kloramin sample-context politikası, K03.9 marine elemental Ca/Mg politikası ve K03.10 conductivity/TDS ürün politikası kabul edildi. K03.11 ve sonraki kararlar açık.** Kabul edilen normatif kapsam ana sözleşme §6.1–6.6, §7, §25.1–25.3 ve §28.1'de kayıtlıdır. Bu araştırma dosyası uygulama kodu veya bilimsel güvenlik eşiği değildir.

Kapsam: K03.0 için ölçüm seçiminin kaynaklarını, K03.1'de kabul edilen NO3/NO2/PO4 ortak raporlama temelini, K03.2'de kabul edilen source-aware giriş/normalizasyon yaklaşımını, K03.3'te kabul edilen amonyak kanonik temellerini, K03.4'te kabul edilen concurrent multi-result cardinality/UI davranışını, K03.5'te kabul edilen marine salinity/SG/conductivity ayrımını, K03.6'da kabul edilen total-alkalinity/KH politikasını, K03.7'de kabul edilen dissolved-oxygen concentration/saturation politikasını, K03.8'de kabul edilen chlorine/chloramine sample-context politikasını, K03.9'da kabul edilen elemental Ca/Mg semantiğini ve K03.10'da kabul edilen conductivity/TDS ürün davranışını izlenebilir tutmak. CO2 ve diğer ek parametrelerin semantiği, kanıtlı profil/dönüşüm tablolarının ayrıntıları ve türetilmiş hesaplar sonraki ayrı kararlar olacak.

## Doğrulanan ayrımlar

1. **Birim ile kimyasal raporlama temeli ayrı bilgiler.** `mg/L NO3` ile `mg/L NO3-N` aynı sayıyı ifade etmez. Hach NT3100sc kılavuzu NO3-N → NO3 için 4.43 çarpanını verir. Örneğin 1 mg/L NO3-N yaklaşık 4.43 mg/L NO3'tür [R1]. Bu, güvenlik eşiği değil kütle temeli dönüşümüdür.
2. **Nitrit için de N ile NO2 ayrımı var.** Hanna üretici açıklaması NO2-N → NO2 için 3.29 çarpanını verir [R2]. Bu not yalnız kimyasal temel ayrımını kullanır; ürünün bütün özellikleri veya kullanım uygunluğu onaylanmış değildir. Uygulamadaki katsayı hassasiyeti ayrıca dondurulmalıdır.
3. **Fosfor ile fosfat sayısal olarak aynı değil.** Hanna, kendi phosphorus checker çıktısında ppb P → ppm PO4 için önce 3.066 ile çarpma, sonra 1000'e bölme tarif eder [R3]. Buradaki kimyasal kütle dönüşümü ile ppb/ppm ölçek dönüşümü farklı adımlardır; bu sayfa tek başına her kaynaktaki ppm'in mg/L veya mg/kg anlamını kanıtlamaz.
4. **Analizin kapsamı da korunmalı.** Hach, doğrudan renk reaksiyonu ile ölçülen ortofosfat/reaktif fosforu; sindirim adımları içeren toplam fosfor ölçümünden ayırır [R4]. Toplam fosforu yalnız bir katsayı ile çarparak ortofosfat ölçümü diye kullanamayız.
5. **ppm ile mg/L evrensel olarak eşit değil.** USGS, düşük çözünmüş madde yoğunluklarında yakın sayısal eşdeğerlikten söz eder; daha yüksek yoğunluklar için density correction gerektiğini belirtir [R5]. Bu, akvaryum ürünü adına göre körlemesine dönüşüm yapma izni değildir. Kaynağın ppm tanımı ve raporlama temeli belirlenmelidir.

## Kod tarafındaki kanıt

İncelenen uygulama snapshot'ı: `de767985c29f3cc36a3c394d0887c163bfbaf6e2`.

- `application/aquarium/LivestockWaterRequirements.kt` içindeki ölçüm ve gereksinim modellerinde `nitratePpm` ve `phosphatePpm` alanları var.
- `app/src/main/assets/livestock_catalog.jsonl` içindeki 687 kaydın anahtarlarının birleşimi tarandı. Alan başına `source`, `evidence`, `basis`, `unit`, `revision` veya `reference` anahtarı bulunmadı.
- Bu dosya incelemesi, kaynakların repo dışında veya başka belgelerde hiç bulunmadığı anlamına gelmez. Mevcut JSONL tek başına NO3/NO3-N, PO4/P veya ppm temelini kanıtlamıyor.
- Mevcut UI NO3, NO2 ve PO4 değerlerini mg/L ile sunuyor. K02, mevcut ölçüm modellerinin genişletilmesini kabul etti. Sonraki K03.0 kararı ana alanı toplam amonyak olarak adlandırır; K03.1, aşağıdaki NO3/NO2/PO4 kanonik standardını kabul eder. Bu kararlar mevcut katalog değerlerinin kaynak temelini kanıtlamaz.

## K03.1 — kabul edilen karar, 26.09.2026

| UI alanı | Kabul edilen kanonik kayıt/motor temeli |
| --- | --- |
| NO3 | Nitrat, mg/L olarak NO3 |
| NO2 | Nitrit, mg/L olarak NO2 |
| PO4 | Ortofosfat/reaktif fosfat sonucu, mg/L olarak PO4 |

Buradaki “PO4 olarak” ifadesi raporlanan kütle temelidir; suda bütün fosfatın yalnız tek bir iyonlaşma halinde bulunduğu iddiası değildir.

Gerekçe: Ekran adlarıyla doğrudan eşleşen, tek anlamlı bir iç temsil sağlar. Kullanıcı bu yaklaşımı kabul etti; normatif karşılığı ana sözleşme §6.1'dir. Örneğin kaynak zaten 18 mg/L NO3 raporluyorsa standart sonuç yine 18 mg/L nitrat olur. N veya P temelinde gelen uyumlu analitik sonuçların dönüşümü kaynak bilgisi doğrulandıktan sonra ayrı politikayla ele alınacaktır. Toplam fosfor yalnız katsayıyla ortofosfat ölçümüne dönüşmez.

## K03.1'in tek başına kapatmadığı konular

- Kullanıcının NO3-N, NO2-N, PO4-P, ppm veya ppb çıktılı kitleri nasıl seçeceği/gireceği ve desteklenecek kaynak formatları.
- Kaynakta ppm'in mg/L olarak mı, kütle oranı olarak mı kullanıldığının doğrulanması; tatlı/deniz suyu dönüşüm politikası.
- Katalogdaki her karşılaştırma aralığının raporlama temeli ve bilimsel kaynağı; belirsiz kaynak için değerlendirilemez durumunun uygulanması.
- Dönüşüm katsayılarının hassasiyeti, kaynak sürümü, raw değer ve source-unit provenance saklama şekli.
- Ana alan olarak kabul edilen toplam amonyağın N veya başka kimyasal kütle temelinde raporlanması; serbest NH3 / yalnız NH4 sonuçlarının ayrı desteklenip desteklenmeyeceği ve pH/sıcaklık/tuzluluk önkoşulları.
- Güvenli/tehlikeli eşikler ve türetilmiş kimyasal hesaplamalar.

K03.1'in kabul edilmesi bu kalan konuları onaylamaz veya uygulanmış yapmaz. Belirsiz kaynak birimleri sessizce kanonik değere çevrilmeyecek.

## K03.2 — kabul edilen test/cihaz ve kaynak-normalizasyon kararı

26.09.2026 tarihinde kabul edildi: **Ham kaynak sonucunu koru; ölçüm anlamını girişte test/cihaz profili veya rehberli typed seçimle çöz; yalnız doğrulanmış anlam ve birimle standart değeri üret.**

1. Test/cihaz tercihi ölçüm parametresine göre tutulur ve owner izolasyonuyla hatırlanır; tek bir global ürün bütün parametrelere uygulanmaz. Tank-bound cihazlarda tank/device assignment sınırı ayrıca geçerlidir.
2. Bilinen üründe marka tek başına yeterli değildir. Doğrulanmış marka+model/yöntem/sonuç modu profili ölçülen analiti, kimyasal raporlama temelini, kaynak birimini, desteklenen sonuç modlarını ve dönüşüm metadata'sını sağlar.
3. Sonraki ölçümlerde kullanıcı normalde yalnız sonucu girer; giriş kutusunda kaynak birimi/raporlama bağlamı görünür, desteklenen dönüşümü application katmanı yapar. Kullanıcıya elle katsayı uygulattırılmaz.
4. Profil birden fazla semantik sonuç yeteneğini açıkça tanımlar. Kaynak bu sonuçları aynı ölçüm olayında birlikte üretebiliyorsa K03.4 uyarınca ayrı input/metric gösterilir ve birlikte kaydedilebilir. Yalnız kaynağın kendi çalışma biçimi sonuçları gerçekten mutually-exclusive yapıyorsa açık mode seçimi gösterilir; uygulama modu sayıya veya önceki tercihe bakarak tahmin etmez.
5. Ürün katalogda yoksa destek bitmez. Kullanıcı kontrollü listeden ölçüm türü, kimyasal raporlama temeli ve kaynak birimini seçer. Serbest metin kimyasal anlamın kaynağı değildir; çözümlenen typed semantik provenance'a yazılır.
6. Girilen ham sonuç, kaynak semantiği, varsa profil kimliği/revision, result mode ve normalize edilmiş sonuç ayrı tutulur. Kaynak aynı anlam/birimdeyse değer değişmez; uyumlu farklı temel/birimde doğrulanmış dönüşüm uygulanır. Yuvarlama yalnız sunumdadır.
7. `ppm` etiketi tek başına `mg/L` sayılmaz. Kaynağın tanımı ve gerekiyorsa yoğunluk koşulları doğrulanmadan dönüşüm yapılmaz; tatlı/deniz suyu için varsayılan katsayı uydurulmaz. NO3-N/NO2-N/ortofosfat-P ile analitik kapsamı farklı toplam fosfor ayrımı korunur.
8. Ürün/yöntem/birim/result mode değeri girildikten sonra değişirse mevcut sayı sessizce yeniden anlamlandırılmaz; açık yeniden onay/re-entry gerekir veya eski ham değer eski source binding'iyle korunur.
9. Ölçüm anlamı/birimi hâlâ çözülemiyorsa o alan normal analiz kaydına commit edilmez ve motor karşılaştırmalarına/türetilmiş hesaplara girmez. Girdi taslak/form durumunda kalabilir; diğer doğrulanmış alanlar değerlendirilebilir ve kaydedilebilir. Bu davranış ölçülmemiş veya sıfır sonucu değildir ve tek başına durable draft-store kararı vermez.

Bu karar UI davranışını ve source-resolution mimarisini dondurur; tam ticari ürün kataloğunun içeriği, profil revision sahipliği, her dönüşüm katsayısının hassasiyeti ve bilimsel eşikler yine kanıtlı veri/uygulama işi olarak tamamlanmalıdır. Katalogdaki karşılaştırma aralıkları da aynı anlam/birim doğrulamasından geçmelidir.

## K03.3 — kabul edilen amonyak kanonik temeli

26.09.2026 tarihinde kabul edildi:

- **Toplam amonyak:** `TOTAL_AMMONIA_NITROGEN (TAN)` → **mg/L as N**.
- **Doğrudan serbest amonyak:** `FREE_AMMONIA_NH3` → **mg/L as NH3**.
- Test/cihazın verdiği ham sonuç, kaynak birimi/raporlama temeli ve profil/revision korunur; kanonik değer ayrı tutulur.
- Dönüşüm yalnız K03.2'deki doğrulanmış profil veya kontrollü typed source semantics destekliyorsa yapılır. `ammonia`, `NH3`, `NH4` veya `ppm` etiketi tek başına dönüşüm yetkisi değildir.
- TAN, doğrudan serbest NH3, NH4-only sonuç ve gelecekteki hesaplanmış NH3 birbirinin yerine kullanılmaz.
- UI ana alanı kullanıcı dostu **`Toplam amonyak`** olarak kalır; seçili testin kaynak birimi/raporlama bağlamı gösterilir ve kullanıcıya `mg/L as N` dönüşümü yaptırılmaz. Direct result varsa **`Serbest amonyak (NH3)`** olarak açıkça ayrılır.

Bu karar bilimsel güvenlik/toxicity eşiği, hesaplanmış serbest NH3 formülü veya tam ticari ürün kataloğu kabulü değildir.

## K03.4 — kabul edilen concurrent multi-result politikası

26.09.2026 tarihinde kabul edildi: gerçek test/cihaz aynı örnekleme/ölçüm olayında birden fazla bağımsız semantik sonuç üretebiliyorsa AquaLight bu sonuçları tek bir mode alanına sıkıştırmaz.

- TAN ve doğrudan serbest NH3 aynı `WaterAnalysisRecord` içinde aynı `observedAt` olayına ait **iki ayrı measured metric** olarak birlikte saklanabilir.
- Add Analysis ekranında kaynak profili ikisini concurrent destekliyorsa **`Toplam amonyak`** ve **`Serbest amonyak (NH3)`** ayrı input olarak görünür; kullanıcı gerçekten ölçtüğü birini veya ikisini girebilir.
- Her metric kendi raw value, source unit/reporting basis, result/channel identity, normalize canonical value ve provenance bilgisini taşır.
- Mode selector yalnız test/cihaz gerçekten mutually-exclusive çıkış modlarından birini seçtiriyorsa kullanılır. Concurrent çıktıları selector ile birbirinin alternatifi yapmak yasaktır.
- TAN girişi direct NH3'ü silmez veya üretmez; direct NH3 TAN'ı silmez veya türetmez. Gelecekte kabul edilecek calculated NH3 de ayrı derived provenance ile tutulur ve measured NH3'ün üzerine yazmaz.
- Detail ekranı iki measured metric'i ayrı gösterir. History özeti kompakt kalabilir ancak birini diğerinin etiketiyle gösteremez.

Bu karar Seachem'e özel istisna değildir; verified source capability metadata'sına dayanan genel ürün davranışıdır.

## K03.5 — kabul edilen deniz salinity / specific-gravity güvenlik politikası

26.09.2026 tarihinde ticari güvenlik standardı olarak kabul edildi:

- `PRACTICAL_SALINITY_PSS78` ayrı typed metric'tir; PSS-78 domain'de unitless tutulur. Bir cihaz `PSU` gösterebilir, ancak bu display/source metadata'sıdır.
- `SPECIFIC_GRAVITY` ayrı typed metric'tir; salt sayı olarak salinity ile eşitlenmez. Kaynak/method gerektiriyorsa calibration/reference temperature ve sample temperature provenance'ta zorunludur.
- Conductivity ayrı measured metric'tir. Conductivity→PSS-78 yalnız standarda dayalı, versioned algoritma ve gerekli conductivity/temperature/pressure-reference girdileriyle yapılır; yaklaşık hobby formülü kullanılmaz.
- `ABSOLUTE_SALINITY_G_PER_KG` veya başka mass-fraction basis yalnız kaynak açıkça o semantiği tanımlıyorsa kullanılır. Generic `ppt` ifadesi tek başına TEOS-10 Absolute Salinity veya PSS-78 değildir.
- SG↔salinity dönüşümü sayıya bakılarak yapılmaz. Source type, scale/calibration, temperature/reference metadata ve evidence-backed algoritma tam değilse conversion unavailable/insufficient-data sonucu üretilir.
- Kapalı/sentetik akvaryuma okyanus longitude/latitude anomaly correction uygulayarak TEOS-10 Absolute Salinity türetilmez.
- Rule catalog her hedef/aralık için metric/basis/reference semantics taşır; motor yalnız aynı basis'i veya doğrulanmış dönüşümü karşılaştırır.
- Aynı elektronik cihaz tek conductivity/temperature observation'dan hem salinity hem SG türetiyorsa bunlar bağımsız iki kanıt sayılmaz; ortak source-observation provenance ile ilişkilendirilir.
- UI kaynak gerçekte ne raporluyorsa onu gösterir. SG değeri `Tuzluluk 1.026` diye yeniden etiketlenmez; `Specific Gravity (SG)` olarak görünür. Bilinmeyen `ppt` semantiği tahmin edilmez.

Bu kararın güvenlik ilkesi: **yanlış normalize edilmiş kesin sonuç yerine doğru source-native kayıt + açık yetersiz veri durumu tercih edilir.**

## K03.6 — kabul edilen alkalinite / KH semantik ve birim politikası

26.09.2026 tarihinde kabul edildi:

- `TOTAL_ALKALINITY` kanonik acid-neutralizing-capacity metric'idir; canonical unit **meq/L**.
- Aynı `TOTAL_ALKALINITY` semantiğini doğrulanmış şekilde raporlayan kaynaklar `dKH`, `meq/L` veya `mg/L as CaCO3` gösterebilir. Raw source value korunur; canonical normalization application katmanında yapılır.
- Verified total-alkalinity kaynaklarında dönüşüm ilişkileri: **1 dKH = 17.86 mg/L as CaCO3 = 0.358 meq/L** ve **1 meq/L = 50 mg/L as CaCO3**. `ppm` tek başına `mg/L as CaCO3` kabul edilmez.
- Marine/reef profilinde tek alkalinite metric'i `TOTAL_ALKALINITY`'dir. UI'da `Alkalinite` alanı gösterilir; aynı test sonucunu ikinci bir `KH` alanı olarak tekrar girmek yasaktır. Reef kullanıcıları için doğrulanmış source/display dKH olabilir; engine/store canonical meq/L kullanır.
- Freshwater'ta `TOTAL_ALKALINITY`, `CARBONATE_HARDNESS` ve `GENERAL_HARDNESS` ayrı semantiktir. `Tampon kapasitesi (KH)` gibi kullanıcı dostu etiket domain semantic'i tek başına belirlemez; verified test profili belirler.
- Carbonate hardness alkalinity ile ilişkili olsa da aynı fiziksel nicelik değildir. Gerekli total-hardness/alkalinity girdileri ve kabul edilmiş yöntem yoksa alkalinity değerinden tek başına `CARBONATE_HARDNESS` sentezlenmez.
- Aynı alkalinite ölçümünün dKH, meq/L ve mg/L as CaCO3 gösterimleri tek measurement/evidence'dır; ayrı kanıt sayılmaz.
- Unknown `KH` test semantic'i fail-closed davranır: source-native değer korunabilir ancak total-alkalinity veya carbonate-hardness rule'una tahminle sokulmaz; gerekirse `INSUFFICIENT_DATA` üretilir.

Bu kararın amacı aquarium-industry shorthand'ını kullanıcıya korurken domain ve analiz motorunda kimyasal semantiği kaybetmemektir.

## K03.7 — kabul edilen çözünmüş oksijen konsantrasyon / doygunluk politikası

26.09.2026 tarihinde kabul edildi:

- `DISSOLVED_OXYGEN_CONCENTRATION` kanonik metric'tir; canonical unit **mg/L O2**.
- `DISSOLVED_OXYGEN_SATURATION_PERCENT` ayrı **% air saturation** metric/representation'dır; mg/L ile aynı field/semantic değildir.
- Aynı probe observation'dan gelen mg/L ve % saturation iki bağımsız measurement/evidence sayılmaz; ortak source-observation provenance ile ilişkilendirilir.
- mg/L↔% saturation conversion yalnız aynı measurement-event'e ait method-required context ile yapılır: water temperature, barometric/pressure reference ve yöntem gerektiriyorsa salinity veya specific conductance. Algoritma, applicability range ve revision versioned/testli olmalıdır.
- Historical/backdated ölçümde bugünün temperature/salinity/conductivity/pressure context'i kullanılmaz.
- Cihaz kendi temperature/salinity/altitude/barometric compensation'ını yapıyorsa verified source profile bunu belirtir; mevcut compensation inputs/settings provenance'ta korunur ve AquaLight aynı düzeltmeyi ikinci kez uygulamaz.
- Kaynak yalnız mg/L veya yalnız % saturation veriyorsa source-native sonuç saklanabilir. Güvenli cross-representation prerequisite yoksa diğer değer üretilmez ve gerektiğinde `INSUFFICIENT_DATA` / conversion unavailable kullanılır.
- `%100 saturation` otomatik health/normal sonucu değildir; yalnız ilgili atmosferik/çevresel koşullardaki dengeyi ifade eder. 100% üzeri supersaturation mümkündür ve ayrı evidence-backed interpretation gerektirir.
- UI `Çözünmüş oksijen` etiketini korur ve verified source unit'ini gösterir. Aynı cihaz iki gösterimi de veriyorsa ikinci gösterim linked secondary value/detail olabilir; ayrı bağımsız health signal oluşturmaz.

Bu kararın güvenlik ilkesi: **same-event context eksikse kesin-looking DO conversion üretme; doğrudan ölçülmüş/source-native değeri koru ve değerlendirme kapsamını açıkça kısıtla.**

## K03.8 — kabul edilen klor / kloramin ve sample-context politikası

26.09.2026 tarihinde profesyonel güvenlik standardı olarak kabul edildi:

- `FREE_CHLORINE_AS_CL2` ve `TOTAL_CHLORINE_AS_CL2` ayrı measured metric'tir; canonical unit **mg/L as Cl2**.
- Aynı sample/event ve method-compatible free + total pair için `COMBINED_CHLORINE_AS_CL2 = total - free` **derived** olarak hesaplanabilir; bu sonuç monokloramin değildir ve `MONOCHLORAMINE` etiketi taşıyamaz.
- `MONOCHLORAMINE_AS_CL2` yalnız monokloramini spesifik ölçen verified yöntem/profile ile direct measured metric olarak kabul edilir. Dichloramine/organic chloramine gibi species free/total farkından türetilmez.
- Total chlorine free + combined available chlorine kapsamıdır; yalnız `free=0`, `total>0` görmek bütün combined residual'in monokloramin olduğunu kanıtlamaz.
- `RAW_SOURCE_WATER`, `CONDITIONED_SOURCE_WATER`, `TANK_WATER` typed sample context'leri ayrıdır. Before/after conditioner ve tank ölçümleri birbirinin üstüne yazılmaz ve cross-context subtraction yapılmaz.
- Combined derivation yalnız aynı sample context, compatible sample time/window ve method pair için yapılır. `total < free` ise değeri zero'ya clamp etme veya negatif combined üretme; pair inconsistent olarak işaretlenir ve re-test/verification gerekir.
- Verified source profile sample matrix applicability/interference bilgisini taşır. Source-water yöntemi marine/tank matrix için doğrulanmamışsa hard assessment üretmez.
- Chlorine residual transient olabilir; sample timestamp ve yöntemin prompt/on-site read gerekliliği provenance/quality state'te korunur.
- UI aynı sample'da free+total destekliyorsa `Serbest klor` ve `Toplam klor` alanlarını ayrı gösterir; direct mono yöntemi seçildiyse `Monokloramin` ayrıca gösterilir. Derived combined value ölçülmüş monokloramin gibi sunulmaz.
- Unknown analyte/basis, unsupported matrix, mismatched sample context veya invalid derivation prerequisites fail-closed olur: `INSUFFICIENT_DATA` / method-semantic unavailable.

Bu karar numerical safety threshold seçmez; yalnız ölçümün ne olduğunu, nereden alındığını ve hangi derivation'ın bilimsel olarak hangi anlama geldiğini dondurur.

## K03.9 — kabul edilen marine kalsiyum / magnezyum elemental-basis politikası

26.09.2026 tarihinde profesyonel güvenlik standardı olarak kabul edildi:

- `CALCIUM_CONCENTRATION` canonical **mg/L as Ca2+**.
- `MAGNESIUM_CONCENTRATION` canonical **mg/L as Mg2+**.
- Generic `ppm` source label tek başına elemental basis değildir; verified product/method profile üreticinin sonucu elemental Ca veya Mg olarak tanımladığını kanıtlamalıdır.
- `CALCIUM_HARDNESS_AS_CACO3`, `MAGNESIUM_HARDNESS_AS_CACO3`, `GENERAL_HARDNESS` ve diğer CaCO3-equivalent hardness değerleri reef elemental Ca/Mg ile aynı semantic değildir.
- Hardness→elemental conversion yalnız method/basis/stoichiometry/sample-matrix/interference/precision politikası explicit ve versioned ise yapılır; generic GH elemental Ca/Mg'ye bölünmez.
- Total-hardness minus calcium-hardness ile elde edilen magnesium-hardness gibi difference methods derived provenance taşır; direct elemental Mg ölçümü gibi sunulmaz.
- Marine profiles method range, seawater applicability, temperature/dilution prerequisites ve known interferences taşır; freshwater hardness yöntemi reef Ca/Mg rule'una otomatik bağlanmaz.
- UI `Kalsiyum` ve `Magnezyum` değerlerini ayrı input/metric olarak gösterir; biri diğerinden, GH'den, alkalinity veya salinity'den tahmin edilmez.

Bu karar reef target range veya dosing recommendation seçmez; yalnız ölçümün elemental semantic'ini dondurur.

## K03.10 — kabul edilen conductivity / TDS ürün politikası

26.09.2026 tarihinde kullanıcı deneyimini sade tutan ve backend varsayımlarını sınırlayan model kabul edildi:

- `ELECTRICAL_CONDUCTIVITY` canonical unit **µS/cm**. mS/cm yalnız aynı physical quantity'nin scale conversion'ıdır ve güvenle µS/cm'e normalize edilebilir.
- Conductivity result'ında source temperature basis korunur: in-situ/raw, reference-temperature compensated (ör. 25 °C), veya verified device-ATC behavior. AquaLight mevcut ATC/reference düzeltmesini ikinci kez uygulamaz.
- `TDS_REPORTED_PPM` kullanıcının cihazında gördüğü TDS değeridir. UI sade biçimde `TDS [ppm]` gösterir; kullanıcı ppm'i aynen girer.
- TDS factor/scale (`0.5`, `0.7`, manufacturer-specific vb.) **TDS kaydı için kullanıcıdan istenmez**. Factor bilinmiyorsa bile valid cihaz okuması örneğin `180 ppm` olarak source-native saklanır.
- Verified cihaz/profile TDS'yi EC25'ten türetiyorsa kullanılan factor/scale ve reference-temperature behavior provenance'ta tutulabilir.
- AquaLight EC↔TDS conversion'ı yalnız factor/scale + temperature basis explicitly biliniyorsa yapar. Evrensel `0.5`, `0.64`, `0.7` varsayımı yapılmaz.
- Aynı meter probe reading'inden üretilen EC ve TDS iki bağımsız evidence sayılmaz; aynı source observation'a bağlanır.
- Existing catalog TDS range'lerinin scale/basis metadata'sı bilinmiyorsa arbitrary TDS meter ppm değeriyle hard safe/unsafe assessment yapılmaz; önce source/rule compatibility doğrulanır.
- TDS freshwater/shrimp/RO/remineralization/trend takibinde faydalı additional metric'tir; GH, KH/alkalinity, salinity, nitrate veya ion-specific measurement yerine geçmez.
- Marine/reef'te primary ionic-strength semantics K03.5 salinity/SG/conductivity modelidir; generic TDS ppm salinity yerine kullanılmaz.

Ürün ilkesi: **kullanıcı cihazdaki TDS ppm değerini aynen girer; factor ancak AquaLight cross-representation conversion yapacaksa backend için önemlidir.**

## K03.11 — sıradaki açık karar

CO2 için doğrudan ölçülen konsantrasyon ile pH/KH tablosu/hesabından türetilen tahmini değerin semantic ayrımı, gerekli prerequisites ve hard assessment'ta kullanılıp kullanılmayacağı kararlaştırılacak.

## Birincil kaynaklar

Tüm bağlantılar 26.09.2026 tarihinde açılıp ilgili bölümleri okundu. Üretici kaynakları ölçüm/raporlama semantiği için kullanıldı; tür sağlığı eşiklerine kanıt olarak kullanılmadı.

| Kimlik | Kaynak ve konum | Doğrulanan bilgi |
| --- | --- | --- |
| R1 | [Hach NT3100sc User Manual, DOC343.97.90749, 03/2026 Edition 6, English p.25, Conversions](https://cdn.hach.com/7FYZVWYB/at/czrwjk7gtxw7gpvgc4pkkb7/DOC3439790749_6ed.pdf) | mg/L NO3-N → mg/L NO3 için 4.43 çarpanı. |
| R2 | [Hanna Instruments UK — How to measure nitrite in marine and freshwater aquariums with Hanna Checkers, 29.01.2025](https://www.hannainstruments.co.uk/blog/post/81-how-to-measure-nitrite-in-marine-and-freshwater-aquariums-with-hanna-checkers) | NO2-N ve NO2 farklı raporlama temelleri; üretici 3.29 dönüşüm çarpanını belirtiyor. |
| R3 | [Hanna Instruments — How to convert Phosphate to Phosphorus?](https://knowledge.hannainst.com/en/knowledge/phosphate-to-phosphorus) | Sayfa başlığına rağmen içerikteki tarifin yönü ppb phosphorus → ppm phosphate; 3.066/1000. Yayın tarihi belirtilmiyor. |
| R4 | [Hach Parameter Knowledge Base — Phosphorus bölümü](https://sea.hach.com/parameters/faq) | Ortofosfat/reaktif fosfor, yoğunlaşmış fosfat ve toplam fosfor analitik kapsamları farklı. Yayın tarihi belirtilmiyor. |
| R5 | [USGS — Brackish Groundwater Assessment, 22.07.2021](https://www.usgs.gov/mission-areas/water-resources/science/brackish-groundwater-assessment) | mg/L–ppm sayısal eşdeğerliğinin yoğunluğa bağlı sınırlılığı. |

## K03.0 ölçüm kapsamı kararının dayanakları

26.09.2026 tarihinde kullanıcı mevcut UI tasarımı korunarak tank türüne göre temel/ilgili/ek ölçümlerin gösterilmesini kabul etti ve hangi türde hangi testin bulunacağının sözleşmeye yazılmasını istedi. **Yetkili eşleme ana sözleşme §25'tedir**; bu bölüm aynı matrisi ikinci kez tanımlamaz.

Kod doğrulaması (`cfbf1c2a8f9ca19784c4c0acf1385c7001b0763d`):

- `application/aquarium/AquariumTankTaxonomy.kt`: Fish, Shrimp, Planted, Marine, Softies, Mixed Reef, SPS, Coral ve Other olmak üzere 9 kanonik tür.
- `ui/tabs/aquarium/common/AquariumTankTaxonomyText.kt` ve `create/steps/TankInfoFragment.kt`: oluşturma ekranı aynı türleri seçip kanonik değer olarak saklıyor.
- `data/care/smartcare/SmartCareTankClassifier.kt`: ilk üçü tatlı su, beş deniz/mercan türü deniz suyu olarak gruplandırılıyor. Bu gruplar doğrulandı; eski sınıflandırıcının metin/anahtar kelime fallback'leri yeni analiz motoruna taşınmayacak.
- `app/src/main/res/values-tr/tank_strings_core.xml`: sözleşmedeki Türkçe tür adları mevcut picker etiketleridir.

Aşağıdaki kaynaklar önceki karar araştırmasında 26.09.2026 tarihinde açılıp okundu. Kaynaklar ölçümlerin kullanım gerekçesini destekler; dokuz uygulama türüne dağıtım ürün politikasıdır. Kaynaklardaki sayısal hedefler, dozlar veya test sıklıkları bu kararla kabul edilmedi.

| Kimlik | Kaynak | Kapsama katkısı / sınırı |
| --- | --- | --- |
| R6 | [OATA — How to test water quality in your freshwater tank](https://ornamentalfish.org/what-we-do/advice-information/care-sheets/caresheets-tropical-freshwater-fish/how-to-test-water-quality-in-your-freshwater-tank-aquarium/) | Tatlı suda amonyak, nitrit, nitrat, pH, sertlik ve fosfat takibi; oksijen ve şebeke suyu bağlamı. Her kayıtta tüm alanların zorunlu olduğu anlamına gelmez. |
| R7 | [UF/IFAS — Ammonia in Aquatic Systems](https://ask.ifas.ufl.edu/publication/FA031) | NH3/NH4 ayrımı; serbest amonyak payının pH, sıcaklık ve tuzlulukla ilişkisi. Sayfadaki genel TAN/ppm anlatımı tüm kitlerin raporlama temelinin aynı olduğunu kanıtlamaz. |
| R8 | [Seachem — MultiTest Ammonia](https://www.seachem.com/multitest-ammonia.php) | Aynı üretici toplam ve serbest amonyak ölçümlerini ayırır; yalnız etiket benzerliğine göre kayıt anlamı seçilemez. |
| R12 | [TEOS-10 — official overview](https://www.teos-10.org/) | Practical Salinity ile Absolute Salinity ayrı niceliklerdir; Practical Salinity conductivity tabanlıdır ve arşivlenen measured salinity olarak kalır, Absolute Salinity g/kg'dır. |
| R13 | [TEOS-10 GSW — Practical Salinity from conductivity](https://www.teos-10.org/pubs/gsw/html/gsw_SP_from_C.html) | PSS-78 Practical Salinity conductivity, in-situ temperature ve pressure girdilerinden hesaplanır; algoritma/applicability explicit olmalıdır. |
| R14 | [NOAA — salinity measurement methods](https://repository.library.noaa.gov/view/noaa/13165/noaa_13165_DS1.pdf) | Practical salinity conductivity ratioyla; SG hydrometerla ölçülebilir ve SG için temperature correction gerekir; refractive-index yönteminde de temperature correction gerekir. |
| R15 | [Red Sea — Seawater Refractometer](https://g1.redseafish.com/red-sea-salts/seawater-refractometer-salinity-test/) | Refractometer scale/calibration temperature ve seawater-vs-brine kalibrasyonu sonucu anlamlı etkiler; yanlış ölçek yaklaşık 1–1.5 ppt sapmaya yol açabilir. |
| R16 | [USGS Water-Supply Paper 2254 — alkalinity](https://pubs.usgs.gov/wsp/wsp2254/pdf/wsp2254a.pdf) | Total alkalinity farklı türlerin toplam acid-neutralizing capacity'sidir; yaygın raporlama mg/L as CaCO3 veya meq/L'dir ve `meq/L = mg/L as CaCO3 / 50` ilişkisi verilir. |
| R17 | [Hanna HI772 Marine Alkalinity manual](https://www.documentation.hannainst.com/manuals/preview/3589) | Marine alkalinity cihazı sonucu ppm olarak verir ve `1 dKH = 17.86 ppm CaCO3 = 0.358 meq/L` dönüşümünü açıkça tanımlar. |
| R18 | [Hach — Hardness vs Alkalinity](https://www.hach.com/parameters/hardness) | Hardness çok değerlikli metal iyonlarıyla, alkalinity acid-neutralizing capacity ile ilgilidir; carbonate hardness total hardness ve total alkalinity ilişkisiyle belirlenir, iki kavram aynı değildir. |
| R19 | [US EPA — Dissolved Oxygen](https://www.epa.gov/caddis/dissolved-oxygen) | DO konsantrasyonu mg/L veya percent saturation olarak raporlanabilir; bunlar ilişkili ama eşdeğer değildir. Saturation sıcaklık, basınç ve salinity'ye bağlıdır. |
| R20 | [USGS — DOTABLES](https://www.usgs.gov/tools/dotables) | DO solubility ve percent saturation hesabı water temperature, barometric pressure ve salinity/specific conductance girdilerini kullanır; algoritmanın geçerlilik aralıkları açıkça tanımlıdır. |
| R21 | [Hach — Dissolved Oxygen](https://www.hach.com/parameters/dissolved-oxygen) | Aynı %100 saturation farklı temperature/pressure/salinity koşullarında farklı mg/L DO değerlerine karşılık gelebilir; supersaturation mümkündür. |
| R22 | [Hach — Chlorine, Free and Total](https://cdn.hach.com/7FYZVWYB/at/hcxnhv6vk3bnzsj6crvbnxp/ex_chlorinefreetotal.pdf) | Free chlorine ve total chlorine ayrı DPD ölçümleridir; total chlorine free + combined available forms kapsamındadır ve sonuçlar mg/L Cl2 olarak raporlanır. |
| R23 | [Hach — Chlorination, Chloramination and Chlorine Measurement](https://cdn.hach.com/7FYZVWYB/at/2pt86h8m6tgxf5hspvssxr8/DOC1805320183.pdf) | Total residual minus free residual **monochloramine ile zorunlu olarak eşit değildir**; monochloramine konsantrasyonu ancak onu spesifik ölçen yöntemle bilinir. |
| R24 | [Hach Method 10171 — Monochloramine](https://cdn.hach.com/7FYZVWYB/at/46kjs8xchb5t2vbh66hjhn26/DR_2400_Procedures_Manual.pdf) | Monochloramine doğrudan, ayrı yöntemle ölçülebilir ve mg/L Cl2 olarak raporlanır. |
| R25 | [MSD Veterinary Manual — Environmental Diseases, chlorine/chloramine](https://www.msdvetmanual.com/exotic-and-laboratory-animals/aquatic-systems/environmental-diseases-of-aquatic-animals-in-aquatic-systems) | Free ve total chlorine birlikte test edilmelidir; chloramine kullanılan suda free chlorine negatifken total chlorine pozitif olabilir. Dechlorination öncesi/sonrası test bağlamı ve source-water/tank-water ayrımı önemlidir. |
| R26 | [Hanna Marine Calcium — HI758 / factory method](https://www.documentation.hannainst.com/manuals/download/6905) | Marine calcium sonucu doğrudan **mg/L of calcium (Ca2+)** olarak raporlanır; reef calcium semantic'i elemental Ca'dır. |
| R27 | [Hanna Marine Magnesium — HI783 / factory method](https://www.documentation.hannainst.com/manuals/download/6907) | Marine magnesium aralığı ve sonucu **mg/L as Mg2+** olarak tanımlanır; temperature ve method koşulları ölçüm doğruluğunu etkiler. |
| R28 | [Red Sea Foundation Pro Test Kits](https://g1.redseafish.com/wp-content/uploads/2020/11/24784-NEW-Manual-Foundation-Test-Kits_GB-DE-FR-NL-SP-_2018a-52p.pdf) | Reef programı calcium ve magnesium'u ayrı elemental testler olarak sunar; calcium resolution 5 ppm, magnesium resolution 20 ppm. |
| R29 | [Hach — Hardness (Total, Ca & Mg)](https://www.hach.com/parameters/hardness) | Hardness çoğunlukla Ca/Mg katkısını CaCO3-equivalent olarak raporlar; elemental Ca/Mg concentration ile hardness semantic'i aynı değildir. |
| R30 | [Hanna HI98194/HI98195 manual — TDS Factor](https://www.documentation.hannainst.com/manuals/download/174) | TDS, conductivity'den `TDS = factor × EC25` olarak hesaplanan değerdir; factor 0.00–1.00 ayarlanabilir, tipik güçlü iyonik çözelti 0.50 ve zayıf iyonik çözelti 0.70 örnekleri verilir. |
| R31 | [Hanna EC/TDS probe guide](https://pages.hannainst.com/hubfs/006-finished-content/EC-Guides/getting-to-know-your-probe-electrical-conductivity-ec-rev1.pdf) | TDS mg/L/ppm olarak gösterilir; bazı cihazlarda factor ayarlanabilir, bazılarında sabittir. Örnek: 100 µS/cm, factor 0.5 ile 50 ppm TDS. |
| R32 | [HM Digital COM-100](https://hmdigital.com/com-100/) | Aynı cihaz 0.5 (NaCl) ve 0.7 (442) TDS ppm scale'lerini destekler; bu, ekranda her ikisi de ppm olsa bile EC→TDS ölçeğinin cihaz/profile bağlı olduğunu doğrular. |
| R9 || R9 | [MSD Veterinary Manual — Equipment Needed for Aquatic Systems and Water Analysis](https://www.msdvetmanual.com/exotic-and-laboratory-animals/aquatic-systems/equipment-needed-for-aquatic-systems-and-water-analysis) | Oksijen, sıcaklık, pH, amonyak, nitrit, alkalinite, sertlik, deniz suyunda tuzluluk ve bağlama göre ek testler. Veteriner değerlendirme kapsamı UI'daki zorunlu alan listesi değildir. |
| R10 | [Red Sea — Foundation manual, “Optimal levels of the Foundation Elements”](https://redseafish.com/wp-content/uploads/2020/11/24653-NEW-Manual-Foundation-Complete-GB-_2018c.pdf) | Deniz/resif profillerinde tuzluluk, alkalinite, kalsiyum ve magnezyum ayrımı. Üretici hedefleri tüm akvaryumlar için evrensel güvenlik sınırı sayılmaz. |
| R11 | [MSD Veterinary Manual — Environmental Diseases, chlorine/chloramine section](https://www.msdvetmanual.com/exotic-and-laboratory-animals/aquatic-systems/environmental-diseases-of-aquatic-animals-in-aquatic-systems) | Serbest klor ve toplam klor ayrı ölçümlerdir; kloramin için yalnız serbest klor sonucunun yeterli olmaması. |

Ek ölçümlerde conductivity/TDS semantiği K03.10 ile kabul edildi. CO2, demir ve potasyum için kesin ölçüm yöntemi ve yorum kuralları henüz araştırılıp kabul edilmiş değildir. Destek kapsamına alınmaları otomatik eşik/doz önerisi veya hesaplanan değerin ölçülmüş gibi kaydı için izin vermez.
