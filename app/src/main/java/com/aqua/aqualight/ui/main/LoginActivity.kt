package com.aqua.aqualight.ui.main

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.material.button.MaterialButton
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderScriptBlur
import android.view.ViewGroup

class LoginActivity : BaseActivity() {

    private lateinit var videoBackground: PlayerView
    private lateinit var blurView: BlurView
    private lateinit var imgLogo: ImageView
    private lateinit var tvAquaLight: TextView
    private lateinit var tvNatureAquarium: TextView
    private lateinit var btnGoogleLogin: MaterialButton
    private lateinit var btnSignIn: MaterialButton
    private lateinit var btnRegister: MaterialButton
    private lateinit var buttonContainer: ConstraintLayout

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔳 Tam ekran görünüm
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        setContentView(R.layout.activity_login)

        // 🎯 ID bağlantıları
        videoBackground = findViewById(R.id.videoBackground)
        blurView = findViewById(R.id.blurView)
        imgLogo = findViewById(R.id.imgLogo)
        tvAquaLight = findViewById(R.id.tvAquaLight)
        tvNatureAquarium = findViewById(R.id.tvNatureAquarium)
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnRegister = findViewById(R.id.btnRegister)
        buttonContainer = findViewById(R.id.buttonContainer)

        setupVideoBackground()
        setupBlur()
    }

    private fun setupVideoBackground() {
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            videoBackground.player = exoPlayer
            val videoUri = "android.resource://${packageName}/${R.raw.aquarium}"
            val mediaItem = MediaItem.fromUri(videoUri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ALL
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
        }
    }

    private fun setupBlur() {
        val radius = 18f
        val decorView: View = window.decorView
        val rootView: ViewGroup = decorView.findViewById(android.R.id.content)
        val windowBackground = decorView.background

        blurView.setupWith(rootView)
            .setFrameClearDrawable(windowBackground)
            .setBlurAlgorithm(RenderScriptBlur(this))
            .setBlurRadius(radius)
            .setBlurAutoUpdate(true)
            .setHasFixedTransformationMatrix(true)
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}