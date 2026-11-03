package com.dysrupit.ani

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class TreatmentPlanActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_treatment_plan)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }
}
