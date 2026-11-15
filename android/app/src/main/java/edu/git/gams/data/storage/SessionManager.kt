package edu.git.gams.data.storage

import android.content.Context
import android.content.SharedPreferences
import edu.git.gams.data.model.UserDto
import java.util.UUID

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gams_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_DEVICE_ID = "hardware_device_id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_COLLEGE_ID = "college_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_ROLE = "user_role"
        private const val KEY_DIVISION = "user_division"
        private const val KEY_BASE_URL = "custom_base_url"
    }

    /**
     * Retrieves or generates a persistent device UUID for hardware binding (FR-2).
     */
    fun getDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrEmpty()) {
            deviceId = "dev-" + UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun saveAuth(token: String, user: UserDto) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_COLLEGE_ID, user.collegeId)
            .putString(KEY_USER_NAME, user.name)
            .putString(KEY_ROLE, user.role)
            .putString(KEY_DIVISION, user.division)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getRole(): String? = prefs.getString(KEY_ROLE, null)

    fun getCollegeId(): String? = prefs.getString(KEY_COLLEGE_ID, null)

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun getDivision(): String = prefs.getString(KEY_DIVISION, "A") ?: "A"

    fun isLoggedIn(): Boolean = !getToken().isNullOrEmpty()

    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_COLLEGE_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_ROLE)
            .remove(KEY_DIVISION)
            .apply()
    }

    fun getSavedBaseUrl(): String? = prefs.getString(KEY_BASE_URL, null)

    fun saveBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }
}
