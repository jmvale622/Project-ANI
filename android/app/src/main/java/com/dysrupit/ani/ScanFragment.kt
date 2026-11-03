package com.dysrupit.ani

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.dysrupit.ani.utils.AuthManager

class ScanFragment : Fragment() {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvGreeting: TextView
    
    private val viewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progressBar)
        tvGreeting = view.findViewById(R.id.tvGreeting)

        // Localized Greeting
        val user = AuthManager.getCurrentUser(requireContext())
        if (user != null) {
            tvGreeting.text = getString(R.string.hello_user, user.fullName)
        } else {
            tvGreeting.text = getString(R.string.welcome)
        }

        view.findViewById<View>(R.id.btnCamera).setOnClickListener { 
            val intent = Intent(requireContext(), NewScanActivity::class.java)
            startActivity(intent)
        }

        val alertBanner = view.findViewById<View>(R.id.bannerAlert)
        alertBanner.isClickable = true
        alertBanner.setOnClickListener {
            val intent = Intent(requireContext(), BayadPinsalaActivity::class.java)
            startActivity(intent)
        }

        // Localized History observation
        viewModel.history.observe(viewLifecycleOwner) { items ->
            if (items.isNotEmpty()) {
                val lastItem = items.first()
                view.findViewById<TextView>(R.id.tvLastDisease).text = "${lastItem.disease} — ${lastItem.crop}"
                
                val confidenceStr = getString(R.string.tiwala_label, "${lastItem.confidence}%")
                view.findViewById<TextView>(R.id.tvLastDetails).text = "$confidenceStr · ${lastItem.timestamp}"
                
                view.findViewById<View>(R.id.cardLastScan).setOnClickListener {
                    val intent = Intent(requireContext(), ResultActivity::class.java).apply {
                        putExtra("crop", lastItem.crop)
                        putExtra("status", lastItem.status)
                        putExtra("overall_accuracy", "${lastItem.confidence}%")
                        putExtra("disease_label", lastItem.disease)
                        putExtra("disease_confidence", "${lastItem.confidence}%")
                    }
                    startActivity(intent)
                }
            }
        }

        if (viewModel.history.value == null) {
            viewModel.fetchHistory(requireContext())
        }
    }
}
