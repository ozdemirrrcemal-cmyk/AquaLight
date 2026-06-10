# AquaLight Catalog Refactor Report

Bu paket, aquarium katalog katmanını UI paketinden ayırmak için düzenlendi.

## Yapılanlar

- Material katalog modelleri ve katalog listeleri `ui/tabs/aquarium/create/materials` altından çıkarıldı.
- Material katalogları şu pakete taşındı:
  - `com.aqua.aqualight.data.aquarium.catalog.material`
- Plant katalog modelleri ve katalog listeleri `ui/tabs/aquarium/create/plants` altından çıkarıldı.
- Plant katalogları şu pakete taşındı:
  - `com.aqua.aqualight.data.aquarium.catalog.plant`
- DataStore ve ViewModel tarafında kullanılan seçim modelleri UI paketinden çıkarıldı:
  - `TankMaterialSelection`
  - `TankPlantTag`
- Seçim modelleri şu pakete taşındı:
  - `com.aqua.aqualight.data.aquarium.model`
- `MaterialCategory` ayrı dosyaya ayrıldı.
- `PlantCategoryKey` eklendi ve `PlantCatalog` kategori stringleri merkezi sabitlerden beslenecek hale getirildi.
- Material/Plant picker UI metinleri için `res/values/catalog_strings.xml` eklendi.
- İlgili importlar yeni data paketlerine göre güncellendi.

## Korunanlar

- `MaterialPickerFragment`, `PlantPickerFragment` ve `PlantTagFragment` UI paketi altında bırakıldı.
- Navigation XML içindeki fragment yolları korunarak mevcut ekran açılışları bozulmadı.
- Mevcut DataStore proto şeması değiştirilmedi.
- `categoryTitle` alanı geriye dönük veri uyumluluğu için korundu.

## Build notu

Bu ortamda Gradle wrapper `https://services.gradle.org/distributions/gradle-8.11.1-all.zip` dosyasını indirmeye çalıştığı için build doğrulaması internet erişimi olmadığından tamamlanamadı. Kod tarafında paket/import refactor işlemleri statik olarak kontrol edildi.
