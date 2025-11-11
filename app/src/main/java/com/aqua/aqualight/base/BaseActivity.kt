package com.aqua.aqualight.base

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

open class BaseActivity : AppCompatActivity() {

    // Ortak coroutine scope (ör: DataStore veya network için)
    protected val uiScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
    }

    /**
     * 🔹 Ortak sistem çubuğu gizleme / immersive mode
     */
    protected fun setupSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    /**
     * 🔹 Ortak hata loglama (ileride FirebaseCrashlytics eklenebilir)
     */
    protected fun logError(tag: String, message: String?, throwable: Throwable? = null) {
        android.util.Log.e(tag, message, throwable)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.coroutineContext.cancel()
    }
}