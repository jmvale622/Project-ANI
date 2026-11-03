package com.dysrupit.ani

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.dysrupit.ani.utils.LanguageManager

class AniProtectActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aniprotect_detail)
        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnApplyInsurance).setOnClickListener {
            startActivity(Intent(this, FileClaimActivity::class.java))
        }
    }
}
