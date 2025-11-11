package com.aqua.aqualight.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.ui.main.MainActivity
import com.aqua.aqualight.ui.main.MainLoginActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : BaseActivity() {

    private val userPrefs by lazy { UserPreferencesManager.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 🎞️ Logo animasyonu başlat
        val logo: ImageView = findViewById(R.id.logoImage)
        val anim = AnimationUtils.loadAnimation(this, R.anim.logo_fade_scale)
        logo.startAnimation(anim)

        // ⚡ Giriş kontrolü
        lifecycleScope.launch {
            // 1️⃣ DataStore'dan giriş durumunu oku
            val prefs = userPrefs.userPrefsFlow.first()
            val isLoggedIn = prefs.isLoggedIn && prefs.idToken.isNotEmpty()

            // 2️⃣ 2.4 saniye sonra uygun ekrana yönlendir
            Handler(Looper.getMainLooper()).postDelayed({
                if (isLoggedIn) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                } else {
                    startActivity(Intent(this@SplashActivity, MainLoginActivity::class.java))
                }
                finish()
            }, 2400)
        }
    }
}