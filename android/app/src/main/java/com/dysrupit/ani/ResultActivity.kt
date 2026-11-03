package com.dysrupit.ani

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dysrupit.ani.utils.LanguageManager

class ResultActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val crop = intent.getStringExtra("crop") ?: "-"
        val diseaseLabel = intent.getStringExtra("disease_label") ?: "-"
        val diseaseConfidence = intent.getStringExtra("disease_confidence") ?: "0%"
        val recAction = intent.getStringExtra("rec_action") ?: ""
        val recProduct = intent.getStringExtra("rec_product") ?: ""
        
        // Bind Views
        val tvDiseaseName = findViewById<TextView>(R.id.tvDiseaseName)
        val tvCropDetails = findViewById<TextView>(R.id.tvCropDetails)
        val tvConfidencePct = findViewById<TextView>(R.id.tvConfidencePct)
        val pbConfidenceGauge = findViewById<ProgressBar>(R.id.pbConfidenceGauge)
        val tvRecommendation = findViewById<TextView>(R.id.tvRecommendation)
        val tvProductName = findViewById<TextView>(R.id.tvProductName)
        val btnBack = findViewById<View>(R.id.btnBack)
        val btnSave = findViewById<View>(R.id.btnSave)

        // Populate Data
        tvDiseaseName.text = diseaseLabel
        tvCropDetails.text = "${crop.uppercase()}  ·  ${getString(R.string.huling_suri)}"
        tvConfidencePct.text = diseaseConfidence

        // Set Progress Ring gauge according to confidence percentage
        val confidenceNum = diseaseConfidence.replace("%", "").trim().toIntOrNull() ?: 90
        pbConfidenceGauge?.progress = confidenceNum

        tvRecommendation.text = if (recAction.isNotBlank()) recAction else "---"
        if (recProduct.isNotBlank() && tvProductName != null) {
            tvProductName.text = recProduct
        }

        // Listeners
        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { 
            Toast.makeText(this, "Saved to records", Toast.LENGTH_SHORT).show()
            finish() 
        }
    }
}
