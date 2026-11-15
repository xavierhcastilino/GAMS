const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const User = require('../models/User');
const AuditLog = require('../models/AuditLog');
const { authenticate, requireRole } = require('../middleware/auth');

/**
 * POST /api/admin/devices/:userId/reset
 * Admin resets bound device for a student (FR-2).
 */
router.post('/devices/:userId/reset', authenticate, requireRole('admin'), async (req, res) => {
  try {
    const user = await User.findById(req.params.userId);
    if (!user) {
      return res.status(404).json({ success: false, message: 'User not found.' });
    }

    const oldDeviceId = user.boundDeviceId;
    user.boundDeviceId = null;
    user.devicePublicKey = null;
    await user.save();

    await AuditLog.create({
      actorId: req.user._id,
      action: 'DEVICE_RESET',
      targetId: user._id.toString(),
      details: { oldDeviceId },
    });

    res.json({ success: true, message: `Device binding cleared for ${user.collegeId}.` });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * POST /api/admin/users/:id/photo
 * Admin enrolls reference ID photo and face embedding (FR-3).
 */
router.post('/users/:id/photo', authenticate, requireRole('admin'), async (req, res) => {
  try {
    const { photoBase64, faceEmbedding } = req.body;
    const user = await User.findById(req.params.id);
    if (!user) {
      return res.status(404).json({ success: false, message: 'User not found.' });
    }

    if (photoBase64) user.referencePhotoBase64 = photoBase64;
    if (faceEmbedding && Array.isArray(faceEmbedding)) user.faceEmbedding = faceEmbedding;
    user.enrolledBy = req.user._id;
    await user.save();

    await AuditLog.create({
      actorId: req.user._id,
      action: 'FACE_ENROLLED',
      targetId: user._id.toString(),
      details: { collegeId: user.collegeId },
    });

    res.json({ success: true, message: `Reference photo and embedding enrolled for ${user.collegeId}.` });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
