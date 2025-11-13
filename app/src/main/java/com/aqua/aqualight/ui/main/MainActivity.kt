package com.aqua.aqualight.ui.main

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
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

        // Ufak flicker’ı azalt
        binding.navHost.visibility = View.INVISIBLE

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        if (savedInstanceState == null) {
            // 🔹 Uygulama ilk açılış: startDestination'ı login durumuna göre ayarla
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

                setupBottomBar(navController)
                binding.navHost.visibility = View.VISIBLE
            }
        } else {
            // 🔹 Tema değişimi / rotate sonrası:
            // Navigation kendi graph + backstack’ini restore ediyor, dokunmuyoruz
            setupBottomBar(navController)
            binding.navHost.visibility = View.VISIBLE
        }
    }

    private fun setupBottomBar(navController: NavController) {
        // Bottom bar ↔ nav bağla
        binding.bottomNav.setupWithNavController(navController)

        // Sadece nav_app hiyerarşisindeyken alt bar gözüksün
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val inApp = generateSequence(destination) { it.parent }
                .any { it.id == R.id.nav_app }
            binding.bottomNav.isVisible = inApp
        }

        // İlk state’i tetikle (recreate sonrası)
        navController.currentDestination?.let { dest ->
            val inApp = generateSequence(dest) { it.parent }
                .any { it.id == R.id.nav_app }
            binding.bottomNav.isVisible = inApp
        }
    }
}