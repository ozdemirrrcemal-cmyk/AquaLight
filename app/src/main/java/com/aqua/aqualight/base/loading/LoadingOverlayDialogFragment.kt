package com.aqua.aqualight.base.loading

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Full-screen, non-cancelable loading renderer owned by FragmentManager. */
class LoadingOverlayDialogFragment : DialogFragment(R.layout.loading_overlay) {

    private var logo: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clearPending(parentFragmentManager, this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.visibility = View.VISIBLE
        view.isClickable = true
        view.isFocusable = true
        logo = view.findViewById<ImageView>(R.id.loadingLogo).also { image ->
            image.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.rotate_pulse_logo)
            )
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.aqua_color_transparent)))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
            attributes = attributes.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                windowAnimations = 0
            }
        }
    }

    override fun onDestroyView() {
        logo?.clearAnimation()
        logo = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "LoadingOverlayDialogFragment"

        private val pendingOverlays =
            WeakHashMap<FragmentManager, WeakReference<LoadingOverlayDialogFragment>>()

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            if (pendingOverlay(fragmentManager) != null) return

            val overlay = LoadingOverlayDialogFragment()
            pendingOverlays[fragmentManager] = WeakReference(overlay)

            try {
                overlay.show(fragmentManager, TAG)
            } catch (error: RuntimeException) {
                clearPending(fragmentManager, overlay)
                throw error
            }
        }

        fun hide(fragmentManager: FragmentManager) {
            val visibleOverlay =
                fragmentManager.findFragmentByTag(TAG) as? LoadingOverlayDialogFragment
            val pendingOverlay = pendingOverlay(fragmentManager)

            pendingOverlay?.let { clearPending(fragmentManager, it) }
            visibleOverlay?.dismissAllowingStateLoss()
            if (pendingOverlay != null && pendingOverlay !== visibleOverlay) {
                pendingOverlay.dismissAllowingStateLoss()
            }
        }

        private fun pendingOverlay(
            fragmentManager: FragmentManager
        ): LoadingOverlayDialogFragment? {
            val overlay = pendingOverlays[fragmentManager]?.get()
            if (overlay == null) pendingOverlays.remove(fragmentManager)
            return overlay
        }

        private fun clearPending(
            fragmentManager: FragmentManager,
            overlay: LoadingOverlayDialogFragment
        ) {
            if (pendingOverlays[fragmentManager]?.get() === overlay) {
                pendingOverlays.remove(fragmentManager)
            }
        }
    }
}
