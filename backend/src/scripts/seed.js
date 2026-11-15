require('dotenv').config({ path: __dirname + '/../../.env' });
const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const dns = require('dns');

try {
  dns.setServers(['8.8.8.8', '1.1.1.1']);
} catch (e) {}

const User = require('../models/User');
const Course = require('../models/Course');
const Session = require('../models/Session');
const Attendance = require('../models/Attendance');

// Generates a mock normalized embedding vector of length dim
function generateMockEmbedding(seedValue, dim = 128) {
  const vec = [];
  let sumSq = 0;
  for (let i = 0; i < dim; i++) {
    const val = Math.sin(seedValue * (i + 1)) * Math.cos(i);
    vec.push(val);
    sumSq += val * val;
  }
  const norm = Math.sqrt(sumSq) || 1;
  return vec.map((v) => parseFloat((v / norm).toFixed(6)));
}

async function seedDatabase() {
  try {
    console.log('[Seed] Connecting to MongoDB at:', process.env.MONGO_URI);
    await mongoose.connect(process.env.MONGO_URI);
    console.log('[Seed] Connected.');

    // Clear previous data
    await User.deleteMany({});
    await Course.deleteMany({});
    await Session.deleteMany({});
    await Attendance.deleteMany({});
    console.log('[Seed] Previous collections cleared.');

    const defaultPasswordHash = await bcrypt.hash('password123', 10);

    // 1. Create Admin
    const admin = await User.create({
      collegeId: 'ADMIN01',
      name: 'System Administrator',
      email: 'admin@git.edu',
      passwordHash: defaultPasswordHash,
      role: 'admin',
      department: 'CSE',
    });

    // 2. Create Teacher
    const teacher = await User.create({
      collegeId: 'T101',
      name: 'Prof. Ananya Sen',
      email: 'ananya.sen@git.edu',
      passwordHash: defaultPasswordHash,
      role: 'teacher',
      department: 'CSE',
    });

    // 3. Create Students
    const student1 = await User.create({
      collegeId: '21CS001',
      name: 'Rahul Verma',
      email: 'rahul.21cs@git.edu',
      passwordHash: defaultPasswordHash,
      role: 'student',
      department: 'CSE',
      division: 'A',
      faceEmbedding: generateMockEmbedding(1.234),
      enrolledBy: admin._id,
    });

    const student2 = await User.create({
      collegeId: '21CS002',
      name: 'Priya Sharma',
      email: 'priya.21cs@git.edu',
      passwordHash: defaultPasswordHash,
      role: 'student',
      department: 'CSE',
      division: 'A',
      faceEmbedding: generateMockEmbedding(5.678),
      enrolledBy: admin._id,
    });

    const student3 = await User.create({
      collegeId: '21CS003',
      name: 'Amit Patel',
      email: 'amit.21cs@git.edu',
      passwordHash: defaultPasswordHash,
      role: 'student',
      department: 'CSE',
      division: 'A',
      faceEmbedding: generateMockEmbedding(9.101),
      enrolledBy: admin._id,
    });

    // 4. Create Sample Course
    const course = await Course.create({
      code: 'CS601',
      name: 'Distributed Systems & Mobile Computing',
      department: 'CSE',
      semester: 6,
      teacherIds: [teacher._id],
      studentIds: [student1._id, student2._id, student3._id],
    });

    console.log('========================================================');
    console.log('✅ SEED COMPLETED SUCCESSFULLY!');
    console.log('Sample Accounts Created: (Password for all: password123)');
    console.log('  👑 Admin:   ADMIN01   (admin@git.edu)');
    console.log('  👨‍🏫 Teacher: T101      (Prof. Ananya Sen)');
    console.log('  🎓 Student: 21CS001   (Rahul Verma, Div A)');
    console.log('  🎓 Student: 21CS002   (Priya Sharma, Div A)');
    console.log('  🎓 Student: 21CS003   (Amit Patel, Div A)');
    console.log(`  📚 Course:  ${course.code} - ${course.name} (ID: ${course._id})`);
    console.log('========================================================');

    await mongoose.disconnect();
    process.exit(0);
  } catch (err) {
    console.error('[Seed Error]:', err);
    process.exit(1);
  }
}

seedDatabase();
