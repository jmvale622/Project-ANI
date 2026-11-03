package com.dysrupit.ani.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Runs on-device image classification and uses a local LiteRT-LM model for
 * recommendations when that optional model has been installed.
 */
object OfflineClassifier {

    suspend fun classifyOffline(
        context: Context,
        imageUri: Uri,
        scanMode: String = "disease",
        selectedCrop: String = "Rice"
    ): PredictionResponse = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                bitmap = BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val bitmapToClassify = bitmap
            ?: error("The selected image could not be decoded.")

        OnnxAniClassifier(context.applicationContext).use { classifier ->
            val prediction = classifier.classify(
                bitmapToClassify,
                scanMode,
                selectedCrop
            )
            val confidencePct = (prediction.confidence * 100).roundToInt().coerceIn(0, 100)
            val confidenceStr = "$confidencePct%"

            val detectedCrop = selectedCrop
            val fallbackAdvice = getRecommendationsForLabel(
                label = prediction.label,
                crop = detectedCrop,
                scanMode = scanMode
            )
            val offlineRecommendation = LiteRtLocalRecommendationEngine.recommend(
                context = context.applicationContext,
                crop = detectedCrop,
                diseaseLabel = prediction.label,
                confidence = confidencePct,
                language = LanguageManager.getLanguage(context)
            ) ?: Recommendations(
                disease = DiseaseRecommendation(
                    action = fallbackAdvice[0],
                    urgency = fallbackAdvice[1],
                    product = fallbackAdvice[2]
                ),
                general_advice = fallbackAdvice[3]
            )

            PredictionResponse(
                crop = detectedCrop,
                status = if (prediction.conditionType.equals("Health", true) || prediction.label.contains("Healthy", true)) "Healthy" else "Diseased",
                overall_accuracy = confidenceStr,
                detections = Detections(
                    disease = DiseaseDetection(
                        label = prediction.label,
                        confidence = confidenceStr
                    )
                ),
                recommendations = offlineRecommendation
            )
        }
    }

    // Synchronous helper wrapper for legacy non-coroutine callers
    fun classifyImage(
        context: Context,
        imageUri: Uri,
        scanMode: String = "disease",
        selectedCrop: String = "Rice"
    ): PredictionResponse {
        return kotlinx.coroutines.runBlocking {
            classifyOffline(context, imageUri, scanMode, selectedCrop)
        }
    }

    private fun getRecommendationsForLabel(
        label: String,
        crop: String,
        scanMode: String
    ): Array<String> {
        val labelLower = label.lowercase()
        return when {
            labelLower.contains("healthy") -> arrayOf(
                "No treatment necessary. Plant displays healthy chlorophyll and vigor.",
                "Low",
                "NPK 14-14-14 Balanced Fertilizer",
                "Maintain regular watering and weed control around plant roots."
            )
            labelLower.contains("blight") -> arrayOf(
                "Apply copper fungicide (Copper Oxychloride 50 WP) at 2g per litre. Drain standing water temporarily to stop waterborne spread.",
                "High",
                "Copper Oxychloride 50 WP",
                "Avoid excessive nitrogen application. Monitor nearby plots for yellow lesion margins."
            )
            labelLower.contains("armyworm") -> arrayOf(
                "Spray Emamectin Benzoate 5SG or Chlorantraniliprole directly into plant whorls in early morning.",
                "Critical",
                "Prevathon 5SC / Proclaim 5SG",
                "Inspect whorls daily for young larvae or fresh frass. Apply spray into whorls immediately."
            )
            labelLower.contains("leaf roller") -> arrayOf(
                "Use integrated pest management: remove badly rolled leaves and consult a local agricultural technician before applying a pesticide.",
                "Moderate",
                "Integrated pest management",
                "Inspect rice leaves for folded or rolled sections and larvae; monitor the field before deciding on treatment."
            )
            labelLower.contains("rice bug") -> arrayOf(
                "Monitor rice panicles and consult a local agricultural technician before applying a pesticide.",
                "Moderate",
                "Integrated pest management",
                "Check panicles during flowering and grain filling, and avoid unnecessary insecticide use that may harm beneficial insects."
            )
            labelLower.contains("stem borer") -> arrayOf(
                "Inspect rice stems for deadhearts or whiteheads and consult a local agricultural technician about locally approved control options.",
                "High",
                "Integrated pest management",
                "Monitor for stem borer symptoms and manage crop residues after harvest to reduce carryover."
            )
            labelLower.contains("leaf hopper") -> arrayOf(
                "Inspect sugarcane leaves and consult a local agricultural technician before applying a locally approved treatment.",
                "Moderate",
                "Integrated pest management",
                "Monitor leaf undersides and avoid unnecessary insecticide use that may harm beneficial insects."
            )
            labelLower.contains("root and base pest") -> arrayOf(
                "Inspect the sugarcane stool and plant base, and consult a local agricultural technician about locally approved control options.",
                "High",
                "Integrated pest management",
                "Check affected stools and field residues; avoid applying pesticides without confirming the pest and local guidance."
            )
            labelLower.contains("shoot borer") -> arrayOf(
                "Inspect young sugarcane shoots for deadhearts and consult a local agricultural technician about locally approved control options.",
                "High",
                "Integrated pest management",
                "Monitor emerging shoots and remove severely affected material where appropriate."
            )
            labelLower.contains("white sucking pest") -> arrayOf(
                "Inspect leaf undersides and consult a local agricultural technician before applying a locally approved treatment.",
                "Moderate",
                "Integrated pest management",
                "Monitor pest populations and protect beneficial insects by avoiding unnecessary broad-spectrum insecticides."
            )
            labelLower.contains("borer") -> arrayOf(
                "Apply Cartap Hydrochloride 50 SP or Chlorantraniliprole to suppress larvae before deadhearting.",
                "High",
                "Padan 50 SP / Prevathon 5SC",
                "Monitor fields for egg masses on leaf tips and whiteheads in rice panicles."
            )
            labelLower.contains("planthopper") -> arrayOf(
                "Apply Buprofezin or Pymetrozine at the base of rice tillers to halt hopper burn.",
                "High",
                "Chess 50 WG / Applaud 25 WP",
                "Avoid excessive nitrogen fertilization and maintain proper plant spacing."
            )
            labelLower.contains("smut") -> arrayOf(
                "Rogue and burn whip-infested plants immediately. Spray Propiconazole or Tebuconazole fungicide.",
                "High",
                "Tilt 250 EC / Folicur 250 EC",
                "Use disease-free seed cane hot-water treated at 52°C for 30 minutes before planting."
            )
            labelLower.contains("rust") -> arrayOf(
                "Apply protective Pyraclostrobin or Mancozeb fungicide spray on leaf surfaces.",
                "Moderate",
                "Headline 250 EC / Mancozeb 80 WP",
                "Ensure proper field drainage and avoid over-irrigating dense stalk rows."
            )
            labelLower.contains("anthracnose") -> arrayOf(
                "Apply Difenoconazole or Mancozeb fungicide spray on young leaves and fruit clusters.",
                "High",
                "Score 250 EC / Mancozeb 80 WP",
                "Prune dense tree canopy branches to improve air ventilation and sunlight."
            )
            labelLower.contains("weevil") -> arrayOf(
                "Apply Deltamethrin or Lambda-cyhalothrin spray on developing fruit clusters.",
                "High",
                "Decis 2.5 EC / Karate 2.5 EC",
                "Bag young fruits early and collect fallen infested fruits for proper disposal."
            )
            else -> arrayOf(
                "Apply recommended protective fungicide or insecticide spray at early symptom stage.",
                "High",
                "Mancozeb 80 WP / Copper Hydroxide",
                "Consult local agricultural technicians for severe field outbreaks."
            )
        }
    }

}
