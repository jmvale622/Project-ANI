package com.dysrupit.ani

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dysrupit.ani.utils.LanguageManager

class FileClaimActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_claim)
        
        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        val spinnerCause = findViewById<Spinner>(R.id.spinnerCause)
        val causes = arrayOf("Typhoon — strong wind", "Flooding", "Insects", "Disease")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, causes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCause.adapter = adapter

        findViewById<Button>(R.id.btnSubmitClaim).setOnClickListener {
            Toast.makeText(this, "Claim submitted successfully!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
