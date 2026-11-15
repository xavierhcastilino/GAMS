package edu.git.gams

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import edu.git.gams.data.api.ApiClient
import edu.git.gams.data.storage.SessionManager
import edu.git.gams.ui.screens.LoginScreen
import edu.git.gams.ui.screens.StudentScreen
import edu.git.gams.ui.screens.TeacherScreen
import edu.git.gams.ui.theme.GAMSTheme

class MainActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)

        // Restore saved server URL if available (and sanitize if legacy local IPs on device)
        val savedUrl = sessionManager.getSavedBaseUrl()
        if (!savedUrl.isNullOrBlank()) {
            if ((savedUrl.contains("10.0.2.2") && !ApiClient.isEmulator())
                || savedUrl.contains("192.168.")
                || savedUrl.contains("10.185.")) {
                val correctUrl = ApiClient.getDefaultBaseUrl()
                ApiClient.setBaseUrl(correctUrl)
                sessionManager.saveBaseUrl(correctUrl)
            } else {
                ApiClient.setBaseUrl(savedUrl)
            }
        } else {
            val defaultUrl = ApiClient.getDefaultBaseUrl()
            ApiClient.setBaseUrl(defaultUrl)
            sessionManager.saveBaseUrl(defaultUrl)
        }

        // Request runtime permissions for Camera and Microphone
        checkAndRequestPermissions()

        setContent {
            GAMSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentRole by remember { mutableStateOf(sessionManager.getRole()) }
                    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }

                    if (!isLoggedIn) {
                        LoginScreen(
                            sessionManager = sessionManager,
                            onLoginSuccess = { user ->
                                currentRole = user.role
                                isLoggedIn = true
                            }
                        )
                    } else if (currentRole == "teacher") {
                        TeacherScreen(
                            sessionManager = sessionManager,
                            onLogout = {
                                isLoggedIn = false
                                currentRole = null
                            }
                        )
                    } else {
                        StudentScreen(
                            sessionManager = sessionManager,
                            onLogout = {
                                isLoggedIn = false
                                currentRole = null
                            }
                        )
                    }
                }
            }
        }
    }


    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

    }
}