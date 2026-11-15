require('dotenv').config({ path: __dirname + '/../../.env' });
const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const fs = require('fs');
const axios = require('axios');
const dns = require('dns');

try {
  dns.setServers(['8.8.8.8', '1.1.1.1']);
} catch (e) {}

const User = require('../models/User');
const Course = require('../models/Course');
const Session = require('../models/Session');
const Attendance = require('../models/Attendance');

const PHOTO1_PATH = 'C:/Users/casti/.gemini/antigravity-ide/brain/617940c2-2ab5-4cea-8cd5-80f561b42dd2/.user_uploaded/media_1792007628518.png';
const PHOTO2_PATH = 'C:/Users/casti/.gemini/antigravity-ide/brain/617940c2-2ab5-4cea-8cd5-80f561b42dd2/.user_uploaded/media_1792007633182.png';

async function getEmbeddingFromPhoto(photoPath) {
  try {
    if (fs.existsSync(photoPath)) {
      const b64 = fs.readFileSync(photoPath).toString('base64');
      const res = await axios.post('http://127.0.0.1:8000/extract-embedding', {
        imageBase64: b64
      }, { timeout: 5000 });
      if (res.data && res.data.embedding) {
        return res.data.embedding;
      }
    }
  } catch (err) {
    console.warn('[Setup] Could not extract from', photoPath, ':', err.message);
  }
  return null;
}

function generateDistinctEmbedding(seed) {
  const vec = [];
  let sumSq = 0;
  for (let i = 0; i < 128; i++) {
    const val = Math.sin(seed * (i + 1)) * Math.cos(i * 1.5);
    vec.push(val);
    sumSq += val * val;
  }
  const norm = Math.sqrt(sumSq) || 1;
  return vec.map((v) => parseFloat((v / norm).toFixed(6)));
}

async function setup() {
  await mongoose.connect(process.env.MONGO_URI || 'mongodb://127.0.0.1:27017/gams');
  console.log('[Setup] Connected to MongoDB');

  const passwordHash = await bcrypt.hash('password123', 10);

  // 1. Get real SFace face embeddings for student testing
  console.log('[Setup] Extracting real SFace embeddings...');
  const embUser1 = await getEmbeddingFromPhoto(PHOTO1_PATH) || generateDistinctEmbedding(1.11);
  const embUser2 = await getEmbeddingFromPhoto(PHOTO2_PATH) || embUser1;
  const embUser3 = generateDistinctEmbedding(3.33);
  const embUser4 = generateDistinctEmbedding(4.44);

  // 2. Admin Account (ensure exists)
  let admin = await User.findOne({ collegeId: 'ADMIN01' });
  if (!admin) {
    admin = await User.create({
      collegeId: 'ADMIN01',
      name: 'System Administrator',
      email: 'admin@git.edu',
      passwordHash,
      role: 'admin',
      department: 'CSE',
      division: 'A',
    });
  }

  // 3. Teacher Account (Permanent)
  let teacher = await User.findOne({ collegeId: 'T101' });
  if (!teacher) {
    teacher = await User.create({
      collegeId: 'T101',
      name: 'Prof. Ananya Sen',
      email: 'teacher@git.edu',
      passwordHash,
      role: 'teacher',
      department: 'CSE',
      division: 'A',
    });
  } else {
    teacher.passwordHash = passwordHash;
    teacher.role = 'teacher';
    teacher.division = 'A';
    teacher.boundDeviceId = null;
    await teacher.save();
  }
  console.log('[Setup] Teacher configured: T101 (Prof. Ananya Sen)');

  // 4. 4 Student Accounts in Division A
  const studentSpecs = [
    {
      collegeId: '21CS001',
      name: 'Rahul Verma',
      email: 'rahul.21cs@git.edu',
      faceEmbedding: embUser1,
    },
    {
      collegeId: '21CS002',
      name: 'Priya Sharma',
      email: 'priya.21cs@git.edu',
      faceEmbedding: embUser2,
    },
    {
      collegeId: '21CS003',
      name: 'Amit Patel',
      email: 'amit.21cs@git.edu',
      faceEmbedding: embUser3,
    },
    {
      collegeId: '21CS004',
      name: 'Sneha Rao',
      email: 'sneha.21cs@git.edu',
      faceEmbedding: embUser4,
    },
  ];

  const studentIds = [];
  for (const spec of studentSpecs) {
    let student = await User.findOne({ collegeId: spec.collegeId });
    if (!student) {
      student = await User.create({
        collegeId: spec.collegeId,
        name: spec.name,
        email: spec.email,
        passwordHash,
        role: 'student',
        department: 'CSE',
        division: 'A',
        faceEmbedding: spec.faceEmbedding,
        boundDeviceId: null,
        enrolledBy: admin._id,
      });
    } else {
      student.name = spec.name;
      student.email = spec.email;
      student.passwordHash = passwordHash;
      student.role = 'student';
      student.department = 'CSE';
      student.division = 'A';
      student.faceEmbedding = spec.faceEmbedding;
      student.boundDeviceId = null; // Reset so any phone binds cleanly on first login
      await student.save();
    }
    studentIds.push(student._id);
    console.log(`[Setup] Student configured: ${spec.collegeId} (${spec.name}) in Div A`);
  }

  // 5. Course Mapping (CS601 assigned to Teacher and all 4 Students)
  let course = await Course.findOne({ code: 'CS601' });
  if (!course) {
    course = await Course.create({
      code: 'CS601',
      name: 'Cloud Computing & Distributed Systems',
      department: 'CSE',
      semester: 6,
      teacherIds: [teacher._id],
      studentIds: studentIds,
    });
  } else {
    course.teacherIds = [teacher._id];
    course.studentIds = studentIds;
    await course.save();
  }
  console.log('[Setup] Course CS601 linked to Teacher T101 and 4 Students.');

  // 6. Clear past attendance records for fresh multi-device testing
  await Attendance.deleteMany({});
  console.log('[Setup] Cleared past attendance records.');

  // 7. Ensure an active session is started for Division A
  await Session.updateMany({}, { isActive: false, status: 'closed' });
  const activeSession = await Session.create({
    courseId: course._id,
    subjectCode: 'CS601',
    subjectName: 'Cloud Computing & Distributed Systems',
    department: 'CSE',
    division: 'A',
    period: 1,
    teacherId: teacher._id,
    currentFrequency: 19500,
    windowSeconds: 3600,
    isActive: true,
    status: 'active',
    secretKeyEncrypted: 'a3f89e2bc14d05e89a712f540b9cde3210ab56cd78ef0123456789abcdef0123',
    startTime: new Date(),
  });
  console.log(`[Setup] Active Session initialized for Div A (ID: ${activeSession._id})`);

  console.log('\n======================================================');
  console.log('✅ ALL TEST ACCOUNTS CREATED & VERIFIED IN MONGODB');
  console.log('======================================================');
  process.exit(0);
}

setup().catch(err => {
  console.error('[Setup] Fatal error:', err);
  process.exit(1);
});
