package com.aqua.aqualight.platform.vision

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

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

    private val closed = AtomicBoolean(false)
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @ExperimentalGetImage
    override fun decode(
        imageProxy: ImageProxy,
        onResult: (Result<String?>) -> Unit
    ) {
        if (closed.get()) {
            imageProxy.close()
            onResult(
                Result.failure(
                    IllegalStateException("Provisioning QR decoder is closed.")
                )
            )
            return
        }

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
        val task = runCatching {
            scanner.process(inputImage)
        }.getOrElse { error ->
            imageProxy.close()
            onResult(Result.failure(error))
            return
        }

        task.addOnSuccessListener { barcodes ->
            val rawValue = barcodes
                .asSequence()
                .mapNotNull(Barcode::getRawValue)
                .firstOrNull(String::isNotBlank)
            imageProxy.close()
            onResult(Result.success(rawValue))
        }.addOnFailureListener { error ->
            imageProxy.close()
            onResult(Result.failure(error))
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            scanner.close()
        }
    }
}
