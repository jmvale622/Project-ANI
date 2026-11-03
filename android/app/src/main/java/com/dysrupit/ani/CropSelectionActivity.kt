package com.dysrupit.ani

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dysrupit.ani.utils.LanguageManager

class CropSelectionActivity : AppCompatActivity() {

    private var scanMode: String = "disease"
    private var selectedCrop: String = "Rice" // Rice, Corn, Sugarcane

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uriStr = result.data?.getStringExtra("image_uri")
            if (uriStr != null) {
                val intent = Intent(this, AnalysisActivity::class.java).apply {
                    putExtra("image_uri", uriStr)
                    putExtra("scan_mode", scanMode)
                    putExtra("selected_crop", selectedCrop)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_selection)

        scanMode = intent.getStringExtra("scan_mode") ?: "disease"

        val btnBack = findViewById<View>(R.id.btnBack)
        val cardRice = findViewById<View>(R.id.cardRice)
        val cardCorn = findViewById<View>(R.id.cardCorn)
        val cardSugarcane = findViewById<View>(R.id.cardSugarcane)

        val rbRice = findViewById<RadioButton>(R.id.rbRice)
        val rbCorn = findViewById<RadioButton>(R.id.rbCorn)
        val rbSugarcane = findViewById<RadioButton>(R.id.rbSugarcane)

        val btnContinue = findViewById<View>(R.id.btnContinueToCamera)

        btnBack.setOnClickListener { finish() }

        fun clearSelections() {
            rbRice.isChecked = false
            rbCorn.isChecked = false
            rbSugarcane.isChecked = false

            cardRice.setBackgroundResource(R.drawable.bg_scan_card_unselected)
            cardCorn.setBackgroundResource(R.drawable.bg_scan_card_unselected)
            cardSugarcane.setBackgroundResource(R.drawable.bg_scan_card_unselected)
        }

        cardRice.setOnClickListener {
            clearSelections()
            selectedCrop = "Rice"
            rbRice.isChecked = true
            cardRice.setBackgroundResource(R.drawable.bg_scan_card_selected)
        }

        cardCorn.setOnClickListener {
            clearSelections()
            selectedCrop = "Corn"
            rbCorn.isChecked = true
            cardCorn.setBackgroundResource(R.drawable.bg_scan_card_selected)
        }

        cardSugarcane.setOnClickListener {
            clearSelections()
            selectedCrop = "Sugarcane"
            rbSugarcane.isChecked = true
            cardSugarcane.setBackgroundResource(R.drawable.bg_scan_card_selected)
        }

        btnContinue.setOnClickListener {
            val intent = Intent(this, CaptureActivity::class.java).apply {
                putExtra("scan_mode", scanMode)
                putExtra("selected_crop", selectedCrop)
            }
            captureLauncher.launch(intent)
        }
    }
}
