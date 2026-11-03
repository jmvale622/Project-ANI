package com.dysrupit.ani

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dysrupit.ani.utils.LanguageManager

class AniLoanActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aniloan_detail)
        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }
    }
}
