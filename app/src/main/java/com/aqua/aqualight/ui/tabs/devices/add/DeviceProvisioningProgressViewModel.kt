package com.aqua.aqualight.ui.tabs.devices.add

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningAddressResolver
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattClient
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattEvent
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningStatus
import com.aqua.aqualight.data.devices.provisioning.repository.AqlProvisioningHandoffSaver
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceProvisioningProgressViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val addressResolver = AqlBleProvisioningAddressResolver(application)
    private val gattClient = AqlBleProvisioningGattClient(application)
    private val handoffSaver = AqlProvisioningHandoffSaver(application)
    private val routeResolver = DeviceRouteResolver()

    private val _uiState = MutableStateFlow(DeviceProvisioningProgressUiState())
    val uiState: StateFlow<DeviceProvisioningProgressUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeviceProvisioningProgressEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var boundSessionId: String? = null
    private var activeDraft: AqlProvisioningDraft? = null
    private var gattEventsJob: Job? = null
    private var handoffSaved = false
    private var startJob: Job? = null

    fun bind(sessionId: String) {
        if (sessionId.isBlank() || boundSessionId == sessionId) {
            return
        }

        boundSessionId = sessionId

        val draft = AqlProvisioningDraftStore.get(sessionId)
        activeDraft = draft

        if (draft == null) {
            _uiState.value = DeviceProvisioningProgressUiState(
                title = "Kurulum oturumu süresi doldu",
                message = "Geri dönüp cihazı tekrar seçin.",
                deviceName = "Unknown device",
                deviceSerial = "Unknown",
                bleAddress = "Unknown",
                wifiSsid = "Unknown",
                stepOne = "✓ Cihaz seçildi",
                stepTwo = "✓ Wi-Fi bilgileri hazırlandı",
                stepThree = "Kurulum oturumu bulunamadı",
                canStart = false,
                buttonText = "Unavailable",
                showProgress = false
            )
            return
        }

        _uiState.value = DeviceProvisioningProgressUiState(
            title = "Cihaz kuruluma hazır",
            message = "Wi-Fi bilgileri hazırlandı. AquaLight cihazına güvenli bağlantı kuruluyor.",
            deviceName = draft.deviceTitle.ifBlank { "AquaLight Device" },
            deviceSerial = draft.deviceSerial.ifBlank { draft.candidateId },
            bleAddress = draft.bleAddress.ifBlank { draft.bleName.ifBlank { "QR cihazı aranacak" } },
            wifiSsid = draft.wifiCredentials.ssid,
            stepOne = "✓ Cihaz seçildi",
            stepTwo = "✓ Wi-Fi bilgileri hazırlandı",
            stepThree = "⏳ Güvenli bağlantı hazırlanıyor",
            canStart = true,
            buttonText = "Try again",
            showProgress = false
        )
    }

    fun onBlePermissionDenied() {
        _uiState.value = _uiState.value.copy(
            title = "Bluetooth izni gerekiyor",
            message = "Cihazla güvenli kurulum bağlantısı kurmak için Bluetooth izni verin.",
            stepThree = "Bluetooth izni bekleniyor",
            canStart = true,
            buttonText = "Try again",
            showProgress = false
        )
    }

    fun startProvisioning() {
        val draft = activeDraft ?: run {
            _uiState.value = _uiState.value.copy(
                title = "Kurulum oturumu süresi doldu",
                message = "Geri dönüp cihazı tekrar seçin.",
                canStart = false,
                buttonText = "Unavailable",
                showProgress = false
            )
            return
        }

        startJob?.cancel()
        handoffSaved = false
        observeGattEvents()

        startJob = viewModelScope.launch {
            val readyDraft = resolveBleAddressIfNeeded(draft) ?: return@launch
            activeDraft = readyDraft

            _uiState.value = _uiState.value.copy(
                title = "Cihaza bağlanılıyor",
                message = "AquaLight cihazı ile güvenli Bluetooth bağlantısı kuruluyor.",
                bleAddress = readyDraft.bleAddress,
                stepThree = "⏳ Bluetooth bağlantısı kuruluyor",
                canStart = false,
                buttonText = "Provisioning...",
                showProgress = true
            )

            gattClient.start(readyDraft)
        }
    }

    private suspend fun resolveBleAddressIfNeeded(
        draft: AqlProvisioningDraft
    ): AqlProvisioningDraft? {
        if (draft.bleAddress.isNotBlank()) {
            return draft
        }

        val bleName = draft.bleName.trim()
        if (bleName.isBlank()) {
            _uiState.value = _uiState.value.copy(
                title = "Cihaz bulunamadı",
                message = "QR içinde Bluetooth adı yok. Cihazı Scan ile arayıp tekrar deneyin.",
                stepThree = "Cihaz bulunamadı",
                canStart = true,
                buttonText = "Try again",
                showProgress = false
            )
            return null
        }

        _uiState.value = _uiState.value.copy(
            title = "QR cihazı aranıyor",
            message = "$bleName isimli AquaLight cihazı Bluetooth üzerinden aranıyor.",
            bleAddress = bleName,
            stepThree = "⏳ QR cihazı Bluetooth ile aranıyor",
            canStart = false,
            buttonText = "Finding...",
            showProgress = true
        )

        val resolvedAddress = addressResolver.resolveAddress(bleName)
            .getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    title = "QR cihazı bulunamadı",
                    message = error.message ?: "QR ile seçilen cihaz Bluetooth üzerinden bulunamadı.",
                    stepThree = "Cihaz bulunamadı",
                    canStart = true,
                    buttonText = "Try again",
                    showProgress = false
                )
                return null
            }

        return draft.copy(
            bleAddress = resolvedAddress
        )
    }

    private fun observeGattEvents() {
        if (gattEventsJob != null) {
            return
        }

        gattEventsJob = viewModelScope.launch {
            gattClient.events.collect { event ->
                handleGattEvent(event)
            }
        }
    }

    private fun handleGattEvent(event: AqlBleProvisioningGattEvent) {
        when (event) {
            is AqlBleProvisioningGattEvent.RuntimeHandoffReceived -> {
                renderRuntimeHandoffReceived(event.handoff)
                saveRuntimeHandoff(event.handoff)
            }

            AqlBleProvisioningGattEvent.Completed -> {
                if (!handoffSaved) {
                    _uiState.value = _uiState.value.copy(
                        title = "Kurulum tamamlandı",
                        message = "Cihaz bilgileri alındı. Cihaz menüsü hazırlanıyor.",
                        stepThree = "⏳ Cihaz menüsü hazırlanıyor",
                        canStart = false,
                        buttonText = "Saving...",
                        showProgress = true
                    )
                }
            }

            else -> {
                _uiState.value = reduceGattEvent(event)
            }
        }
    }

    private fun reduceGattEvent(
        event: AqlBleProvisioningGattEvent
    ): DeviceProvisioningProgressUiState {
        return when (event) {
            is AqlBleProvisioningGattEvent.Connecting -> {
                _uiState.value.copy(
                    title = "Cihaza bağlanılıyor",
                    message = "AquaLight cihazı ile güvenli Bluetooth bağlantısı kuruluyor.",
                    stepThree = "⏳ Bluetooth bağlantısı kuruluyor",
                    canStart = false,
                    buttonText = "Provisioning...",
                    showProgress = true
                )
            }

            is AqlBleProvisioningGattEvent.Connected -> {
                _uiState.value.copy(
                    title = "Cihaz bulundu",
                    message = "Kurulum servisi hazırlanıyor.",
                    stepThree = "✓ Bluetooth bağlantısı kuruldu",
                    showProgress = true
                )
            }

            AqlBleProvisioningGattEvent.ServicesDiscovered -> {
                _uiState.value.copy(
                    title = "Güvenli oturum hazırlanıyor",
                    message = "Cihaz kurulum oturumu başlatılıyor.",
                    stepThree = "⏳ Güvenli oturum başlatılıyor",
                    showProgress = true
                )
            }

            AqlBleProvisioningGattEvent.StartSessionWritten -> {
                _uiState.value.copy(
                    title = "Güvenli oturum başladı",
                    message = "Wi-Fi bilgileri cihazınıza gönderiliyor.",
                    stepThree = "⏳ Wi-Fi bilgileri gönderiliyor",
                    showProgress = true
                )
            }

            AqlBleProvisioningGattEvent.WifiCredentialsWritten -> {
                _uiState.value.copy(
                    title = "Wi-Fi bilgileri gönderildi",
                    message = "Cihazınız Wi-Fi ağına bağlanıyor.",
                    stepThree = "⏳ Cihaz Wi-Fi ağına bağlanıyor",
                    showProgress = true
                )
            }

            is AqlBleProvisioningGattEvent.StatusReceived -> {
                _uiState.value.copy(
                    title = event.statusMessage.status.toProgressTitle(),
                    message = event.statusMessage.status.toProgressMessage(
                        fallback = event.statusMessage.message
                    ),
                    stepThree = event.statusMessage.status.toProgressStep(),
                    showProgress = event.statusMessage.status !in terminalStatuses,
                    canStart = event.statusMessage.status in retryStatuses,
                    buttonText = "Try again"
                )
            }

            is AqlBleProvisioningGattEvent.RuntimeHandoffReceived -> {
                _uiState.value
            }

            AqlBleProvisioningGattEvent.Completed -> {
                _uiState.value
            }

            is AqlBleProvisioningGattEvent.Failed -> {
                _uiState.value.copy(
                    title = "Kurulum tamamlanamadı",
                    message = event.message.toFriendlyError(),
                    stepThree = "Kurulum durdu",
                    canStart = true,
                    buttonText = "Try again",
                    showProgress = false
                )
            }

            AqlBleProvisioningGattEvent.Disconnected -> {
                if (handoffSaved) {
                    _uiState.value
                } else {
                    _uiState.value.copy(
                        title = "Bağlantı kapandı",
                        message = "Cihaz kurulum bağlantısı kapandı. Cihaz kurulum modundaysa tekrar deneyebilirsiniz.",
                        stepThree = "Bağlantı kapandı",
                        canStart = true,
                        buttonText = "Try again",
                        showProgress = false
                    )
                }
            }
        }
    }

    private fun renderRuntimeHandoffReceived(
        handoff: AqlProvisioningRuntimeHandoff
    ) {
        _uiState.value = _uiState.value.copy(
            title = "Cihaz çevrimiçi",
            message = "AquaLight cihazı Wi-Fi ağına bağlandı. Cihaz menüsü hazırlanıyor.",
            stepThree = "⏳ Cihaz menüsü hazırlanıyor",
            canStart = false,
            buttonText = "Saving...",
            showProgress = true
        )
    }

    private fun saveRuntimeHandoff(
        handoff: AqlProvisioningRuntimeHandoff
    ) {
        val draft = activeDraft ?: run {
            _uiState.value = _uiState.value.copy(
                title = "Kurulum oturumu süresi doldu",
                message = "Cihaz bilgileri geldi fakat yerel kurulum oturumu bulunamadı.",
                stepThree = "Cihaz kaydı yapılamadı",
                canStart = true,
                buttonText = "Try again",
                showProgress = false
            )
            return
        }

        viewModelScope.launch {
            val result = handoffSaver.saveAndConnect(
                draft = draft,
                handoff = handoff
            )

            result.onSuccess { snapshot ->
                handoffSaved = true
                boundSessionId?.let { sessionId ->
                    AqlProvisioningDraftStore.remove(sessionId)
                }

                val route = routeResolver.resolve(
                    snapshot = snapshot,
                    requestedDeviceUid = snapshot.deviceUid.value
                )

                _uiState.value = _uiState.value.copy(
                    title = "Cihaz eklendi",
                    message = "${snapshot.title} hazır. Cihaz menüsü açılıyor.",
                    stepThree = "✓ Cihaz menüsü açılıyor",
                    canStart = false,
                    buttonText = "Opening...",
                    showProgress = true
                )

                _events.send(
                    DeviceProvisioningProgressEvent.OpenAddedDevice(route)
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    title = "Cihaz kaydedilemedi",
                    message = error.message ?: "Cihaz bilgileri kaydedilemedi.",
                    stepThree = "Cihaz kaydı yapılamadı",
                    canStart = true,
                    buttonText = "Try again",
                    showProgress = false
                )
            }
        }
    }

    private fun AqlProvisioningStatus.toProgressTitle(): String {
        return when (this) {
            AqlProvisioningStatus.IDLE,
            AqlProvisioningStatus.FACTORY,
            AqlProvisioningStatus.PHYSICAL_RESET -> "Cihaz kurulum modunda"
            AqlProvisioningStatus.PROVISIONING_IN_PROGRESS,
            AqlProvisioningStatus.CLAIM_VALIDATING -> "Kurulum doğrulanıyor"
            AqlProvisioningStatus.WIFI_CREDENTIALS_RECEIVED -> "Wi-Fi bilgileri alındı"
            AqlProvisioningStatus.WIFI_CONNECTING -> "Wi-Fi ağına bağlanıyor"
            AqlProvisioningStatus.WIFI_CONNECTED -> "Wi-Fi bağlantısı kuruldu"
            AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY -> "Cihaz çevrimiçi"
            AqlProvisioningStatus.COMPLETED -> "Kurulum tamamlandı"
            AqlProvisioningStatus.WIFI_FAILED -> "Wi-Fi bağlantısı kurulamadı"
            AqlProvisioningStatus.CLAIM_REJECTED -> "Cihaz doğrulanamadı"
            AqlProvisioningStatus.TIMEOUT -> "Kurulum süresi doldu"
            AqlProvisioningStatus.ERROR,
            AqlProvisioningStatus.UNKNOWN -> "Cihazdan durum bekleniyor"
        }
    }

    private fun AqlProvisioningStatus.toProgressMessage(fallback: String): String {
        return when (this) {
            AqlProvisioningStatus.IDLE,
            AqlProvisioningStatus.FACTORY,
            AqlProvisioningStatus.PHYSICAL_RESET -> "Cihaz kurulum modunda. Güvenli oturum hazırlanıyor."
            AqlProvisioningStatus.PROVISIONING_IN_PROGRESS,
            AqlProvisioningStatus.CLAIM_VALIDATING -> "AquaLight cihazı kurulum isteğini doğruluyor."
            AqlProvisioningStatus.WIFI_CREDENTIALS_RECEIVED -> "Cihaz Wi-Fi bilgilerini aldı. Bağlantı başlatılıyor."
            AqlProvisioningStatus.WIFI_CONNECTING -> "Cihaz seçilen 2.4 GHz Wi-Fi ağına bağlanıyor."
            AqlProvisioningStatus.WIFI_CONNECTED -> "Wi-Fi bağlantısı başarılı. Cihaz menüsü hazırlanıyor."
            AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY -> "Cihaz çalışma bağlantısı hazır. Menü açılıyor."
            AqlProvisioningStatus.COMPLETED -> "Kurulum tamamlandı. Cihaz menüsü açılıyor."
            AqlProvisioningStatus.WIFI_FAILED -> "Şifre hatalı olabilir veya cihaz 2.4 GHz ağı göremiyor. Ağı ve şifreyi kontrol edin."
            AqlProvisioningStatus.CLAIM_REJECTED -> "Cihaz bu kurulum isteğini kabul etmedi. QR ile ekleyin veya setup tuşuna 5 saniye basıp tekrar deneyin."
            AqlProvisioningStatus.TIMEOUT -> "Cihaz kurulum süresi doldu. Setup tuşuna 5 saniye basıp tekrar deneyin."
            AqlProvisioningStatus.ERROR,
            AqlProvisioningStatus.UNKNOWN -> fallback.ifBlank { "Cihazdan kurulum durumu bekleniyor." }
        }
    }

    private fun AqlProvisioningStatus.toProgressStep(): String {
        return when (this) {
            AqlProvisioningStatus.WIFI_CREDENTIALS_RECEIVED -> "✓ Wi-Fi bilgileri cihaza ulaştı"
            AqlProvisioningStatus.WIFI_CONNECTING -> "⏳ Cihaz Wi-Fi ağına bağlanıyor"
            AqlProvisioningStatus.WIFI_CONNECTED -> "✓ Wi-Fi bağlantısı başarılı"
            AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY -> "⏳ Cihaz menüsü hazırlanıyor"
            AqlProvisioningStatus.COMPLETED -> "✓ Kurulum tamamlandı"
            AqlProvisioningStatus.WIFI_FAILED -> "Wi-Fi bağlantısı kurulamadı"
            AqlProvisioningStatus.CLAIM_REJECTED -> "Cihaz doğrulanamadı"
            AqlProvisioningStatus.TIMEOUT -> "Kurulum süresi doldu"
            AqlProvisioningStatus.ERROR -> "Kurulum durdu"
            else -> "⏳ Kurulum devam ediyor"
        }
    }

    private fun String.toFriendlyError(): String {
        val normalized = trim()
        return when {
            normalized.contains("StartSession is required", ignoreCase = true) ->
                "Cihaz kurulum oturumunu başlatmadan Wi-Fi bilgisini kabul etmedi. Cihazı setup moduna alıp tekrar deneyin."
            normalized.contains("WiFi", ignoreCase = true) || normalized.contains("wifi", ignoreCase = true) ->
                "Wi-Fi bağlantısı kurulamadı. Ağın 2.4 GHz olduğundan ve şifrenin doğru olduğundan emin olun."
            normalized.isNotBlank() -> normalized
            else -> "Kurulum sırasında beklenmeyen bir sorun oluştu. Tekrar deneyin."
        }
    }

    override fun onCleared() {
        startJob?.cancel()
        gattEventsJob?.cancel()
        gattClient.close()
        super.onCleared()
    }

    private companion object {
        val terminalStatuses = setOf(
            AqlProvisioningStatus.COMPLETED,
            AqlProvisioningStatus.WIFI_FAILED,
            AqlProvisioningStatus.CLAIM_REJECTED,
            AqlProvisioningStatus.TIMEOUT,
            AqlProvisioningStatus.ERROR
        )

        val retryStatuses = setOf(
            AqlProvisioningStatus.WIFI_FAILED,
            AqlProvisioningStatus.CLAIM_REJECTED,
            AqlProvisioningStatus.TIMEOUT,
            AqlProvisioningStatus.ERROR
        )
    }
}

sealed interface DeviceProvisioningProgressEvent {
    data class OpenAddedDevice(
        val route: DeviceRoute
    ) : DeviceProvisioningProgressEvent
}

data class DeviceProvisioningProgressUiState(
    val title: String = "Cihaz kuruluyor",
    val message: String = "AquaLight cihazı güvenli şekilde hazırlanıyor.",
    val deviceName: String = "",
    val deviceSerial: String = "",
    val bleAddress: String = "",
    val wifiSsid: String = "",
    val stepOne: String = "✓ Cihaz seçildi",
    val stepTwo: String = "✓ Wi-Fi bilgileri hazırlandı",
    val stepThree: String = "⏳ Güvenli bağlantı hazırlanıyor",
    val canStart: Boolean = false,
    val buttonText: String = "Try again",
    val showProgress: Boolean = false
)
