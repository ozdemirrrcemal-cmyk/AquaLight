package com.aqua.aqualight.ui.tabs.devices.add

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.provisioning.qr.AqlProvisioningQrParser
import com.aqua.aqualight.data.devices.provisioning.qr.AqlProvisioningQrPayload
import com.aqua.aqualight.databinding.FragmentDeviceQrScanBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DeviceQrScanFragment : Fragment(R.layout.fragment_device_qr_scan) {

    private var _binding: FragmentDeviceQrScanBinding? = null
    private val binding get() = _binding!!

    private val qrParser = AqlProvisioningQrParser()
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var barcodeScanner: BarcodeScanner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isProcessingFrame = false
    private var hasResult = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            showCameraPermissionRequired()
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceQrScanBinding.bind(view)

        setupHeader()
        setupActions()
        ensureCameraPermission()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = "QR Setup",
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun setupActions() {
        binding.btnRequestCamera.setOnClickListener {
            requestCameraPermission()
        }
    }

    private fun ensureCameraPermission() {
        if (hasCameraPermission()) {
            startCamera()
        } else {
            showCameraPermissionRequired()
        }
    }

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showCameraPermissionRequired() {
        if (_binding == null) return

        binding.btnRequestCamera.isVisible = true
        binding.tvScanTitle.text = "Camera permission required"
        binding.tvScanStatus.text = "Allow camera access to scan the device QR code."
    }

    private fun startCamera() {
        if (_binding == null || hasResult) return

        binding.btnRequestCamera.isVisible = false
        binding.tvScanTitle.text = "Align the QR code inside the frame"
        binding.tvScanStatus.text = "Setup will continue automatically after the code is detected."

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        barcodeScanner = BarcodeScanning.getClient(options)

        val providerFuture = ProcessCameraProvider.getInstance(requireContext())

        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder()
                    .build()
                    .also { cameraPreview ->
                        cameraPreview.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            analyzeQrFrame(imageProxy)
                        }
                    }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        viewLifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }.onFailure { error ->
                    if (_binding != null) {
                        binding.tvScanTitle.text = "Camera unavailable"
                        binding.tvScanStatus.text = error.message ?: "The camera could not be started right now."
                    }
                }
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeQrFrame(imageProxy: ImageProxy) {
        if (isProcessingFrame || hasResult || _binding == null) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessingFrame = true

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        val scanner = barcodeScanner
        if (scanner == null) {
            imageProxy.close()
            isProcessingFrame = false
            return
        }

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val rawValue = barcodes
                    .asSequence()
                    .mapNotNull { barcode -> barcode.rawValue }
                    .firstOrNull { raw -> raw.isNotBlank() }

                if (rawValue != null) {
                    handleQrPayload(rawValue)
                }
            }
            .addOnFailureListener { error ->
                if (_binding != null && !hasResult) {
                    binding.tvScanStatus.text = error.message ?: "QR code could not be read. Try again."
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
                isProcessingFrame = false
            }
    }

    private fun handleQrPayload(rawValue: String) {
        if (hasResult) return

        val payload = qrParser.parse(rawValue)
            .getOrElse { error ->
                if (_binding != null) {
                    binding.tvScanTitle.text = "Invalid QR code"
                    binding.tvScanStatus.text = error.message ?: "This is not an AquaLight setup code."
                }
                return
            }

        hasResult = true

        if (_binding != null) {
            binding.tvScanTitle.text = "QR code verified"
            binding.tvScanStatus.text = "Opening Wi‑Fi setup."
        }

        openWifiProvisioning(payload)
    }

    private fun openWifiProvisioning(payload: AqlProvisioningQrPayload) {
        findNavController().navigate(
            DeviceQrScanFragmentDirections.actionDeviceQrScanFragmentToDeviceWifiProvisioningFragment(
                candidateId = payload.deviceUid.value,
                deviceTitle = payload.model.ifBlank { "AquaLight Device" },
                deviceSerial = payload.deviceUid.value,
                deviceModel = "QR setup",
                bleAddress = "",
                bleName = payload.bleName,
                claimCode = payload.claimCode,
                rawQrPayload = payload.raw
            )
        )
    }

    override fun onDestroyView() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        barcodeScanner?.close()
        barcodeScanner = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
