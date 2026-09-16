# Akıllı aydınlatma ticari V1 sözleşmesi

## Değişmez kapsam

Bu belge Android `feature/smart-light-quick-setup` ile firmware
`feature/smart-light-automation-plan` dallarının ticari akıllı aydınlatma akışını
tanımlar. Pinli firmware commit'i
`455298833668537fedc16b851067558815d2cc7b` değeridir.

- Tek sözleşme `aqualight.light.v1`, tek storage/schema sürümü `1`'dir. V2 ve
  migration/compatibility yolu yoktur.
- Android öneriyi üretir ve kullanıcı onayından önce kesin girdileri kalıcı denetim
  kaydına yazar. Firmware planı strict doğrular, atomik kaydeder ve çalıştırır.
- Telefon tarihi plan otoritesi değildir. Her hesaplamada yeni `light.status.get`
  alınır; `scheduler.ready=true` ve geçerli firmware yerel tarihi yoksa işlem
  fail-closed kapanır.
- Yüzdeler cihaz kanal komutudur. Exact ürün/hardware/fixture kalibrasyonu yokken
  PPFD veya DLI ölçümü/tahmini gösterilmez.
- Taban türü hiçbir ekranda kullanıcıya sorulmaz. Kullanıcı yalnız ürün adını seçer;
  semantik sınıf sürümlü katalog kaydından gelir.

## Veri sahipliği ve soru politikası

| Veri | Tek doğru kaynak | Kullanıcıya sorulma koşulu | Eksik/şüpheli davranış |
|---|---|---|---|
| Tank tipi, cam ölçüleri, kurulum tarihi | Mevcut tank kaydı | Tekrar sorulmaz | Tarih eksik/gelecekteyse 6 saatlik başlangıçta kalır |
| Gerçek su derinliği | Tank otomasyon profili | İlk hassas ışık kurulumunda bir kez; düzenlemede değiştirilebilir | Plan tamamlanmaz |
| Lamba–su mesafesi | Tank–cihaz montaj profili | İlk atamada; düzenlemede montaj değişikliği girilebilir | Plan tamamlanmaz; değişiklik 6 saate resetler |
| Product key, hardware revision | Doğrulanmış cihaz kimliği | Sorulmaz | Akış açılmaz |
| Fixture uzunluğu ve cihaz yerel tarihi | Güncel firmware status | Sorulmaz | Akış açılmaz |
| Kalibrasyon kimliği/revizyonu | AquaLight kalibrasyon kataloğu | Sorulmaz | Kalibre yoğunluk ve PPFD/DLI kapalı; kanal tavanı en fazla `%50` |
| Bitki türü/ışık talebi | Stabil bitki katalog kimliği | Yalnız özel/bilinmeyen bitkide düşük–orta–yüksek | Bilinmeyen talep sessizce tahmin edilmez; cevap exact katalog kayıtlarının kanıtlı en yüksek talebini düşüremez |
| Bitki kaplaması | Tank otomasyon profili | Bir kez seyrek–orta–yoğun | Bitki adedi yoğunluk yerine kullanılmaz |
| CO₂ ekipmanı varlığı | Mevcut tank malzemeleri | Tekrar sorulmaz | Varlık, aktif/hazır kabul edilmez |
| CO₂ çalışma durumu | Zaman damgalı tank otomasyon profili | CO₂ ekipmanı varsa ilk kurulumda ve her yeniden değerlendirmede tek soru: “Işık açıldığında CO₂ hazır ve stabil mi?” | Hazır değilse süre artışı tutulur ve çıkış sınırlandırılır |
| Taban semantiği | Exact ürün ID'li taban kataloğu | Asla sorulmaz | Bilinmeyen ürün aktif toprak sayılmaz |
| Gün ışığı sınıfı/penceresi | Tank çevre profili | İlk kurulumda; direkt ise başlangıç/bitiş de istenir; yeniden değerlendirmede mevcut değer düzenlenebilir | Direkt ışıkta korumalı çıkış ve artış hold |
| Güncel yosun/biofilm | Zaman damgalı gözlem | Her yeni plan değerlendirmesinde tek kısa soru | Sabit/artan yosunda artış yok; hedef karides biofilmi yosun sayılmaz |
| Karides ve saklanma | Mevcut canlı kaydı + profil | Yalnız karides varsa tek soru | Yetersizse çıkış sınırlandırılır |
| Kapanış saati | Profil veya mevcut firmware planı | İlk kez eksikse | `22:00` sessizce atanmaz; aynı günü aşan plan reddedilir |
| Son büyük dikim/taban/lamba değişimi | Tank olayı + montaj profili | Akışta tekrar sorulmaz | Bitki veya taban kompozisyonu değişince profil geçersizleşir; ilk yeni plan cihaz tarihiyle 21 günlük yaşam döngüsünü başlatır, taban değişiminde su derinliği yeniden ölçülür |
| Input snapshot, politika, kaynaklar, sonuç | Öneri sistemi | Sorulmaz | Denetim kaydı oluşmadan firmware'e yazılmaz |

### Taban kataloğu

Ticari V1 katalog girdileri exact ürün ID'siyle şu semantiği taşır:

- `ACTIVE_SOIL`: Chihiros Aqua Soil 3 L ve 9 L.
- `NUTRIENT_BASE`: Dennerle Deponit Mix Professional 9in1.
- `ADDITIVE`: ADA Tourmaline BC.
- `INERT`: ADA Aqua Gravel S/M, Dennerle Nano Shrimp Gravel ürünleri, JBL Sansibar
  Dark/White ve Aquael Basalt Gravel.

Kategori adı, ürün adındaki “soil” kelimesi veya serbest metin teknik sınıf
otoritesi değildir. Özel ürün `UNKNOWN` kalır; kullanıcıdan “aktif/inert” seçmesi
istenmez ve ürün sessizce aktif toprağa çevrilmez. Üreticisi ve sabit bileşimi
olmayan genel “Natural River Sand” girdisi bu nedenle ticari katalogdan çıkarılmıştır;
kullanıcı kendi ürün adını özel ürün olarak kaydedebilir.

## Ticari karar politikası

### Süre

Tropica'nın yeni tank rehberindeki ilk 2–3 hafta 6 saat ve sonrasında kademeli
artış yaklaşımı şu kontrollü politika olarak uygulanır:

1. Yeni kurulum veya son büyük dikim/montaj değişiminden sonraki ilk 21 gün: `6 saat`.
2. En az 21 gün geçmiş, son uygulamadan en az 14 gün geçmiş ve koruma sinyali yok:
   kullanıcı değerlendirmesiyle `7 saat`.
3. Bir başka en az 14 günlük stabil dönem ve yeni değerlendirme sonrasında: `8 saat`.
4. `8 saat` ticari tabandır; otomatik olarak 8 saatin üstüne çıkılmaz.

Her onayda firmware'e yalnız bugünden başlayan tek açık uçlu faz gönderilir.
Gelecekteki 7/8 saatlik artışlar önceden programlanmaz. `transitionDays=0`, her gün
maskesi `127`, günlük sunrise/sunset rampası `60 dakika` ve managed-plan
`initialStartPercent=100`'dür. Kullanıcı gözlemi olmadan süre veya kanal çıkışı
artmaz.

Bitki/taban kompozisyonu geçersizleştiğinde veya son uygulanmış kayda göre product
key, hardware revision ya da fixture uzunluğu değiştiğinde başlangıç günü telefon
saatinden alınmaz. Güncel firmware scheduler tarihi montaj/yaşam döngüsü olayına
kalıcı olarak yazılır ve yeni 21 günlük pencere buradan başlar.

### Kanal çıkışı

- Kalibrasyonsuz yeni kurulumun ilk 21 gününde en yüksek kanal en fazla `%30`,
  sonraki değerlendirmelerde en fazla `%50`'dir. `%30`, Chihiros'un yeni dikim
  rehberinden alınmış çapraz-ürün temkin sınırıdır; AquaLight PAR kalibrasyonu
  veya biyolojik optimum olarak sunulmaz.
- CO₂ ışık açılışında hazır değilse veya dikim seyrekse tavan en fazla `%40`.
- Direkt gün ışığı veya stabil yosunda en fazla `%35`; artan yosun ya da karideste
  saklanma yoksa en fazla `%30`.
- Optik mesafe 25 cm veya altındaysa en fazla `%35`.
- Bir kez uygulanmış doğru kanal tavanı, yeni bir ölçüm/kalibrasyon olmadan yukarı
  çekilmez. Güvenlik koşulu kötüleşirse aşağı çekilebilir.
- WRGB ve RGB kanal şekli ürünün exact firmware sahne alanlarıyla üretilir. Genel
  ürün yüzdesi, watt veya lümen PPFD'ye çevrilmez.

Bu yüzdeler biyolojik optimum iddiası değil, ürün kalibrasyonu tamamlanana kadar
fail-safe mühendislik sınırıdır. Exact hardware revision + fixture length için
laboratuvar PAR haritası yayınlanmadan `CALIBRATED` karar üretilemez.

### CO₂

CO₂ varlığı yalnız bileşen varlığıdır. Hesap için gereken sinyal, ışık açıldığı anda
CO₂'nin hazır ve stabil olup olmadığıdır. Bu dinamik cevap ilk kurulumda ve her
yeniden değerlendirmede doğrulanır. Sabit “ışıklardan tam iki saat önce”
kuralı uygulanmaz: gerekli lead time tank hacmi, akış, difüzör ve hedef seviyeye
göre değişir. Kullanıcıya tek soru sorulur; yardım metni CO₂ zamanlamasının ışık
dönemiyle birlikte ayarlanmasını ve canlı güvenliğinin izlenmesini açıklar.

## Firmware yazma ve denetim akışı

1. Android her değerlendirmede yeni status alıp ürün, hardware, fixture uzunluğu ve
   cihaz yerel tarihini doğrular.
2. `light.auto.plan.get` ile `revision`, `storageGeneration` ve varsa `planId`
   alınır.
3. Hesap girdileri, politika/kaynak kimlikleri, exact R/G/B(/W), başlangıç–bitiş,
   rampa, cihaz yerel günü ve yeniden değerlendirme günü değiştirilemez bir
   `PREPARED` denetim satırına yazılır.
4. Denetim satırı başarıyla kaydolmadan `light.auto.plan.apply` gönderilmez.
5. Firmware başarısında dönen exact faz ile hazırlanan payload karşılaştırılır;
   `planId`, plan revision ve storage generation kayda eklenip `APPLIED` yapılır.
6. Kesin reddedilen deneme `FAILED`, timeout/taşıma sonucu bilinmeyen deneme
   `INDETERMINATE` olur. Aynı önerinin yeni denemesi ayrı audit ID alır; terminal
   kayıt üzerine yazılmaz.
   `PREPARED` kaydından sonra yerel profil yazımı tamamlanamaz veya işlem iptal
   edilirse firmware çağrılmaz; yarım audit `FAILED` terminal durumuna yazmak için
   sınırlı yeniden deneme yapılır.
7. Son doğru doz yalnız kayıtlı `planId + planRevision` güncel firmware status ile
   birebir eşleşiyorsa kullanılır. Plan silinmiş/değişmişse eski kayıt artış için
   otorite sayılmaz.
8. `STALE_REVISION` veya `STALE_STORAGE_GENERATION` otomatik yeniden gönderilmez;
   otorite yeniden okunur ve kullanıcının yeniden onayı gerekir.

Firmware RTC hazır değilse, tarih parse edilemiyorsa, cihaz kimliği/fixture uzunluğu
doğrulanamıyorsa, kalıcı snapshot yazılamıyorsa veya exact firmware cevabı hazırlanan
planla eşleşmiyorsa akış fail-closed kalır.

## UI akışı

- Üst kart kayıtlı tank, ürün ve katalogdan türetilen sinyalleri salt okunur gösterir.
- Ekran yalnız eksik zorunlu alanları açar. Aynı veri tank oluştururken veya ürün
  seçerken alınmışsa tekrar sorulmaz.
- Su derinliği ve lamba–su mesafesi ilk kurulumda bir kez ölçülür; düzenleme modunda
  mevcut değerleri tekrar cevaplamadan değiştirmek mümkündür. Bitki talebi yalnız
  katalog dışı bitkide, CO₂ sorusu yalnız CO₂ bileşeni varsa, saklanma yalnız
  karides varsa açılır.
- Taban ürünü yeniden seçtirilmez ve teknik taban sınıfı seçeneği gösterilmez.
- Bilinen ve katalog dışı bitkiler birlikteyse kullanıcı cevabı yalnız belirsizliği
  tamamlar; exact katalogdan gelen orta/yüksek talebi aşağı çekemez.
- Kapanış saati kullanıcıdan alınır veya mevcut plan/profilden korunur; sabit
  `14:00–22:00` üretilmez.
- Program önizlemesi süreyi, exact kanal yüzdelerini, rampayı, uyarıları ve bir
  sonraki değerlendirme gününü gösterir. PPFD/DLI göstermez.
- Kurulu plan aktif ekranda salt okunur gösterilir. “Koşulları güncelle”, CO₂ bileşeni
  varsa CO₂ hazırlığını ve her durumda yeni yosun/biofilm gözlemini tekrar ister;
  kayıtlı gün ışığı ile karides saklanma değerlerini de zorunlu yeniden seçim olmadan
  düzenlenebilir gösterir; ardından açık kullanıcı onayı gerekir.

## Kanıt dayanakları

- Yeni akvaryumda ilk 2–3 hafta günde 6 saat, ardından kademeli artış ve CO₂'nin ilk
  günden kullanılması: [Tropica Growing-in](https://tropica.com/en/guide/get-the-right-start/growing-in/).
- İlk üç hafta 6 saat ve daha sonra kademeli olarak en fazla 8 saat:
  [Tropica Quick Guide](https://tropica.com/media/870849/REDUCEDP14-11434-Quickguide_ny-UK.pdf).
- Yosun gözleminde doğrudan güneş ve zamanlayıcının ayrıca kontrol edilmesi:
  [Tropica Algae control](https://tropica.com/en/guide/algae-control/).
- Yeni dikimde ışık yoğunluğunun `%30`'u aşmaması ve ürün/tank koşullarının birlikte
  değerlendirilmesi: [Chihiros ışık yoğunluğu rehberi](https://bbs.chihirosaquaticstudio.com/threads/how-to-set-light-intensity.4/).
- CO₂ solenoidinin aydınlatma dönemiyle sınırlandırılması:
  [Colombo CO₂ Profi Set manual](https://aquadistri.com/wp-content/uploads/2024/08/Manual-Colombo-CO2-Profi-Set-1200.pdf).
- Işık ve inorganik karbonun su altı fotosentezinde birlikte sınırlayıcı olması:
  [Kitaya ve ark., 2003](https://pubmed.ncbi.nlm.nih.gov/14503512/).
- Tür bazlı ışık talebi: [Tropica plant database](https://tropica.com/en/plants/).
- Katalog taban semantiği; ürün üreticilerinin exact ürün sayfaları ve her kayıtla
  saklanan evidence source ID üzerinden izlenir.

## Yayın kapıları

- Android unit test, lint/detekt, proto doğrulama ve debug/releaseSmoke derlemeleri.
- Firmware host contract testi ve yedi ürün PlatformIO build'i.
- Exact WRGB ve RGB donanımında RTC/gece yarısı, bağlantı kesilmesi, stale authority,
  reboot/persistence, plan silme/değiştirme ve termal/power limiter smoke testleri.
- Her satış fixture/hardware revizyonu için laboratuvar PAR haritası, kalibrasyon
  profil revizyonu ve izlenebilir ölçüm raporu. Bu kapı tamamlanmadan uygulama yalnız
  `CONSERVATIVE_UNCALIBRATED` üretir.
- RGB Pro Slim firmware status'ü doğrulanmış fixture uzunluğu sağlamadığı sürece
  Android bu ürün için akıllı kurulumu bilerek açmaz. Tahmini uzunluk veya model
  adından çıkarım yayın çözümü değildir.
