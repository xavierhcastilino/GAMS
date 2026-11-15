const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const Session = require('../models/Session');
const Attendance = require('../models/Attendance');
const Course = require('../models/Course');
const User = require('../models/User');
const AuditLog = require('../models/AuditLog');
const { authenticate, requireRole } = require('../middleware/auth');
const { getCurrentToken } = require('../utils/token');

/**
 * GET /api/sessions/teacher/courses
 * List courses assigned to the logged-in teacher.
 */
router.get('/teacher/courses', authenticate, requireRole('teacher', 'admin'), async (req, res) => {
  try {
    const courses = await Course.find({ teacherIds: req.user._id });
    res.json({ success: true, courses });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * GET /api/sessions/teacher/current
 * Teacher gets their current active or most recent session.
 */
router.get('/teacher/current', authenticate, requireRole('teacher', 'admin'), async (req, res) => {
  try {
    const active = await Session.findOne({ teacherId: req.user._id, status: 'active' })
      .populate('courseId', 'code name')
      .sort({ createdAt: -1 });
    if (active) {
      return res.json({ success: true, session: active, status: 'active' });
    }
    const recent = await Session.findOne({ teacherId: req.user._id })
      .populate('courseId', 'code name')
      .sort({ createdAt: -1 });
    res.json({ success: true, session: recent, status: recent ? recent.status : null });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * POST /api/sessions/start
 * Teacher starts a new session (FR-4).
 */
router.post('/start', authenticate, requireRole('teacher', 'admin'), async (req, res) => {
  try {
    const { courseId, division, period, windowSeconds = 20 } = req.body;

    if (!courseId) {
      return res.status(400).json({ success: false, message: 'courseId is required.' });
    }

    // Close any previous active session for this teacher
    await Session.updateMany(
      { teacherId: req.user._id, status: 'active' },
      { status: 'closed', endTime: new Date() }
    );

    // Generate random secret key for HMAC rotating token
    const secretKey = crypto.randomBytes(32).toString('hex');

    const session = await Session.create({
      courseId,
      teacherId: req.user._id,
      division: division || 'A',
      period: period || 1,
      secretKeyEncrypted: secretKey,
      windowSeconds: parseInt(windowSeconds, 10) || 20,
      status: 'active',
    });

    const tokenInfo = getCurrentToken(session._id, secretKey, session.windowSeconds);

    await AuditLog.create({
      actorId: req.user._id,
      action: 'SESSION_STARTED',
      targetId: session._id.toString(),
      details: { courseId, division, period },
    });

    res.json({
      success: true,
      message: 'Session started successfully.',
      session: {
        id: session._id,
        courseId: session.courseId,
        division: session.division,
        period: session.period,
        startTime: session.startTime,
        status: session.status,
        windowSeconds: session.windowSeconds,
      },
      currentToken: tokenInfo.token,
      remainingSeconds: tokenInfo.remainingSeconds,
    });
  } catch (err) {
    console.error('Session start error:', err);
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * GET /api/sessions/:id/live
 * Real-time stats and rotating token for teacher's transmitter.
 */
router.get('/:id/live', authenticate, async (req, res) => {
  try {
    const session = await Session.findById(req.params.id).populate('courseId', 'code name');
    if (!session) {
      return res.status(404).json({ success: false, message: 'Session not found.' });
    }

    const tokenInfo = session.status === 'active'
      ? getCurrentToken(session._id, session.secretKeyEncrypted, session.windowSeconds)
      : null;

    // Fetch all students enrolled in this division
    const enrolledStudents = await User.find({
      role: 'student',
      division: session.division,
    }).select('collegeId name email').sort({ collegeId: 1 });

    // Fetch all attendance records for this session
    const attendanceRecords = await Attendance.find({
      sessionId: session._id,
    });
    const recordMap = new Map();
    attendanceRecords.forEach((r) => recordMap.set(r.studentId.toString(), r));

    const presentList = [];
    const reviewList = [];
    const absentList = [];

    for (const st of enrolledStudents) {
      const rec = recordMap.get(st._id.toString());
      if (!rec || rec.status === 'absent' || rec.status === 'rejected') {
        absentList.push({
          id: st._id,
          collegeId: st.collegeId,
          name: st.name,
          status: rec ? rec.status : 'absent',
          matchScore: rec ? rec.matchScore : 0.0,
          reviewReason: rec ? rec.reviewReason : null,
        });
      } else if (['present', 'approved', 'manual'].includes(rec.status)) {
        presentList.push({
          id: st._id,
          collegeId: st.collegeId,
          name: st.name,
          status: rec.status,
          matchScore: rec.matchScore,
          reviewReason: rec.reviewReason,
          markedAt: rec.markedAt,
        });
      } else if (rec.status === 'review') {
        reviewList.push({
          id: st._id,
          collegeId: st.collegeId,
          name: st.name,
          status: 'review',
          matchScore: rec.matchScore,
          reviewReason: rec.reviewReason,
          markedAt: rec.markedAt,
        });
      }
    }

    res.json({
      success: true,
      session: {
        id: session._id,
        course: session.courseId,
        division: session.division,
        period: session.period,
        status: session.status,
        startTime: session.startTime,
        endTime: session.endTime,
      },
      currentToken: tokenInfo ? tokenInfo.token : null,
      remainingSeconds: tokenInfo ? tokenInfo.remainingSeconds : 0,
      stats: {
        presentCount: presentList.length,
        reviewCount: reviewList.length,
        absentCount: absentList.length,
        totalCount: enrolledStudents.length,
      },
      presentList,
      reviewList,
      absentList,
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * GET /api/sessions/active
 * Student looks up active session in their division.
 */
router.get('/active/current', authenticate, async (req, res) => {
  try {
    const student = req.user;
    const session = await Session.findOne({
      division: student.division,
      status: 'active',
    }).populate('courseId', 'code name').sort({ startTime: -1 });

    if (!session) {
      return res.json({ success: true, activeSession: null });
    }

    // Check if student already marked
    const attendance = await Attendance.findOne({
      sessionId: session._id,
      studentId: student._id,
    });

    res.json({
      success: true,
      activeSession: {
        id: session._id,
        course: session.courseId,
        division: session.division,
        period: session.period,
        startTime: session.startTime,
        windowSeconds: session.windowSeconds,
      },
      alreadyMarked: !!attendance,
      attendanceStatus: attendance ? attendance.status : null,
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * POST /api/sessions/:id/close
 * Teacher closes session (FR-10). Finalizes absentees and cleans review selfies (DPDP Act).
 */
router.post('/:id/close', authenticate, requireRole('teacher', 'admin'), async (req, res) => {
  try {
    const session = await Session.findById(req.params.id);
    if (!session) {
      return res.status(404).json({ success: false, message: 'Session not found.' });
    }

    session.status = 'closed';
    session.endTime = new Date();
    await session.save();

    // Mark remaining enrolled students as 'absent' if not marked
    const enrolledStudents = await User.find({
      role: 'student',
      division: session.division,
    });

    const attendedStudentIds = (
      await Attendance.find({ sessionId: session._id }).select('studentId')
    ).map((a) => a.studentId.toString());

    const absentRecords = [];
    for (const st of enrolledStudents) {
      if (!attendedStudentIds.includes(st._id.toString())) {
        absentRecords.push({
          sessionId: session._id,
          studentId: st._id,
          status: 'absent',
          matchScore: 0.0,
        });
      }
    }

    if (absentRecords.length > 0) {
      await Attendance.insertMany(absentRecords, { ordered: false });
    }

    // Privacy (DPDP Act): Delete temporary selfies from review queue
    await Attendance.updateMany(
      { sessionId: session._id },
      { $unset: { selfieBase64: '' } }
    );

    await AuditLog.create({
      actorId: req.user._id,
      action: 'SESSION_CLOSED',
      targetId: session._id.toString(),
      details: { absenteesMarked: absentRecords.length },
    });

    res.json({
      success: true,
      message: 'Session closed and attendance finalized.',
      absenteesMarked: absentRecords.length,
    });
  } catch (err) {
    console.error('Session close error:', err);
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * POST /api/sessions/demo/start
 * Dev helper: Starts a practice session for CS601 Div A
 */
router.post('/demo/start', async (req, res) => {
  try {
    const teacher = await User.findOne({ role: 'teacher' });
    const course = await Course.findOne({ code: 'CS601' });

    if (!teacher || !course) {
      return res.status(400).json({ success: false, message: 'Teacher or course not found.' });
    }

    await Session.updateMany({ division: 'A', status: 'active' }, { status: 'closed' });

    const session = await Session.create({
      courseId: course._id,
      teacherId: teacher._id,
      division: 'A',
      period: 1,
      secretKeyEncrypted: crypto.randomBytes(32).toString('hex'),
      windowSeconds: 20,
      status: 'active',
    });

    res.json({ success: true, session });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
