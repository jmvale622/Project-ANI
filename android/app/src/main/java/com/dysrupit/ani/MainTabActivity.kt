package com.dysrupit.ani

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.dysrupit.ani.utils.LanguageManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainTabActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_tab)

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(ScanFragment())
                    true
                }
                R.id.nav_history -> {
                    loadFragment(HistoryFragment())
                    true
                }
                R.id.nav_alerto -> {
                    loadFragment(AlertoFragment())
                    true
                }
                R.id.nav_pondo -> {
                    loadFragment(PondoFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }

        // Default or target fragment selection
        if (intent.getStringExtra("open_tab") == "profile") {
            bottomNavigation.selectedItemId = R.id.nav_profile
        } else if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.nav_home
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
