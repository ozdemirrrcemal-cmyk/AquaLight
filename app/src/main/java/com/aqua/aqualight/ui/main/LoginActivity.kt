package com.aqua.aqualight.ui.main

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.material.button.MaterialButton
import eightbitlab.com.blurview.BlurView

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    }
}