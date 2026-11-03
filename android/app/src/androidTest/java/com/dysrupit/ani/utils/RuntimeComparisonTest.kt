package com.dysrupit.ani.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class RuntimeComparisonTest {

    @Test
    fun compareOnDeviceRuntimes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(
                        x,
                        y,
                        Color.rgb(
                            (x * 17 + y * 3) % 256,
                            (x * 7 + y * 13) % 256,
                            (x * 11 + y * 19) % 256
                        )
                    )
                }
            }
        }

        try {
            benchmark(
                "ONNX Runtime",
                bitmap,
                create = { OnnxAniClassifier(context) },
                classify = { classifier, input ->
                    classifier.classify(input, "disease", "Maize")
                }
            )
            benchmark(
                "TensorFlow Lite",
                bitmap,
                create = { LiteRtAniClassifier(context) },
                classify = { classifier, input ->
                    classifier.classify(input, "disease", "Maize")
                }
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun <T : java.io.Closeable> benchmark(
        name: String,
        bitmap: Bitmap,
        create: () -> T,
        classify: (T, Bitmap) -> AniPrediction
    ) {
        var created: T? = null
        val initializationMs = measureNanoTime {
            created = create()
        } / 1_000_000.0
        val classifier = requireNotNull(created)

        try {
            val samples = mutableListOf<Double>()
            repeat(WARMUP_RUNS) {
                runInference(classifier, bitmap, classify)
            }
            repeat(MEASURED_RUNS) {
                samples += measureNanoTime {
                    runInference(classifier, bitmap, classify)
                } / 1_000_000.0
            }
            val sorted = samples.sorted()
            Log.i(
                "AniRuntimeBenchmark",
                "$name init_ms=$initializationMs inference_ms " +
                    "median=${percentile(sorted, 0.50)} " +
                    "p90=${percentile(sorted, 0.90)} " +
                    "min=${sorted.first()} max=${sorted.last()} n=${sorted.size}"
            )
        } finally {
            classifier.close()
        }
    }

    private fun <T : java.io.Closeable> runInference(
        classifier: T,
        bitmap: Bitmap,
        classify: (T, Bitmap) -> AniPrediction
    ) {
        try {
            classify(classifier, bitmap)
        } catch (_: NoSuitablePredictionException) {
            // Random benchmark pixels may correctly fail the confidence gate.
        }
    }

    private fun percentile(values: List<Double>, percentile: Double): Double {
        val index = ceil(percentile * values.size).toInt()
            .coerceIn(1, values.size) - 1
        return values[index]
    }

    private companion object {
        const val WARMUP_RUNS = 5
        const val MEASURED_RUNS = 40
    }
}
