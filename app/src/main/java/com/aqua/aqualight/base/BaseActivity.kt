package com.aqua.aqualight.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
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
        // Tema ve sistem bar renklerini tamamen theme.xml’den yöneteceğiz
    }

    /**
     * setContentView her çağrıldığında Android otomatik olarak
     * burayı tetikler. Böylece tüm override karmaşasından kurtuluyoruz.
     */
    override fun onContentChanged() {
        super.onContentChanged()
        ensureLoadingOverlay()
    }

    /**
     * 🧱 Loading overlay sadece bir kere eklenir (tekrarlanmaz)
     */
    private fun ensureLoadingOverlay() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        if (loadingOverlay == null && rootView != null) {
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