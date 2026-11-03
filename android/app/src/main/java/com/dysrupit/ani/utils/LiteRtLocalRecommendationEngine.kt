package com.dysrupit.ani.utils

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object LiteRtLocalRecommendationEngine {

    private const val TAG = "AniOfflineLlm"
    private const val SAFE_PRODUCT_ADVICE =
        "Consult your local agricultural officer before using any crop treatment."
    private const val SAFE_PRODUCT_ADVICE_TL =
        "Kumonsulta sa lokal na agricultural technician bago gumamit ng anumang produkto."

    private val generationMutex = Mutex()
    private var engine: Engine? = null

    suspend fun recommend(
        context: Context,
        crop: String,
        diseaseLabel: String,
        confidence: Int,
        language: String
    ): Recommendations? = withContext(Dispatchers.IO) {
        if (!OfflineLlmModelManager.isDeviceSupported(context)) return@withContext null
        val modelFile = OfflineLlmModelManager.installedModel(context) ?: return@withContext null

        try {
            generationMutex.withLock {
                val localEngine = getEngine(context.applicationContext, modelFile.absolutePath)
                val systemInstruction = if (language == "tl" || language == "fil") {
                    TAGALOG_SYSTEM_INSTRUCTION
                } else {
                    ENGLISH_SYSTEM_INSTRUCTION
                }
                val config = ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction),
                    samplerConfig = SamplerConfig(
                        topK = 20,
                        topP = 0.85,
                        temperature = 0.2
                    )
                )

                localEngine.createConversation(config).use { conversation ->
                    val response = conversation.sendMessage(
                        """
                        Crop: ${crop.take(80)}
                        Model result: ${diseaseLabel.take(120)}
                        Classifier confidence: ${confidence.coerceIn(0, 100)}%

                        Return one JSON object with exactly these string fields:
                        {"action":"one short, practical observation or low-risk next step",
                         "urgency":"high, medium, or low",
                         "general_advice":"one short tip"}
                        """.trimIndent()
                    )
                    val responseText = response.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString(separator = "") { it.text }
                    parseRecommendation(responseText, language)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: OutOfMemoryError) {
            Log.e(TAG, "Offline model ran out of memory; using built-in advice.", error)
            null
        } catch (error: Exception) {
            Log.w(TAG, "Offline recommendation generation failed; using built-in advice.", error)
            null
        }
    }

    private suspend fun getEngine(context: Context, modelPath: String): Engine {
        engine?.let { return it }
        val gpuEngine = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                cacheDir = context.cacheDir.absolutePath
            )
        )
        try {
            gpuEngine.initialize()
            engine = gpuEngine
            return gpuEngine
        } catch (gpuError: Exception) {
            gpuEngine.close()
            Log.i(TAG, "GPU backend unavailable; retrying with CPU.", gpuError)
        }

        return Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )
        ).also {
            it.initialize()
            engine = it
        }
    }

    suspend fun release() = withContext(Dispatchers.IO) {
        generationMutex.withLock {
            engine?.close()
            engine = null
        }
    }

    internal fun parseRecommendation(
        response: String,
        language: String = "en"
    ): Recommendations? {
        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) return null

        return try {
            val json = JsonParser.parseString(response.substring(jsonStart, jsonEnd + 1))
                .asJsonObject
            val action = json.get("action")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= 400 }
                ?: return null
            val generalAdvice = json.get("general_advice")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= 400 }
                ?: return null
            val urgency = when (
                json.get("urgency")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    ?.trim()?.lowercase()
            ) {
                "high" -> "High"
                "medium" -> "Moderate"
                "low" -> "Low"
                else -> return null
            }
            if (
                UNSAFE_TREATMENT_PATTERN.containsMatchIn(action) ||
                UNSAFE_TREATMENT_PATTERN.containsMatchIn(generalAdvice)
            ) {
                return null
            }

            Recommendations(
                disease = DiseaseRecommendation(
                    action = action,
                    urgency = urgency,
                    product = if (language == "tl" || language == "fil") {
                        SAFE_PRODUCT_ADVICE_TL
                    } else {
                        SAFE_PRODUCT_ADVICE
                    }
                ),
                general_advice = generalAdvice
            )
        } catch (_: Exception) {
            null
        }
    }

    private const val ENGLISH_SYSTEM_INSTRUCTION =
        """
        You are an offline crop-care assistant for smallholder farmers in the Philippines.
        The image classifier can be wrong: describe results as possible, not certain.
        Give short, cautious, practical advice. Prefer monitoring, sanitation, field
        inspection, and local extension support. Never prescribe pesticides, brands,
        chemicals, mixing rates, or application dosages. Never claim to diagnose beyond
        the supplied crop and model label. Return only valid JSON with action, urgency
        (high, medium, or low), and general_advice fields. Use English.
        """

    private const val TAGALOG_SYSTEM_INSTRUCTION =
        """
        Ikaw ay offline na katuwang sa pangangalaga ng pananim para sa maliliit na magsasaka
        sa Pilipinas. Maaaring magkamali ang image classifier; sabihin na posibleng kondisyon
        lamang ito. Magbigay ng maikli at maingat na payo. Unahin ang pagmamasid, paglilinis,
        pagsusuri sa bukid, at pagkonsulta sa lokal na agricultural technician. Huwag magreseta
        ng pestisidyo, brand, kemikal, timpla, o dami ng aplikasyon. Huwag magbigay ng diagnosis
        lampas sa ibinigay na pananim at label. Magbalik lamang ng wastong JSON na may action,
        urgency (high, medium, o low), at general_advice. Gumamit ng Filipino.
        """

    private val UNSAFE_TREATMENT_PATTERN = Regex(
        "\\b(pesticide|insecticide|fungicide|herbicide|chemical|dosage|dose|spray|brand)\\b|" +
            "\\b(pestisidyo|kemikal|timpla|dosis)\\b",
        RegexOption.IGNORE_CASE
    )
}
