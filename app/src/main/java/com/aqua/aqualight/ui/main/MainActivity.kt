package com.aqua.aqualight.ui.main

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val userPrefs by lazy { UserPreferencesManager.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // İçerik hazır olana kadar NavHost'u gizle (ufak flicker'ı azaltır)
        binding.navHost.visibility = View.INVISIBLE

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val prefs = userPrefs.userPrefsFlow.first()
                val loggedIn = prefs.isLoggedIn && prefs.idToken.isNotEmpty()

                val graph = navController.navInflater.inflate(R.navigation.nav_root).apply {
                    setStartDestination(
                        if (loggedIn) R.id.nav_app
                        else R.id.authContainerFragment
                    )
                }
                navController.graph = graph

                navController.handleDeepLink(intent)

                binding.bottomNav.setupWithNavController(navController)
                hookUiForDestination(navController)

                binding.navHost.visibility = View.VISIBLE
            }
        } else {
            binding.bottomNav.setupWithNavController(navController)
            hookUiForDestination(navController)
            binding.navHost.visibility = View.VISIBLE
        }
    }

    private fun hookUiForDestination(navController: androidx.navigation.NavController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // 1) App içi tab'lerde bottom bar görünsün
            val inApp = generateSequence(destination) { it.parent }
                .any { it.id == R.id.nav_app }
            binding.bottomNav.isVisible = inApp

            // 2) Auth flow'da (AuthContainerFragment altı) tam ekran olsun
            val inAuthFlow = generateSequence(destination) { it.parent }
                .any { it.id == R.id.authContainerFragment }

            // 🔥 Login / Register / ResetPassword → fullscreen
            // 🔥 Aquarium / Devices / Settings → normal sistem çubuklu
            setFullscreen(inAuthFlow)
        }
    }
}