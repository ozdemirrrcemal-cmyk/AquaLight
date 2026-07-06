package com.aqua.aqualight.base

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class BaseActivity : AppCompatActivity() {

    enum class SnackType {
        NORMAL,
        SUCCESS,
        ERROR,
        WARNING
    }

    private val activityJob =
    SupervisorJob()

    protected val uiScope: CoroutineScope =
    CoroutineScope(
        Dispatchers.Main.immediate + activityJob
    )

    private var loadingDialog: Dialog? = null
    private var loadingLogo: ImageView? = null
    private var activeInfoDialog: Dialog? = null

    private val loadingOwners: MutableSet<String> =
        linkedSetOf()

    private val legacyLoadingOwner =
        "${BaseActivity::class.java.name}.LegacyLoadingOwner"

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )
    }

    fun setGlobalLoading(
        owner: Any,
        show: Boolean
    ) {
        setGlobalLoading(
            ownerKey = owner.toLoadingOwnerKey(),
            show = show
        )
    }

    fun setGlobalLoading(
        ownerKey: String,
        show: Boolean
    ) {
        val normalizedOwnerKey =
            ownerKey.trim()
                .ifBlank {
                    legacyLoadingOwner
                }

        val changed =
            if (show) {
                loadingOwners.add(
                    normalizedOwnerKey
                )
            } else {
                loadingOwners.remove(
                    normalizedOwnerKey
                )
            }

        if (!changed) {
            return
        }

        if (loadingOwners.isNotEmpty()) {
            showLoadingDialog()
        } else {
            hideLoadingDialog()
        }
    }

    fun clearGlobalLoading(
        owner: Any
    ) {
        clearGlobalLoading(
            ownerKey = owner.toLoadingOwnerKey()
        )
    }

    fun clearGlobalLoading(
        ownerKey: String
    ) {
        setGlobalLoading(
            ownerKey = ownerKey,
            show = false
        )
    }

    fun showLoading(
        show: Boolean
    ) {
        setGlobalLoading(
            ownerKey = legacyLoadingOwner,
            show = show
        )
    }

    fun showDeviceOfflineDialog(
        deviceTitle: String,
        message: String
    ) {
        if (
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        val safeTitle = deviceTitle.trim()
            .ifBlank {
                DEFAULT_DEVICE_TITLE
            }

        val safeMessage = message.trim()
            .ifBlank {
                DEFAULT_DEVICE_OFFLINE_MESSAGE
            }

        activeInfoDialog?.dismiss()

        activeInfoDialog = MaterialAlertDialogBuilder(this)
            .setTitle(DEVICE_OFFLINE_DIALOG_TITLE)
            .setMessage("$safeTitle is offline right now.\n\n$safeMessage")
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .also { dialog ->
                dialog.setOnDismissListener {
                    if (activeInfoDialog == dialog) {
                        activeInfoDialog = null
                    }
                }
                dialog.show()
            }
    }

    private fun Any.toLoadingOwnerKey(): String {
        return "${this::class.java.name}@${System.identityHashCode(this)}"
    }

    private fun showLoadingDialog() {
        if (
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        if (loadingDialog?.isShowing == true) {
            return
        }

        val dialog = Dialog(
            this,
            android.R.style.Theme_Translucent_NoTitleBar
        ).apply {
            requestWindowFeature(
                Window.FEATURE_NO_TITLE
            )

            setCancelable(
                false
            )

            setCanceledOnTouchOutside(
                false
            )
        }

        val overlay = LayoutInflater.from(this)
        .inflate(
            R.layout.loading_overlay,
            null,
            false
        ) as FrameLayout

        overlay.visibility = View.VISIBLE
        overlay.isClickable = true
        overlay.isFocusable = true

        val logo = overlay.findViewById<ImageView>(
            R.id.loadingLogo
        )

        dialog.setContentView(
            overlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        loadingDialog = dialog
        loadingLogo = logo

        dialog.show()

        dialog.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            setBackgroundDrawable(
                ColorDrawable(
                    Color.TRANSPARENT
                )
            )

            clearFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )

            setDimAmount(
                0f
            )

            attributes = attributes.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                windowAnimations = 0
            }
        }

        val anim = AnimationUtils.loadAnimation(
            this,
            R.anim.rotate_pulse_logo
        )

        logo.startAnimation(
            anim
        )
    }

    private fun hideLoadingDialog() {
        loadingLogo?.clearAnimation()

        loadingDialog?.dismiss()

        loadingDialog = null
        loadingLogo = null
    }

    fun showSnackBar(
        message: String,
        type: SnackType = SnackType.NORMAL
    ) {
        val root =
        findViewById<View>(
            android.R.id.content
        ) ?: return

        val snackbar =
        Snackbar.make(
            root,
            message,
            Snackbar.LENGTH_LONG
        )

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

        val params =
        snackView.layoutParams

        if (params is ViewGroup.MarginLayoutParams) {
            params.setMargins(
                24,
                0,
                24,
                24
            )

            snackView.layoutParams =
            params
        }

        snackView.elevation = 8f

        snackView.background =
        ContextCompat.getDrawable(
            this,
            R.drawable.bg_snackbar
        )

        snackbar.show()
    }

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

        loadingOwners.clear()

        activeInfoDialog?.dismiss()
        activeInfoDialog = null

        hideLoadingDialog()

        super.onDestroy()
    }

    private companion object {
        const val DEVICE_OFFLINE_DIALOG_TITLE = "Device Offline"
        const val DEFAULT_DEVICE_TITLE = "Device"
        const val DEFAULT_DEVICE_OFFLINE_MESSAGE = "Make sure it is powered on and connected to the same Wi-Fi network."
    }
}
