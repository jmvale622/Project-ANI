package com.dysrupit.ani

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import com.dysrupit.ani.utils.LanguageManager

class NewScanActivity : AppCompatActivity() {

    private var selectedScanMode = "disease" // default: "disease" or "pest"

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_scan)

        val btnBack = findViewById<View>(R.id.btnBack)
        val cardDisease = findViewById<View>(R.id.cardPhoneCamera)
        val cardPest = findViewById<View>(R.id.cardPestClassification)
        val rbDisease = findViewById<RadioButton>(R.id.rbPhoneCamera)
        val rbPest = findViewById<RadioButton>(R.id.rbPestClassification)
        val btnContinue = findViewById<View>(R.id.btnContinue)

        btnBack.setOnClickListener { finish() }

        cardDisease.setOnClickListener {
            selectedScanMode = "disease"
            rbDisease.isChecked = true
            rbPest.isChecked = false
            cardDisease.setBackgroundResource(R.drawable.bg_scan_card_selected)
            cardPest.setBackgroundResource(R.drawable.bg_scan_card_unselected)
        }

        cardPest.setOnClickListener {
            selectedScanMode = "pest"
            rbDisease.isChecked = false
            rbPest.isChecked = true
            cardDisease.setBackgroundResource(R.drawable.bg_scan_card_unselected)
            cardPest.setBackgroundResource(R.drawable.bg_scan_card_selected)
        }

        btnContinue.setOnClickListener {
            val intent = Intent(this, CropSelectionActivity::class.java).apply {
                putExtra("scan_mode", selectedScanMode)
            }
            startActivity(intent)
            finish()
        }
    }
}
