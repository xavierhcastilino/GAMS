require('dotenv').config({ path: __dirname + '/../../.env' });
const mongoose = require('mongoose');
const fs = require('fs');
const axios = require('axios');
const User = require('../models/User');
const Attendance = require('../models/Attendance');

const PHOTO_PATH = 'C:/Users/casti/.gemini/antigravity-ide/brain/617940c2-2ab5-4cea-8cd5-80f561b42dd2/.user_uploaded/media_1792007628518.png';

async function enrollUser() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log('[Enroll] Connected to MongoDB');

  if (!fs.existsSync(PHOTO_PATH)) {
    console.error('[Enroll] Photo not found at:', PHOTO_PATH);
    process.exit(1);
  }

  const imageBuffer = fs.readFileSync(PHOTO_PATH);
  const base64Image = imageBuffer.toString('base64');

  console.log('[Enroll] Calling Face Service to extract SFace embedding...');
  const res = await axios.post('http://127.0.0.1:8000/extract-embedding', {
    imageBase64: base64Image
  });

  const embedding = res.data.embedding;
  console.log('[Enroll] Extracted embedding vector of length:', embedding.length);

  const student = await User.findOne({ collegeId: '21CS001' });
  if (!student) {
    console.error('[Enroll] Student 21CS001 not found');
    process.exit(1);
  }

  student.faceEmbedding = embedding;
  student.isRealFaceEnrolled = true;
  await student.save();
  console.log('[Enroll] Successfully saved real face embedding to MongoDB for student:', student.name, '(', student.collegeId, ')');

  // Clear any old attendance records for clean testing
  await Attendance.deleteMany({ studentId: student._id });
  console.log('[Enroll] Cleared past attendance records so student is ready for a fresh test.');

  process.exit(0);
}

enrollUser().catch(err => {
  console.error('[Enroll] Error:', err.message);
  process.exit(1);
});
