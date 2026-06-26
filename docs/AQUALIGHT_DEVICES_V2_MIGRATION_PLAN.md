# AquaLight Devices V2 Runtime Migration Plan

Bu branch `release` taban alınarak açıldı. Hedef, cihaz mimarisini firmware tarafındaki ticari seviye üstü BLE + QR provisioning, UDP v2 discovery ve WebSocket runtime sözleşmesine taşımaktır.

## Ana hedef

- Eski AP onboarding geri gelmeyecek.
- Eski HTTP REST runtime geri gelmeyecek.
- Eski UDP v1 geri gelmeyecek.
- Eski Android static device catalog geri gelmeyecek.
- Eski `Device id` merkezli cihaz açılışı ana akış olmayacak.
- Ana cihaz kimliği `deviceUid` olacak.
- Cihaz ailesi, limitleri, kabiliyetleri ve desteklenen ekranları firmware'den gelecek.
- Router ara Fragment olarak kullanılmayacak.
- Cihaz kartına basınca `DeviceRouteResolver` karar verecek ve NavController doğrudan ilgili root ekrana gidecek.
- Online/offline durumu eski monitor mantığının daha profesyonel haliyle UDP + WebSocket + Android network lifecycle sinyallerinden üretilecek.

## Firmware sözleşmesi

Firmware tarafındaki yeni ticari runtime akışı:

1. BLE + QR provisioning
2. UDP v2 discovery
3. WebSocket runtime/auth/events/commands

Android tarafında BLE sadece kurulum/provisioning için kullanılacak. UDP sadece LAN discovery için kullanılacak. Runtime kontrol ve event akışı WebSocket üzerinden yapılacak.

## Yeni paket iskeleti

```text
data/devices/
  contract/
    AqlDiscoveryContract.kt
    AqlBleProvisioningContract.kt
    AqlWsContract.kt

  model/
    DeviceUid.kt
    DeviceFamily.kt
    DeviceIdentity.kt
    DeviceProduct.kt
    DeviceCapabilities.kt
    DeviceLimits.kt
    DeviceRuntimeEndpoint.kt
    DeviceSnapshot.kt
    DeviceConnectionState.kt
    DeviceOnlineState.kt

  discovery/udp/
    AqlDiscoveryUdpScanner.kt
    AqlDiscoveryParser.kt
    AqlDiscoveryRefreshSender.kt
    AqlDiscoverySupervisor.kt
    AqlDiscoveredDevice.kt

  runtime/ws/
    AqlWsClient.kt
    AqlWsConnection.kt
    AqlWsCommandClient.kt
    AqlWsAuthManager.kt
    AqlWsEventStream.kt
    AqlWsMessageParser.kt
    AqlWsHealthMonitor.kt

  provisioning/ble/
    AqlQrPayload.kt
    AqlBleScanner.kt
    AqlBleProvisioningClient.kt
    AqlBleProvisioningSession.kt
    AqlWifiCredentials.kt
    AqlProvisioningStatus.kt
    AqlRuntimeEndpointResult.kt

  monitor/
    DevicePresenceSupervisor.kt
    DeviceConnectivityObserver.kt
    DeviceStatusAggregator.kt
    DeviceHeartbeatPolicy.kt

  repository/
    DevicesRepository.kt
    DeviceDiscoveryRepository.kt
    DeviceRuntimeRepository.kt
    DeviceProvisioningRepository.kt

  store/
    DeviceRegistryStore.kt
    DeviceCredentialStore.kt
    DeviceLastSeenStore.kt
```

## UI iskeleti

```text
ui/tabs/devices/
  DevicesFragment.kt
  DevicesViewModel.kt

  route/
    DeviceRoute.kt
    DeviceRouteResolver.kt

  add/
    DeviceAddFragment.kt
    DeviceQrScanFragment.kt
    DeviceBleScanFragment.kt
    DeviceWifiProvisioningFragment.kt
    DeviceProvisioningResultFragment.kt

  detail/
    light/
      DeviceLightRootFragment.kt
      DeviceLightViewModel.kt

    dosing/
      DeviceDosingRootFragment.kt
      DeviceDosingViewModel.kt

    timer/
      DeviceTimerRootFragment.kt
      DeviceTimerViewModel.kt

    cooling/
      DeviceCoolingRootFragment.kt
      DeviceCoolingViewModel.kt
```

## Sıralı geçiş planı

### 1. Temiz temel kontrolü

- Taban branch: `release`
- Yeni branch: `feature/devices-v2-runtime`
- Eski kalıntılar aranacak:
  - `DeviceDiscoveryService`
  - `DevicesDataStoreManager`
  - `AquaDeviceCatalog`
  - `SetupApScanner`
  - `DeviceAddCandidateLoader`
  - `aql.discovery.v1`
  - `RefreshUDP`
  - `httpPort`
  - `apiBasePath`
  - cihaz detaylarında `childFragmentManager`
  - eski `deviceIp` navigation argümanları

### 2. Contract ve model katmanı

Önce firmware sözleşmesine karşılık gelen sabitler ve modeller yazılacak:

- `AqlDiscoveryContract`
- `AqlBleProvisioningContract`
- `AqlWsContract`
- `DeviceSnapshot`
- `DeviceCapabilities`
- `DeviceLimits`
- `DeviceRuntimeEndpoint`

### 3. UDP v2 parser

Önce socket açmadan parser yazılacak. Parser sadece şunu kabul edecek:

- `schema = aql.discovery.v2`
- `messageType = device_announce`
- `udpVersion = 20260624`
- `runtimeTransport = websocket`
- `device.uid` zorunlu
- `product.family` zorunlu

Eski UDP v1, HTTP alanları veya eksik kimlik kabul edilmeyecek.

### 4. UDP scanner + refresh sender

- Port: `10888`
- App foreground olunca aktif `discovery_refresh` gönderilecek.
- Aynı cihaz `deviceUid` ile merge edilecek.
- IP değişirse aynı cihazın endpoint'i güncellenecek.
- Duplicate kart oluşmayacak.

### 5. Profesyonel online/offline sistemi

Eski monitor geri gelmeyecek; yerine daha gelişmiş yapı kurulacak:

- `DevicePresenceSupervisor`
- `DeviceConnectivityObserver`
- `DeviceStatusAggregator`
- `DeviceHeartbeatPolicy`

Durum sinyalleri:

- UDP son görülme zamanı
- WebSocket connected/authenticated durumu
- Android network lifecycle
- provisioning/OTA/auth-required gibi özel runtime durumları

Örnek iç state:

```kotlin
enum class DeviceOnlineState {
    UNKNOWN,
    DISCOVERING,
    ONLINE_LAN,
    CONNECTING_WS,
    AUTHENTICATED,
    STALE,
    OFFLINE,
    LOCAL_NETWORK_OFFLINE,
    AUTH_REQUIRED,
    PROVISIONING,
    OTA_UPDATING,
    ERROR
}
```

### 6. Store katmanı

- `DeviceRegistryStore`: cihaz kimliği, product, capabilities, limits, last endpoint, lastSeen
- `DeviceCredentialStore`: `deviceUid -> runtime token`
- `DeviceLastSeenStore`: son görülme bilgileri

Token düz açık veri olarak saklanmayacak; güvenli saklama tercih edilecek.

### 7. WebSocket runtime

Akış:

1. UDP ile endpoint bulunur.
2. WebSocket bağlanır.
3. Hello okunur.
4. `security.status.get`
5. token varsa `auth`
6. `device.identity.get`
7. `device.capabilities.get`
8. ilgili module status/event akışı başlar.

### 8. DevicesRepository ana omurga

Repository şu kaynakları birleştirecek:

- UDP discovery
- WebSocket runtime
- local registry
- credential store
- network observer

UI doğrudan UDP/BLE/WebSocket bilmeyecek.

### 9. Devices UI bağlantısı

Devices ekranı yeni `DevicesRepository` üzerinden `StateFlow` dinleyecek.

Kartlarda gösterilecek temel bilgiler:

- title/customName
- family
- model/displayName
- online/offline state
- last seen
- capability badges

### 10. RouteResolver ve navigation

`DeviceRouterFragment` kaldırılacak. Kart tıklanınca:

1. `deviceUid` alınır.
2. `DeviceSnapshot` okunur.
3. `DeviceRouteResolver` family/capabilities/supportedScreens ile route üretir.
4. NavController doğrudan ilgili root ekrana gider.

Ana navigation argümanı:

```text
deviceUid: String
```

`deviceId`, `deviceIp`, `deviceType`, `modelName`, `channelCount`, `httpPort`, `apiBasePath` navigation argümanı olmayacak.

### 11. İlk root ekran: Light

Önce Light root kurulacak. WRGB Pro Elite ve RGB Pro Slim aynı root yapıyı kullanacak ama capabilities/limits'e göre UI dinamik oluşacak.

### 12. Diğer root ekranlar

Sıra:

1. Light
2. Timer
3. Dosing
4. Cooling

### 13. BLE + QR provisioning

BLE en başta değil, UDP + store + repository + WebSocket temeli hazırlandıktan sonra bağlanacak.

Akış:

1. QR okutulur.
2. BLE cihazı bulunur.
3. StartSession gönderilir.
4. Claim doğrulanır.
5. WiFi credentials gönderilir.
6. ProvisioningStatus takip edilir.
7. RuntimeEndpoint + token alınır.
8. Token güvenli saklanır.
9. UDP refresh gönderilir.
10. Cihaz registry'ye eklenir.
11. Devices ekranına dönülür.

## Patch/Termux çalışma kuralı

Uzun veya riskli kodlarda en güvenli yöntem:

1. Patch burada hazırlanır.
2. Kullanıcı Termux ile doğru branch'e geçer.
3. Patch uygulanır.
4. Derleme/test alınır.
5. Kullanıcı push eder.

Doğru branch kontrolü:

```bash
git fetch origin
git checkout feature/devices-v2-runtime
git status
git branch --show-current
```

Patch uygulama öncesi branch mutlaka `feature/devices-v2-runtime` olmalıdır.
