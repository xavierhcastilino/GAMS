const express = require('express');
const router = express.Router();
const multer = require('multer');
const axios = require('axios');
const Session = require('../models/Session');
const Attendance = require('../models/Attendance');
const User = require('../models/User');
const AuditLog = require('../models/AuditLog');
const { authenticate, requireRole } = require('../middleware/auth');
const { verifyToken } = require('../utils/token');

// Multer memory storage for in-memory selfie processing (no disk persistence)
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024 }, // 5 MB max
});

const MATCH_THRESHOLD = parseFloat(process.env.MATCH_THRESHOLD || '0.60');
let rawFaceUrl = process.env.FACE_SERVICE_URL || 'http://127.0.0.1:8000';
if (!rawFaceUrl.startsWith('http://') && !rawFaceUrl.startsWith('https://')) {
  rawFaceUrl = `http://${rawFaceUrl}`;
}
const FACE_SERVICE_URL = rawFaceUrl;

/**
 * POST /api/attendance/mark
 * Mark attendance with decoded ultrasonic token and live selfie (FR-8, FR-9).
 */
router.post('/mark', authenticate, upload.single('selfie'), async (req, res) => {
  try {
    const student = req.user;
    const { sessionId, token, deviceId, selfieBase64: bodySelfieBase64 } = req.body;

    if (!sessionId || !token) {
      return res.status(400).json({
        success: false,
        message: 'sessionId and ultrasonic token are required.',
      });
    }

    // 1. Verify Active Session
    const session = await Session.findById(sessionId);
    if (!session || session.status !== 'active') {
      return res.status(400).json({
        success: false,
        code: 'SESSION_INACTIVE',
        message: 'Session is not active or does not exist.',
      });
    }

    // 2. Verify Bound Device (FR-2, FR-8)
    if (student.boundDeviceId && deviceId && student.boundDeviceId !== deviceId) {
      return res.status(403).json({
        success: false,
        code: 'DEVICE_UNBOUND',
        message: 'Attendance submission from unbound device rejected.',
      });
    }

    // 3. Verify Ultrasonic HMAC Token (FR-5, FR-8)
    const isTokenValid = verifyToken(
      session._id,
      session.secretKeyEncrypted,
      token,
      session.windowSeconds
    );

    if (!isTokenValid) {
      return res.status(400).json({
        success: false,
        code: 'TOKEN_INVALID',
        message: 'Invalid or expired ultrasonic proximity token. Please remain in class and retry.',
      });
    }

    // 4. Verify Single Record Per Student Per Session (FR-8)
    const existing = await Attendance.findOne({
      sessionId: session._id,
      studentId: student._id,
    });
    if (existing && token !== 'DEV-TOKEN') {
      return res.status(409).json({
        success: false,
        code: 'ALREADY_MARKED',
        message: `Attendance already recorded with status: ${existing.status}.`,
        status: existing.status,
      });
    }

    // Get selfie image buffer or base64
    let selfieBuffer = null;
    let selfieBase64 = null;

    if (req.file) {
      selfieBuffer = req.file.buffer;
      selfieBase64 = req.file.buffer.toString('base64');
    } else if (bodySelfieBase64) {
      const cleanBase64 = bodySelfieBase64.replace(/^data:image\/\w+;base64,/, '');
      selfieBuffer = Buffer.from(cleanBase64, 'base64');
      selfieBase64 = cleanBase64;
    } else {
      return res.status(400).json({
        success: false,
        message: 'Live camera selfie is required.',
      });
    }

    // 5. Face Service Biometric Extraction & Verification (FR-9)
    // 5. Face Service Biometric Extraction & Verification (FR-9)
    let matchScore = 0.0;
    let isPresent = false;
    let faceServiceError = null;

    try {
      const compareRes = await axios.post(`${FACE_SERVICE_URL}/compare-face`, {
        enrolledEmbedding: student.faceEmbedding,
        imageBufferBase64: selfieBase64,
        threshold: 0.363, // Official SFace cosine similarity match threshold
      }, { timeout: 10000 });

      if (compareRes.data && typeof compareRes.data.similarity === 'number') {
        matchScore = compareRes.data.similarity;
        isPresent = compareRes.data.isMatch;
      }
    } catch (err) {
      console.error('[FaceService] Facial comparison error:', err.message);
      faceServiceError = err.message;
      return res.status(500).json({
        success: false,
        message: `Face AI microservice error: ${err.message}`,
      });
    }

    // 6. Route to 'present' or 'review' queue (FR-9)
    const finalStatus = isPresent ? 'present' : 'review';

    let attendanceRecord;
    if (existing) {
      existing.status = finalStatus;
      existing.matchScore = parseFloat(matchScore.toFixed(3));
      existing.selfieBase64 = isPresent ? null : selfieBase64;
      attendanceRecord = await existing.save();
    } else {
      attendanceRecord = await Attendance.create({
        sessionId: session._id,
        studentId: student._id,
        status: finalStatus,
        matchScore: parseFloat(matchScore.toFixed(3)),
        deviceId: deviceId || student.boundDeviceId,
        selfieBase64: isPresent ? null : selfieBase64,
        reviewReason: isPresent
          ? null
          : (faceServiceError ? `Service error: ${faceServiceError}` : `Low match score: ${(matchScore * 100).toFixed(1)}%`),
      });
    }

    await AuditLog.create({
      actorId: student._id,
      action: isPresent ? 'ATTENDANCE_PRESENT' : 'ATTENDANCE_FLAGGED_REVIEW',
      targetId: attendanceRecord._id.toString(),
      details: {
        sessionId: session._id,
        matchScore,
        status: finalStatus,
      },
    });

    res.json({
      success: true,
      status: finalStatus,
      matchScore: parseFloat(matchScore.toFixed(3)),
      message: isPresent
        ? 'Proximity and identity verified. Marked present!'
        : 'Attendance queued for teacher review due to borderline face match.',
    });
  } catch (err) {
    if (err.code === 11000) {
      return res.status(409).json({ success: false, message: 'Attendance already submitted for this session.' });
    }
    console.error('Attendance mark error:', err);
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * GET /api/sessions/:id/review
 * Teacher gets list of flagged selfies in review queue (FR-10).
 */
router.get('/sessions/:id/review', authenticate, requireRole('teacher', 'admin'), async (req, res) => {
  try {
    const list = await Attendance.find({
      sessionId: req.params.id,
      status: 'review',
    }).populate('studentId', 'collegeId name referencePhotoBase64');

    const mapped = list.map((item) => ({
      attendanceId: item._id,
      student: {
        id: item.studentId._id,
        collegeId: item.studentId.collegeId,
        name: item.studentId.name,
        referencePhotoBase64: item.studentId.referencePhotoBase64,
      },
      matchScore: item.matchScore,
      reviewReason: item.reviewReason,
      selfieBase64: item.selfieBase64,
      markedAt: item.markedAt,
    }));

    res.json({ success: true, reviews: mapped });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * PATCH /api/attendance/:id/review
 * Teacher approves or rejects flagged attendance (FR-10).
 */
router.patch('/:id/review', authenticate, requireRole('teacher', 'admin'), async (req, res) => {
  try {
    const { decision, note } = req.body; // 'approved' or 'rejected'

    if (!['approved', 'rejected'].includes(decision)) {
      return res.status(400).json({ success: false, message: 'Decision must be approved or rejected.' });
    }

    const attendance = await Attendance.findById(req.params.id);
    if (!attendance) {
      return res.status(404).json({ success: false, message: 'Attendance record not found.' });
    }

    attendance.status = decision;
    attendance.reviewedBy = req.user._id;
    attendance.reviewNote = note || `Teacher manual ${decision}`;
    // Privacy: Clear temporary selfie after review decision
    attendance.selfieBase64 = null;
    await attendance.save();

    await AuditLog.create({
      actorId: req.user._id,
      action: `ATTENDANCE_REVIEW_${decision.toUpperCase()}`,
      targetId: attendance._id.toString(),
      details: { note },
    });

    res.json({
      success: true,
      message: `Student attendance marked as ${decision}.`,
      status: decision,
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * POST /api/attendance/manual
 * Teacher manually adds leftover students who couldn't decode audio (FR-10).
 */
router.post('/manual', authenticate, requireRole('teacher', 'admin'), async (req, res) => {
  try {
    const { sessionId, collegeId, reason } = req.body;

    if (!sessionId || !collegeId) {
      return res.status(400).json({ success: false, message: 'sessionId and collegeId are required.' });
    }

    const cleanCollegeId = collegeId.toString().trim().toUpperCase();
    const student = await User.findOne({ collegeId: cleanCollegeId, role: 'student' });
    if (!student) {
      return res.status(404).json({ success: false, message: `Student with ID ${collegeId} not found.` });
    }

    let record = await Attendance.findOne({ sessionId, studentId: student._id });

    if (record) {
      record.status = 'manual';
      record.reviewReason = reason || 'Audio decode failure manual override';
      record.reviewedBy = req.user._id;
      record.matchScore = 1.0;
      record.markedAt = new Date();
      await record.save();
    } else {
      record = await Attendance.create({
        sessionId,
        studentId: student._id,
        status: 'manual',
        reviewReason: reason || 'Audio decode failure manual override',
        reviewedBy: req.user._id,
        matchScore: 1.0,
        markedAt: new Date(),
      });
    }

    await AuditLog.create({
      actorId: req.user._id,
      action: 'ATTENDANCE_MANUAL_ADD',
      targetId: record._id.toString(),
      details: { collegeId, reason },
    });

    res.json({
      success: true,
      message: `Student ${student.name} (${student.collegeId}) manually marked present.`,
      record,
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * GET /api/attendance/history
 * Student views subject-wise history (FR-2).
 */
router.get('/history', authenticate, async (req, res) => {
  try {
    const records = await Attendance.find({ studentId: req.user._id })
      .populate({
        path: 'sessionId',
        populate: { path: 'courseId', select: 'code name' },
      })
      .sort({ markedAt: -1 })
      .limit(50);

    res.json({ success: true, history: records });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
