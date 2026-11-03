package com.dysrupit.ani.utils

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class LiteRtAniClassifier(context: Context) : Closeable {

    private val appContext = context.applicationContext
    private val classes = AniModelCommon.readClasses(appContext)
    private val interpreter: Interpreter

    init {
        val fileDescriptor = appContext.assets.openFd("models/best.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

        interpreter = Interpreter(modelBuffer)
    }

    fun classify(
        bitmap: Bitmap,
        scanMode: String = "disease",
        selectedCrop: String = "Rice"
    ): AniPrediction {
        val input = AniModelCommon.preprocess(bitmap)

        val started = System.nanoTime()

        val byteBuffer = ByteBuffer.allocateDirect(4 * input.size).apply {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().put(input)
        }

        val outputScores = Array(1) { FloatArray(classes.size) }
        interpreter.run(byteBuffer, outputScores)

        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        return AniModelCommon.makePrediction(
            scores = outputScores[0],
            classes = classes,
            inferenceMs = elapsedMs,
            scanMode = scanMode,
            selectedCrop = selectedCrop
        )
    }

    override fun close() {
        interpreter.close()
    }
}
