const mongoose = require('mongoose');

const attendanceSchema = new mongoose.Schema(
  {
    sessionId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'Session',
      required: true,
      index: true,
    },
    studentId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true,
    },
    status: {
      type: String,
      enum: ['present', 'review', 'approved', 'rejected', 'absent', 'manual'],
      default: 'present',
    },
    markedAt: {
      type: Date,
      default: Date.now,
    },
    matchScore: {
      type: Number,
      default: 0.0,
    },
    deviceId: {
      type: String,
      default: null,
    },
    // Temporary selfie storage for teacher review queue (cleared on session close)
    selfieBase64: {
      type: String,
      default: null,
    },
    reviewReason: {
      type: String,
      default: null,
    },
    reviewedBy: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      default: null,
    },
    reviewNote: {
      type: String,
      default: null,
    },
  },
  { timestamps: true }
);

// Enforce one record per student per session (PRD FR-8)
attendanceSchema.index({ sessionId: 1, studentId: 1 }, { unique: true });

module.exports = mongoose.model('Attendance', attendanceSchema);
