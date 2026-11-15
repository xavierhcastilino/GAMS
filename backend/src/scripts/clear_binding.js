require('dotenv').config({ path: __dirname + '/../../.env' });
const mongoose = require('mongoose');
const User = require('../models/User');

async function clear() {
  await mongoose.connect(process.env.MONGO_URI);
  await User.updateMany({ role: 'student' }, { $set: { boundDeviceId: null } });
  console.log('RESET_ALL_STUDENT_DEVICES_SUCCESS');
  process.exit(0);
}

clear();
