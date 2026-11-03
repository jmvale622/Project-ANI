
package com.dysrupit.ani.utils

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

class `OnnxAniClassifier`(context: Context) : Closeable {

    private val appContext = context.applicationContext
    private val environment = OrtEnvironment.getEnvironment()
    private val classes = AniModelCommon.readClasses(appContext)

    private val session: OrtSession

    init {
        val modelFile = copyAssetIfNeeded(
            "models/best.onnx",
            File(appContext.filesDir, "ani_best.onnx")
        )

        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(2)

        session = try {
            environment.createSession(modelFile.absolutePath, options)
        } finally {
            options.close()
        }

        require(session.inputNames.size == 1) {
            "Expected one model input."
        }
        require(session.outputNames.isNotEmpty()) {
            "Model has no output."
        }
    }

    fun classify(
        bitmap: Bitmap,
        scanMode: String = "disease",
        selectedCrop: String = "Rice"
    ): AniPrediction {
        val input = AniModelCommon.preprocess(bitmap)

        val inputName = session.inputNames.first()
        val outputName = session.outputNames.first()

        val started = System.nanoTime()

        val scores = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, 224, 224)
        ).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val optionalValue = result.get(outputName)
                val rawValue = if (optionalValue.isPresent) optionalValue.get().value else null

                when (rawValue) {
                    is Array<*> -> {
                        val row = rawValue.firstOrNull()
                        when (row) {
                            is FloatArray -> row
                            else -> error("Unsupported ONNX output row type: ${row?.let { it::class.java }}")
                        }
                    }
                    is FloatArray -> rawValue
                    else -> error("Unsupported ONNX output type: ${rawValue?.let { it::class.java }}")
                }
            }
        }

        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        return AniModelCommon.makePrediction(
            scores = scores,
            classes = classes,
            inferenceMs = elapsedMs,
            scanMode = scanMode,
            selectedCrop = selectedCrop
        )
    }

    private fun copyAssetIfNeeded(
        assetName: String,
        destination: File
    ): File {
        val modelVersion = appContext.assets
            .open("models/ani_model_version.txt")
            .bufferedReader()
            .use { it.readText().trim() }
        require(modelVersion.isNotEmpty()) { "Model version asset is empty." }

        val versionFile = File(destination.parentFile, destination.name + ".version")
        if (
            destination.exists() &&
            destination.length() > 0L &&
            versionFile.isFile &&
            versionFile.readText() == modelVersion
        ) {
            return destination
        }

        val temporary = File(destination.parentFile, destination.name + ".tmp")

        appContext.assets.open(assetName).use { input ->
            FileOutputStream(temporary).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }

        check(temporary.length() > 0L) { "Copied model is empty." }

        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        versionFile.writeText(modelVersion)

        return destination
    }

    override fun close() {
        session.close()
    }
}
