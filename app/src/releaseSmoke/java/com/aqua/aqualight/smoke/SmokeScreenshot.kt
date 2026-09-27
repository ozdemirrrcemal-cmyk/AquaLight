package com.aqua.aqualight.smoke

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream

internal fun android.app.Activity.captureSmokeScreen(name: String, smokeProfile: String) {
        val root = window.decorView.rootView
        check(root.width > 0 && root.height > 0) {
            "$name has invalid render bounds ${root.width}x${root.height}"
        }
        val bitmap = createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val screenshotRoot = getExternalFilesDir(null) ?: filesDir
        val directory = File(screenshotRoot, "smoke-screens").apply { mkdirs() }
        val output = File(
            directory,
            "$smokeProfile-${name.removeSuffix("Fragment").lowercase()}.png"
        )
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)) {
                "$name screenshot could not be encoded"
            }
        }
        check(output.isFile && output.length() > MIN_SCREENSHOT_BYTES) {
            "$name screenshot is empty"
        }
        bitmap.recycle()
    }


private const val MIN_SCREENSHOT_BYTES = 1024L

private const val PNG_QUALITY = 100
