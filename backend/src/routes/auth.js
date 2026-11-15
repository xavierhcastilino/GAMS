const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const User = require('../models/User');
const AuditLog = require('../models/AuditLog');
const { authenticate } = require('../middleware/auth');

/**
 * POST /api/auth/login
 * Login with college ID and password.
 */
router.post('/login', async (req, res) => {
  try {
    const { collegeId, password, deviceId } = req.body;

    if (!collegeId || !password) {
      return res.status(400).json({ success: false, message: 'collegeId and password are required.' });
    }

    const identifier = collegeId.trim();
    const user = await User.findOne({
      $or: [
        { collegeId: identifier.toUpperCase() },
        { email: identifier.toLowerCase() }
      ]
    });
    if (!user) {
      return res.status(401).json({ success: false, message: 'Invalid credentials.' });
    }

    const isMatch = await bcrypt.compare(password, user.passwordHash);
    if (!isMatch) {
      return res.status(401).json({ success: false, message: 'Invalid credentials.' });
    }

    // Check device binding for students (FR-2)
    if (user.role === 'student' && deviceId) {
      if (!user.boundDeviceId || user.boundDeviceId !== deviceId) {
        console.log(`[Auth] Binding/updating device for ${user.collegeId}: ${deviceId}`);
        user.boundDeviceId = deviceId;
        await user.save();
        await AuditLog.create({
          actorId: user._id,
          action: 'DEVICE_BOUND',
          targetId: deviceId,
          details: { collegeId: user.collegeId },
        });
      }
    }

    const token = jwt.sign(
      { id: user._id, role: user.role, collegeId: user.collegeId },
      process.env.JWT_SECRET || 'gams_secret',
      { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
    );

    res.json({
      success: true,
      token,
      user: {
        id: user._id,
        collegeId: user.collegeId,
        name: user.name,
        email: user.email,
        role: user.role,
        department: user.department,
        division: user.division,
        boundDeviceId: user.boundDeviceId,
        hasFaceEnrolled: user.faceEmbedding && user.faceEmbedding.length > 0,
      },
    });
  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ success: false, message: 'Server error during login.' });
  }
});

/**
 * POST /api/devices/register
 * Bind device public key & ID (FR-2).
 */
router.post('/devices/register', authenticate, async (req, res) => {
  try {
    const { deviceId, devicePublicKey } = req.body;
    if (!deviceId) {
      return res.status(400).json({ success: false, message: 'deviceId is required.' });
    }

    const user = req.user;
    if (user.boundDeviceId && user.boundDeviceId !== deviceId) {
      return res.status(403).json({
        success: false,
        message: 'Account is already bound to a different device.',
      });
    }

    user.boundDeviceId = deviceId;
    if (devicePublicKey) user.devicePublicKey = devicePublicKey;
    await user.save();

    res.json({ success: true, message: 'Device successfully bound.', boundDeviceId: user.boundDeviceId });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * GET /api/auth/me
 * Current user profile.
 */
router.get('/me', authenticate, async (req, res) => {
  res.json({
    success: true,
    user: {
      id: req.user._id,
      collegeId: req.user.collegeId,
      name: req.user.name,
      email: req.user.email,
      role: req.user.role,
      department: req.user.department,
      division: req.user.division,
      boundDeviceId: req.user.boundDeviceId,
      hasFaceEnrolled: req.user.faceEmbedding && req.user.faceEmbedding.length > 0,
    },
  });
});

module.exports = router;
