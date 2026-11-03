package com.dysrupit.ani

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dysrupit.ani.utils.AuthManager
import com.dysrupit.ani.utils.LanguageManager
import kotlinx.coroutines.launch

class SignInActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Auto-login logic
        val currentUser = AuthManager.getCurrentUser(this)
        if (currentUser != null) {
            val intent = Intent(this, MainTabActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_sign_in)

        val etContact: EditText = findViewById(R.id.etContact)
        val etPassword: EditText = findViewById(R.id.etPassword)

        findViewById<Button>(R.id.btnSignIn).setOnClickListener {
            val contact: String = etContact.text.toString()
            val password: String = etPassword.text.toString()

            if (contact.isBlank() || password.isBlank()) {
                Toast.makeText(this, getString(R.string.error_fill_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val button = findViewById<Button>(R.id.btnSignIn)
            button.isEnabled = false
            lifecycleScope.launch {
                val result = AuthManager.signIn(this@SignInActivity, contact, password)
                result.onSuccess {
                    val intent = Intent(this@SignInActivity, MainTabActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }.onFailure { error ->
                    Toast.makeText(
                        this@SignInActivity,
                        error.message ?: getString(R.string.error_invalid_auth),
                        Toast.LENGTH_SHORT
                    ).show()
                    button.isEnabled = true
                }
            }
        }

        findViewById<TextView>(R.id.tvGoToSignUp).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }
}
