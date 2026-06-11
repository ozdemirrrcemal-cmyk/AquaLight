package com.aqua.aqualight.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.auth.AuthSessionManager
import com.aqua.aqualight.ui.main.MainActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : BaseActivity() {

    private val authSessionManager by lazy {
        AuthSessionManager.create(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 🎞️ Logo animasyonu
        val logo: ImageView = findViewById(R.id.logoImage)
        val anim = AnimationUtils.loadAnimation(this, R.anim.logo_fade_scale)
        logo.startAnimation(anim)

        lifecycleScope.launch {
            val sessionStateDeferred = async {
                runCatching {
                    authSessionManager.currentSessionState()
                }.getOrDefault(
                    AuthSessionManager.SessionState.Unauthenticated
                )
            }

            // Splash süresi
            delay(2400L)

            val loggedIn = sessionStateDeferred.await() is
                AuthSessionManager.SessionState.Authenticated

            // MainActivity tekrar kontrol eder. Bu extra sadece ilk graph tercihi için ipucudur.
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
