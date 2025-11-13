package com.aqua.aqualight.base

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aqua.aqualight.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class BaseActivity : AppCompatActivity() {

    // 🔹 Activity yaşam döngüsüne bağlı coroutine scope
    private val activityJob = SupervisorJob()
    protected val uiScope: CoroutineScope =
        CoroutineScope(Dispatchers.Main.immediate + activityJob)

    // 🔹 Loading overlay öğeleri
    private var loadingOverlay: FrameLayout? = null
    private var loadingLogo: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fullscreen burada otomatik verilmesin
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        ensureLoadingOverlay()
    }

    override fun setContentView(view: View) {
        super.setContentView(view)
        ensureLoadingOverlay()
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        super.setContentView(view, params)
        ensureLoadingOverlay()
    }

    /**
     * 🧱 Loading overlay sadece bir kere eklenir (tekrarlanmaz)
     */
    private fun ensureLoadingOverlay() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        if (loadingOverlay == null) {
            val overlay = LayoutInflater.from(this)
                .inflate(R.layout.loading_overlay, rootView, false) as FrameLayout
            rootView.addView(overlay)
            loadingOverlay = overlay
            loadingLogo = overlay.findViewById(R.id.loadingLogo)
        }
    }

    /**
     * ⚡ Loading ekranını göster veya gizle
     */
    fun showLoading(show: Boolean) {
        val overlay = loadingOverlay ?: return
        val logo = loadingLogo ?: return

        if (show) {
            if (overlay.visibility != View.VISIBLE) {
                overlay.visibility = View.VISIBLE
                val anim = AnimationUtils.loadAnimation(this, R.anim.rotate_pulse_logo)
                logo.startAnimation(anim)
            }
        } else {
            logo.clearAnimation()
            overlay.visibility = View.GONE
        }
    }

    /**
     * 🌈 Tam ekran immersive (edge-to-edge) görünüm kontrolü
     * fullscreen = true ➜ sistem çubukları gizli
     * fullscreen = false ➜ sistem çubukları görünür
     */
    protected fun setFullscreen(fullscreen: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, !fullscreen)
            val controller = WindowInsetsControllerCompat(window, window.decorView)

            if (fullscreen) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(
                    WindowInsetsCompat.Type.statusBars() or
                            WindowInsetsCompat.Type.navigationBars()
                )
            } else {
                controller.show(
                    WindowInsetsCompat.Type.statusBars() or
                            WindowInsetsCompat.Type.navigationBars()
                )
            }
        } else {
            @Suppress("DEPRECATION")
            if (fullscreen) {
                window.decorView.systemUiVisibility =
                    (View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            } else {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    /**
     * 🪶 Hata loglama
     */
    protected fun logError(tag: String, message: String?, throwable: Throwable? = null) {
        android.util.Log.e(tag, message, throwable)
    }

    override fun onDestroy() {
        activityJob.cancel()
        super.onDestroy()
    }
}