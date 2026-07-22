package com.aqua.aqualight.ui.auth

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.fragment.NavHostFragment
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.main.AquaAppShellLayout
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur

class AuthContainerFragment : Fragment() {

    private var playerView: PlayerView? = null
    private var blurView: BlurView? = null
    private var posterImage: ImageView? = null
    private var safeContent: View? = null
    private var topSystemBarScrim: View? = null
    private var bottomSystemBarScrim: View? = null
    private var appShell: AquaAppShellLayout? = null

    private var player: ExoPlayer? = null

    private val safeDrawingTypes =
        WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_auth_container, container, false)

        playerView = root.findViewById(R.id.videoBackground)
        blurView = root.findViewById(R.id.blurView)
        posterImage = root.findViewById(R.id.posterImage)
        safeContent = root.findViewById(R.id.authSafeContent)
        topSystemBarScrim = root.findViewById(R.id.authTopSystemBarScrim)
        bottomSystemBarScrim = root.findViewById(R.id.authBottomSystemBarScrim)

        appShell = requireActivity().findViewById(R.id.appShell)
        appShell?.setContentDrawsBehindSystemBars(true)
        installSafeContentInsets(root)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val childNavHost =
                childFragmentManager.findFragmentById(R.id.auth_nav_host) as? NavHostFragment
            val current = childNavHost?.navController?.currentDestination?.id
            if (current == R.id.loginFragment) requireActivity().finish()
            else childNavHost?.navController?.popBackStack()
        }

        return root
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 24) initPlayerIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < 24 || player == null) {
            initPlayerIfNeeded()
        }

        player?.playWhenReady = true

        applyBlurIfSupported()
        applyDesaturationIfSupported()
    }

    override fun onPause() {
        player?.playWhenReady = false
        super.onPause()
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= 24) releasePlayer()
        super.onStop()
    }

    override fun onDestroyView() {
        releasePlayer()
        appShell?.setContentDrawsBehindSystemBars(false)

        playerView = null
        blurView = null
        posterImage = null
        safeContent = null
        topSystemBarScrim = null
        bottomSystemBarScrim = null
        appShell = null

        super.onDestroyView()
    }

    private fun installSafeContentInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val safeDrawingInsets = windowInsets.getInsets(safeDrawingTypes)
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            topSystemBarScrim.setInsetScrimHeight(safeDrawingInsets.top)
            bottomSystemBarScrim.setInsetScrimHeight(safeDrawingInsets.bottom)

            safeContent?.updatePadding(
                left = safeDrawingInsets.left,
                top = safeDrawingInsets.top,
                right = safeDrawingInsets.right,
                bottom = maxOf(
                    safeDrawingInsets.bottom,
                    imeInsets.bottom
                )
            )

            // The auth container is the sole inset owner for its interactive foreground. Child
            // login/register screens therefore remain simple and cannot accidentally double-inset.
            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(
                    safeDrawingTypes,
                    Insets.NONE
                )
                .setInsets(
                    WindowInsetsCompat.Type.ime(),
                    Insets.NONE
                )
                .build()
        }

        root.doOnAttach { attachedRoot ->
            ViewCompat.requestApplyInsets(attachedRoot)
        }
    }

    private fun View?.setInsetScrimHeight(insetHeight: Int) {
        val scrim = this ?: return
        val safeHeight = insetHeight.coerceAtLeast(0)
        val currentLayoutParams = scrim.layoutParams

        if (currentLayoutParams.height != safeHeight) {
            currentLayoutParams.height = safeHeight
            scrim.layoutParams = currentLayoutParams
        }
        scrim.visibility = if (safeHeight > 0) View.VISIBLE else View.GONE
    }

    private fun initPlayerIfNeeded() {
        if (player != null || playerView == null) return

        try {
            val exo = ExoPlayer.Builder(requireContext()).build().also { exo ->
                playerView?.player = exo
                playerView?.useController = false

                exo.repeatMode = Player.REPEAT_MODE_ONE
                exo.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        posterImage?.visibility = View.GONE
                    }
                })

                val uri = Uri.parse(
                    "android.resource://${requireContext().packageName}/${R.raw.aquarium}"
                )
                exo.setMediaItem(MediaItem.fromUri(uri))
                exo.volume = 0f
                exo.prepare()
                exo.playWhenReady = true
            }

            player = exo
        } catch (_: Exception) {
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        playerView?.player = null
        player?.release()
        player = null
        posterImage?.visibility = View.VISIBLE
    }

    private fun applyBlurIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val root = view as? ViewGroup ?: return
                blurView?.visibility = View.VISIBLE
                blurView?.setupWith(root, RenderEffectBlur())
                    ?.setFrameClearDrawable(
                        requireActivity().window.decorView.background
                            ?: ColorDrawable(
                                ContextCompat.getColor(
                                    requireContext(),
                                    R.color.aqua_color_transparent
                                )
                            )
                    )
                    ?.setBlurRadius(22f)
            } catch (_: Exception) {
                blurView?.visibility = View.GONE
            }
        } else {
            blurView?.visibility = View.GONE
        }
    }

    private fun applyDesaturationIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val colorMatrix = ColorMatrix().apply { setSaturation(0.9f) }
                val colorFilter = ColorMatrixColorFilter(colorMatrix)
                val effect = RenderEffect.createColorFilterEffect(colorFilter)
                playerView?.setRenderEffect(effect)
            } catch (_: Exception) {
                // Unsupported vendor implementations keep the unfiltered video.
            }
        }
    }
}
