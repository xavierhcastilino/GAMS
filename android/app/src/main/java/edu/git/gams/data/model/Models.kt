package edu.git.gams.data.model

import com.google.gson.annotations.SerializedName

// Authentication
data class LoginRequest(
    val collegeId: String,
    val password: String,
    val deviceId: String? = null
)

data class UserDto(
    val id: String,
    val collegeId: String,
    val name: String,
    val email: String,
    val role: String,
    val department: String,
    val division: String,
    val boundDeviceId: String?,
    val hasFaceEnrolled: Boolean
)

data class LoginResponse(
    val success: Boolean,
    val message: String?,
    val token: String?,
    val user: UserDto?
)

// Courses
data class CourseDto(
    @SerializedName("_id") val id: String,
    val code: String,
    val name: String,
    val department: String,
    val semester: Int
)

data class CoursesResponse(
    val success: Boolean,
    val courses: List<CourseDto>
)

// Sessions
data class StartSessionRequest(
    val courseId: String,
    val division: String = "A",
    val period: Int = 1,
    val windowSeconds: Int = 20
)

data class SessionInfoDto(
    val id: String,
    val courseId: String?,
    val division: String,
    val period: Int,
    val startTime: String,
    val status: String,
    val windowSeconds: Int
)

data class StartSessionResponse(
    val success: Boolean,
    val message: String?,
    val session: SessionInfoDto?,
    val currentToken: String?,
    val remainingSeconds: Int?
)

data class TeacherSessionDto(
    @SerializedName("_id") val id: String,
    val division: String,
    val period: Int,
    val status: String,
    val startTime: String?,
    val endTime: String?,
    val courseId: CourseDto?
)

data class TeacherCurrentSessionResponse(
    val success: Boolean,
    val session: TeacherSessionDto?,
    val status: String?
)

data class LiveStatsDto(
    val presentCount: Int,
    val reviewCount: Int,
    val absentCount: Int = 0,
    val totalCount: Int = 0
)

data class StudentRosterItemDto(
    val id: String,
    val collegeId: String,
    val name: String,
    val status: String,
    val matchScore: Float? = null,
    val reviewReason: String? = null,
    val markedAt: String? = null
)

data class LiveSessionResponse(
    val success: Boolean,
    val currentToken: String?,
    val remainingSeconds: Int,
    val stats: LiveStatsDto,
    val presentList: List<StudentRosterItemDto> = emptyList(),
    val reviewList: List<StudentRosterItemDto> = emptyList(),
    val absentList: List<StudentRosterItemDto> = emptyList()
)

data class ActiveSessionDto(
    val id: String,
    val course: CourseDto?,
    val division: String,
    val period: Int,
    val startTime: String,
    val windowSeconds: Int
)

data class ActiveSessionResponse(
    val success: Boolean,
    val activeSession: ActiveSessionDto?,
    val alreadyMarked: Boolean,
    val attendanceStatus: String?
)

// Attendance
data class MarkAttendanceRequest(
    val sessionId: String,
    val token: String,
    val deviceId: String,
    val selfieBase64: String
)

data class MarkAttendanceResponse(
    val success: Boolean,
    val status: String?, // "present", "review", "already_marked"
    val matchScore: Float?,
    val message: String?
)

data class ReviewStudentDto(
    val id: String,
    val collegeId: String,
    val name: String,
    val referencePhotoBase64: String?
)

data class ReviewItemDto(
    val attendanceId: String,
    val student: ReviewStudentDto,
    val matchScore: Float,
    val reviewReason: String?,
    val selfieBase64: String?,
    val markedAt: String
)

data class ReviewQueueResponse(
    val success: Boolean,
    val reviews: List<ReviewItemDto>
)

data class ReviewDecisionRequest(
    val decision: String, // "approved" or "rejected"
    val note: String? = null
)

data class ManualAttendanceRequest(
    val sessionId: String,
    val collegeId: String,
    val reason: String = "Manual entry"
)

data class SimpleApiResponse(
    val success: Boolean,
    val message: String?
)
