# K03 — Konsantrasyon birimleri ve kimyasal raporlama temeli

Araştırma tarihi: 26.09.2026 (Europe/Istanbul).

Durum: **Araştırma notu ve karar önerisi; K03 henüz kullanıcı tarafından kabul edilmedi.** Bu dosya uygulama kodu, bilimsel güvenlik eşiği veya normatif sözleşme kararı değildir.

Kapsam: Önce K03.1 kapsamında NO3, NO2 ve PO4 alanlarının motor/kayıt içindeki ortak raporlama temelini seçmek. Kaynak birimlerinin dönüştürülmesi, test kiti giriş yöntemi ve amonyak semantiği sonraki ayrı kararlar olacak.

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
- Mevcut UI NO3, NO2 ve PO4 değerlerini mg/L ile sunuyor. K02, mevcut ölçüm modellerinin genişletilmesini kabul etti; K03 için herhangi bir kimyasal anlam henüz dondurulmadı.

## K03.1 için önerilen karar — onay bekliyor

| UI alanı | Önerilen kanonik kayıt/motor temeli |
| --- | --- |
| NO3 | Nitrat, mg/L olarak NO3 |
| NO2 | Nitrit, mg/L olarak NO2 |
| PO4 | Ortofosfat/reaktif fosfat sonucu, mg/L olarak PO4 |

Buradaki “PO4 olarak” ifadesi raporlanan kütle temelidir; suda bütün fosfatın yalnız tek bir iyonlaşma halinde bulunduğu iddiası değildir.

Gerekçe: Mevcut ekran adlarıyla doğrudan eşleşen, tek anlamlı bir iç temsil sağlar. N veya P temelinde gelen sonuçlar doğru kaynak bilgisiyle daha sonra normalize edilebilir. Alternatif, NO3/NO2'yi N ve fosfatı P temelinde saklamaktır; o yaklaşım da geçerlidir fakat mevcut UI ile sürekli açık dönüşüm gerektirir. Kullanıcı hangi yaklaşımın seçileceğine henüz karar vermedi.

## K03.1'in tek başına kapatmadığı konular

- Kullanıcının NO3-N, NO2-N, PO4-P, ppm veya ppb çıktılı kitleri nasıl seçeceği/gireceği ve desteklenecek kaynak formatları.
- Kaynakta ppm'in mg/L olarak mı, kütle oranı olarak mı kullanıldığının doğrulanması; tatlı/deniz suyu dönüşüm politikası.
- Katalogdaki her karşılaştırma aralığının raporlama temeli ve bilimsel kaynağı; belirsiz kaynak için değerlendirilemez durumunun uygulanması.
- Dönüşüm katsayılarının hassasiyeti, kaynak sürümü, raw değer ve source-unit provenance saklama şekli.
- NH3, NH4, total ammonia ve TAN seçimi; buna bağlı pH/sıcaklık/tuzluluk önkoşulları.
- Güvenli/tehlikeli eşikler ve türetilmiş kimyasal hesaplamalar.

K03.1 kabul edilse bile bu kalan konular onaylanmış veya uygulanmış sayılmayacak. Belirsiz kaynak birimleri sessizce kanonik değere çevrilmeyecek.

## Birincil kaynaklar

Tüm bağlantılar 26.09.2026 tarihinde açılıp ilgili bölümleri okundu. Üretici kaynakları ölçüm/raporlama semantiği için kullanıldı; tür sağlığı eşiklerine kanıt olarak kullanılmadı.

| Kimlik | Kaynak ve konum | Doğrulanan bilgi |
| --- | --- | --- |
| R1 | [Hach NT3100sc User Manual, DOC343.97.90749, 03/2026 Edition 6, English p.25, Conversions](https://cdn.hach.com/7FYZVWYB/at/czrwjk7gtxw7gpvgc4pkkb7/DOC3439790749_6ed.pdf) | mg/L NO3-N → mg/L NO3 için 4.43 çarpanı. |
| R2 | [Hanna Instruments UK — How to measure nitrite in marine and freshwater aquariums with Hanna Checkers, 29.01.2025](https://www.hannainstruments.co.uk/blog/post/81-how-to-measure-nitrite-in-marine-and-freshwater-aquariums-with-hanna-checkers) | NO2-N ve NO2 farklı raporlama temelleri; üretici 3.29 dönüşüm çarpanını belirtiyor. |
| R3 | [Hanna Instruments — How to convert Phosphate to Phosphorus?](https://knowledge.hannainst.com/en/knowledge/phosphate-to-phosphorus) | Sayfa başlığına rağmen içerikteki tarifin yönü ppb phosphorus → ppm phosphate; 3.066/1000. Yayın tarihi belirtilmiyor. |
| R4 | [Hach Parameter Knowledge Base — Phosphorus bölümü](https://sea.hach.com/parameters/faq) | Ortofosfat/reaktif fosfor, yoğunlaşmış fosfat ve toplam fosfor analitik kapsamları farklı. Yayın tarihi belirtilmiyor. |
| R5 | [USGS — Brackish Groundwater Assessment, 22.07.2021](https://www.usgs.gov/mission-areas/water-resources/science/brackish-groundwater-assessment) | mg/L–ppm sayısal eşdeğerliğinin yoğunluğa bağlı sınırlılığı. |
