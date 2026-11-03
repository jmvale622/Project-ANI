package com.dysrupit.ani

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class DroneSurveyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drone_survey)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btnSaveRecord).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btnViewTreatment).setOnClickListener {
            startActivity(Intent(this, TreatmentPlanActivity::class.java))
        }
    }
}
