package edu.git.gams.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import edu.git.gams.data.api.ApiClient
import edu.git.gams.data.model.LoginRequest
import edu.git.gams.data.model.UserDto
import edu.git.gams.data.storage.SessionManager
import edu.git.gams.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    sessionManager: SessionManager,
    onLoginSuccess: (UserDto) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var isTeacher by remember { mutableStateOf(false) }
    var collegeId by remember { mutableStateOf("21CS001") }
    var password by remember { mutableStateOf("password123") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(ApiClient.getBaseUrl()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BackgroundTop,
                        BackgroundMid,
                        BackgroundBottom
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Rotated Diamond Accent Mark (Matches design reference)
            Canvas(modifier = Modifier.size(36.dp)) {
                val strokePx = 3.5.dp.toPx()
                val rectSize = size.minDimension * 0.58f
                val offsetVal = (size.minDimension - rectSize) / 2f
                rotate(45f) {
                    drawRoundRect(
                        color = BrandAccent,
                        topLeft = Offset(offsetVal, offsetVal),
                        size = Size(rectSize, rectSize),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(width = strokePx)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // App Name Branding
            Text(
                text = "GAMS",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle with sparkle
            Text(
                text = "Mark attendance with a tap ✨",
                fontSize = 14.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Main Slate Navy Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    // Welcome Header
                    Text(
                        text = "Welcome back 👋",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Dynamic Subheading based on role
                    Text(
                        text = if (isTeacher) {
                            "Sign in to mark and review your classes."
                        } else {
                            "Sign in to find the class your teacher started."
                        },
                        fontSize = 13.5.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Pill-shaped Segmented Role Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(SegmentTrack, RoundedCornerShape(14.dp))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Teacher Option (Left)
                        val teacherBg by animateColorAsState(
                            targetValue = if (isTeacher) SegmentActive else Color.Transparent,
                            label = "teacherBg"
                        )
                        val teacherText by animateColorAsState(
                            targetValue = if (isTeacher) TextPrimary else TextMuted,
                            label = "teacherText"
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(teacherBg, RoundedCornerShape(10.dp))
                                .clickable {
                                    if (!isTeacher) {
                                        isTeacher = true
                                        collegeId = "T101"
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isTeacher) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "🧑‍🏫 Teacher",
                                fontSize = 13.5.sp,
                                fontWeight = if (isTeacher) FontWeight.SemiBold else FontWeight.Medium,
                                color = teacherText
                            )
                        }

                        // Student Option (Right)
                        val studentBg by animateColorAsState(
                            targetValue = if (!isTeacher) SegmentActive else Color.Transparent,
                            label = "studentBg"
                        )
                        val studentText by animateColorAsState(
                            targetValue = if (!isTeacher) TextPrimary else TextMuted,
                            label = "studentText"
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(studentBg, RoundedCornerShape(10.dp))
                                .clickable {
                                    if (isTeacher) {
                                        isTeacher = false
                                        collegeId = "21CS001"
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (!isTeacher) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "🎓 Student",
                                fontSize = 13.5.sp,
                                fontWeight = if (!isTeacher) FontWeight.SemiBold else FontWeight.Medium,
                                color = studentText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Identifier Field (USN/Email or Employee ID)
                    OutlinedTextField(
                        value = collegeId,
                        onValueChange = { collegeId = it },
                        placeholder = {
                            Text(
                                text = if (isTeacher) "Your email or employee ID" else "Your USN or email",
                                fontSize = 14.sp,
                                color = TextMuted
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputSurface,
                            unfocusedContainerColor = InputSurface,
                            focusedBorderColor = InputBorderFocused,
                            unfocusedBorderColor = InputBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = BrandBlue
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password Field with Eye Toggle
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = {
                            Text(
                                text = "Password",
                                fontSize = 14.sp,
                                color = TextMuted
                            )
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InputSurface,
                            unfocusedContainerColor = InputSurface,
                            focusedBorderColor = InputBorderFocused,
                            unfocusedBorderColor = InputBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = BrandBlue
                        )
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = ErrorRed,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp
                        )
                        if (errorMessage?.contains("Connection error", ignoreCase = true) == true) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Target: ${ApiClient.getBaseUrl()} (Tap to change)",
                                color = AccentLink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    serverUrlInput = ApiClient.getBaseUrl()
                                    showServerDialog = true
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Button: "Let's go"
                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            val deviceId = sessionManager.getDeviceId()

                            coroutineScope.launch {
                                try {
                                    val res = ApiClient.service.login(
                                        LoginRequest(collegeId.trim(), password, deviceId)
                                    )
                                    if (res.isSuccessful && res.body()?.success == true) {
                                        val body = res.body()!!
                                        sessionManager.saveAuth(body.token!!, body.user!!)
                                        onLoginSuccess(body.user)
                                    } else {
                                        errorMessage = res.body()?.message ?: "Login failed. Code: ${res.code()}"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Cannot connect to server (${ApiClient.getBaseUrl()}): ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading && collegeId.isNotBlank() && password.isNotBlank(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonPrimary,
                            disabledContainerColor = ButtonPrimaryDisabled
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = TextPrimary,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Text(
                                text = "Let's go",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer Link
            Text(
                text = if (isTeacher) "New here? Create a teacher account" else "New here? Create a student account",
                color = AccentLink,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable {
                        // Inform user or handle registration
                    }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Device Binding identifier (subtle helper)
            Text(
                text = "Device Bound ID: ${sessionManager.getDeviceId().take(12)}...",
                color = TextMuted.copy(alpha = 0.5f),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Server Settings button (top-right, placed above Column with zIndex so it reliably receives clicks)
        IconButton(
            onClick = {
                serverUrlInput = ApiClient.getBaseUrl()
                showServerDialog = true
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(10f)
                .padding(top = 8.dp)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Server Settings",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Server URL Config Dialog (Kept accessible for backend configuration)
        if (showServerDialog) {
            AlertDialog(
                onDismissRequest = { showServerDialog = false },
                containerColor = CardSurface,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                title = { Text("Server Backend URL") },
                text = {
                    Column {
                        Text(
                            "Enter the IP address or host of your backend server:",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = { serverUrlInput = it },
                            placeholder = { Text("https://gams-backend-keso.onrender.com/", color = TextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = InputSurface,
                                unfocusedContainerColor = InputSurface,
                                focusedBorderColor = InputBorderFocused,
                                unfocusedBorderColor = InputBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { serverUrlInput = "https://gams-backend-keso.onrender.com/" },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Use Cloud Server (Render)", fontSize = 12.sp, color = AccentLink)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            ApiClient.setBaseUrl(serverUrlInput)
                            sessionManager.saveBaseUrl(serverUrlInput)
                            showServerDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ButtonPrimary)
                    ) {
                        Text("Save", color = TextPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showServerDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }
    }
}
