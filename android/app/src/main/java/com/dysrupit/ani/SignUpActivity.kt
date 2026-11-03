package com.dysrupit.ani

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dysrupit.ani.utils.AniUser
import com.dysrupit.ani.utils.AuthManager
import com.dysrupit.ani.utils.LanguageManager
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private val cropOptions = arrayOf("Rice", "Corn", "Sugarcane")
    private val selectedCrops = BooleanArray(cropOptions.size)
    private val selectedItemsList = mutableListOf<Int>()

    private val batangasTowns = arrayOf(
        "Ibaan",
        "Lipa City",
        "Batangas City",
        "Tanauan City",
        "Sto. Tomas",
        "San Jose",
        "Rosario",
        "Nasugbu",
        "Bauan",
        "Calatagan",
        "Balayan"
    )

    private val barangaysByTown = mapOf(
        "Ibaan" to arrayOf(
            "Brgy. Poblacion",
            "Brgy. Sabang",
            "Brgy. Balamban",
            "Brgy. Catandala",
            "Brgy. Coliat",
            "Brgy. Dayapan",
            "Brgy. Mabalor",
            "Brgy. Malainin",
            "Brgy. Matagbak",
            "Brgy. Palindan",
            "Brgy. Pangao",
            "Brgy. Salaban",
            "Brgy. San Agustin",
            "Brgy. Talaibon"
        ),
        "Lipa City" to arrayOf(
            "Brgy. Sabang",
            "Brgy. Marawoy",
            "Brgy. Antipolo",
            "Brgy. Dagatan",
            "Brgy. San Isidro",
            "Brgy. Banaybanay",
            "Brgy. Lodlod",
            "Brgy. Mataas Na Lupa",
            "Brgy. Latag",
            "Brgy. Tambo"
        ),
        "Batangas City" to arrayOf(
            "Brgy. Kumintang Ibaba",
            "Brgy. Kumintang Ilaya",
            "Brgy. Alangilan",
            "Brgy. Bolbok",
            "Brgy. Calicanto",
            "Brgy. Gulod Labac",
            "Brgy. Pallocan Silangan",
            "Brgy. Tinga Itaas"
        ),
        "Tanauan City" to arrayOf(
            "Brgy. Darasa",
            "Brgy. Ambulong",
            "Brgy. Bagumbayan",
            "Brgy. Hidalgo",
            "Brgy. Janopol",
            "Brgy. Natatas",
            "Brgy. Trapiche"
        ),
        "Sto. Tomas" to arrayOf(
            "Brgy. San Antonio",
            "Brgy. San Bartolome",
            "Brgy. San Felix",
            "Brgy. San Rafael",
            "Brgy. Santa Anastacia",
            "Brgy. Santiago"
        ),
        "San Jose" to arrayOf(
            "Brgy. Banaybanay 1st",
            "Brgy. Galamay Amo",
            "Brgy. Lalayat",
            "Brgy. Lapolapo",
            "Brgy. Pinagtung-ulan",
            "Brgy. Taysan"
        ),
        "Rosario" to arrayOf(
            "Brgy. Alupay",
            "Brgy. Bagong Pook",
            "Brgy. Namunga",
            "Brgy. San Carlos",
            "Brgy. San Juan",
            "Brgy. Mabato"
        ),
        "Nasugbu" to arrayOf(
            "Brgy. Aga",
            "Brgy. Bilaran",
            "Brgy. Cogunan",
            "Brgy. Lumbangan",
            "Brgy. Wawa",
            "Brgy. Bucana"
        )
    )

    private var selectedCity = ""
    private var selectedBarangay = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etContact = findViewById<EditText>(R.id.etContact)
        val tvCityDropdown = findViewById<TextView>(R.id.tvCityDropdown)
        val tvBarangayDropdown = findViewById<TextView>(R.id.tvBarangayDropdown)
        val layoutCropsDropdown = findViewById<View>(R.id.layoutCropsDropdown)
        val tvSelectedCrops = findViewById<TextView>(R.id.tvSelectedCrops)
        val etPlots = findViewById<EditText>(R.id.etPlots)
        val etHectares = findViewById<EditText>(R.id.etHectares)
        val tvLanguageDropdown = findViewById<TextView>(R.id.tvLanguageDropdown)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        tvCityDropdown?.setOnClickListener { showCityDialog(tvCityDropdown, tvBarangayDropdown) }
        tvBarangayDropdown?.setOnClickListener { showBarangayDialog(tvBarangayDropdown) }

        layoutCropsDropdown?.setOnClickListener {
            showCropsDialog(tvSelectedCrops)
        }

        tvLanguageDropdown?.setOnClickListener {
            showLanguageDialog(tvLanguageDropdown)
        }

        findViewById<View>(R.id.tvTermsLink)?.setOnClickListener {
            showTermsDialog {
                // User reviewed terms
            }
        }

        findViewById<View>(R.id.btnSignUp).setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val contact = etContact.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val plotsInput = etPlots?.text?.toString()?.ifBlank { "3" } ?: "3"
            val hectaresInput = etHectares?.text?.toString()?.ifBlank { "0.8" } ?: "0.8"

            val fullAddress = when {
                selectedBarangay.isNotBlank() && selectedCity.isNotBlank() -> "$selectedBarangay, $selectedCity, Batangas"
                selectedCity.isNotBlank() -> "$selectedCity, Batangas"
                else -> "Batangas"
            }

            if (fullName.isBlank() || contact.isBlank() || password.isBlank()) {
                Toast.makeText(this, getString(R.string.error_fill_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedItemsList.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_select_crop), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Present Terms & Conditions during registration
            showTermsDialog {
                val signUpButton = findViewById<View>(R.id.btnSignUp)
                signUpButton.isEnabled = false
                lifecycleScope.launch {
                    val result = AuthManager.signUp(
                        this@SignUpActivity,
                        AniUser(
                            fullName = fullName,
                            contact = contact,
                            address = fullAddress,
                            password = password,
                            profileImageUri = "",
                            plots = plotsInput,
                            hectares = hectaresInput,
                            crops = selectedItemsList
                                .map { cropOptions[it] }
                                .joinToString(", ")
                        )
                    )
                    result.onSuccess {
                        Toast.makeText(
                            this@SignUpActivity,
                            getString(R.string.signup_success),
                            Toast.LENGTH_LONG
                        ).show()
                        val intent = Intent(this@SignUpActivity, MainTabActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }.onFailure {
                        Toast.makeText(this@SignUpActivity, it.message, Toast.LENGTH_SHORT).show()
                        signUpButton.isEnabled = true
                    }
                }
            }
        }

        findViewById<View>(R.id.tvGoToSignIn).setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
        }
    }

    private fun showTermsDialog(onAgree: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.activity_terms, null)
        dialogView.findViewById<View>(R.id.btnBack)?.visibility = View.GONE
        val btnAccept = dialogView.findViewById<Button>(R.id.btnAcceptTerms)
        btnAccept?.text = "I Agree & Create Account"

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnAccept?.setOnClickListener {
            alertDialog.dismiss()
            onAgree()
        }

        alertDialog.show()
    }

    private fun showCityDialog(tvCity: TextView, tvBarangay: TextView) {
        val selectedIndex = batangasTowns.indexOf(selectedCity).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Select Town / Municipality")
            .setSingleChoiceItems(batangasTowns, selectedIndex) { dialog, which ->
                selectedCity = batangasTowns[which]
                tvCity.text = "$selectedCity, Batangas"
                
                // Reset barangay when town changes
                selectedBarangay = ""
                tvBarangay.text = "Select Barangay"
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBarangayDialog(tvBarangay: TextView) {
        if (selectedCity.isBlank()) {
            Toast.makeText(this, "Please select a Town / Municipality first.", Toast.LENGTH_SHORT).show()
            return
        }

        val availableBarangays = barangaysByTown[selectedCity] ?: arrayOf(
            "Brgy. Poblacion",
            "Brgy. San Isidro",
            "Brgy. Sabang",
            "Brgy. Antipolo",
            "Brgy. Dagatan"
        )

        val selectedIndex = availableBarangays.indexOf(selectedBarangay).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Select Barangay ($selectedCity)")
            .setSingleChoiceItems(availableBarangays, selectedIndex) { dialog, which ->
                selectedBarangay = availableBarangays[which]
                tvBarangay.text = selectedBarangay
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLanguageDialog(textView: TextView) {
        val languages = arrayOf("English", "Tagalog (Filipino)")
        val codes = arrayOf("en", "tl")
        val currentLang = LanguageManager.getLanguage(this)
        val selectedIndex = if (currentLang == "tl" || currentLang == "fil") 1 else 0

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_language))
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                val chosenCode = codes[which]
                val chosenName = languages[which].split(" ")[0]
                LanguageManager.setLanguage(this, chosenCode)
                textView.text = chosenName
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCropsDialog(textView: TextView) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.crops_label))
        builder.setCancelable(false)
        builder.setMultiChoiceItems(cropOptions, selectedCrops) { _, i, isChecked ->
            if (isChecked) {
                if (!selectedItemsList.contains(i)) {
                    selectedItemsList.add(i)
                }
            } else {
                selectedItemsList.remove(i)
            }
        }

        builder.setPositiveButton("OK") { _, _ ->
            if (selectedItemsList.isEmpty()) {
                textView.text = getString(R.string.crops_hint)
            } else {
                val stringBuilder = StringBuilder()
                for (j in 0 until selectedItemsList.size) {
                    stringBuilder.append(cropOptions[selectedItemsList[j]])
                    if (j != selectedItemsList.size - 1) {
                        stringBuilder.append(", ")
                    }
                }
                textView.text = stringBuilder.toString()
            }
        }

        builder.setNegativeButton("Cancel") { dialogInterface, _ ->
            dialogInterface.dismiss()
        }

        builder.setNeutralButton("Clear All") { _, _ ->
            for (i in selectedCrops.indices) {
                selectedCrops[i] = false
            }
            selectedItemsList.clear()
            textView.text = getString(R.string.crops_hint)
        }

        builder.show()
    }
}
