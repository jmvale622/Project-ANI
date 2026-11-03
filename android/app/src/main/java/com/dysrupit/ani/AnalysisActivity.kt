package com.dysrupit.ani

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dysrupit.ani.utils.LanguageManager
import com.dysrupit.ani.utils.NoPredictionReason
import com.dysrupit.ani.utils.NoSuitablePredictionException
import com.dysrupit.ani.utils.uploadImageForPrediction
import kotlinx.coroutines.launch

class AnalysisActivity : AppCompatActivity() {

    private lateinit var ivPreview: ImageView
    private lateinit var vScanLine: View
    private lateinit var dotStep2: View
    private lateinit var tvStep2: TextView

    private var imageUri: Uri? = null
    private var scanMode: String = "disease"
    private var selectedCrop: String = "Rice"

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)

        ivPreview = findViewById(R.id.ivAnalysisPreview)
        vScanLine = findViewById(R.id.vScanLine)
        dotStep2 = findViewById(R.id.dotStep2)
        tvStep2 = findViewById(R.id.tvStep2)

        scanMode = intent.getStringExtra("scan_mode")?.lowercase() ?: "disease"
        selectedCrop = intent.getStringExtra("selected_crop") ?: "Rice"

        imageUri = intent.getStringExtra("image_uri")?.let(Uri::parse)

        val uri = imageUri
        if (uri == null) {
            Toast.makeText(this, "Error: No image found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        ivPreview.setImageURI(uri)
        startScanningAnimation()
        performAnalysis(uri)
    }

    private fun startScanningAnimation() {
        vScanLine.post {
            if (isFinishing || isDestroyed) return@post
            val parentHeight = findViewById<View>(R.id.cardPreview).height.toFloat()
            if (parentHeight <= 0f) return@post

            val animation = TranslateAnimation(0f, 0f, 0f, parentHeight).apply {
                duration = 2000
                repeatCount = Animation.INFINITE
                repeatMode = Animation.REVERSE
            }
            vScanLine.startAnimation(animation)
        }
    }

    private fun performAnalysis(uri: Uri) {
        lifecycleScope.launch {
            try {
                val predictionResponse = uploadImageForPrediction(
                    context = applicationContext,
                    imageUri = uri,
                    scanMode = scanMode,
                    selectedCrop = selectedCrop
                )

                vScanLine.clearAnimation()

                // Open ResultActivity
                val resultIntent = Intent(this@AnalysisActivity, ResultActivity::class.java).apply {
                    putExtra("crop", predictionResponse.crop)
                    putExtra("status", predictionResponse.status)
                    putExtra("overall_accuracy", predictionResponse.overall_accuracy)
                    putExtra("disease_label", predictionResponse.detections.disease.label)
                    putExtra("disease_confidence", predictionResponse.detections.disease.confidence)
                    putExtra("rec_action", predictionResponse.recommendations.disease.action)
                    putExtra("rec_urgency", predictionResponse.recommendations.disease.urgency)
                    putExtra("rec_product", predictionResponse.recommendations.disease.product)
                    putExtra("general_advice", predictionResponse.recommendations.general_advice)
                    putExtra("image_uri", uri.toString())
                }
                startActivity(resultIntent)
                finish()
            } catch (e: NoSuitablePredictionException) {
                vScanLine.clearAnimation()
                showNoPredictionDialog(e.reason)
            } catch (e: Exception) {
                e.printStackTrace()
                vScanLine.clearAnimation()
                Toast.makeText(
                    this@AnalysisActivity,
                    "Analysis error: ${e.message ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun showNoPredictionDialog(reason: NoPredictionReason) {
        val message = when (reason) {
            NoPredictionReason.UNSUPPORTED_CROP -> getString(
                R.string.no_prediction_crop_unsupported,
                selectedCrop
            )
            NoPredictionReason.NO_CLASSES_FOR_SELECTION -> getString(
                R.string.no_prediction_category_unsupported,
                scanMode,
                selectedCrop
            )
            NoPredictionReason.LOW_CONFIDENCE -> getString(
                R.string.no_prediction_unsuitable_image,
                getString(
                    if (scanMode == "pest") {
                        R.string.pest_category
                    } else {
                        R.string.disease_category
                    }
                ),
                selectedCrop
            )
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.no_prediction_title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.scan_again) { _, _ ->
                startActivity(Intent(this, NewScanActivity::class.java))
                finish()
            }
            .setNegativeButton(R.string.close) { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        vScanLine?.clearAnimation()
        super.onDestroy()
    }
}
