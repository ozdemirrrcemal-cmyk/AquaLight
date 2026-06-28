package com.aqua.aqualight.ui.tabs.devices.add

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningCandidate
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceAddViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val bleScanner = AqlBleProvisioningScanner(application)

    private val _uiState = MutableStateFlow(DeviceAddUiState())
    val uiState: StateFlow<DeviceAddUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeviceAddEvent>(capacity = Channel.BUFFERED)
    val events: Flow<DeviceAddEvent> = _events.receiveAsFlow()

    private var scanCollectJob: Job? = null
    private var scanTimeoutJob: Job? = null

    fun onQrClicked() {
        viewModelScope.launch {
            _events.send(DeviceAddEvent.OpenQrScanner)
        }
    }

    fun onBlePermissionDenied() {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.PERMISSION_REQUIRED,
            heroTitle = "Bluetooth izni gerekiyor",
            heroSubtitle = "Yakındaki kurulum modundaki AquaLight cihazlarını bulmak için Bluetooth izni verin.",
            scanBadge = "Permission",
            emptyTitle = "İzin gerekli",
            emptyMessage = "Bluetooth izni verildikten sonra Scan ile cihaz arayabilirsiniz."
        )
    }

    fun startBleScan() {
        scanCollectJob?.cancel()
        scanTimeoutJob?.cancel()

        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.SCANNING,
            heroTitle = "Yakındaki cihazlar aranıyor",
            heroSubtitle = "Cihazı telefonunuza yakın tutun. Kurulum modundaki AquaLight cihazları burada listelenecek.",
            scanBadge = "Scanning",
            candidates = emptyList(),
            emptyTitle = "Aranıyor...",
            emptyMessage = "Kurulum modundaki AquaLight cihazları taranıyor."
        )

        when (val result = bleScanner.startScan()) {
            AqlBleProvisioningScanner.StartResult.Started -> {
                observeBleCandidates()
                startScanTimeout()
            }

            AqlBleProvisioningScanner.StartResult.MissingPermission -> {
                onBlePermissionDenied()
            }

            AqlBleProvisioningScanner.StartResult.BluetoothOff -> {
                showBluetoothOff()
            }

            AqlBleProvisioningScanner.StartResult.BluetoothUnavailable -> {
                showBluetoothUnavailable()
            }

            is AqlBleProvisioningScanner.StartResult.Failed -> {
                showBleError(result.message)
            }
        }
    }

    fun onScanAgainClicked() {
        startBleScan()
    }

    fun onCandidateClicked(candidate: DeviceAddCandidateUi) {
        stopBleScan()
        viewModelScope.launch {
            _events.send(
                DeviceAddEvent.OpenWifiProvisioning(
                    candidate = candidate
                )
            )
        }
    }

    private fun observeBleCandidates() {
        scanCollectJob = viewModelScope.launch {
            bleScanner.candidates.collect { candidates ->
                val uiCandidates = candidates.map { candidate ->
                    candidate.toUi()
                }

                if (uiCandidates.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        mode = DeviceAddScanMode.SCANNING,
                        candidates = emptyList(),
                        emptyTitle = "Aranıyor...",
                        emptyMessage = "Kurulum modundaki AquaLight cihazları taranıyor."
                    )
                } else {
                    _uiState.value = DeviceAddUiState(
                        mode = DeviceAddScanMode.RESULTS,
                        heroTitle = "${uiCandidates.size} cihaz bulundu",
                        heroSubtitle = "Kurulum yapmak istediğiniz AquaLight cihazını seçin.",
                        scanBadge = "Nearby",
                        candidates = uiCandidates,
                        emptyTitle = "",
                        emptyMessage = ""
                    )
                }
            }
        }
    }

    private fun startScanTimeout() {
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT_MS)

            val hasCandidates = _uiState.value.candidates.isNotEmpty()
            if (!hasCandidates) {
                bleScanner.stopScan()
                _uiState.value = DeviceAddUiState(
                    mode = DeviceAddScanMode.EMPTY,
                    heroTitle = "Cihaz bulunamadı",
                    heroSubtitle = "Cihazın açık olduğundan emin olun. Daha önce kurulmuş cihazlar için setup tuşuna 5 saniye basıp tekrar deneyin.",
                    scanBadge = "Ready",
                    candidates = emptyList(),
                    emptyTitle = "Yakında cihaz yok",
                    emptyMessage = "Cihazı kurulum moduna alın, telefonunuza yakın tutun ve tekrar Scan butonuna basın. QR ile ekleme sağ üsttedir."
                )
            }
        }
    }

    private fun showBluetoothOff() {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.BLUETOOTH_OFF,
            heroTitle = "Bluetooth kapalı",
            heroSubtitle = "Yakındaki AquaLight cihazlarını bulmak için Bluetooth'u açın.",
            scanBadge = "Bluetooth",
            emptyTitle = "Bluetooth kapalı",
            emptyMessage = "Telefon ayarlarından Bluetooth'u açın, sonra tekrar Scan yapın."
        )
    }

    private fun showBluetoothUnavailable() {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.ERROR,
            heroTitle = "Bluetooth kullanılamıyor",
            heroSubtitle = "Bu telefonda BLE kurulum taraması başlatılamadı.",
            scanBadge = "Unavailable",
            emptyTitle = "Bluetooth kullanılamıyor",
            emptyMessage = "Bu cihaz şu anda BLE provisioning için uygun görünmüyor."
        )
    }

    private fun showBleError(message: String) {
        stopBleScan()
        _uiState.value = DeviceAddUiState(
            mode = DeviceAddScanMode.ERROR,
            heroTitle = "Scan başlatılamadı",
            heroSubtitle = "Bluetooth taraması başlatılırken sorun oluştu.",
            scanBadge = "Error",
            emptyTitle = "Scan başarısız",
            emptyMessage = message.ifBlank { "BLE scan başarısız oldu. Tekrar deneyin." }
        )
    }

    private fun stopBleScan() {
        scanCollectJob?.cancel()
        scanCollectJob = null
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        bleScanner.stopScan()
    }

    private fun AqlBleProvisioningCandidate.toUi(): DeviceAddCandidateUi {
        val modelLabel = buildList {
            if (model.isNotBlank()) add(model)
            add("Setup mode")
            add("RSSI $rssi dBm")
        }.joinToString(separator = " • ")

        return DeviceAddCandidateUi(
            id = deviceUid.ifBlank { address },
            title = displayTitle,
            serial = displaySerial,
            model = modelLabel,
            status = displayStatus.ifBlank { "Ready" },
            rssiLabel = "$rssi dBm",
            bleAddress = address,
            bleName = name
        )
    }

    override fun onCleared() {
        stopBleScan()
        super.onCleared()
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 10_000L
    }
}

data class DeviceAddUiState(
    val mode: DeviceAddScanMode = DeviceAddScanMode.READY,
    val heroTitle: String = "Yakındaki AquaLight cihazlarını bulun",
    val heroSubtitle: String = "Cihazınız kurulum modundaysa Scan butonuna basın. QR ile eklemek için sağ üstteki QR ikonunu kullanın.",
    val scanBadge: String = "Ready",
    val candidates: List<DeviceAddCandidateUi> = emptyList(),
    val emptyTitle: String = "Scan hazır",
    val emptyMessage: String = "Scan butonuna bastığınızda kurulum modundaki AquaLight cihazları burada listelenir."
)

enum class DeviceAddScanMode {
    READY,
    SCANNING,
    RESULTS,
    EMPTY,
    PERMISSION_REQUIRED,
    BLUETOOTH_OFF,
    ERROR
}

sealed interface DeviceAddEvent {
    data class ShowMessage(val message: String) : DeviceAddEvent

    object OpenQrScanner : DeviceAddEvent

    data class OpenWifiProvisioning(
        val candidate: DeviceAddCandidateUi
    ) : DeviceAddEvent
}
