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

    // Activity’ye özel coroutine scope
    private val activityJob = SupervisorJob()
    protected val uiScope: CoroutineScope =
        CoroutineScope(Dispatchers.Main.immediate + activityJob)

    // Loading overlay referansları
    private var loadingOverlay: FrameLayout? = null
    private var loadingLogo: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // System bars henüz oluşmadı, burada immersive mode uygulanmaz.
    }

    // İçerik yüklendikten hemen sonra immersive mode uygula + loading overlay ekle
    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        attachLoadingOverlay()
        applyEdgeToEdge()
    }

    override fun setContentView(view: View) {
        super.setContentView(view)
        attachLoadingOverlay()
        applyEdgeToEdge()
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        super.setContentView(view, params)
        attachLoadingOverlay()
        applyEdgeToEdge()
    }

    /**
     * Loading overlay layout’unu activity köküne ekler (tek seferlik)
     */
    private fun attachLoadingOverlay() {
        if (loadingOverlay == null) {
            val rootView = findViewById<ViewGroup>(android.R.id.content)
            val overlay = LayoutInflater.from(this)
                .inflate(R.layout.loading_overlay, rootView, false) as FrameLayout
            rootView.addView(overlay)
            loadingOverlay = overlay
            loadingLogo = overlay.findViewById(R.id.loadingLogo)
        }
    }

    /**
     * Ekranda dönen logo ile loading overlay’i gösterir veya gizler
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
     * Tam ekran immersive mode uygular (status/navigation bar gizli)
     */
    protected fun applyEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
            )
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

    protected fun logError(tag: String, message: String?, throwable: Throwable? = null) {
        android.util.Log.e(tag, message, throwable)
    }

    override fun onDestroy() {
        activityJob.cancel()
        super.onDestroy()
    }
}