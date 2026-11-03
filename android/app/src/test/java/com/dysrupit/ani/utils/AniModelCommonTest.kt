package com.dysrupit.ani.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AniModelCommonTest {

    private val classes = listOf(
        AniClass("Maize", "Pest", "Corn Borer"),
        AniClass("Maize", "Disease", "Maize Blight"),
        AniClass("Maize", "Health", "Healthy Maize"),
        AniClass("Maize", "Pest", "Fall Armyworm"),
        AniClass("OutOfScope", "Other", "Mango"),
        AniClass("Rice", "Disease", "Rice Bacterial Blight"),
        AniClass("Rice", "Disease", "Rice Brown Spot"),
        AniClass("Rice", "Pest", "Rice Bug"),
        AniClass("Rice", "Disease", "Rice False Smut"),
        AniClass("Rice", "Health", "Healthy Rice"),
        AniClass("Rice", "Disease", "Rice Leaf Blast"),
        AniClass("Rice", "Pest", "Rice Leaf Roller"),
        AniClass("Rice", "Disease", "Rice Leaf Smut"),
        AniClass("Rice", "Disease", "Rice Neck Blast"),
        AniClass("Rice", "Pest", "Rice Stem Borer"),
        AniClass("Sugarcane", "Disease", "Sugarcane Red Rot"),
        AniClass("Sugarcane", "Health", "Healthy Sugarcane"),
        AniClass("Sugarcane", "Pest", "Sugarcane Leaf Hopper"),
        AniClass("Sugarcane", "Pest", "Sugarcane Root and Base Pest"),
        AniClass("Sugarcane", "Pest", "Sugarcane Shoot Borer"),
        AniClass("Sugarcane", "Pest", "Sugarcane White Sucking Pest")
    )

    private fun scores(
        bestLabel: String,
        bestScore: Float,
        otherLabel: String,
        otherScore: Float
    ): FloatArray {
        val remainingScore = (1f - bestScore - otherScore) / (classes.size - 2)
        return FloatArray(classes.size) { remainingScore }.apply {
            this[classes.indexOfFirst { it.label == bestLabel }] = bestScore
            this[classes.indexOfFirst { it.label == otherLabel }] = otherScore
        }
    }

    @Test
    fun diseaseScanExcludesPestClasses() {
        val prediction = AniModelCommon.makePrediction(
            scores = scores("Maize Blight", 0.75f, "Corn Borer", 0.10f),
            classes = classes,
            inferenceMs = 1,
            scanMode = "disease",
            selectedCrop = "Corn"
        )

        assertEquals("Maize Blight", prediction.label)
        assertEquals("Disease", prediction.conditionType)
    }

    @Test
    fun pestDominantImageInDiseaseModeReturnsNoPrediction() {
        val exception = org.junit.Assert.assertThrows(
            NoSuitablePredictionException::class.java
        ) {
            AniModelCommon.makePrediction(
                scores = scores("Corn Borer", 0.80f, "Maize Blight", 0.10f),
                classes = classes,
                inferenceMs = 1,
                scanMode = "disease",
                selectedCrop = "Corn"
            )
        }

        assertEquals(NoPredictionReason.LOW_CONFIDENCE, exception.reason)
    }

    @Test
    fun diseaseScanCanStillIdentifyHealthyPlants() {
        val prediction = AniModelCommon.makePrediction(
            scores = scores("Healthy Maize", 0.75f, "Maize Blight", 0.10f),
            classes = classes,
            inferenceMs = 1,
            scanMode = "disease",
            selectedCrop = "Maize"
        )

        assertEquals("Healthy Maize", prediction.label)
        assertEquals("Health", prediction.conditionType)
    }

    @Test
    fun pestScanExcludesDiseaseAndHealthyClasses() {
        val prediction = AniModelCommon.makePrediction(
            scores = scores("Fall Armyworm", 0.75f, "Maize Blight", 0.10f),
            classes = classes,
            inferenceMs = 1,
            scanMode = "pest",
            selectedCrop = "Corn"
        )

        assertEquals("Fall Armyworm", prediction.label)
        assertEquals("Pest", prediction.conditionType)
    }

    @Test
    fun nonPestImageInPestModeReturnsNoPrediction() {
        val exception = org.junit.Assert.assertThrows(
            NoSuitablePredictionException::class.java
        ) {
            AniModelCommon.makePrediction(
                scores = scores("Maize Blight", 0.80f, "Corn Borer", 0.10f),
                classes = classes,
                inferenceMs = 1,
                scanMode = "pest",
                selectedCrop = "Maize"
            )
        }

        assertEquals(NoPredictionReason.LOW_CONFIDENCE, exception.reason)
    }

    @Test
    fun sugarcaneScanCannotReturnMaizeClass() {
        val prediction = AniModelCommon.makePrediction(
            scores = scores("Sugarcane Red Rot", 0.80f, "Corn Borer", 0.10f),
            classes = classes,
            inferenceMs = 1,
            scanMode = "disease",
            selectedCrop = "Sugarcane"
        )

        assertEquals("Sugarcane Red Rot", prediction.label)
        assertEquals("Sugarcane", prediction.crop)
    }

    @Test
    fun unsupportedCropReturnsNoPrediction() {
        val exception = org.junit.Assert.assertThrows(
            NoSuitablePredictionException::class.java
        ) {
            AniModelCommon.makePrediction(
                scores = scores("Corn Borer", 0.75f, "Maize Blight", 0.10f),
                classes = classes,
                inferenceMs = 1,
                scanMode = "disease",
                selectedCrop = "Apple"
            )
        }

        assertEquals(NoPredictionReason.UNSUPPORTED_CROP, exception.reason)
    }

    @Test
    fun lowMatchingCropScoreReturnsNoPrediction() {
        val exception = org.junit.Assert.assertThrows(
            NoSuitablePredictionException::class.java
        ) {
            AniModelCommon.makePrediction(
                scores = scores("Sugarcane Red Rot", 0.75f, "Corn Borer", 0.10f),
                classes = classes,
                inferenceMs = 1,
                scanMode = "disease",
                selectedCrop = "Corn"
            )
        }

        assertEquals(NoPredictionReason.LOW_CONFIDENCE, exception.reason)
    }

    @Test
    fun sugarcanePestScanExcludesRicePestClasses() {
        val prediction = AniModelCommon.makePrediction(
            scores = scores(
                "Sugarcane Shoot Borer",
                0.75f,
                "Rice Bug",
                0.10f
            ),
            classes = classes,
            inferenceMs = 1,
            scanMode = "pest",
            selectedCrop = "Sugarcane"
        )

        assertEquals("Sugarcane", prediction.crop)
        assertEquals("Sugarcane Shoot Borer", prediction.label)
    }

    @Test
    fun sugarcanePestScanCanReturnEachSugarcanePestClass() {
        listOf(
            "Sugarcane Leaf Hopper",
            "Sugarcane Root and Base Pest",
            "Sugarcane Shoot Borer",
            "Sugarcane White Sucking Pest"
        ).forEach { pest ->
            val prediction = AniModelCommon.makePrediction(
                scores = scores(pest, 0.80f, "Sugarcane Red Rot", 0.10f),
                classes = classes,
                inferenceMs = 1,
                scanMode = "pest",
                selectedCrop = "Sugarcane"
            )

            assertEquals("Sugarcane", prediction.crop)
            assertEquals("Pest", prediction.conditionType)
            assertEquals(pest, prediction.label)
        }
    }

    @Test
    fun sugarcaneDiseaseScanExcludesSugarcanePestClasses() {
        val prediction = AniModelCommon.makePrediction(
            scores = scores(
                "Sugarcane Red Rot",
                0.75f,
                "Sugarcane Leaf Hopper",
                0.20f
            ),
            classes = classes,
            inferenceMs = 1,
            scanMode = "disease",
            selectedCrop = "Sugarcane"
        )

        assertEquals("Sugarcane Red Rot", prediction.label)
        assertEquals("Disease", prediction.conditionType)
    }

    @Test
    fun riceDiseaseScanReturnsOnlyRiceDiseaseOrHealthyClasses() {
        val prediction = AniModelCommon.makePrediction(
            scores = scores("Rice Leaf Blast", 0.80f, "Sugarcane Red Rot", 0.10f),
            classes = classes,
            inferenceMs = 1,
            scanMode = "disease",
            selectedCrop = "Rice"
        )

        assertEquals("Rice", prediction.crop)
        assertEquals("Rice Leaf Blast", prediction.label)
    }

    @Test
    fun riceDiseaseScanExcludesRicePestClasses() {
        val prediction = AniModelCommon.makePrediction(
            scores("Rice Leaf Blast", 0.75f, "Rice Bug", 0.20f),
            classes = classes,
            inferenceMs = 1,
            scanMode = "disease",
            selectedCrop = "Rice"
        )

        assertEquals("Rice Leaf Blast", prediction.label)
        assertEquals("Disease", prediction.conditionType)
    }

    @Test
    fun ricePestScanCanReturnEachRicePestClass() {
        listOf("Rice Bug", "Rice Leaf Roller", "Rice Stem Borer").forEach { pest ->
            val prediction = AniModelCommon.makePrediction(
                scores = scores(pest, 0.80f, "Rice Leaf Blast", 0.10f),
                classes = classes,
                inferenceMs = 1,
                scanMode = "pest",
                selectedCrop = "Rice"
            )

            assertEquals("Rice", prediction.crop)
            assertEquals("Pest", prediction.conditionType)
            assertEquals(pest, prediction.label)
        }
    }

    @Test
    fun mangoOutOfScopePredictionDoesNotPassCropConfidenceGate() {
        val exception = org.junit.Assert.assertThrows(
            NoSuitablePredictionException::class.java
        ) {
            AniModelCommon.makePrediction(
                scores = scores("Mango", 0.80f, "Maize Blight", 0.10f),
                classes = classes,
                inferenceMs = 1,
                scanMode = "disease",
                selectedCrop = "Maize"
            )
        }

        assertEquals(NoPredictionReason.LOW_CONFIDENCE, exception.reason)
    }
}
