package com.aqua.aqualight.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.ui.main.MainActivity

/**
 * Visual handoff only. Authentication and owner runtime startup are coordinated
 * exclusively by MainActivity through AppSessionCoordinator.
 */
class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo: ImageView = findViewById(R.id.logoImage)
        logo.startAnimation(
            AnimationUtils.loadAnimation(
                this,
                R.anim.logo_fade_scale
            )
        )

        logo.post {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
            )
            overridePendingTransition(0, 0)
            finish()
        }
    }
}
