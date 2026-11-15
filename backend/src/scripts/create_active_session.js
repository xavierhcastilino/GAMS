require('dotenv').config({ path: __dirname + '/../../.env' });
const mongoose = require('mongoose');
const User = require('../models/User');
const Course = require('../models/Course');
const Session = require('../models/Session');
const crypto = require('crypto');

async function ensureActiveSession() {
  await mongoose.connect(process.env.MONGO_URI);
  
  const teacher = await User.findOne({ role: 'teacher' });
  const course = await Course.findOne({ code: 'CS601' });

  if (!teacher || !course) {
    console.error('Teacher or course not found');
    process.exit(1);
  }

  // Check if an active session already exists
  let session = await Session.findOne({ status: 'active', division: 'A' });
  if (!session) {
    session = await Session.create({
      courseId: course._id,
      teacherId: teacher._id,
      division: 'A',
      period: 1,
      secretKeyEncrypted: crypto.randomBytes(32).toString('hex'),
      windowSeconds: 20,
      status: 'active',
    });
    console.log('CREATED_ACTIVE_SESSION_ID:', session._id);
  } else {
    console.log('EXISTING_ACTIVE_SESSION_ID:', session._id);
  }

  process.exit(0);
}

ensureActiveSession();
