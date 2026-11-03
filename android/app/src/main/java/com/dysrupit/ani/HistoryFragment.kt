package com.dysrupit.ani

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dysrupit.ani.utils.HistoryItem

class HistoryFragment : Fragment() {

    private lateinit var rvTalaan: RecyclerView
    private val viewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvTalaan = view.findViewById(R.id.rvTalaan)
        rvTalaan.layoutManager = LinearLayoutManager(requireContext())

        viewModel.history.observe(viewLifecycleOwner) { items ->
            rvTalaan.adapter = TalaanAdapter(items)
        }

        if (viewModel.history.value == null) {
            viewModel.fetchHistory(requireContext())
        }
    }

    inner class TalaanAdapter(private val items: List<HistoryItem>) : RecyclerView.Adapter<TalaanAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val leafColor: View = v.findViewById(R.id.ivLeafColor)
            val diagnosis: TextView = v.findViewById(R.id.tvDiagnosis)
            val timestamp: TextView = v.findViewById(R.id.tvTimestamp)
            val confidence: TextView = v.findViewById(R.id.tvConfidence)

            init {
                v.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val item = items[position]
                        val intent = Intent(requireContext(), ResultActivity::class.java).apply {
                            putExtra("crop", item.crop)
                            putExtra("status", item.status)
                            putExtra("overall_accuracy", "${item.confidence}%")
                            putExtra("disease_label", item.disease)
                            putExtra("disease_confidence", "${item.confidence}%")
                            putExtra("rec_action", "Consult an agriculturist for specific treatment.")
                            putExtra("general_advice", "Ensure proper irrigation and nutrient management.")
                        }
                        startActivity(intent)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_talaan_scan, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.diagnosis.text = getString(R.string.payment_item, item.disease, item.crop)
            holder.confidence.text = "${item.confidence}%"
            
            val displayTime = if (item.timestamp.contains("T")) {
                val parts = item.timestamp.split("T")
                if (parts.size > 1) {
                    val timeParts = parts[1].split(".")
                    "${parts[0]}, ${timeParts[0]}"
                } else parts[0]
            } else item.timestamp
            
            holder.timestamp.text = displayTime

            // Dynamic colors based on disease
            when {
                item.disease.contains("Anthracnose", true) -> holder.leafColor.setBackgroundColor(Color.parseColor("#BBA692"))
                item.disease.contains("Blight", true) -> holder.leafColor.setBackgroundColor(Color.parseColor("#5D4E3C"))
                else -> holder.leafColor.setBackgroundColor(Color.parseColor("#8D7E6B"))
            }
        }

        override fun getItemCount() = items.size
    }
}
