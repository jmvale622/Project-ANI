package com.dysrupit.ani.utils

import android.content.Context
import android.net.Uri
import com.dysrupit.ani.BuildConfig
import com.dysrupit.ani.model.WeatherAdvisoryResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Query
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

// Response Models
data class PredictionResponse(
    val crop: String,
    val status: String,
    val overall_accuracy: String,
    val detections: Detections,
    val recommendations: Recommendations
)

data class Detections(
    val disease: DiseaseDetection
)

data class DiseaseDetection(
    val label: String,
    val confidence: String
)

data class Recommendations(
    val disease: DiseaseRecommendation,
    val general_advice: String
)

data class DiseaseRecommendation(
    val action: String,
    val urgency: String,
    val product: String
)

data class HistoryItem(
    val scan_id: String,
    val user_id: String,
    val crop: String,
    val disease: String,
    val status: String,
    val confidence: String,
    val timestamp: String
)

data class HistoryResponse(
    val history: List<HistoryItem>
)

data class RecentScan(
    val crop: String,
    val disease: String,
    val status: String
)

data class AuthUser(
    val userId: String,
    val fullName: String,
    val contact: String,
    val address: String,
    val plots: String,
    val hectares: String,
    val crops: String
)

data class AuthApiResponse(
    val access_token: String,
    val token_type: String,
    val user: AuthUser
)

data class SignUpRequest(
    val fullName: String,
    val contact: String,
    val address: String,
    val password: String,
    val plots: String,
    val hectares: String,
    val crops: String
)

data class SignInRequest(
    val contact: String,
    val password: String
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

// API Interface
interface PredictionApiService {
    @retrofit2.http.POST("auth/signup")
    suspend fun signUp(@Body request: SignUpRequest): Response<AuthApiResponse>

    @retrofit2.http.POST("auth/signin")
    suspend fun signIn(@Body request: SignInRequest): Response<AuthApiResponse>

    @retrofit2.http.POST("auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") authorization: String,
        @Body request: ChangePasswordRequest
    ): Response<Unit>

    @Multipart
    @POST("predict")
    fun uploadImage(
        @Part image: MultipartBody.Part,
        @Header("Authorization") authorization: String,
        @Query("scan_mode") scanMode: String,
        @Query("selected_crop") selectedCrop: String
    ): Call<PredictionResponse>

    @retrofit2.http.GET("history")
    fun getHistory(
        @Header("Authorization") authorization: String,
        @retrofit2.http.Query("limit") limit: Int
    ): Call<HistoryResponse>

    @retrofit2.http.GET("history/{scan_id}/image")
    fun getScanImage(
        @Header("Authorization") authorization: String,
        @retrofit2.http.Path("scan_id") scanId: String
    ): Call<okhttp3.ResponseBody>

    @retrofit2.http.GET("weather-advisory")
    fun getWeatherAdvisory(
        @retrofit2.http.Query("location") location: String,
        @retrofit2.http.Query("crop") crop: String
    ): Call<WeatherAdvisoryResponse>
}

// Retrofit instance
object RetrofitClient {
    val instance: PredictionApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.ANI_API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(PredictionApiService::class.java)
    }
}

fun isNetworkConnected(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

suspend fun uploadImageForPrediction(
    context: Context,
    imageUri: Uri,
    scanMode: String = "disease",
    selectedCrop: String = "Rice"
): PredictionResponse = withContext(Dispatchers.IO) {
    val file = getFileFromUri(context, imageUri)
    try {
        val accessToken = AuthManager.getAccessToken(context)
            ?: throw IOException("Sign in to save and classify a scan.")
        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val response = RetrofitClient.instance.uploadImage(
            image = body,
            authorization = "Bearer $accessToken",
            scanMode = scanMode,
            selectedCrop = selectedCrop
        ).execute()

        if (response.isSuccessful) {
            response.body() ?: throw IOException("Prediction API returned an empty response.")
        } else {
            val errorBody = response.errorBody()?.string()
            val detail = errorBody?.let(::readErrorDetail)
            val noPredictionReason = NoPredictionReason.values()
                .firstOrNull { it.name == detail }
            if (noPredictionReason != null) {
                throw NoSuitablePredictionException(noPredictionReason)
            }
            throw IOException(
                "Prediction API returned HTTP ${response.code()}: " +
                        (detail ?: response.message())
            )
        }
    } finally {
        file.delete()
    }
}

private fun getFileFromUri(context: Context, uri: Uri): File {
    val file = File.createTempFile("ani_upload_", ".jpg", context.cacheDir)
    var copied = false
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("The selected image could not be opened.")
        inputStream.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        copied = true
    } finally {
        if (!copied) file.delete()
    }
    return file
}

private fun readErrorDetail(errorBody: String): String? {
    return try {
        JSONObject(errorBody).optString("detail").takeIf { it.isNotBlank() }
    } catch (_: JSONException) {
        errorBody.takeIf { it.isNotBlank() }
    }
}
