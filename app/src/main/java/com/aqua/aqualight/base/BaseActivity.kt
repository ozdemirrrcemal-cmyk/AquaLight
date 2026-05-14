package com.aqua.aqualight.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class BaseActivity : AppCompatActivity() {

    // ---------------------------------------------------
    // SNACKBAR TYPES
    // ---------------------------------------------------

    enum class SnackType {
        NORMAL,
        SUCCESS,
        ERROR,
        WARNING
    }

    // ---------------------------------------------------
    // COROUTINE SCOPE
    // ---------------------------------------------------

    private val activityJob =
        SupervisorJob()

    protected val uiScope: CoroutineScope =
        CoroutineScope(
            Dispatchers.Main.immediate + activityJob
        )

    // ---------------------------------------------------
    // LOADING OVERLAY
    // ---------------------------------------------------

    private var loadingOverlay: FrameLayout? =
        null

    private var loadingLogo: ImageView? =
        null

    // ---------------------------------------------------
    // ON CREATE
    // ---------------------------------------------------

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)
    }

    // ---------------------------------------------------
    // CONTENT CHANGED
    // ---------------------------------------------------

    override fun onContentChanged() {

        super.onContentChanged()

        ensureLoadingOverlay()
    }

    // ---------------------------------------------------
    // ENSURE LOADING OVERLAY
    // ---------------------------------------------------

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

    // ---------------------------------------------------
    // SHOW LOADING
    // ---------------------------------------------------

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

    // ---------------------------------------------------
    // GLOBAL SNACKBAR
    // ---------------------------------------------------

    fun showSnackBar(
        message: String,
        type: SnackType = SnackType.NORMAL
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

        // ---------------------------------------------------
        // COLORS
        // ---------------------------------------------------

        val backgroundColor =
            when (type) {

                SnackType.SUCCESS -> {

                    ContextCompat.getColor(
                        this,
                        R.color.snackbar_success
                    )
                }

                SnackType.ERROR -> {

                    ContextCompat.getColor(
                        this,
                        R.color.snackbar_error
                    )
                }

                SnackType.WARNING -> {

                    ContextCompat.getColor(
                        this,
                        R.color.snackbar_warning
                    )
                }

                SnackType.NORMAL -> {

                    ContextCompat.getColor(
                        this,
                        R.color.aqua_button_blue
                    )
                }
            }

        snackbar.setBackgroundTint(
            backgroundColor
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

        // ---------------------------------------------------
        // TEXT VIEW
        // ---------------------------------------------------

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

        // ---------------------------------------------------
        // MARGINS
        // ---------------------------------------------------

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

        // ---------------------------------------------------
        // ELEVATION
        // ---------------------------------------------------

        snackView.elevation = 8f

        // ---------------------------------------------------
        // BACKGROUND
        // ---------------------------------------------------

        snackView.background =
            ContextCompat.getDrawable(
                this,
                R.drawable.bg_snackbar
            )

        snackbar.show()
    }

    // ---------------------------------------------------
    // ERROR LOG
    // ---------------------------------------------------

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

    // ---------------------------------------------------
    // DESTROY
    // ---------------------------------------------------

    override fun onDestroy() {

        activityJob.cancel()

        loadingLogo?.clearAnimation()

        loadingLogo = null

        loadingOverlay = null

        super.onDestroy()
    }
}