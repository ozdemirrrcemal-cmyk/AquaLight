package com.aqua.aqualight.ui.common.loading

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R

/** Full-screen, non-cancelable loading renderer owned by FragmentManager. */
class LoadingOverlayDialogFragment : DialogFragment(R.layout.loading_overlay) {

    private var logo: ImageView? = null

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
            image.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.rotate_pulse_logo))
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            LoadingOverlayDialogFragment().show(fragmentManager, TAG)
        }

        fun hide(fragmentManager: FragmentManager) {
            (fragmentManager.findFragmentByTag(TAG) as? DialogFragment)
                ?.dismissAllowingStateLoss()
        }
    }
}
