package com.dysrupit.ani.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.Locale
import kotlin.coroutines.coroutineContext

object OfflineLlmModelManager {

    const val MODEL_SIZE_BYTES = 347_251_840L
    const val MODEL_SIZE_LABEL = "331 MiB"

    private const val MODEL_FILE_NAME = "ani_recommendation_qwen3_0.6b.litertlm"
    private const val MODEL_SHA256 =
        "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139"
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/" +
            "256f33043931947c173d7f3cc1d84ee7a8538cc2/" +
            "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm?download=true"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .build()

    fun installedModel(context: Context): File? {
        if (!isDeviceSupported(context)) return null
        val model = modelFile(context)
        val checksum = File(model.parentFile, "${model.name}.sha256")
        return model.takeIf {
            it.isFile &&
                it.length() == MODEL_SIZE_BYTES &&
                checksum.isFile &&
                checksum.readText().trim() == MODEL_SHA256
        }
    }

    fun isInstalled(context: Context): Boolean = installedModel(context) != null

    fun isDeviceSupported(context: Context): Boolean {
        val hasRuntimeAbi = Build.SUPPORTED_ABIS.any {
            it == "arm64-v8a" || it == "x86_64"
        }
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return hasRuntimeAbi && activityManager?.isLowRamDevice != true
    }

    fun removeModel(context: Context) {
        val model = modelFile(context)
        check(!model.exists() || model.delete()) {
            "Could not remove the offline recommendation model."
        }
        val checksum = File(model.parentFile, "${model.name}.sha256")
        check(!checksum.exists() || checksum.delete()) {
            "Could not remove the offline model checksum."
        }
        val partial = File(model.parentFile, "${model.name}.download")
        check(!partial.exists() || partial.delete()) {
            "Could not remove the incomplete model download."
        }
    }

    suspend fun downloadModel(
        context: Context,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        check(isDeviceSupported(context)) {
            "The offline language model requires a supported 64-bit, non-low-RAM device."
        }
        val destination = modelFile(context)
        installedModel(context)?.let { return@withContext it }

        val directory = destination.parentFile
            ?: error("Unable to create the offline model directory.")
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create the offline model directory."
        }

        val partial = File(directory, "${destination.name}.download")
        if (partial.length() > MODEL_SIZE_BYTES) {
            check(partial.delete()) { "Unable to remove the incomplete model download." }
        }

        var existingBytes = partial.length()
        if (existingBytes == MODEL_SIZE_BYTES) {
            if (sha256(partial) == MODEL_SHA256) {
                install(partial, destination)
                return@withContext destination
            }
            check(partial.delete()) {
                "The saved model download is corrupted and could not be removed."
            }
            existingBytes = 0L
        }

        val request = Request.Builder()
            .url(MODEL_URL)
            .apply {
                if (existingBytes > 0) header("Range", "bytes=$existingBytes-")
            }
            .build()

        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "Model download failed with HTTP ${response.code}."
            }

            val append = existingBytes > 0 && response.code == 206
            if (append) {
                val contentRange = response.header("Content-Range")
                check(contentRange?.startsWith("bytes $existingBytes-") == true) {
                    "Model server returned an invalid resume range."
                }
            }

            val startingBytes = if (append) existingBytes else 0L
            val digest = MessageDigest.getInstance("SHA-256")
            if (append) {
                FileInputStream(partial).buffered().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
            }

            val body = response.body ?: error("Model server returned an empty response.")
            val fileOutput = FileOutputStream(partial, append)
            BufferedOutputStream(fileOutput).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = startingBytes
                    onProgress(downloaded, MODEL_SIZE_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, MODEL_SIZE_BYTES)
                    }
                }
                output.flush()
                fileOutput.fd.sync()
            }

            check(partial.length() == MODEL_SIZE_BYTES) {
                "Model download is incomplete. Keep the app open and try again."
            }
            val actualHash = digest.digest().joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }
            if (actualHash != MODEL_SHA256) {
                partial.delete()
                error("Downloaded model checksum did not match; the incomplete file was removed.")
            }
        }

        install(partial, destination)
        destination
    }

    private fun modelFile(context: Context): File =
        File(context.noBackupFilesDir, "models/$MODEL_FILE_NAME")

    private fun install(partial: File, destination: File) {
        if (destination.exists()) {
            check(destination.delete()) { "Unable to replace the existing offline model." }
        }
        check(partial.renameTo(destination)) {
            "Unable to install the offline recommendation model."
        }
        FileOutputStream(File(destination.parentFile, "${destination.name}.sha256")).use {
            it.write(MODEL_SHA256.toByteArray(Charsets.US_ASCII))
            it.fd.sync()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }.lowercase(Locale.ROOT)
    }
}
