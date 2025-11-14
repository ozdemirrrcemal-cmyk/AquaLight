package com.aqua.aqualight.ui.main

import android.os.Bundle
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

        // Tema değişimi vb. durumlarda daha önce oluşturulmuş NavHost var mı?
        val existingHost =
            supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment?

        if (existingHost == null) {
            // 🔹 Uygulama ilk açılış (Splash'tan sonrası)
            binding.navHost.isVisible = false
            binding.bottomNav.isVisible = false

            lifecycleScope.launch {
                val loggedIn = try {
                    val prefs = userPrefs.userPrefsFlow.first()
                    prefs.isLoggedIn && prefs.idToken.isNotEmpty()
                } catch (_: Exception) {
                    false // prefs okunamazsa login'e gönder
                }

                // 1) NavHostFragment'i oluştur ve container'a ekle
                val navHost = NavHostFragment.create(R.navigation.nav_root)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_host, navHost)
                    .setPrimaryNavigationFragment(navHost) // defaultNavHost = true
                    .commitNow()

                val navController = navHost.navController

                // 2) Login olmuşsa startDestination'ı nav_app'e çek
                if (loggedIn) {
                    val graph = navController.navInflater.inflate(R.navigation.nav_root).apply {
                        setStartDestination(R.id.nav_app)
                    }
                    navController.graph = graph
                }

                // 3) Bottom bar ↔ nav bağla
                binding.bottomNav.setupWithNavController(navController)

                // 4) Login ise alt barı göster, değilse gizli kalsın
                binding.bottomNav.isVisible = loggedIn

                // 5) Artık her şey hazır, host'u göster
                binding.navHost.isVisible = true
            }
        } else {
            // 🔹 Tema değişimi / rotate sonrası:
            // NavHost + graph zaten restore edildi, sadece tekrar bağlan
            val navController = existingHost.navController

            // Bottom bar ↔ nav bağla (selection vs. için)
            binding.bottomNav.setupWithNavController(navController)

            // Login durumuna göre alt bar görünürlüğünü tekrar ayarla
            lifecycleScope.launch {
                val loggedIn = try {
                    val prefs = userPrefs.userPrefsFlow.first()
                    prefs.isLoggedIn && prefs.idToken.isNotEmpty()
                } catch (_: Exception) {
                    false
                }
                binding.bottomNav.isVisible = loggedIn
            }

            binding.navHost.isVisible = true
        }
    }
}