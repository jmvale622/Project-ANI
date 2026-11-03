package com.dysrupit.ani

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.dysrupit.ani.model.*
import com.dysrupit.ani.utils.HistoryItem
import com.dysrupit.ani.utils.HistoryResponse
import com.dysrupit.ani.utils.AuthManager
import com.dysrupit.ani.utils.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SharedViewModel : ViewModel() {

    private val _history = MutableLiveData<List<HistoryItem>>()
    val history: LiveData<List<HistoryItem>> = _history

    private val _alerts = MutableLiveData<List<AlertItem>>().apply {
        value = getDemoAlertList()
    }
    val alerts: LiveData<List<AlertItem>> = _alerts

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun fetchHistory(context: Context) {
        val accessToken = AuthManager.getAccessToken(context) ?: return
        _isLoading.value = true
        RetrofitClient.instance.getHistory("Bearer $accessToken", 20).enqueue(object : Callback<HistoryResponse> {
            override fun onResponse(call: Call<HistoryResponse>, response: Response<HistoryResponse>) {
                _isLoading.value = false
                if (response.isSuccessful) {
                    _history.value = response.body()?.history ?: emptyList()
                }
            }
            override fun onFailure(call: Call<HistoryResponse>, t: Throwable) {
                _isLoading.value = false
            }
        })
    }

    fun fetchAlerts(location: String = "Batangas", crop: String = "Rice") {
        _isLoading.value = true
        val targetLocation = if (location.isNotBlank()) location else "Batangas"

        RetrofitClient.instance.getWeatherAdvisory(targetLocation, crop).enqueue(object : Callback<WeatherAdvisoryResponse> {
            override fun onResponse(call: Call<WeatherAdvisoryResponse>, response: Response<WeatherAdvisoryResponse>) {
                _isLoading.value = false
                if (response.isSuccessful && response.body() != null) {
                    try {
                        val mapped = mapResponseToAlerts(response.body()!!)
                        _alerts.value = if (mapped.isNotEmpty()) mapped else getDemoAlertList(targetLocation)
                    } catch (e: Exception) {
                        _alerts.value = getDemoAlertList(targetLocation)
                    }
                } else {
                    _alerts.value = getDemoAlertList(targetLocation)
                }
            }
            override fun onFailure(call: Call<WeatherAdvisoryResponse>, t: Throwable) {
                _isLoading.value = false
                _alerts.value = getDemoAlertList(targetLocation)
            }
        })
    }

    private fun mapResponseToAlerts(res: WeatherAdvisoryResponse): List<AlertItem> {
        val list = mutableListOf<AlertItem>()

        // 1. Map Cyclone Advisory
        res.cycloneAdvisory?.text?.let { text ->
            if (text.isNotBlank() && text.lowercase() != "no active cyclone advisory") {
                list.add(AlertItem(
                    id = "cyclone",
                    type = "Typhoon",
                    title = "Tropical Cyclone Alert",
                    description = text,
                    signal = if (text.contains("Signal", ignoreCase = true)) "ACTIVE" else null,
                    timestamp = "PAGASA BULLETIN"
                ))
            }
        }

        // 2. Map Weather Advisory
        val advisoryNum = res.weatherAdvisory?.advisoryNumber
        val fullText = res.weatherAdvisory?.fullText
        if (!advisoryNum.isNullOrEmpty()) {
            list.add(AlertItem(
                id = "weather",
                type = "Typhoon",
                title = advisoryNum,
                description = fullText?.take(250) ?: "",
                timestamp = "WEATHER ADVISORY"
            ))
        }

        // 3. Map Impact Assessment
        res.impactAssessment?.impactText?.let { text ->
            if (text.isNotEmpty()) {
                list.add(AlertItem(
                    id = "impact",
                    type = "Disease",
                    title = "Agricultural Impact Assessment",
                    description = text,
                    timestamp = "AGRI-WEATHER"
                ))
            }
        }

        // 4. Map Farm Forecast
        res.farmForecast?.farmText?.let { text ->
            if (text.isNotEmpty()) {
                list.add(AlertItem(
                    id = "farm",
                    type = "Disease",
                    title = "Farm Weather Forecast",
                    description = text,
                    timestamp = "FARM BULLETIN"
                ))
            }
        }

        return if (list.isEmpty()) getDemoAlertList() else list
    }

    private fun getDemoAlertList(location: String = "Batangas"): List<AlertItem> {
        val loc = if (location.isNotBlank()) location else "Batangas"
        return listOf(
            AlertItem(
                id = "1",
                type = "Typhoon",
                title = "TC Dalisay approaching $loc",
                description = "Expected winds: 90–100 kph. Harvest ripe fruit and secure livestock before it arrives within 36 hours.",
                signal = "SIGNAL NO. 2",
                timestamp = "PAGASA bulletin · 2 hours ago"
            ),
            AlertItem(
                id = "2",
                type = "Outbreak",
                title = "Fall Armyworm — 3 plots malapit sa'yo",
                description = "Confirmed outbreak in Brgy. San Isidro. Check your corn over the next 2–3 days.",
                signal = null,
                timestamp = "Yesterday, 6:40 AM"
            ),
            AlertItem(
                id = "3",
                type = "Disease",
                title = "Rice Blast detected in $loc",
                description = "6 neighboring farms have confirmed cases this week.",
                signal = null,
                timestamp = "3 days ago"
            )
        )
    }
}
