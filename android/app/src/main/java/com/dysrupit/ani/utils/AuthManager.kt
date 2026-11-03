package com.dysrupit.ani.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

val Context.authDataStore by preferencesDataStore(name = "ani_auth")

data class AniUser(
    val fullName: String,
    val contact: String,
    val address: String,
    val password: String = "",
    val profileImageUri: String = "",
    val plots: String = "3",
    val hectares: String = "0.8",
    val crops: String = ""
)

object AuthManager {
    private val CURRENT_USER_KEY = stringPreferencesKey("current_user")
    private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")

    suspend fun signUp(context: Context, user: AniUser): Result<AniUser> =
        withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.signUp(
                    SignUpRequest(
                        fullName = user.fullName,
                        contact = user.contact,
                        address = user.address,
                        password = user.password,
                        plots = user.plots,
                        hectares = user.hectares,
                        crops = user.crops
                    )
                )
                if (!response.isSuccessful) {
                    return@withContext Result.failure(apiError(response.code(), response.errorBody()?.string()))
                }
                val authResponse = response.body()
                    ?: return@withContext Result.failure(IOException("Registration returned an empty response."))
                saveSession(context, authResponse)
                Result.success(authResponse.user.toAniUser())
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    suspend fun signIn(context: Context, contact: String, password: String): Result<AniUser> =
        withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.signIn(
                    SignInRequest(contact = contact.trim(), password = password)
                )
                if (!response.isSuccessful) {
                    return@withContext Result.failure(apiError(response.code(), response.errorBody()?.string()))
                }
                val authResponse = response.body()
                    ?: return@withContext Result.failure(IOException("Sign-in returned an empty response."))
                saveSession(context, authResponse)
                Result.success(authResponse.user.toAniUser())
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    fun updateUser(context: Context, updatedUser: AniUser): Result<AniUser> = runBlocking {
        val current = getCurrentUser(context)
            ?: return@runBlocking Result.failure(Exception("No user is signed in."))
        val updated = updatedUser.copy(
            contact = updatedUser.contact.trim(),
            password = "",
            profileImageUri = updatedUser.profileImageUri.ifBlank { current.profileImageUri }
        )
        val savedUser = context.authDataStore.data.first()[CURRENT_USER_KEY]
            ?: return@runBlocking Result.failure(Exception("No user is signed in."))
        val userId = JSONObject(savedUser).optString("userId")
        context.authDataStore.edit { settings ->
            settings[CURRENT_USER_KEY] = updated.toJson(userId)
        }
        Result.success(updated)
    }

    suspend fun updatePin(
        context: Context,
        currentPin: String,
        newPin: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken(context)
                ?: return@withContext Result.failure(IOException("Sign in again to change your PIN."))
            val response = RetrofitClient.instance.changePassword(
                authorization = "Bearer $accessToken",
                request = ChangePasswordRequest(currentPin, newPin)
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(apiError(response.code(), response.errorBody()?.string()))
            }
            Result.success(true)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun signOut(context: Context) = runBlocking {
        context.authDataStore.edit { settings ->
            settings.remove(CURRENT_USER_KEY)
            settings.remove(ACCESS_TOKEN_KEY)
        }
    }

    fun getCurrentUser(context: Context): AniUser? = runBlocking {
        val prefs = context.authDataStore.data.first()
        if (prefs[ACCESS_TOKEN_KEY].isNullOrBlank()) return@runBlocking null
        val json = prefs[CURRENT_USER_KEY] ?: return@runBlocking null
        val obj = JSONObject(json)
        AniUser(
            fullName = obj.optString("fullName"),
            contact = obj.optString("contact"),
            address = obj.optString("address"),
            profileImageUri = obj.optString("profileImageUri"),
            plots = obj.optString("plots").ifBlank { "3" },
            hectares = obj.optString("hectares").ifBlank { "0.8" },
            crops = obj.optString("crops")
        )
    }

    fun getAccessToken(context: Context): String? = runBlocking {
        context.authDataStore.data.first()[ACCESS_TOKEN_KEY]
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun saveSession(context: Context, response: AuthApiResponse) {
        val user = response.user.toAniUser()
        context.authDataStore.edit { settings ->
            settings[ACCESS_TOKEN_KEY] = response.access_token
            settings[CURRENT_USER_KEY] = user.toJson(response.user.userId)
        }
    }

    private fun AniUser.toJson(userId: String): String = JSONObject().apply {
        put("userId", userId)
        put("fullName", fullName)
        put("contact", contact)
        put("address", address)
        put("profileImageUri", profileImageUri)
        put("plots", plots)
        put("hectares", hectares)
        put("crops", crops)
    }.toString()

    private fun AuthUser.toAniUser() = AniUser(
        fullName = fullName,
        contact = contact,
        address = address,
        plots = plots,
        hectares = hectares,
        crops = crops
    )

    private fun apiError(statusCode: Int, body: String?): IOException {
        val detail = try {
            body?.let { JSONObject(it).optString("detail").takeIf(String::isNotBlank) }
        } catch (_: JSONException) {
            body?.takeIf(String::isNotBlank)
        }
        return IOException(detail ?: "Account service returned HTTP $statusCode.")
    }
}
