package com.dysrupit.ani

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.dysrupit.ani.utils.AniUser
import com.dysrupit.ani.utils.AuthManager
import com.dysrupit.ani.utils.LanguageManager
import com.dysrupit.ani.utils.LiteRtLocalRecommendationEngine
import com.dysrupit.ani.utils.OfflineLlmModelManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileContact: TextView
    private lateinit var tvInitials: TextView
    private lateinit var ivProfilePicture: ImageView
    private lateinit var tvFarmLocation: TextView
    private lateinit var tvFarmCrops: TextView
    private lateinit var tvLanguage: TextView
    private lateinit var tvOfflineLlmStatus: TextView
    private lateinit var tvPlotsVal: TextView
    private lateinit var tvHectaresVal: TextView
    private var offlineModelDownloadJob: Job? = null

    private val pickProfileImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            saveAndDisplayProfilePicture(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvProfileName = view.findViewById(R.id.tvProfileName)
        tvProfileContact = view.findViewById(R.id.tvProfileContact)
        tvInitials = view.findViewById(R.id.tvInitials)
        ivProfilePicture = view.findViewById(R.id.ivProfilePicture)
        tvFarmLocation = view.findViewById(R.id.tvFarmLocation)
        tvFarmCrops = view.findViewById(R.id.tvFarmCrops)
        tvLanguage = view.findViewById(R.id.tvLanguage)
        tvOfflineLlmStatus = view.findViewById(R.id.tvOfflineLlmStatus)
        tvPlotsVal = view.findViewById(R.id.tvPlotsVal)
        tvHectaresVal = view.findViewById(R.id.tvHectaresVal)

        loadUserData()
        updateOfflineModelStatus()

        // Avatar Click Listener -> Select new Profile Photo
        view.findViewById<View>(R.id.cardAvatar).setOnClickListener {
            pickProfileImageLauncher.launch("image/*")
        }

        // Edit Profile Listeners
        view.findViewById<View>(R.id.btnEditProfile).setOnClickListener { showEditProfileDialog() }
        view.findViewById<View>(R.id.rowCrops).setOnClickListener { showEditProfileDialog() }
        view.findViewById<View>(R.id.rowLocation).setOnClickListener { showEditProfileDialog() }

        // Language Listener
        view.findViewById<View>(R.id.rowLanguage).setOnClickListener { showLanguageDialog() }
        view.findViewById<View>(R.id.rowOfflineLlm).setOnClickListener {
            showOfflineLlmDialog()
        }

        // Change PIN Listener
        view.findViewById<View>(R.id.btnChangePin).setOnClickListener { showChangePinDialog() }

        // Terms & Conditions Listener
        view.findViewById<View>(R.id.rowTerms)?.setOnClickListener {
            startActivity(Intent(requireContext(), TermsActivity::class.java))
        }

        // Logout
        view.findViewById<View>(R.id.btnLogout).setOnClickListener {
            logout()
        }
    }

    override fun onResume() {
        super.onResume()
        updateOfflineModelStatus()
    }

    override fun onDestroyView() {
        offlineModelDownloadJob?.cancel()
        offlineModelDownloadJob = null
        super.onDestroyView()
    }

    private fun updateOfflineModelStatus() {
        if (!::tvOfflineLlmStatus.isInitialized || !isAdded) return
        tvOfflineLlmStatus.setText(
            if (!OfflineLlmModelManager.isDeviceSupported(requireContext())) {
                R.string.offline_llm_unsupported
            } else if (OfflineLlmModelManager.isInstalled(requireContext())) {
                R.string.offline_llm_status_ready
            } else {
                R.string.offline_llm_status_missing
            }
        )
    }

    private fun showOfflineLlmDialog() {
        val context = requireContext()
        if (!OfflineLlmModelManager.isDeviceSupported(context)) {
            AlertDialog.Builder(context)
                .setTitle(R.string.offline_llm_title)
                .setMessage(R.string.offline_llm_unsupported_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        if (OfflineLlmModelManager.isInstalled(context)) {
            AlertDialog.Builder(context)
                .setTitle(R.string.offline_llm_title)
                .setMessage(R.string.offline_llm_remove_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.offline_llm_remove_action) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            LiteRtLocalRecommendationEngine.release()
                            OfflineLlmModelManager.removeModel(context)
                            updateOfflineModelStatus()
                            Toast.makeText(
                                context,
                                R.string.offline_llm_removed,
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Toast.makeText(
                                context,
                                getString(R.string.offline_llm_error, error.message.orEmpty()),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .show()
            return
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.offline_llm_title)
            .setMessage(
                getString(
                    R.string.offline_llm_download_message,
                    OfflineLlmModelManager.MODEL_SIZE_LABEL
                )
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.offline_llm_download_action) { _, _ ->
                downloadOfflineLlmModel()
            }
            .show()
    }

    private fun downloadOfflineLlmModel() {
        val context = requireContext()
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
        val progressLabel = TextView(context).apply {
            setText(R.string.offline_llm_downloading)
            setPadding(0, 0, 0, 12)
        }
        val progressView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(
                progressLabel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                progress,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.offline_llm_title)
            .setView(progressView)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setCancelable(false)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                offlineModelDownloadJob?.cancel()
                dialog.dismiss()
            }
        }
        dialog.show()

        offlineModelDownloadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                OfflineLlmModelManager.downloadModel(context) { downloaded, total ->
                    withContext(Dispatchers.Main) {
                        val percent = (downloaded * 100 / total).toInt().coerceIn(0, 100)
                        progress.isIndeterminate = false
                        progress.max = 100
                        progress.progress = percent
                        progressLabel.text = getString(
                            R.string.offline_llm_progress,
                            percent,
                            downloaded / (1024 * 1024),
                            total / (1024 * 1024)
                        )
                    }
                }
                if (isAdded) {
                    Toast.makeText(
                        context,
                        R.string.offline_llm_downloaded,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                    if (isAdded) {
                        Toast.makeText(
                            context,
                            getString(R.string.offline_llm_error, error.message.orEmpty()),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } finally {
                    dialog.dismiss()
                    updateOfflineModelStatus()
                }
            }
        }

    private fun loadUserData() {
        val user = AuthManager.getCurrentUser(requireContext())
        user?.let {
            tvProfileName.text = it.fullName
            tvProfileContact.text = it.contact
            tvPlotsVal.text = it.plots.ifBlank { "3" }
            tvHectaresVal.text = it.hectares.ifBlank { "0.8" }

            if (it.address.isNotBlank()) {
                tvFarmLocation.text = "${it.address} ›"
            }
            if (it.fullName.isNotEmpty()) {
                val names = it.fullName.split(" ")
                val initials = if (names.size > 1 && names[1].isNotEmpty()) {
                    "${names[0][0]}${names[1][0]}"
                } else {
                    "${names[0][0]}"
                }
                tvInitials.text = initials.uppercase()
            }

            // Load Profile Picture if set
            if (it.profileImageUri.isNotBlank()) {
                try {
                    val imageUri = Uri.parse(it.profileImageUri)
                    ivProfilePicture.setImageURI(imageUri)
                    ivProfilePicture.visibility = View.VISIBLE
                    tvInitials.visibility = View.GONE
                } catch (e: Exception) {
                    ivProfilePicture.visibility = View.GONE
                    tvInitials.visibility = View.VISIBLE
                }
            } else {
                ivProfilePicture.visibility = View.GONE
                tvInitials.visibility = View.VISIBLE
            }
        }
        tvLanguage.text = "${LanguageManager.getLanguageDisplayName(requireContext())} ›"
    }

    private fun saveAndDisplayProfilePicture(uri: Uri) {
        val user = AuthManager.getCurrentUser(requireContext()) ?: return
        val updatedUser = user.copy(profileImageUri = uri.toString())
        val result = AuthManager.updateUser(requireContext(), updatedUser)
        result.onSuccess {
            ivProfilePicture.setImageURI(uri)
            ivProfilePicture.visibility = View.VISIBLE
            tvInitials.visibility = View.GONE
            Toast.makeText(requireContext(), "Profile picture updated!", Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            Toast.makeText(requireContext(), "Failed to update photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Tagalog (Filipino)")
        val codes = arrayOf("en", "tl")
        val currentLang = LanguageManager.getLanguage(requireContext())
        val selectedIndex = if (currentLang == "tl" || currentLang == "fil") 1 else 0

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.choose_language))
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                val chosenCode = codes[which]
                val chosenName = languages[which].split(" ")[0]
                LanguageManager.setLanguage(requireContext(), chosenCode)
                Toast.makeText(requireContext(), "Language set to $chosenName", Toast.LENGTH_SHORT).show()
                dialog.dismiss()

                val intent = Intent(requireContext(), MainTabActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("open_tab", "profile")
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null)
        val etEditName = dialogView.findViewById<EditText>(R.id.etEditName)
        val etEditPhone = dialogView.findViewById<EditText>(R.id.etEditPhone)
        val etEditLocation = dialogView.findViewById<EditText>(R.id.etEditLocation)
        val etEditCrops = dialogView.findViewById<EditText>(R.id.etEditCrops)
        val etEditPlots = dialogView.findViewById<EditText>(R.id.etEditPlots)
        val etEditHectares = dialogView.findViewById<EditText>(R.id.etEditHectares)
        val btnSaveEdit = dialogView.findViewById<View>(R.id.btnSaveEdit)
        val btnCancelEdit = dialogView.findViewById<View>(R.id.btnCancelEdit)

        val currentUser = AuthManager.getCurrentUser(requireContext())
        currentUser?.let {
            etEditName.setText(it.fullName)
            etEditPhone.setText(it.contact)
            etEditLocation.setText(it.address)
            etEditCrops.setText(tvFarmCrops.text.toString().replace(" ›", ""))
            etEditPlots.setText(it.plots)
            etEditHectares.setText(it.hectares)
        }

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnCancelEdit.setOnClickListener { alertDialog.dismiss() }

        btnSaveEdit.setOnClickListener {
            val newName = etEditName.text.toString().trim()
            val newPhone = etEditPhone.text.toString().trim()
            val newLocation = etEditLocation.text.toString().trim()
            val newCrops = etEditCrops.text.toString().trim()
            val newPlots = etEditPlots.text.toString().trim().ifBlank { "3" }
            val newHectares = etEditHectares.text.toString().trim().ifBlank { "0.8" }

            if (newName.isEmpty() || newPhone.isEmpty()) {
                Toast.makeText(requireContext(), "Name and Phone Number cannot be empty.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedUser = AniUser(
                fullName = newName,
                contact = newPhone,
                address = newLocation,
                password = currentUser?.password ?: "1234",
                profileImageUri = currentUser?.profileImageUri ?: "",
                plots = newPlots,
                hectares = newHectares,
                crops = currentUser?.crops.orEmpty()
            )

            val result = AuthManager.updateUser(requireContext(), updatedUser)
            result.onSuccess {
                loadUserData()
                if (newCrops.isNotBlank()) {
                    tvFarmCrops.text = "$newCrops ›"
                }
                Toast.makeText(requireContext(), "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                alertDialog.dismiss()
            }.onFailure { e ->
                Toast.makeText(requireContext(), "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        alertDialog.show()
    }

    private fun showChangePinDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_pin, null)
        val etCurrentPin = dialogView.findViewById<EditText>(R.id.etCurrentPin)
        val etNewPin = dialogView.findViewById<EditText>(R.id.etNewPin)
        val etConfirmNewPin = dialogView.findViewById<EditText>(R.id.etConfirmNewPin)
        val btnSavePin = dialogView.findViewById<View>(R.id.btnSavePin)
        val btnCancelPin = dialogView.findViewById<View>(R.id.btnCancelPin)

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnCancelPin.setOnClickListener { alertDialog.dismiss() }

        btnSavePin.setOnClickListener {
            val currentPin = etCurrentPin.text.toString().trim()
            val newPin = etNewPin.text.toString().trim()
            val confirmPin = etConfirmNewPin.text.toString().trim()

            if (currentPin.isEmpty() || newPin.isEmpty() || confirmPin.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all PIN fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPin != confirmPin) {
                Toast.makeText(requireContext(), "New PINs do not match.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPin.length < 4) {
                Toast.makeText(requireContext(), "New PIN must be at least 4 digits.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSavePin.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val result = AuthManager.updatePin(requireContext(), currentPin, newPin)
                result.onSuccess {
                    Toast.makeText(requireContext(), "PIN updated successfully!", Toast.LENGTH_SHORT).show()
                    alertDialog.dismiss()
                }.onFailure { e ->
                    Toast.makeText(requireContext(), e.message ?: "Failed to update PIN.", Toast.LENGTH_SHORT).show()
                    btnSavePin.isEnabled = true
                }
            }
        }

        alertDialog.show()
    }

    private fun logout() {
        AuthManager.signOut(requireContext())
        val intent = Intent(requireContext(), SignInActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
