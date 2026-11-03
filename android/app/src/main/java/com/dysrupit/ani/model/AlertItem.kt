package com.dysrupit.ani.model

import com.google.gson.annotations.SerializedName

data class WeatherAdvisoryResponse(
    val timestamp: String? = null,
    val location: String? = null,
    val crop: String? = null,
    @SerializedName("daily_forecast") val dailyForecast: DailyForecastData? = null,
    @SerializedName("weather_advisory") val weatherAdvisory: WeatherAdvisoryData? = null,
    @SerializedName("cyclone_advisory") val cycloneAdvisory: CycloneAdvisoryData? = null,
    @SerializedName("farm_forecast") val farmForecast: FarmForecastData? = null,
    @SerializedName("impact_assessment") val impactAssessment: ImpactAssessmentData? = null
)

data class DailyForecastData(val source: String? = null, val synopsis: String? = null)
data class WeatherAdvisoryData(val source: String? = null, @SerializedName("advisory_number") val advisoryNumber: String? = null, @SerializedName("full_text") val fullText: String? = null)
data class CycloneAdvisoryData(val source: String? = null, val text: String? = null)
data class FarmForecastData(val source: String? = null, @SerializedName("farm_text") val farmText: String? = null)
data class ImpactAssessmentData(val source: String? = null, @SerializedName("impact_text") val impactText: String? = null)

data class AlertItem(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val signal: String? = null,
    val timestamp: String
)
