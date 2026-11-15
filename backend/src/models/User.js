const mongoose = require('mongoose');

const userSchema = new mongoose.Schema(
  {
    collegeId: {
      type: String,
      required: true,
      unique: true,
      trim: true,
      uppercase: true,
    },
    name: {
      type: String,
      required: true,
      trim: true,
    },
    email: {
      type: String,
      required: true,
      unique: true,
      lowercase: true,
      trim: true,
    },
    passwordHash: {
      type: String,
      required: true,
    },
    role: {
      type: String,
      enum: ['student', 'teacher', 'admin'],
      default: 'student',
    },
    department: {
      type: String,
      default: 'CSE',
    },
    division: {
      type: String,
      default: 'A',
    },
    devicePublicKey: {
      type: String,
      default: null,
    },
    boundDeviceId: {
      type: String,
      default: null,
    },
    fcmToken: {
      type: String,
      default: null,
    },
    // Array of floats representing the 128/512-dim facial embedding vector
    faceEmbedding: {
      type: [Number],
      default: [],
    },
    // Reference enrolled photo in base64 format for side-by-side review
    referencePhotoBase64: {
      type: String,
      default: null,
    },
    enrolledBy: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      default: null,
    },
  },
  { timestamps: true }
);

module.exports = mongoose.model('User', userSchema);
