package com.aqua.aqualight.ui.auth

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.aqua.aqualight.R
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.StyledPlayerView
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur

class AuthContainerFragment : Fragment() {

    private var playerView: StyledPlayerView? = null
    private var blurView: BlurView? = null
    private var posterImage: ImageView? = null

    private var player: ExoPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_auth_container, container, false)

        playerView = v.findViewById(R.id.videoBackground)
        blurView = v.findViewById(R.id.blurView)
        posterImage = v.findViewById(R.id.posterImage)

        // geri tuşu davranışı
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val childNavHost =
                childFragmentManager.findFragmentById(R.id.auth_nav_host) as? NavHostFragment
            val current = childNavHost?.navController?.currentDestination?.id
            if (current == R.id.loginFragment) requireActivity().finish()
            else childNavHost?.navController?.popBackStack()
        }

        return v
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
        super.onDestroyView()
        releasePlayer()
        playerView = null
        blurView = null
        posterImage = null
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
                        // video hazır olduğunda posteri gizle
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
        // fragment yeniden açılırsa posteri tekrar göster
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
                            ?: ColorDrawable(Color.TRANSPARENT)
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
                val cm = ColorMatrix().apply { setSaturation(0.9f) }
                val cf = ColorMatrixColorFilter(cm)
                val effect = RenderEffect.createColorFilterEffect(cf)
                playerView?.setRenderEffect(effect)
            } catch (_: Exception) {
                // cihaz desteklemiyorsa yoksay
            }
        }
    }
}