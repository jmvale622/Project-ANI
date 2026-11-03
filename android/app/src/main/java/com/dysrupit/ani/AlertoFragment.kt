package com.dysrupit.ani

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dysrupit.ani.adapter.AlertAdapter

class AlertoFragment : Fragment() {

    private val viewModel: SharedViewModel by activityViewModels()
    private lateinit var rvAlerts: RecyclerView
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_alerto, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvAlerts = view.findViewById(R.id.rvAlerts)
        progressBar = view.findViewById(R.id.progressBar)
        
        rvAlerts.layoutManager = LinearLayoutManager(requireContext())

        // Initial set from ViewModel
        rvAlerts.adapter = AlertAdapter(viewModel.alerts.value ?: emptyList())

        viewModel.alerts.observe(viewLifecycleOwner) { alerts ->
            if (alerts != null) {
                rvAlerts.adapter = AlertAdapter(alerts)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        // Trigger fetch
        val user = com.dysrupit.ani.utils.AuthManager.getCurrentUser(requireContext())
        viewModel.fetchAlerts(location = user?.address ?: "Batangas", crop = "Rice")
    }
}
