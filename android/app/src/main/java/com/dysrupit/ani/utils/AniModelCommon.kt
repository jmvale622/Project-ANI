
package com.dysrupit.ani.utils

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

private const val IMAGE_SIZE = 224

data class AniClass(
    val crop: String,
    val type: String,
    val label: String
)

data class AniPrediction(
    val crop: String,
    val conditionType: String,
    val label: String,
    val confidence: Float,
    val inferenceMs: Long
)

enum class NoPredictionReason {
    UNSUPPORTED_CROP,
    NO_CLASSES_FOR_SELECTION,
    LOW_CONFIDENCE
}

class NoSuitablePredictionException(
    val reason: NoPredictionReason
) : IllegalStateException(reason.name)

object AniModelCommon {

    private const val MIN_PREDICTION_CONFIDENCE = 0.70f

    fun preprocess(bitmap: Bitmap): FloatArray {
        require(!bitmap.isRecycled) { "Image bitmap is recycled." }

        val side = min(bitmap.width, bitmap.height)
        require(side > 0) { "Invalid image dimensions." }

        // Resize the shortest side to 224, then center-crop.
        val scale = IMAGE_SIZE.toFloat() / side
        val width = (bitmap.width * scale).roundToInt()
        val height = (bitmap.height * scale).roundToInt()

        val resized = Bitmap.createScaledBitmap(
            bitmap, width, height, true
        )

        try {
            val left = (width - IMAGE_SIZE) / 2
            val top = (height - IMAGE_SIZE) / 2

            val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
            resized.getPixels(
                pixels, 0, IMAGE_SIZE,
                left, top, IMAGE_SIZE, IMAGE_SIZE
            )

            // NCHW layout: [1, 3, 224, 224]
            val plane = IMAGE_SIZE * IMAGE_SIZE
            val input = FloatArray(3 * plane)

            for (i in pixels.indices) {
                val pixel = pixels[i]

                input[i] = Color.red(pixel) / 255.0f
                input[plane + i] = Color.green(pixel) / 255.0f
                input[2 * plane + i] = Color.blue(pixel) / 255.0f
            }

            return input
        } finally {
            if (resized !== bitmap) resized.recycle()
        }
    }

    fun readClasses(context: android.content.Context): List<AniClass> {
        val classes = context.assets
            .open("models/ani_labels.txt")
            .bufferedReader()
            .useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { line ->
                        val parts = line.split("|", limit = 3)
                        require(parts.size == 3) {
                            "Invalid label entry: $line. " +
                                    "Expected crop|type|label"
                        }

                        AniClass(
                            crop = parts[0].trim(),
                            type = parts[1].trim(),
                            label = parts[2].trim()
                        )
                    }
                    .toList()
            }

        require(classes.isNotEmpty()) {
            "ANI model label file is empty."
        }

        return classes
    }

    fun makePrediction(
        scores: FloatArray,
        classes: List<AniClass>,
        inferenceMs: Long,
        scanMode: String = "disease",
        selectedCrop: String = "Rice"
    ): AniPrediction {
        require(scores.isNotEmpty()) { "Model returned no scores." }
        require(scores.size == classes.size) {
            "Output has ${scores.size} scores, but " +
                    "${classes.size} labels were loaded."
        }
        require(scores.all { it.isFinite() }) {
            "Model output contains invalid numeric values."
        }

        // If already probabilities, preserve them.
        // Otherwise apply softmax to logits.
        val sum = scores.sum()
        val areProbabilities =
            scores.all { it >= 0f && it <= 1f } &&
                    kotlin.math.abs(sum - 1f) < 0.02f

        val probabilities = if (areProbabilities) {
            scores
        } else {
            val maxScore = scores.maxOrNull()!!
            val exps = scores.map {
                exp((it - maxScore).toDouble()).toFloat()
            }
            val total = exps.sum()
            require(total.isFinite() && total > 0f) {
                "Cannot normalize model output."
            }
            exps.map { it / total }.toFloatArray()
        }

        val allowedTypes = when (scanMode.trim().lowercase()) {
            "disease" -> setOf("Disease", "Health")
            "pest" -> setOf("Pest")
            else -> error("Unsupported scan mode: $scanMode")
        }

        val normalizedCrop = normalizeCrop(selectedCrop)
        val cropIndices = classes.indices.filter { index ->
            normalizeCrop(classes[index].crop) == normalizedCrop
        }
        if (cropIndices.isEmpty()) {
            throw NoSuitablePredictionException(NoPredictionReason.UNSUPPORTED_CROP)
        }

        val candidateIndices = cropIndices.filter { index ->
            classes[index].type in allowedTypes
        }
        if (candidateIndices.isEmpty()) {
            throw NoSuitablePredictionException(
                NoPredictionReason.NO_CLASSES_FOR_SELECTION
            )
        }

        val bestIndex = candidateIndices.maxByOrNull {
            probabilities[it]
        } ?: error("No predicted class.")
        if (probabilities[bestIndex] < MIN_PREDICTION_CONFIDENCE) {
            throw NoSuitablePredictionException(NoPredictionReason.LOW_CONFIDENCE)
        }

        val predicted = classes[bestIndex]

        return AniPrediction(
            crop = predicted.crop,
            conditionType = predicted.type,
            label = predicted.label,
            confidence = probabilities[bestIndex],
            inferenceMs = inferenceMs
        )
    }

    private fun normalizeCrop(crop: String): String {
        return when (crop.trim().lowercase().filter(Char::isLetter)) {
            "corn", "maize" -> "maize"
            "sugarcane" -> "sugarcane"
            else -> crop.trim().lowercase().filter(Char::isLetter)
        }
    }
}
