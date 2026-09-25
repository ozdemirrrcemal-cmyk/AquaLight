# K03 — Konsantrasyon birimleri ve kimyasal raporlama temeli

Araştırma tarihi: 26.09.2026 (Europe/Istanbul).

Durum: **K03.0 ölçüm kapsamı, K03.1 NO3/NO2/PO4 kanonik kayıt anlamı/birimleri, K03.2 test/cihaz seçimi + kaynak semantiği çözümleme + normalizasyon akışı ve K03.3 amonyak kanonik temelleri kabul edildi. K03.4 ve sonraki kararlar açık.** Kabul edilen normatif kapsam ana sözleşme §6.1–6.5, §7, §25.1–25.3 ve §28.1'de kayıtlıdır. Bu araştırma dosyası uygulama kodu veya bilimsel güvenlik eşiği değildir.

Kapsam: K03.0 için ölçüm seçiminin kaynaklarını, K03.1'de kabul edilen NO3/NO2/PO4 ortak raporlama temelini, K03.2'de kabul edilen source-aware giriş/normalizasyon yaklaşımını ve K03.3'te kabul edilen amonyak kanonik temellerini izlenebilir tutmak. Aynı ölçüm olayında çok-sonuçlu amonyak testlerinin kayıt/UI davranışı, ek parametrelerin semantiği, kanıtlı profil/dönüşüm tablolarının ayrıntıları ve türetilmiş hesaplar sonraki ayrı kararlar olacak.

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
4. Aynı ürün birden fazla semantik sonuç veriyorsa yalnız gerekli açık seçim gösterilir. Örneğin toplam amonyak ile doğrudan serbest NH3 birbirinden ayrı result mode'dur; uygulama hangisinin ölçüldüğünü sayıya veya önceki tercihe bakarak tahmin etmez.
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

## K03.4 — sıradaki açık karar

Aynı çok-sonuçlu test/kit aynı ölçüm olayında hem toplam amonyak hem doğrudan serbest NH3 sonucu veriyorsa, iki sonucun **aynı WaterAnalysisRecord içinde birlikte saklanıp saklanmayacağı**, giriş UI'ında bunun nasıl açılacağı ve history/detail'de iki ayrı measured metric olarak nasıl gösterileceği kararlaştırılacak. K03.3 iki metriğin anlamını dondurur ancak aynı olayda cardinality/UI davranışını seçmez.

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
| R9 | [MSD Veterinary Manual — Equipment Needed for Aquatic Systems and Water Analysis](https://www.msdvetmanual.com/exotic-and-laboratory-animals/aquatic-systems/equipment-needed-for-aquatic-systems-and-water-analysis) | Oksijen, sıcaklık, pH, amonyak, nitrit, alkalinite, sertlik, deniz suyunda tuzluluk ve bağlama göre ek testler. Veteriner değerlendirme kapsamı UI'daki zorunlu alan listesi değildir. |
| R10 | [Red Sea — Foundation manual, “Optimal levels of the Foundation Elements”](https://redseafish.com/wp-content/uploads/2020/11/24653-NEW-Manual-Foundation-Complete-GB-_2018c.pdf) | Deniz/resif profillerinde tuzluluk, alkalinite, kalsiyum ve magnezyum ayrımı. Üretici hedefleri tüm akvaryumlar için evrensel güvenlik sınırı sayılmaz. |
| R11 | [MSD Veterinary Manual — Environmental Diseases, chlorine/chloramine section](https://www.msdvetmanual.com/exotic-and-laboratory-animals/aquatic-systems/environmental-diseases-of-aquatic-animals-in-aquatic-systems) | Serbest klor ve toplam klor ayrı ölçümlerdir; kloramin için yalnız serbest klor sonucunun yeterli olmaması. |

Ek ölçümlerde CO2, iletkenlik/TDS, demir ve potasyum için kesin ölçüm yöntemi ve yorum kuralları henüz araştırılıp kabul edilmiş değildir. Destek kapsamına alınmaları otomatik eşik/doz önerisi veya hesaplanan değerin ölçülmüş gibi kaydı için izin vermez.
