package com.aqua.aqualight.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.ui.main.MainActivity

class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 🎞️ Logo animasyonu
        val logo: ImageView = findViewById(R.id.logoImage)
        val anim = AnimationUtils.loadAnimation(this, R.anim.logo_fade_scale)
        logo.startAnimation(anim)

        // 2.4 sn sonra tek hedef: MainActivity (auth akışı onun içinde)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(
                Intent(this@SplashActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
            overridePendingTransition(0, 0)
            finish()
        }, 2400)
    }
}