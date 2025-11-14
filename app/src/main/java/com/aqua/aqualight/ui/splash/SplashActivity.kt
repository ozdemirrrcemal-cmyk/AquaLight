package com.aqua.aqualight.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.ui.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : BaseActivity() {

    private val userPrefs by lazy { UserPreferencesManager.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 🎞️ Logo animasyonu
        val logo: ImageView = findViewById(R.id.logoImage)
        val anim = AnimationUtils.loadAnimation(this, R.anim.logo_fade_scale)
        logo.startAnimation(anim)

        // 🔹 Hem login durumunu oku, hem de 2.4 sn splash beklet
        lifecycleScope.launch {
            val prefs = try {
                userPrefs.userPrefsFlow.first()
            } catch (_: Exception) {
                null
            }

            val loggedIn = prefs?.let {
                it.isLoggedIn && it.idToken.isNotEmpty()
            } ?: false

            // Splash süresi
            delay(2400L)

            // MainActivity'yi login durumuna göre bilgilendir
            val intent = Intent(this@SplashActivity, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_START_IN_APP, loggedIn)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }
}