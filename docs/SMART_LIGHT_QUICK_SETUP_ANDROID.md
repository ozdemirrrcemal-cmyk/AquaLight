# Akıllı aydınlatma hızlı kurulum sözleşmesi

## Kapsam ve otoriteler

Bu belge, `feature/smart-light-quick-setup` Android uygulamasının firmware
`455298833668537fedc16b851067558815d2cc7b` ile çalışan hızlı aydınlatma
otomasyonunu tanımlar.

- Android; kayıtlı tank ve atanmış cihaz verilerini, profilde bulunmayan iki gözlem
  sinyaliyle (gün ışığı ve yosun durumu) birleştirerek deterministik öneri üretir.
- Firmware; planı doğrulayan, kalıcılaştıran ve çalıştıran tek otoritedir.
- Öneri, tıbbi/biyolojik kesinlik iddiası taşımaz. Sensör tabanlı PAR ölçümü
  bulunmadığı için PPFD/DLI ölçülmüş değer gibi kullanıcıya gösterilmez.
- Firmware'in revision veya storage generation değeri değişirse Android körlemesine
  tekrar yazmaz; authoritative planı yeniden okur ve tank verisinden yeni bir plan
  hesaplar. Yeniden uygulama yine açık kullanıcı eylemi gerektirir.

## Girdi sahipliği ve mevcut veri yeterliliği

| Girdi | Android kaynağı | Otomasyon davranışı | Kurulum ekranında düzenlenir mi? |
|---|---|---|---|
| Tank–cihaz ilişkisi | `TankDeviceAssignmentRepository` | Zorunlu; yoksa akış açılmaz | Hayır |
| Kurulum tarihi | `SavedAquariumTank.setupDateEpochDay` | Yaşı ve yaşam evresini belirler; eksik/gelecek tarih temkinli yeni tank davranışıdır | Hayır |
| Akvaryum yüksekliği | `SavedAquariumTank.heightCm` | Değer doğrudan optik modelde kullanılır; yapay `-5 cm` düzeltmesi yoktur | Hayır |
| Bitki listesi | Tank store | İsim/kategori sinyallerinden az/orta/yüksek ihtiyaç tahmini | Hayır |
| Bitki yoğunluğu | Bitki adedi | `0–2` seyrek, `3–7` orta, `8+` yoğun | Hayır |
| CO₂ kurulumu | `SmartCareTankClassifier` | Kayıtlı CO₂ kategorisi/sinyali otomatik kullanılır | Hayır |
| Aktif toprak | Malzeme `categoryKey=substrate` | Taban kategorisindeki her ürün aktif toprak kabul edilir; ürün adında `soil` aranmaz | Hayır |
| Ürün ve kanal şekli | Authoritative cihaz snapshot'ı | WRGB/RGB sahnesini seçer | Hayır |
| Standart montaj mesafesi | Ürün politikası | WRGB için 10 cm, RGB için 8 cm güvenli varsayım | Hayır |
| Program penceresi | Ürün politikası | Mevcut evrenin süresi 22:00'de bitecek şekilde otomatik yerleştirilir | Hayır |
| Gün ışığı etkisi | Kullanıcı gözlemi | Az / dolaylı / direkt; yapay çıkışı temkinli azaltır | Evet |
| Yosun durumu | Kullanıcı gözlemi | Yok / hafif / belirgin; artışı sürdürür, bekletir veya daha güçlü sınırlar | Evet |
| Substrat seviyesinde tam profil PAR | Sensör/veri yok | Muhafazakâr iç model; ölçülmüş PPFD/DLI olarak sunulmaz | Hayır |

Sonuç: Bu ekran bir plan editörü değildir. Tank profili salt-okunur özetlenir;
kullanıcı yalnız uygulamanın bilemeyeceği iki güncel koşulu seçer. Montaj mesafesi
ürün politikasıyla, ortam etkisi kullanıcı gözlemiyle ele alınır. Program cihazda
zaten kuruluysa ekran tekrar oluşturma eylemi sunmaz; aktif planı gösterir ve ancak
"Koşulları güncelle" eylemiyle yeni değerlendirme başlatır.

## Karar modeli

### Fotoperiyot ve yaşam evreleri

| Tank günü | Süre | Olgun yoğunluğa göre çarpan |
|---:|---:|---:|
| 1–21 | 6 saat | %75 |
| 22–42 | 6,5 saat | %85 |
| 43–63 | 7 saat | %92 |
| 64–84 | 7,5 saat | %96 |
| 85+ | 8 saat | %100 |

Her faz her gün çalışır ve 60 dakika gün doğumu + 60 dakika gün batımı kullanır.
Tank daha yaşlıysa geçmiş fazlar gönderilmez; ilk gönderilen faz bugünden başlar,
sonraki sınırlar tank kurulum tarihine bağlı kalır. Fazlar aynı gün içinde,
bitişik ve son faz açık uçlu olacak şekilde firmware'in en fazla sekiz fazlık
sözleşmesine uyar.

Bu süreler evrensel bitki sabiti değildir. Yeni akvaryumda ilk 2–3 hafta 6 saatle
başlayıp sonra artırma yaklaşımını kullanan işletim rehberi ile, su altında ışık ve
inorganik karbonun birlikte sınırlayıcı olabildiğini gösteren literatürden türetilmiş
muhafazakâr bir ürün politikasıdır.

### Olgun yoğunluk yüzdesi

1. Bitki ihtiyacı tabanı: düşük `%42`, orta `%56`, yüksek `%72`.
2. Bitki yoğunluğu: seyrek `-5`, orta `0`, yoğun `+5` puan.
3. Optik mesafe düzeltmesi:
   `(akvaryum yüksekliği + ürünün standart montaj mesafesi - 45) / 4` puan.
4. Aktif toprak bulunan tankta başlangıç riski için `-3` puan.
5. Gün ışığı: az `0`, dolaylı `-6`, direkt `-15` puan; direkt ışıkta üst sınır `%50`.
6. Yosun: yok `0`, hafif `-14`, belirgin `-24` puan; belirgin yosunda üst sınır `%45`.
7. Kayıtlı CO₂ sistemi yoksa sonuç en fazla `%55`.
8. Nihai olgun değer `%25–85` aralığına sıkıştırılır ve yaşam evresi çarpanı
   uygulanır.

Hafif veya belirgin yosun seçildiğinde Android gelecekteki otomatik artış fazlarını
önceden kurmaz; mevcut güvenli evre açık uçlu tutulur ve yedi gün sonra yeniden
değerlendirme istenir. Yosun yoksa yaşam evresi fazları ve 14 günlük kontrol ritmi
devam eder.

Yüksek ışık isteyen bitki + CO₂ yok kombinasyonu ayrıca kullanıcıya uyarı verir.
Bu sınırlar güvenli mühendislik guardrail'leridir; tür bazlı fotosentez doygunluk
noktası iddiası değildir.

### PPFD, DLI ve spektrum

Yalnız öneri üretimini mümkün kılmak için 45 cm optik mesafede WRGB için `105`,
RGB için `78 µmol/m²/s` referansı ve
`exp(-0.018 × (akvaryum yüksekliği + standart montaj mesafesi - 45))` sönüm
tahmini kullanılır; tahmin `35–180 µmol/m²/s` ile sınırlıdır. Bu katsayılar cihaz
başına kalibre edilmiş PAR haritasının yerini tutmaz.

İki doğrusal rampanın toplamı bir saatlik tam güç eşdeğeridir. Gösterilen DLI:

`DLI = hedef PPFD × (fotoperiyot dakikası - 60) × 60 / 1.000.000`

WRGB sahne oranı `R 0,86 / G 0,68 / B 0,74 / W 1,00`; RGB oranı
`R 1,00 / G 0,72 / B 0,82` olup her kanal hesaplanan yoğunluğa göre ölçeklenir.
Oranlar dengeli görsel/işletim profili varsayımıdır; tür bazlı aksiyon spektrumu
ölçümü değildir.

Yeni tankta firmware geçiş başlangıcı aktif toprakta `%60`, diğer tankta `%70`;
21 günden yaşlı tankta `%85` olur. Her yeni faz için `transitionDays = 7` kullanılır.
Bu firmware geçişi, günlük 60 dakikalık gün doğumu/gün batımı rampasından farklıdır.

## Firmware yazma protokolü

Android aşağıdaki exact Light V1 işlemlerini kullanır:

1. `light.auto.plan.get` ile `revision`, `storageGeneration`, plan ve runtime okunur.
2. `light.auto.plan.apply` çağrısına her iki beklenen otorite değeri, nullable
   `planId`, başlangıç yüzdesi ve fazlar gönderilir.
3. Başarı yanıtı authoritative snapshot olarak saklanır; cihaz AUTO moduna geçer.
4. `STALE_REVISION` veya `STALE_STORAGE_GENERATION` durumunda otomatik retry yoktur.
   Plan yeniden okunur ve güncel tank profiliyle öneri yeniden hesaplanır; otomatik
   ikinci yazma yapılmaz.
5. Eski/uyumsuz firmware fail-closed davranır ve yükseltme mesajı gösterir.

Plan; 10957–47481 epoch-day aralığı, 0–90 geçiş günü, 20–100 arası beşlik başlangıç
yüzdesi, aynı-gün zaman aralığı, bitişik fazlar ve en fazla sekiz faz kurallarının
hem Android hem firmware tarafında strict doğrulamasından geçer.

## UI akışı

Tek ekranlı yapı iki açık duruma sahiptir:

1. **İlk oluşturma / düzenleme:** kayıtlı profil kompakt gösterilir; yalnız gün ışığı
   ve yosun durumu sorulur. İki seçim tamamlanınca 24 saatlik WRGB/RGB eğrisi,
   süre, geçiş ve tepe çıkışı önizlenir. `Programı oluştur` yalnız bu durumda vardır.
2. **Program aktif:** firmware snapshot'ındaki bugünkü eğri, çalışma durumu, son ve
   sonraki değerlendirme ile karar gerekçeleri gösterilir. Oluşturma butonu yoktur;
   kullanıcı isterse içerideki `Koşulları güncelle` eylemiyle düzenleme durumuna geçer.
3. **Başarılı apply:** ekran kapanmaz; aynı state anında aktif programa dönüşür ve
   tekrar yazmayı sağlayan CTA kaybolur.
4. **Hesap ayrıntıları:** model varsayımı ve faz süreleri açılır; kalibrasyon yoksa
   PPFD/DLI sayıları gösterilmez ve hiçbir firmware alanı elle düzenlenmez.

Ekran mevcut AquaLight Compose kartları, renk token'ları, tipografi ve merkezî
fragment header/navigation yapısını kullanır. Sunum katmanı runtime/data tiplerine
doğrudan bağımlı değildir; application boundary ve owner-scope dependency graph
korunur.

## Bilimsel ve işletim dayanakları

- Pedersen, Colmer ve Sand-Jensen, su altı fotosentezinde ışık ile inorganik karbon
  erişiminin temel sınırlayıcılar olduğunu ve gaz difüzyonunun suda çok daha yavaş
  olduğunu özetler: [Underwater Photosynthesis of Submerged Plants](https://pmc.ncbi.nlm.nih.gov/articles/PMC3659369/).
- Guo ve ark. ışık aklimasyonu ile CO₂ yanıtının su altı fotosentezinde birlikte
  değerlendirilmesi gerektiğini gösterir: [Frontiers in Plant Science, 2024](https://www.frontiersin.org/journals/plant-science/articles/10.3389/fpls.2024.1355729/full).
- DLI, PPFD'nin gün boyunca integrali olarak kullanılır; fotoperiyot ve yoğunluğu
  tek günlük dozda birleştirir: [Faust ve Logan, HortScience 2018](https://journals.ashs.org/view/journals/hortsci/53/9/article-p1250.xml).
- 6 saatlik yeni tank başlangıcı ve kademeli artış, bilimsel sabit olarak değil,
  yaygın akvaryum işletim pratiği olarak alınmıştır:
  [Tropica başlangıç rehberi](https://tropica.com/en/guide/get-the-right-start/growing-in/).

## Yayın doğrulaması

- Deterministik hesaplayıcı: yeni/olgun tank, CO₂ güvenlik tavanı, doğrudan
  akvaryum yüksekliği, eksik/gelecek tarih ve WRGB/RGB kanal şekli birim testleri.
- ViewModel: iki koşul tamamlanmadan hesaplamama, başarılı apply sonrası aktif moda
  geçiş, kurulu planda oluşturma eylemini kapatma ve stale-authority sonrası refetch
  + bilinçli yeniden uygulama.
- Tank sınıflandırması: yalnız `substrate` kategori anahtarının aktif toprak
  otoritesi olduğunu doğrulayan test.
- Runtime: exact serializer alanları, strict parser, hata alanları ve golden fixture
  byte/hash pinleri.
- Repo kapıları: tam JVM test paketi, detekt (sıfır yeni borç), Android lint,
  protokol/firmware guard'ları ve debug APK.
- Yayın öncesinde gerçek WRGB ve RGB cihazında saat dilimi/gece yarısı, bağlantı
  kesilmesi, eşzamanlı plan değişimi, yeniden başlatma ve tüm 85 günlük faz
  sınırlarını hızlandırılmış saatle kapsayan fiziksel smoke test zorunludur.
