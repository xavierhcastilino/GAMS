package edu.git.gams.data.api

import edu.git.gams.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/sessions/teacher/courses")
    suspend fun getTeacherCourses(
        @Header("Authorization") token: String
    ): Response<CoursesResponse>

    @GET("api/sessions/teacher/current")
    suspend fun getTeacherCurrentSession(
        @Header("Authorization") token: String
    ): Response<TeacherCurrentSessionResponse>

    @POST("api/sessions/start")
    suspend fun startSession(
        @Header("Authorization") token: String,
        @Body request: StartSessionRequest
    ): Response<StartSessionResponse>

    @GET("api/sessions/{id}/live")
    suspend fun getLiveSession(
        @Header("Authorization") token: String,
        @Path("id") sessionId: String
    ): Response<LiveSessionResponse>

    @GET("api/sessions/active/current")
    suspend fun getActiveSession(
        @Header("Authorization") token: String
    ): Response<ActiveSessionResponse>

    @POST("api/sessions/{id}/close")
    suspend fun closeSession(
        @Header("Authorization") token: String,
        @Path("id") sessionId: String
    ): Response<SimpleApiResponse>

    @POST("api/sessions/demo/start")
    suspend fun startDemoSession(
        @Header("Authorization") token: String
    ): Response<SimpleApiResponse>

    @POST("api/attendance/mark")
    suspend fun markAttendance(
        @Header("Authorization") token: String,
        @Body request: MarkAttendanceRequest
    ): Response<MarkAttendanceResponse>

    @GET("api/attendance/sessions/{id}/review")
    suspend fun getReviewQueue(
        @Header("Authorization") token: String,
        @Path("id") sessionId: String
    ): Response<ReviewQueueResponse>

    @PATCH("api/attendance/{id}/review")
    suspend fun submitReviewDecision(
        @Header("Authorization") token: String,
        @Path("id") attendanceId: String,
        @Body request: ReviewDecisionRequest
    ): Response<SimpleApiResponse>

    @POST("api/attendance/manual")
    suspend fun markManualAttendance(
        @Header("Authorization") token: String,
        @Body request: ManualAttendanceRequest
    ): Response<SimpleApiResponse>
}
