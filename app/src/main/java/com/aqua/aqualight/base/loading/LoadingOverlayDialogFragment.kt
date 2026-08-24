package com.aqua.aqualight.base.loading

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.debug.dosing.DosingDebugOverlayView
import com.aqua.aqualight.debug.dosing.DosingDebugTrace
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
            window?.applyLoadingOverlayWindowContract()
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

        if (DosingDebugOverlayView.enabled(requireContext())) {
            val root = view as? FrameLayout
            if (root != null) {
                val overlay = DosingDebugOverlayView(requireContext())
                root.addView(
                    overlay,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP
                    )
                )
                overlay.bringToFront()
                viewLifecycleOwner.lifecycleScope.launch {
                    DosingDebugTrace.lines.collect(overlay::submit)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.applyLoadingOverlayWindowContract()
    }

    override fun onDestroyView() {
        logo?.clearAnimation()
        logo = null
        super.onDestroyView()
    }

    private fun Window.applyLoadingOverlayWindowContract() {
        WindowCompat.setDecorFitsSystemWindows(this, false)
        addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundDrawable(
            ColorDrawable(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_color_transparent
                )
            )
        )
        clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setDimAmount(0f)
        attributes = attributes.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            windowAnimations = 0
        }
        mirrorHostSystemBarAppearance()
    }

    @Suppress("DEPRECATION")
    private fun Window.mirrorHostSystemBarAppearance() {
        val hostWindow = requireActivity().window

        statusBarColor = hostWindow.statusBarColor
        navigationBarColor = hostWindow.navigationBarColor

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            navigationBarDividerColor = hostWindow.navigationBarDividerColor
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isStatusBarContrastEnforced = hostWindow.isStatusBarContrastEnforced
            isNavigationBarContrastEnforced = hostWindow.isNavigationBarContrastEnforced
        }

        val hostController = WindowCompat.getInsetsController(
            hostWindow,
            hostWindow.decorView
        )
        val overlayController = WindowCompat.getInsetsController(this, decorView)

        overlayController.isAppearanceLightStatusBars =
            hostController.isAppearanceLightStatusBars
        overlayController.isAppearanceLightNavigationBars =
            hostController.isAppearanceLightNavigationBars
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
