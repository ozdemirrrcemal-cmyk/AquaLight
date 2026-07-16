package com.aqua.aqualight.platform.vision

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

interface ProvisioningQrFrameDecoder : AutoCloseable {
    fun decode(
        imageProxy: ImageProxy,
        onResult: (Result<String?>) -> Unit
    )
}

interface ProvisioningQrFrameDecoderFactory {
    fun create(): ProvisioningQrFrameDecoder
}

class MlKitProvisioningQrFrameDecoderFactory : ProvisioningQrFrameDecoderFactory {
    override fun create(): ProvisioningQrFrameDecoder = MlKitProvisioningQrFrameDecoder()
}

private class MlKitProvisioningQrFrameDecoder : ProvisioningQrFrameDecoder {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun decode(
        imageProxy: ImageProxy,
        onResult: (Result<String?>) -> Unit
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            onResult(Result.success(null))
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val rawValue = barcodes
                    .asSequence()
                    .mapNotNull(Barcode::getRawValue)
                    .firstOrNull(String::isNotBlank)
                onResult(Result.success(rawValue))
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    override fun close() {
        scanner.close()
    }
}
