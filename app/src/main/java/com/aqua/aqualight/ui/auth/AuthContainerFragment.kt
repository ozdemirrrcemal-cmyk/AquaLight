package com.aqua.aqualight.ui.auth

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.aqua.aqualight.R
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur

@OptIn(UnstableApi::class) // setVideoTextureView / clearVideoTextureView için
class AuthContainerFragment : Fragment() {

    private var textureView: TextureView? = null
    private var blurView: BlurView? = null
    private var posterImage: ImageView? = null   // 🐟 Poster overlay

    private var player: ExoPlayer? = null
    private var videoWidth = 0
    private var videoHeight = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_auth_container, container, false)
        textureView = v.findViewById(R.id.videoBackground)
        blurView = v.findViewById(R.id.blurView)
        posterImage = v.findViewById(R.id.posterImage)

        // geri davranışı
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val childNavHost =
                childFragmentManager.findFragmentById(R.id.auth_nav_host) as? NavHostFragment
            val current = childNavHost?.navController?.currentDestination?.id
            if (current == R.id.loginFragment) requireActivity().finish()
            else childNavHost?.navController?.popBackStack()
        }
        return v
    }

    // ▶️ ExoPlayer lifecycle (Media3)
    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 24) initPlayerIfNeeded()
    }

    override fun onResume() {
        super.onResume()

        // Player yoksa veya eski cihazsa yeniden kur
        if (Build.VERSION.SDK_INT < 24 || player == null) {
            initPlayerIfNeeded()
        }

        // 🔥 Google Sign-In'den / başka ekrandan dönünce videoyu tekrar oynat
        player?.playWhenReady = true

        applyBlurIfSupported()
        applyDesaturationIfSupported()
    }

    override fun onPause() {
        super.onPause()
        // 🔹 Artık burada release yok; sadece pause
        player?.playWhenReady = false
    }

    override fun onStop() {
        super.onStop()
        // 🔹 Ekrandan tamamen çıkarken player'ı bırak
        if (Build.VERSION.SDK_INT >= 24) releasePlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
        textureView = null
        blurView = null
        posterImage = null
    }

    private fun initPlayerIfNeeded() {
        if (player != null || textureView == null) return
        try {
            val exo = ExoPlayer.Builder(requireContext()).build().also { exo ->
                exo.setVideoTextureView(textureView) // Media3
                exo.repeatMode = Player.REPEAT_MODE_ONE

                exo.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        videoWidth = videoSize.width
                        videoHeight = videoSize.height
                        val w = textureView?.width ?: 0
                        val h = textureView?.height ?: 0
                        if (w > 0 && h > 0) scaleVideoToFillScreen(w, h)
                    }

                    override fun onRenderedFirstFrame() {
                        // 🎬 Video ilk kareyi çizdiği an posteri gizle
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

            textureView?.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val w = v.width
                val h = v.height
                if (w > 0 && h > 0 && videoWidth > 0 && videoHeight > 0) {
                    scaleVideoToFillScreen(w, h)
                }
            }
        } catch (_: Exception) {
            releasePlayer() // sessiz düş
        }
    }

    private fun releasePlayer() {
        try {
            player?.run {
                // siyah ekran/Surface leak riskini azalt
                textureView?.let { clearVideoTextureView(it) }
                playWhenReady = false
                release()
            }
        } finally {
            player = null
            videoWidth = 0
            videoHeight = 0
            // Fragment tekrar açılırsa poster başta tekrar görünsün
            posterImage?.visibility = View.VISIBLE
        }
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
                textureView?.setRenderEffect(effect)
            } catch (_: Exception) {
                // cihaz desteklemiyorsa yoksay
            }
        }
    }

    private fun scaleVideoToFillScreen(viewWidth: Int, viewHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return
        val scaleX = viewWidth.toFloat() / videoWidth
        val scaleY = viewHeight.toFloat() / videoHeight
        val scale = maxOf(scaleX, scaleY)
        val dx = (viewWidth - scale * videoWidth) / 2f
        val dy = (viewHeight - scale * videoHeight) / 2f
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        textureView?.setTransform(m)
    }
}