package com.dysrupit.ani

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class PondoFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pondo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnOpenLoan).setOnClickListener {
            startActivity(Intent(requireContext(), AniLoanActivity::class.java))
        }

        view.findViewById<View>(R.id.btnOpenProtect).setOnClickListener {
            startActivity(Intent(requireContext(), AniProtectActivity::class.java))
        }

        view.findViewById<View>(R.id.itemActiveLoan).setOnClickListener {
            // Already implemented logic for loan repayment schedule view could go here
            // For now, following the specific detail screen from Image 4
            startActivity(Intent(requireContext(), AniLoanActivity::class.java))
        }

        view.findViewById<View>(R.id.itemInsuranceClaim).setOnClickListener {
            startActivity(Intent(requireContext(), FileClaimActivity::class.java))
        }
    }
}
