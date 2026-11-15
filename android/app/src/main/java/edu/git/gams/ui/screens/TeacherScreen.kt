package edu.git.gams.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.git.gams.audio.UltrasonicTransmitter
import edu.git.gams.data.api.ApiClient
import edu.git.gams.data.model.*
import edu.git.gams.data.storage.SessionManager
import edu.git.gams.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TeacherViewMode {
    SETUP,           // Pre-session setup form
    BROADCASTING,    // Ultrasonic frequency broadcast active with countdown
    SESSION_ENDED    // Dedicated Post-Session Absent Students Roster
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherScreen(
    sessionManager: SessionManager,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val token = "Bearer " + (sessionManager.getToken() ?: "")

    val transmitter = remember { UltrasonicTransmitter() }
    DisposableEffect(Unit) {
        onDispose {
            transmitter.stop()
        }
    }

    var courses by remember { mutableStateOf<List<CourseDto>>(emptyList()) }
    var selectedCourse by remember { mutableStateOf<CourseDto?>(null) }
    var division by remember { mutableStateOf("A") }
    var period by remember { mutableStateOf(1) }
    var chosenDurationSeconds by remember { mutableStateOf(120) } // Default 2 minutes
    var isStarting by remember { mutableStateOf(false) }

    // Screen View State
    var viewMode by remember { mutableStateOf(TeacherViewMode.SETUP) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var sessionRemainingSeconds by remember { mutableStateOf(0) }
    var currentToken by remember { mutableStateOf("") }
    var tokenRotatingRemainingSecs by remember { mutableStateOf(0) }

    // Roster Lists
    var presentList by remember { mutableStateOf<List<StudentRosterItemDto>>(emptyList()) }
    var reviewList by remember { mutableStateOf<List<StudentRosterItemDto>>(emptyList()) }
    var absentList by remember { mutableStateOf<List<StudentRosterItemDto>>(emptyList()) }
    var reviewsQueue by remember { mutableStateOf<List<ReviewItemDto>>(emptyList()) }

    // Expandable Present list toggle in SESSION_ENDED mode
    var showPresentListInEndedMode by remember { mutableStateOf(false) }

    // Manual Override Dialog State
    var studentForManualOverride by remember { mutableStateOf<StudentRosterItemDto?>(null) }
    var manualReason by remember { mutableStateOf("Microphone failed to decode tone") }
    var isSubmittingManual by remember { mutableStateOf(false) }

    // Recent session info on startup
    var recentSessionId by remember { mutableStateOf<String?>(null) }
    var recentSessionCourseCode by remember { mutableStateOf<String?>(null) }

    // Load Teacher's Courses and Check Recent Session on startup
    LaunchedEffect(Unit) {
        try {
            val res = ApiClient.service.getTeacherCourses(token)
            if (res.isSuccessful && res.body()?.success == true) {
                courses = res.body()!!.courses
                if (courses.isNotEmpty()) {
                    selectedCourse = courses[0]
                }
            }
        } catch (_: Exception) {}



        try {
            val curRes = ApiClient.service.getTeacherCurrentSession(token)
            if (curRes.isSuccessful && curRes.body()?.success == true) {
                val s = curRes.body()!!.session
                if (s != null) {
                    recentSessionId = s.id
                    recentSessionCourseCode = s.courseId?.code ?: "CS601"
                    if (s.status == "active") {
                        activeSessionId = s.id
                        viewMode = TeacherViewMode.BROADCASTING
                        sessionRemainingSeconds = 60
                        transmitter.start(currentToken, coroutineScope)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // Polling live session stats & updating rotating ultrasonic token
    LaunchedEffect(activeSessionId) {
        val sId = activeSessionId ?: return@LaunchedEffect
        while (activeSessionId != null) {
            try {
                val liveRes = ApiClient.service.getLiveSession(token, sId)
                if (liveRes.isSuccessful && liveRes.body()?.success == true) {
                    val body = liveRes.body()!!
                    val newTok = body.currentToken ?: ""
                    if (viewMode == TeacherViewMode.BROADCASTING && newTok.isNotEmpty() && newTok != currentToken) {
                        currentToken = newTok
                        transmitter.updateToken(newTok)
                    }
                    tokenRotatingRemainingSecs = body.remainingSeconds
                    presentList = body.presentList
                    reviewList = body.reviewList
                    absentList = body.absentList
                }

                // Fetch review queue details
                val reviewRes = ApiClient.service.getReviewQueue(token, sId)
                if (reviewRes.isSuccessful && reviewRes.body()?.success == true) {
                    reviewsQueue = reviewRes.body()!!.reviews
                }
            } catch (_: Exception) {}
            delay(2000)
        }
    }

    // Ultrasonic Session Broadcast Countdown Timer
    LaunchedEffect(viewMode, sessionRemainingSeconds) {
        if (viewMode == TeacherViewMode.BROADCASTING && sessionRemainingSeconds > 0) {
            delay(1000)
            sessionRemainingSeconds--
            if (sessionRemainingSeconds <= 0) {
                // Time period has ended!
                transmitter.stop()
                viewMode = TeacherViewMode.SESSION_ENDED
                Toast.makeText(context, "Ultrasonic session period ended. Viewing absent students in Div $division.", Toast.LENGTH_LONG).show()

                // Finalize session on backend to seal absentees
                val sId = activeSessionId
                if (sId != null) {
                    try {
                        ApiClient.service.closeSession(token, sId)
                        // Refresh live roster to show finalized absentees
                        val liveRes = ApiClient.service.getLiveSession(token, sId)
                        if (liveRes.isSuccessful && liveRes.body()?.success == true) {
                            val body = liveRes.body()!!
                            presentList = body.presentList
                            reviewList = body.reviewList
                            absentList = body.absentList
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

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
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(BrandBlue.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .border(1.dp, BrandBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cast,
                                    contentDescription = null,
                                    tint = BrandAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "GAMS",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Faculty: ${sessionManager.getUserName() ?: "Prof. Ananya Sen"}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            transmitter.stop()
                            sessionManager.clear()
                            onLogout()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Logout",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BackgroundTop.copy(alpha = 0.85f)
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (viewMode) {
                    // ==========================================
                    // 1. SETUP STATE: CREATE SESSION
                    // ==========================================
                    TeacherViewMode.SETUP -> {
                        // Optional Banner to view recent session absentees if available
                        if (recentSessionId != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CardSurface,
                                border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeSessionId = recentSessionId
                                        viewMode = TeacherViewMode.SESSION_ENDED
                                        coroutineScope.launch {
                                            try {
                                                val liveRes = ApiClient.service.getLiveSession(token, recentSessionId!!)
                                                if (liveRes.isSuccessful && liveRes.body()?.success == true) {
                                                    val body = liveRes.body()!!
                                                    presentList = body.presentList
                                                    reviewList = body.reviewList
                                                    absentList = body.absentList
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = BrandAccent, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Previous Session (${recentSessionCourseCode ?: "CS601"} - Div A)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Click to inspect remaining absent students list",
                                            fontSize = 11.5.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(BrandBlue, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Create Attendance Session",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text("Selected Course", fontSize = 12.5.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = InputSurface,
                                    border = BorderStroke(1.dp, InputBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.School, contentDescription = null, tint = BrandAccent, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = if (selectedCourse != null) "${selectedCourse!!.code} - ${selectedCourse!!.name}" else "CS601 - Distributed Systems",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = division,
                                        onValueChange = { division = it.uppercase() },
                                        label = { Text("Division", color = TextSecondary) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            focusedBorderColor = BrandBlue,
                                            unfocusedBorderColor = InputBorder,
                                            focusedContainerColor = InputSurface,
                                            unfocusedContainerColor = InputSurface
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = period.toString(),
                                        onValueChange = { period = it.toIntOrNull() ?: 1 },
                                        label = { Text("Period", color = TextSecondary) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            focusedBorderColor = BrandBlue,
                                            unfocusedBorderColor = InputBorder,
                                            focusedContainerColor = InputSurface,
                                            unfocusedContainerColor = InputSurface
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Ultrasonic Broadcast Duration Selector
                                Text("Ultrasonic Frequency Session Period", fontSize = 12.5.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(60 to "1 Min", 120 to "2 Mins", 180 to "3 Mins", 300 to "5 Mins").forEach { (secs, label) ->
                                        val isSelected = chosenDurationSeconds == secs
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isSelected) BrandBlue else InputSurface,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) BrandAccent else InputBorder,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable { chosenDurationSeconds = secs }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else TextSecondary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(22.dp))

                                Button(
                                    onClick = {
                                        val cId = selectedCourse?.id ?: courses.firstOrNull()?.id ?: return@Button
                                        isStarting = true
                                        coroutineScope.launch {
                                            try {
                                                val res = ApiClient.service.startSession(
                                                    token,
                                                    StartSessionRequest(cId, division, period)
                                                )
                                                if (res.isSuccessful && res.body()?.success == true) {
                                                    val body = res.body()!!
                                                    activeSessionId = body.session?.id
                                                    recentSessionId = body.session?.id
                                                    currentToken = body.currentToken ?: ""
                                                    sessionRemainingSeconds = chosenDurationSeconds
                                                    viewMode = TeacherViewMode.BROADCASTING
                                                    transmitter.start(currentToken, coroutineScope)
                                                } else {
                                                    Toast.makeText(context, "Failed to start: ${res.message()}", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isStarting = false
                                            }
                                        }
                                    },
                                    enabled = !isStarting && (selectedCourse != null || courses.isNotEmpty()),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ButtonPrimary,
                                        disabledContainerColor = ButtonPrimaryDisabled
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    if (isStarting) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = TextPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Start Ultrasonic Session (Div $division)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // 2. BROADCASTING STATE: RADAR & COUNTDOWN
                    // ==========================================
                    TeacherViewMode.BROADCASTING -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Pulsing acoustic transmission indicator
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 0.85f,
                                    targetValue = 1.25f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1200, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "scale"
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size((14 * scale).dp)
                                            .background(SuccessGreen, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Broadcasting Ultrasonic Signal (18.0 - 19.5 kHz)",
                                        color = SuccessGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Current rotating acoustic token
                                Text(
                                    text = currentToken.ifEmpty { "TRANSMITTING..." },
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    letterSpacing = 4.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "HMAC Token rotates in ${tokenRotatingRemainingSecs}s",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Session Countdown Clock
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SegmentTrack,
                                    border = BorderStroke(1.dp, InputBorder),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Timer, contentDescription = null, tint = BrandAccent, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val mins = sessionRemainingSeconds / 60
                                        val secs = sessionRemainingSeconds % 60
                                        Text(
                                            text = "Broadcast Ends In: %02d:%02d".format(mins, secs),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sessionRemainingSeconds <= 15) ErrorRed else BrandAccent
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Live Mini Counters
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = presentList.size.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                        Text(text = "Marked Present", fontSize = 11.5.sp, color = TextSecondary)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = absentList.size.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                                        Text(text = "Absent Remaining", fontSize = 11.5.sp, color = TextSecondary)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = reviewList.size.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                                        Text(text = "In Review", fontSize = 11.5.sp, color = TextSecondary)
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            sessionRemainingSeconds += 30
                                            Toast.makeText(context, "+30s added to broadcast", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                        border = BorderStroke(1.dp, InputBorder),
                                        modifier = Modifier.weight(1f).height(44.dp)
                                    ) {
                                        Text("+30s", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    // Button to End Session and immediately view Absent Students
                                    Button(
                                        onClick = {
                                            transmitter.stop()
                                            viewMode = TeacherViewMode.SESSION_ENDED
                                            val sId = activeSessionId
                                            if (sId != null) {
                                                coroutineScope.launch {
                                                    try {
                                                        ApiClient.service.closeSession(token, sId)
                                                        val liveRes = ApiClient.service.getLiveSession(token, sId)
                                                        if (liveRes.isSuccessful && liveRes.body()?.success == true) {
                                                            val body = liveRes.body()!!
                                                            presentList = body.presentList
                                                            reviewList = body.reviewList
                                                            absentList = body.absentList
                                                        }
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                            Toast.makeText(context, "Session ended. Viewing absent students list.", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(2f).height(44.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("End & View Absentees", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // HEADING: LIVE REMAINING ABSENT STUDENTS IN DIVISION A
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LIVE REMAINING ABSENT (DIV $division)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp,
                                color = ErrorRed,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${absentList.size} remaining",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // LIVE ABSENT STUDENTS LIST (Real-time updates as students submit)
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (absentList.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "All students have checked in! 🎉",
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(text = "0 remaining absentees in Division $division.", color = TextSecondary, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(absentList, key = { it.collegeId }) { student ->
                                        AbsentStudentCard(
                                            student = student,
                                            onMarkPresentClick = {
                                                studentForManualOverride = student
                                                manualReason = "Microphone failed to decode tone"
                                            }
                                        )
                                    }

                                    // Optional toggle to view already marked present students
                                    item {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = CardSurface,
                                            border = BorderStroke(1.dp, CardBorder),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showPresentListInEndedMode = !showPresentListInEndedMode }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "View Marked Present Students (${presentList.size})",
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = TextPrimary
                                                    )
                                                }
                                                Icon(
                                                    imageVector = if (showPresentListInEndedMode) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    tint = TextSecondary
                                                )
                                            }
                                        }
                                    }

                                    if (showPresentListInEndedMode) {
                                        items(presentList, key = { "live_pres_" + it.collegeId }) { student ->
                                            PresentStudentCard(student = student)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // 3. POST-SESSION ENDED: ABSENT STUDENTS ROSTER (DIVISION A)
                    // ==========================================
                    TeacherViewMode.SESSION_ENDED -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Red/Coral Top Banner explicitly showing Session Ended
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface),
                                border = BorderStroke(1.5.dp, ErrorRed.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(ErrorRed.copy(alpha = 0.2f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.StopCircle, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Ultrasonic Session Ended",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "Division $division Attendance Roster",
                                                    fontSize = 12.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                        }

                                        // Total KPI Pill
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = SegmentTrack,
                                            border = BorderStroke(1.dp, CardBorder)
                                        ) {
                                            Text(
                                                text = "${presentList.size}/${presentList.size + absentList.size} Present",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (absentList.isEmpty()) SuccessGreen else BrandAccent,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "Acoustic broadcast has finished. The students listed below did not check in and are marked ABSENT. You can manually mark present anyone who faced microphone or device issues.",
                                        fontSize = 11.5.sp,
                                        color = TextSecondary,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2-Metric Summary Pill: Absent count vs Present count
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ErrorRed.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = "❌ Absent: ", fontSize = 13.sp, color = ErrorRed, fontWeight = FontWeight.SemiBold)
                                        Text(text = "${absentList.size} students", fontSize = 14.sp, color = ErrorRed, fontWeight = FontWeight.ExtraBold)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SuccessGreen.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = "✅ Present: ", fontSize = 13.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                                        Text(text = "${presentList.size} students", fontSize = 14.sp, color = SuccessGreen, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // HEADING: REMAINING ABSENT STUDENTS IN DIVISION A
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ABSENT STUDENTS (DIV $division)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = ErrorRed,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "${absentList.size} remaining",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // ABSENT STUDENTS LIST (Primary focus)
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (absentList.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = SuccessGreen,
                                                modifier = Modifier.size(52.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                "🎉 100% Attendance in Div $division!",
                                                color = TextPrimary,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("All enrolled students are marked present.", color = TextSecondary, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(absentList, key = { it.collegeId }) { student ->
                                            AbsentStudentCard(
                                                student = student,
                                                onMarkPresentClick = {
                                                    studentForManualOverride = student
                                                    manualReason = "Microphone failed to decode tone"
                                                }
                                            )
                                        }

                                        // Expandable Present Students section
                                        item {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = CardSurface,
                                                border = BorderStroke(1.dp, CardBorder),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showPresentListInEndedMode = !showPresentListInEndedMode }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "View Marked Present Students (${presentList.size})",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextPrimary
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = if (showPresentListInEndedMode) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                        contentDescription = null,
                                                        tint = TextSecondary
                                                    )
                                                }
                                            }
                                        }

                                        if (showPresentListInEndedMode) {
                                            items(presentList, key = { "present_" + it.collegeId }) { student ->
                                                PresentStudentCard(student = student)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Bottom Navigation Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewMode = TeacherViewMode.BROADCASTING
                                        sessionRemainingSeconds = 60
                                        transmitter.start(currentToken, coroutineScope)
                                        Toast.makeText(context, "Resumed broadcasting for 1 minute", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandAccent),
                                    border = BorderStroke(1.dp, BrandBlue),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = BrandAccent)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resume (+1m)", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        transmitter.stop()
                                        viewMode = TeacherViewMode.SETUP
                                        activeSessionId = null
                                        Toast.makeText(context, "Attendance finalized and saved to database.", Toast.LENGTH_LONG).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ButtonPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.3f).height(44.dp)
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Finish & Exit", fontSize = 12.5.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // MANUAL OVERRIDE POPUP DIALOG
        // ==========================================
        studentForManualOverride?.let { student ->
            AlertDialog(
                onDismissRequest = {
                    if (!isSubmittingManual) studentForManualOverride = null
                },
                containerColor = CardSurface,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(SuccessGreen.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Mark Present (Manual)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("${student.name} (${student.collegeId})", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Select reason for manual attendance override:",
                            fontSize = 12.5.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val reasons = listOf(
                            "Microphone failed to decode tone",
                            "Acoustic noise / distance issue",
                            "Biometric face recognition issue",
                            "Student phone battery drained / issue",
                            "Direct teacher in-person confirmation"
                        )

                        reasons.forEach { r ->
                            val isSelected = manualReason == r
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { manualReason = r }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { manualReason = r },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = BrandBlue,
                                        unselectedColor = TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = r,
                                    fontSize = 12.sp,
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val sId = activeSessionId ?: recentSessionId ?: return@Button
                            isSubmittingManual = true
                            coroutineScope.launch {
                                try {
                                    val res = ApiClient.service.markManualAttendance(
                                        token,
                                        ManualAttendanceRequest(
                                            sessionId = sId,
                                            collegeId = student.collegeId,
                                            reason = manualReason
                                        )
                                    )
                                    if (res.isSuccessful && res.body()?.success == true) {
                                        Toast.makeText(
                                            context,
                                            "✓ ${student.name} marked Present!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        // Update local lists immediately
                                        absentList = absentList.filter { it.collegeId != student.collegeId }
                                        presentList = presentList + student.copy(
                                            status = "manual",
                                            reviewReason = manualReason
                                        )
                                        studentForManualOverride = null
                                    } else {
                                        Toast.makeText(context, "Override failed: ${res.message()}", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSubmittingManual = false
                                }
                            }
                        },
                        enabled = !isSubmittingManual,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSubmittingManual) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Confirm & Mark Present", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { studentForManualOverride = null },
                        enabled = !isSubmittingManual
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
fun AbsentStudentCard(
    student: StudentRosterItemDto,
    onMarkPresentClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(ErrorRed.copy(alpha = 0.15f), CircleShape)
                        .border(1.2.dp, ErrorRed.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = student.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SegmentTrack,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = student.collegeId,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ErrorRed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "ABSENT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 1-Tap Manual Override Button
            Button(
                onClick = onMarkPresentClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Mark Present",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun PresentStudentCard(student: StudentRosterItemDto) {
    val isManual = student.status == "manual"
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isManual) BrandBlue.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (isManual) BrandBlue.copy(alpha = 0.4f) else SuccessGreen.copy(alpha = 0.4f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isManual) Icons.Default.Edit else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isManual) BrandAccent else SuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = student.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = student.collegeId,
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isManual) BrandBlue.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f),
                    border = BorderStroke(
                        1.dp,
                        if (isManual) BrandAccent.copy(alpha = 0.4f) else SuccessGreen.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = if (isManual) "Manual Override" else {
                            val score = student.matchScore
                            if (score != null && score > 0) "Verified (${(score * 100).toInt()}%)" else "Verified"
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isManual) BrandAccent else SuccessGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (!student.reviewReason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = student.reviewReason,
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
