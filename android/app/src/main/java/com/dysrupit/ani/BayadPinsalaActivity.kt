package com.dysrupit.ani

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dysrupit.ani.utils.LanguageManager

class BayadPinsalaActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bayad_pinsala)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Localize dynamic status texts
        findViewById<TextView>(R.id.tvPayoutStatus).text = getString(R.string.payout_status_gcash, "Jul 12")
        findViewById<TextView>(R.id.tvSatelliteStatus).text = getString(R.string.damage_confirmed, "Jul 10")
        findViewById<TextView>(R.id.tvTriggerStatus).text = getString(R.string.threshold_reached, "Jul 10")
        findViewById<TextView>(R.id.tvClaimStatus).text = getString(R.string.no_claim_form, "Jul 12")
    }
}
