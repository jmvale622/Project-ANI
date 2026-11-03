package com.dysrupit.ani.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiteRtLocalRecommendationEngineTest {

    @Test
    fun parsesValidJsonAndKeepsProductAdviceConservative() {
        val result = LiteRtLocalRecommendationEngine.parseRecommendation(
            """
            Here is the recommendation:
            {"action":"Inspect nearby leaves and monitor the affected area.",
             "urgency":"medium",
             "general_advice":"Take another clear photo if symptoms change."}
            """.trimIndent()
        )

        assertEquals(
            "Inspect nearby leaves and monitor the affected area.",
            result?.disease?.action
        )
        assertEquals("Moderate", result?.disease?.urgency)
        assertEquals(
            "Consult your local agricultural officer before using any crop treatment.",
            result?.disease?.product
        )
        assertEquals(
            "Take another clear photo if symptoms change.",
            result?.general_advice
        )
    }

    @Test
    fun localizesSafeProductAdviceForTagalog() {
        val result = LiteRtLocalRecommendationEngine.parseRecommendation(
            """{"action":"Suriin ang mga dahon.","urgency":"low","general_advice":"Subaybayan ang pananim."}""",
            language = "tl"
        )

        assertEquals("Low", result?.disease?.urgency)
        assertEquals(
            "Kumonsulta sa lokal na agricultural technician bago gumamit ng anumang produkto.",
            result?.disease?.product
        )
    }

    @Test
    fun rejectsInvalidOrUnsafeGeneratedAdvice() {
        assertNull(
            LiteRtLocalRecommendationEngine.parseRecommendation(
                """{"action":"Spray pesticide now.","urgency":"high","general_advice":"Monitor the crop."}"""
            )
        )
        assertNull(
            LiteRtLocalRecommendationEngine.parseRecommendation(
                """{"action":"Monitor the crop.","urgency":"urgent","general_advice":"Check the leaves."}"""
            )
        )
        assertNull(
            LiteRtLocalRecommendationEngine.parseRecommendation(
                """{"action":"Monitor the crop.","urgency":"low","general_advice":"Use pesticide as needed."}"""
            )
        )
        assertNull(
            LiteRtLocalRecommendationEngine.parseRecommendation(
                """{"action":"Monitor the crop.","urgency":"low"}"""
            )
        )
        assertNull(LiteRtLocalRecommendationEngine.parseRecommendation("No JSON response"))
    }
}
