package com.aqua.aqualight.ui.main

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
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

class LoginActivity : BaseActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var blurView: BlurView
    private lateinit var player: ExoPlayer

    private lateinit var imgLogo: ImageView
    private lateinit var tvAquaLight: TextView
    private lateinit var tvNatureAquarium: TextView
    private lateinit var btnGoogleLogin: MaterialButton
    private lateinit var btnSignIn: MaterialButton
    private lateinit var btnRegister: MaterialButton
    private lateinit var buttonContainer: ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 🧱 ID eşleştirmeleri
        playerView = findViewById(R.id.videoBackground)
        blurView = findViewById(R.id.blurView)
        imgLogo = findViewById(R.id.imgLogo)
        tvAquaLight = findViewById(R.id.tvAquaLight)
        tvNatureAquarium = findViewById(R.id.tvNatureAquarium)
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnRegister = findViewById(R.id.btnRegister)
        buttonContainer = findViewById(R.id.buttonContainer)

        setupFullScreen()
        setupVideoBackground()
        setupBlurView()
    }

    // 🎬 ExoPlayer video arka plan
    private fun setupVideoBackground() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        playerView.useController = false
        playerView.keepScreenOn = true

        val mediaItem = MediaItem.fromUri("android.resource://${packageName}/${R.raw.aquarium}")
        player.setMediaItem(mediaItem)
        player.repeatMode = ExoPlayer.REPEAT_MODE_ALL
        player.prepare()
        player.playWhenReady = true
    }

    // 🧊 BlurView - yüksek performanslı bulanık arka plan
    private fun setupBlurView() {
        val radius = 15f
        val decorView = window.decorView
        val rootView = decorView.findViewById<ViewGroup>(android.R.id.content)
        val windowBackground = decorView.background

        blurView.setupWith(rootView)
            .setFrameClearDrawable(windowBackground)
            .setBlurAlgorithm(RenderScriptBlur(this))
            .setBlurRadius(radius)
            .setBlurAutoUpdate(true)
            .setHasFixedTransformationMatrix(true)
    }

    // 🔳 Tam ekran immersive görünüm
    private fun setupFullScreen() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}