package com.aqua.aqualight.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class BaseActivity : AppCompatActivity() {

    // 🔹 Activity yaşam döngüsüne bağlı coroutine scope
    private val activityJob = SupervisorJob()

    protected val uiScope: CoroutineScope =
        CoroutineScope(
            Dispatchers.Main.immediate + activityJob
        )

    // 🔹 Loading overlay öğeleri
    private var loadingOverlay: FrameLayout? = null
    private var loadingLogo: ImageView? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        // Tema ve sistem bar renkleri
        // theme.xml tarafından yönetilir
    }

    /**
     * setContentView sonrası otomatik çalışır
     */
    override fun onContentChanged() {

        super.onContentChanged()

        ensureLoadingOverlay()
    }

    /**
     * 🧱 Loading overlay sadece 1 kere eklenir
     */
    private fun ensureLoadingOverlay() {

        val rootView =
            findViewById<ViewGroup>(
                android.R.id.content
            )

        if (
            loadingOverlay == null &&
            rootView != null
        ) {

            val overlay =
                LayoutInflater.from(this)
                    .inflate(
                        R.layout.loading_overlay,
                        rootView,
                        false
                    ) as FrameLayout

            rootView.addView(overlay)

            loadingOverlay = overlay

            loadingLogo =
                overlay.findViewById(
                    R.id.loadingLogo
                )
        }
    }

    /**
     * ⚡ Loading göster / gizle
     */
    fun showLoading(
        show: Boolean
    ) {

        val overlay =
            loadingOverlay ?: return

        val logo =
            loadingLogo ?: return

        if (show) {

            if (overlay.visibility != View.VISIBLE) {

                overlay.visibility =
                    View.VISIBLE

                val anim =
                    AnimationUtils.loadAnimation(
                        this,
                        R.anim.rotate_pulse_logo
                    )

                logo.startAnimation(anim)
            }

        } else {

            logo.clearAnimation()

            overlay.visibility =
                View.GONE
        }
    }

    /**
     * 🔔 Global Snackbar
     */
    fun showSnackBar(
        message: String
    ) {

        val root =
            findViewById<View>(
                android.R.id.content
            )

        val snackbar =
            Snackbar.make(
                root,
                message,
                Snackbar.LENGTH_LONG
            )

        snackbar.setBackgroundTint(
            ContextCompat.getColor(
                this,
                R.color.aqua_button_blue
            )
        )

        snackbar.setTextColor(
            ContextCompat.getColor(
                this,
                android.R.color.white
            )
        )

        snackbar.animationMode =
            Snackbar.ANIMATION_MODE_FADE

        val snackView =
            snackbar.view

        // 🔹 Snackbar TextView
        val textView =
            snackView.findViewById<TextView>(
                com.google.android.material.R.id.snackbar_text
            )

        textView.textSize = 15f

        textView.maxLines = 2

        textView.setPadding(
            0,
            0,
            0,
            0
        )

        // 🔹 Margin
        val params =
            snackView.layoutParams
                as FrameLayout.LayoutParams

        params.setMargins(
            24,
            0,
            24,
            24
        )

        snackView.layoutParams =
            params

        // 🔹 Elevation
        snackView.elevation = 8f

        // 🔹 Custom background
        snackView.background =
            ContextCompat.getDrawable(
                this,
                R.drawable.bg_snackbar
            )

        snackbar.show()
    }

    /**
     * 🪶 Hata loglama
     */
    protected fun logError(
        tag: String,
        message: String?,
        throwable: Throwable? = null
    ) {

        android.util.Log.e(
            tag,
            message,
            throwable
        )
    }

    override fun onDestroy() {

        activityJob.cancel()

        loadingLogo?.clearAnimation()

        loadingLogo = null

        loadingOverlay = null

        super.onDestroy()
    }
}