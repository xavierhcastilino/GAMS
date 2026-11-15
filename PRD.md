# Product Requirements Document: Proximity-Verified Attendance System for GIT College

**Project type:** Major Phase Project | **Platform:** Android (Kotlin) + Node.js backend 

## 1. Overview & Solution
Roll calls waste lecture time and proxy attendance is easy via shared codes or borrowed phones. This system marks attendance in two steps:
1. **Proximity Proof:** The teacher's phone plays a near-ultrasonic tone (18.0 to 19.5 kHz) carrying a rotating cryptographic token every 15 to 30 seconds. The student taps "I'm in class" to listen and decode the token.
2. **Identity Proof:** Decoding a valid token unlocks the live camera (no gallery or file picker). The server's Python face service compares the live selfie against the student's enrolled college ID embedding. Strong matches mark the student present; uncertain matches go to the teacher's review queue. Students whose phones fail to decode the audio are added manually by the teacher at the end of the session.

**Non-Goals:** iOS app, ERP/LMS integration, Bluetooth (BLE) checks, active liveness challenges (blink/head-turn), or Android biometric unlock as identity proof.

---

## 2. User Roles & Core Workflows

| Role | Core Responsibilities |
| :--- | :--- |
| **Teacher** | Start session by course/division/period (emits ultrasonic signal); view live present and review counts; approve/reject flagged selfies in one tap; manually mark leftover students; close session to save finalized records to the centralized database. |
| **Student** | Receive session push notification; tap "I'm in class" to decode the ultrasonic token; take a live selfie; view attendance status and subject-wise history. |
| **Admin / HOD** | Import users, courses, and timetables; enroll student reference photos; reset bound devices. |

---

## 3. Core Functional Requirements (MVP)

| ID | Category | Requirement |
| :--- | :--- | :--- |
| **FR-1** | Auth & Roles | Login with college ID and password; roles: Student, Teacher, Admin; JWT with refresh tokens. |
| **FR-2** | Device Binding | Student account binds to one device via an Android Keystore key at first login; resets require Admin approval. |
| **FR-3** | Enrollment | Reference photos enrolled only by Admin; first login requires microphone, camera, and face data consent. |
| **FR-4** | Transmission | Teacher starts/stops session; foreground service emits rotating HMAC ultrasonic token every 15–30 seconds. |
| **FR-5** | Reception | Student foreground service listens for up to 30 seconds using `AudioRecord` (`UNPROCESSED` where supported) and a Goertzel/FFT detector. |
| **FR-6** | Strict Gate | Camera unlocks *only* after decoding a valid token. If no token is heard, camera remains locked and student requests manual entry from the teacher. |
| **FR-7** | Live Capture | Live camera capture only. On-device ML Kit checks for one centered, well-lit face before upload (up to 2 retakes). Raw audio is never stored; selfie is deleted from the phone after upload. |
| **FR-8** | Verification | Server verifies enrollment, active session, JWT, bound device, and token validity (current and adjacent time windows). One record per student per session. |
| **FR-9** | Face Match | Python service compares selfie embedding against enrolled embedding. High score marks `present`; low score or error sends to the teacher review queue (never auto-rejected). |
| **FR-10** | Review & Close | Teacher approves/rejects queued selfies (side-by-side with ID photo) or manually adds students with a reason. Closing the session finalizes absentees in the database. |
| **FR-11** | Notifications | FCM push notifications sent when a session starts and when a review decision is made. |

---

## 4. Technical Design (Ultrasonic & ML)

* **Ultrasonic Signal:** 18.0 to 19.5 kHz band at a 48 kHz sample rate. Uses Frequency-Shift Keying (FSK) with a sync preamble, truncated HMAC payload, and checksum, repeated every 1–2 seconds.
* **On-Device Detection:** Google ML Kit Face Detection checks framing and lighting prior to upload (selfies resized to ~640 px).
* **Server Recognition:** Pretrained InsightFace (`buffalo_l` or `buffalo_s`) or MobileFaceNet running on the Python FastAPI server extracts embeddings and computes cosine similarity.

---

## 5. Architecture, Database & API

**Stack:** Kotlin Android App (MVVM, CameraX, Retrofit) <-> Node.js API (Express/Fastify) <-> MongoDB (Mongoose) + Python FastAPI Face Service + Firebase Cloud Messaging (FCM). Deployed via Docker Compose.

### 5.1 Database Collections (MongoDB)
* **`users`**: `collegeId`, `name`, `email`, `passwordHash`, `role`, `department`, `division`, `devicePublicKey`, `boundDeviceId`, `fcmToken`, `faceEmbedding`, `enrolledBy`
* **`courses`**: `code`, `name`, `department`, `semester`, `teacherIds`, `studentIds`
* **`sessions`**: `courseId`, `teacherId`, `division`, `period`, `startTime`, `endTime`, `status`, `secretKeyEncrypted`, `windowSeconds`
* **`attendance`**: `sessionId`, `studentId`, `status` (`present`, `review`, `approved`, `rejected`, `absent`, `manual`), `markedAt`, `matchScore`, `deviceId`, `reviewReason`, `reviewedBy`, `reviewNote` *(Unique index on `sessionId` + `studentId`)*
* **`auditLogs`**: `actorId`, `action`, `targetId`, `details`, `timestamp`

### 5.2 Core API Endpoints
* **Auth & Devices:** `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/devices/register`
* **Sessions:** `POST /api/sessions/start`, `POST /api/sessions/:id/close`, `GET /api/sessions/:id/live`
* **Attendance & Review:** `POST /api/attendance/mark`, `GET /api/sessions/:id/review`, `PATCH /api/attendance/:id/review`, `POST /api/attendance/manual`
* **Records & Admin:** `GET /api/reports/student/:id`, `GET /api/reports/session/:id`, `POST /api/admin/users/import`, `POST /api/admin/users/:id/photo`, `POST /api/admin/devices/:userId/reset`

---

## 6. Security, Privacy & Non-Functional Requirements

* **Security Controls:** Rotating ultrasonic tokens block remote link sharing and replay attacks; Keystore device binding blocks account sharing; server-side matching prevents client tampering; HTTPS/TLS, bcrypt hashes, and endpoint rate limiting.
* **Privacy (DPDP Act 2023):** Written college approval and user consent required. Only face embeddings are permanently stored; temporary selfies are deleted immediately after matching and session review. No raw audio or location data is recorded.
* **System Targets:** Android 8.0 (API 26)+ support; >= 85% automatic decode and match rate for in-room students; < 15% average review queue size; < 2% false acceptance rate.

---

## 7. Acceptance Criteria (v1)

1. Teacher can start a session, emit the rotating ultrasonic signal, and close the session to save records to MongoDB.
2. Camera remains locked until a valid ultrasonic token is decoded, and only permits live capture.
3. Valid token + strong face match automatically marks the student `present`.
4. Valid token + low face match score routes the selfie to the teacher's review queue alongside the enrolled ID photo.
5. Teacher can approve/reject queued selfies and manually mark remaining students.
6. Duplicate submissions, unbound devices, and expired tokens are blocked.
7. Entire backend stack launches via a single `docker-compose` command.

---

## 8. Future Scope (Deferred Features)
The following non-essential features have been deferred to post-MVP phases to keep the core build focused:
* **Audio Polish:** Burst fade-in/fade-out and transmission health indicators (battery/volume warnings).
* **Advanced ML & Analytics:** Passive anti-spoofing (`MiniFASNet`) and automated anomaly flagging (e.g., repeated near-threshold scores or last-second submissions).
* **Reporting & Admin Extras:** CSV/PDF file exports, automated shortage lists, end-of-session headcount mismatch checks, and low-attendance warning pushes.
* **Platform Expansion:** iOS app, ERP/LMS integration, Play Integrity API attestation, and audible backup tones.