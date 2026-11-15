const mongoose = require('mongoose');

const sessionSchema = new mongoose.Schema(
  {
    courseId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'Course',
      required: true,
    },
    teacherId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
    },
    division: {
      type: String,
      required: true,
      default: 'A',
    },
    period: {
      type: Number,
      required: true,
      default: 1,
    },
    startTime: {
      type: Date,
      default: Date.now,
    },
    endTime: {
      type: Date,
      default: null,
    },
    status: {
      type: String,
      enum: ['active', 'closed'],
      default: 'active',
    },
    // Secret key for HMAC rotating token calculation
    secretKeyEncrypted: {
      type: String,
      required: true,
    },
    windowSeconds: {
      type: Number,
      default: 20,
    },
  },
  { timestamps: true }
);

module.exports = mongoose.model('Session', sessionSchema);
