package com.aqua.aqualight.ui.main

import android.os.Bundle
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

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        if (savedInstanceState == null) {
            // 🔹 Uygulama ilk açılış: login durumuna göre grafiği *bir kez* ayarla
            binding.navHost.isVisible = false
            binding.bottomNav.isVisible = false

            lifecycleScope.launch {
                val loggedIn = try {
                    val prefs = userPrefs.userPrefsFlow.first()
                    prefs.isLoggedIn && prefs.idToken.isNotEmpty()
                } catch (_: Exception) {
                    // prefs okunamazsa fail-safe: login ekranına gönder
                    false
                }

                // nav_root'u inflate edip startDestination'ı override et
                val graph = navController.navInflater.inflate(R.navigation.nav_root).apply {
                    setStartDestination(
                        if (loggedIn) R.id.nav_app
                        else R.id.authContainerFragment
                    )
                }
                navController.graph = graph

                setupBottomBar(navController)

                // 🔹 Şu anki destination'a göre alt bar görünürlüğünü ayarla
                navController.currentDestination?.let { dest ->
                    binding.bottomNav.isVisible = isInAppDest(dest.id)
                }

                // Her şey hazır, artık kullanıcıya göster
                binding.navHost.isVisible = true
            }
        } else {
            // 🔹 Tema değişimi / rotate sonrası:
            // Navigation kendi graph + backstack'ini restore ediyor, biz elle dokunmuyoruz
            setupBottomBar(navController)

            // Mevcut destination'a göre alt bar'ı ayarla
            navController.currentDestination?.let { dest ->
                binding.bottomNav.isVisible = isInAppDest(dest.id)
            }

            binding.navHost.isVisible = true
        }
    }

    // 🔹 Bu ID'ler app içindeki tab'leri temsil ediyor
    private fun isInAppDest(destinationId: Int): Boolean {
        return when (destinationId) {
            R.id.nav_app,              // nested graph
            R.id.aquariumFragment,
            R.id.devicesFragment,
            R.id.settingsFragment -> true
            else -> false
        }
    }

    private fun setupBottomBar(navController: NavController) {
        // Bottom bar ↔ nav bağla
        binding.bottomNav.setupWithNavController(navController)

        // Her destination değiştiğinde görünürlüğü güncelle
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible = isInAppDest(destination.id)
        }
    }
}