# Akıllı aydınlatma hızlı kurulum sözleşmesi

## Kapsam ve otoriteler

Bu belge, `feature/smart-light-quick-setup` Android uygulamasının firmware
`455298833668537fedc16b851067558815d2cc7b` ile çalışan hızlı aydınlatma
otomasyonunu tanımlar.

- Android; tank verisini toplar, kullanıcıya doğrulatır ve deterministik bir
  öneri üretir.
- Firmware; planı doğrulayan, kalıcılaştıran ve çalıştıran tek otoritedir.
- Öneri, tıbbi/biyolojik kesinlik iddiası taşımaz. PAR ölçümü yoksa sonuç açıkça
  `ESTIMATED`, ölçüm girilmişse `CALIBRATED` olarak gösterilir.
- Firmware'in revision veya storage generation değeri değişirse Android körlemesine
  tekrar yazmaz; authoritative planı yeniden okur ve kullanıcıdan yeniden hesaplama
  ister.

## Girdi sahipliği ve mevcut veri yeterliliği

| Girdi | Android kaynağı | Başlangıç davranışı | Kullanıcı doğrulaması |
|---|---|---|---|
| Tank–cihaz ilişkisi | `TankDeviceAssignmentRepository` | Zorunlu; yoksa akış açılmaz | Hayır |
| Kurulum tarihi | `SavedAquariumTank.setupDateEpochDay` | Yaşı ve mevcut yaşam evresini belirler | Tank ekranında görünür; eksik/gelecek tarih uyarıdır |
| Tank yüksekliği | Tank store | Su derinliği için `yükseklik - 5 cm` başlangıcı | Evet, 10–100 cm |
| Bitki listesi | Tank store | İsim/kategori sinyallerinden düşük/orta/yüksek ihtiyaç tahmini | Evet |
| Bitki yoğunluğu | Bitki adedi | `0–2` seyrek, `3–7` orta, `8+` yoğun | Evet |
| CO₂ kurulumu | `SmartCareTankClassifier` | Mevcut tank sınıflandırmasıyla doldurulur | Evet |
| Aktif toprak | `SmartCareTankClassifier` | Mevcut malzeme sınıflandırmasıyla doldurulur | Evet |
| Ürün ve kanal şekli | Authoritative cihaz snapshot'ı | WRGB/RGB sahnesini seçer | Hayır |
| Lamba–su mesafesi | Tank verisinde yok | Devam etmek için zorunlu | Evet, 0–60 cm |
| Ortam ışığı | Tank verisinde yok | Güvenli varsayılan `LOW` | Evet |
| Işıkların kapanış saati | Tank verisinde yok | Yerel kullanıcı tercihi, başlangıç 22:00 | Evet, 20:00–23:30 |
| Substrat seviyesinde tam profil PAR | Sensör/veri yok | Opsiyonel muhafazakâr ürün tahmini | Opsiyonel, 20–500 µmol/m²/s |

Sonuç: Android'de tank yaşı, boyutları, bitkiler, CO₂, toprak, atanan cihaz ve
ürün bilgisi karşılanmaktadır. Bilimsel doğruluğu en çok artıracak eksikler lamba
yüksekliği, ortam ışığı ve substrat seviyesindeki PAR'dır; ilk ikisi akışta alınır,
PAR ise ölçüm yoksa dürüstçe tahmin olarak işaretlenir.

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
3. Ortam ışığı: az `0`, orta `-4`, fazla `-8` puan.
4. Optik mesafe düzeltmesi:
   `(su derinliği + lamba yüksekliği - 45) / 4` puan.
5. Aktif toprak bulunan tankta başlangıç riski için `-3` puan.
6. CO₂ hazır değilse sonuç en fazla `%55`.
7. Nihai olgun değer `%25–85` aralığına sıkıştırılır ve yaşam evresi çarpanı
   uygulanır.

Yüksek ışık isteyen bitki + CO₂ yok kombinasyonu ayrıca kullanıcıya uyarı verir.
Bu sınırlar güvenli mühendislik guardrail'leridir; tür bazlı fotosentez doygunluk
noktası iddiası değildir.

### PPFD, DLI ve spektrum

Ölçülmüş tam-profil PPFD varsa doğrudan kullanılır. Yoksa yalnız öneri üretimini
mümkün kılmak için 45 cm optik mesafede WRGB için `105`, RGB için `78 µmol/m²/s`
referansı ve `exp(-0.018 × (mesafe - 45))` sönüm tahmini kullanılır; tahmin
`35–180 µmol/m²/s` ile sınırlıdır. Bu katsayılar cihaz başına kalibre edilmiş PAR
haritasının yerini tutmaz.

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
   Plan yeniden okunur, oluşturulan öneri iptal edilir ve kullanıcı tercihler
   adımına döner.
5. Eski/uyumsuz firmware fail-closed davranır ve yükseltme mesajı gösterir.

Plan; 10957–47481 epoch-day aralığı, 0–90 geçiş günü, 20–100 arası beşlik başlangıç
yüzdesi, aynı-gün zaman aralığı, bitişik fazlar ve en fazla sekiz faz kurallarının
hem Android hem firmware tarafında strict doğrulamasından geçer.

## UI akışı

1. **Tank verileri:** atanan tank, gün yaşı, bitki ihtiyacı/yoğunluğu, CO₂, aktif
   toprak, su derinliği ve zorunlu lamba yüksekliği.
2. **Tercihler:** kapanış saati, ortam ışığı, CO₂/toprak doğrulaması ve opsiyonel
   PAR ölçümü.
3. **Önerilen plan:** bugünkü zaman çizelgesi, tüm yaşam fazları, PPFD, DLI,
   tahmin/kalibrasyon güveni, gerekçeler, uyarılar ve cihaza uygulama.

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

- Deterministik hesaplayıcı: yeni/olgun tank, CO₂ güvenlik tavanı, ölçülmüş PAR,
  eksik/gelecek tarih ve WRGB/RGB kanal şekli birim testleri.
- ViewModel: yükleme, hesaplama, authoritative apply ve stale-authority uzlaşması.
- Runtime: exact serializer alanları, strict parser, hata alanları ve golden fixture
  byte/hash pinleri.
- Repo kapıları: tam JVM test paketi, detekt (sıfır yeni borç), Android lint,
  protokol/firmware guard'ları ve debug APK.
- Yayın öncesinde gerçek WRGB ve RGB cihazında saat dilimi/gece yarısı, bağlantı
  kesilmesi, eşzamanlı plan değişimi, yeniden başlatma ve tüm 85 günlük faz
  sınırlarını hızlandırılmış saatle kapsayan fiziksel smoke test zorunludur.
