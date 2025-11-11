// 🌐 Bu sayfa: MainLoginActivity (BaseActivity’den türetilmiş)
package com.aqua.aqualight.ui.main

import android.content.Intent
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.eightbitlab.blurview.BlurView
import com.eightbitlab.blurview.RenderEffectBlur
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.VideoSize
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.launch

class MainLoginActivity : BaseActivity() {

    private lateinit var textureView: TextureView
    private lateinit var blurView: BlurView

    private var player: ExoPlayer? = null
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    // ✅ Şifreli DataStore yapısına uygun kullanım
    private val userPrefs by lazy { UserPreferencesManager.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_login)
        setupSystemBars() // BaseActivity’deki sistem UI ayarı

        textureView = findViewById(R.id.videoBackground)
        blurView = findViewById(R.id.blurView)

        // 🔹 Oturum kontrolü (şifreli DataStore’dan oku)
        observeUserSession()
    }

    private fun observeUserSession() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPrefs.userPrefsFlow.collect { prefs ->
                    val hasSession = prefs.isLoggedIn && prefs.idToken.isNotEmpty()
                    if (hasSession) {
                        startActivity(Intent(this@MainLoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        showFragment(LoginFragment())
                    }
                }
            }
        }
    }

    fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitAllowingStateLoss()
    }

    // ---- Lifecycle: ExoPlayer başlat / durdur ----
    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= 24) initPlayerIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (Util.SDK_INT < 24 || player == null) initPlayerIfNeeded()
        applyBlurIfSupported()
    }

    override fun onPause() {
        super.onPause()
        if (Util.SDK_INT < 24) {
            releasePlayer()
        } else {
            player?.playWhenReady = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT >= 24) releasePlayer()
    }

    // ---- ExoPlayer kurulumu ----
    private fun initPlayerIfNeeded() {
        if (player != null) return

        try {
            val exo = ExoPlayer.Builder(this).build().also {
                it.setVideoTextureView(textureView)

                it.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        videoWidth = videoSize.width
                        videoHeight = videoSize.height
                        val w = textureView.width
                        val h = textureView.height
                        if (w > 0 && h > 0) scaleVideoToFillScreen(w, h)
                    }
                })

                val uri = Uri.parse("android.resource://$packageName/${R.raw.aquarium}")
                val mediaItem = MediaItem.fromUri(uri)
                it.setMediaItem(mediaItem)
                it.repeatMode = Player.REPEAT_MODE_ALL
                it.volume = 0f
                it.prepare()
                it.playWhenReady = true
            }

            player = exo

            textureView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val w = v.width
                val h = v.height
                if (w > 0 && h > 0 && videoWidth > 0 && videoHeight > 0) {
                    scaleVideoToFillScreen(w, h)
                }
            }
        } catch (e: Exception) {
            logError("MainLoginActivity", "Video başlatma hatası: ${e.message}")
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        try {
            player?.run {
                playWhenReady = false
                release()
            }
        } catch (_: Exception) {
        } finally {
            player = null
            videoWidth = 0
            videoHeight = 0
        }
    }

    // ---- BlurView kurulumu ----
    private fun applyBlurIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val root = findViewById<View>(R.id.rootLayout) as ViewGroup
                blurView.visibility = View.VISIBLE
                blurView.setupWith(root)
                    .setFrameClearDrawable(window.decorView.background)
                    .setBlurAlgorithm(RenderEffectBlur())
                    .setBlurRadius(15f)
                    .setHasFixedTransformationMatrix(true)
            } catch (e: Exception) {
                blurView.visibility = View.GONE
                logError("MainLoginActivity", "BlurView hatası: ${e.message}")
            }
        } else {
            blurView.visibility = View.GONE
        }
    }

    // ---- Video’yu ekranı dolduracak şekilde ölçekle ----
    private fun scaleVideoToFillScreen(viewWidth: Int, viewHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return

        val scaleX = viewWidth.toFloat() / videoWidth
        val scaleY = viewHeight.toFloat() / videoHeight
        val scale = maxOf(scaleX, scaleY)

        val scaledWidth = scale * videoWidth
        val scaledHeight = scale * videoHeight
        val dx = (viewWidth - scaledWidth) / 2f
        val dy = (viewHeight - scaledHeight) / 2f

        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        textureView.setTransform(matrix)
    }
}