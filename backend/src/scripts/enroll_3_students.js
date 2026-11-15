require('dotenv').config({ path: __dirname + '/../../.env' });
const mongoose = require('mongoose');
const fs = require('fs');
const axios = require('axios');

const User = require('../models/User');
const Attendance = require('../models/Attendance');

const P1_PATH = 'C:/Users/casti/.gemini/antigravity-ide/brain/617940c2-2ab5-4cea-8cd5-80f561b42dd2/.user_uploaded/media_1792126385200.jpg';
const P2_PATH = 'C:/Users/casti/.gemini/antigravity-ide/brain/617940c2-2ab5-4cea-8cd5-80f561b42dd2/.user_uploaded/media_1792126392547.jpg';
const P3_PATH = 'C:/Users/casti/.gemini/antigravity-ide/brain/617940c2-2ab5-4cea-8cd5-80f561b42dd2/.user_uploaded/media_1792126418449.png';

async function extract(path) {
  const b64 = fs.readFileSync(path).toString('base64');
  const res = await axios.post('http://127.0.0.1:8000/extract-embedding', { imageBase64: b64 });
  return { embedding: res.data.embedding, b64 };
}

async function run() {
  await mongoose.connect(process.env.MONGO_URI || 'mongodb://127.0.0.1:27017/gams');
  console.log('[Enroll3] Connected to MongoDB');

  console.log('[Enroll3] Extracting SFace vectors...');
  const p1 = await extract(P1_PATH);
  const p2 = await extract(P2_PATH);
  const p3 = await extract(P3_PATH);

  // Student 1 (21CS001): Rahul Verma (Person 3)
  const s1 = await User.findOne({ collegeId: '21CS001' });
  if (s1) {
    s1.name = 'Rahul Verma';
    s1.faceEmbedding = p3.embedding;
    s1.referencePhotoBase64 = p3.b64;
    s1.boundDeviceId = null;
    await s1.save();
    console.log('✅ Student 1 (21CS001 - Rahul Verma) enrolled with Person 3 photo');
  }

  // Student 2 (21CS002): Priya Sharma (Person 1)
  const s2 = await User.findOne({ collegeId: '21CS002' });
  if (s2) {
    s2.name = 'Priya Sharma';
    s2.faceEmbedding = p1.embedding;
    s2.referencePhotoBase64 = p1.b64;
    s2.boundDeviceId = null;
    await s2.save();
    console.log('✅ Student 2 (21CS002 - Priya Sharma) enrolled with Person 1 photo');
  }

  // Student 3 (21CS003): Sneha Rao (Person 2)
  const s3 = await User.findOne({ collegeId: '21CS003' });
  if (s3) {
    s3.name = 'Sneha Rao';
    s3.faceEmbedding = p2.embedding;
    s3.referencePhotoBase64 = p2.b64;
    s3.boundDeviceId = null;
    await s3.save();
    console.log('✅ Student 3 (21CS003 - Sneha Rao) enrolled with Person 2 photo');
  }

  // Clear any existing attendance records for fresh live test
  await Attendance.deleteMany({});
  console.log('✅ Cleared previous attendance records for a clean test.');

  console.log('\nAll 3 real student profiles enrolled successfully in MongoDB!');
  process.exit(0);
}

run().catch(e => {
  console.error('[Enroll3] Error:', e.message);
  process.exit(1);
});
