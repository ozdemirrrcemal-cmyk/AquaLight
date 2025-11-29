package com.aqua.aqualight.ui.main

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.ActivityMainBinding
import com.aqua.aqualight.lan.LanMonitor

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_START_IN_APP = "EXTRA_START_IN_APP"
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔹 Uygulama açılır açılmaz LAN monitörü başlat
        val userPrefs = UserPreferencesManager.create(applicationContext)
        LanMonitor.start(applicationContext, userPrefs)

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        val startInApp = intent.getBooleanExtra(EXTRA_START_IN_APP, false)

        if (savedInstanceState == null) {
            // 🔹 Uygulama ilk açılış: login olmuşsa root graph'in startDestination'ını nav_app yap
            binding.navHost.isVisible = false
            binding.bottomNav.isVisible = false

            val graph = navController.navInflater.inflate(R.navigation.nav_root).apply {
                setStartDestination(
                    if (startInApp) R.id.nav_app
                    else R.id.authContainerFragment
                )
            }
            navController.graph = graph
        }

        setupBottomBar(navController)

        // İlk görünürlük state'i (recreate dahil)
        navController.currentDestination?.let { dest ->
            binding.bottomNav.isVisible = isInAppDest(dest.id)
        }

        // Artık host'u göster
        binding.navHost.isVisible = true
    }

    // App içi tab ekranlarını temsil eden id'ler
    private fun isInAppDest(destinationId: Int): Boolean {
        return when (destinationId) {
            R.id.aquariumFragment,
            R.id.devicesFragment,
            R.id.settingsFragment -> true
            else -> false
        }
    }

    private fun setupBottomBar(navController: NavController) {
        // Bottom bar ↔ nav bağla
        binding.bottomNav.setupWithNavController(navController)

        // Destination değişince görünürlüğü güncelle
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible = isInAppDest(destination.id)
        }
    }
}