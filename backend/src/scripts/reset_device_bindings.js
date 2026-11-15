const mongoose = require('mongoose');

async function resetBindings() {
  await mongoose.connect('mongodb://127.0.0.1:27017/gams');
  const User = mongoose.model('User', new mongoose.Schema({}, { strict: false }));
  await User.updateMany({}, { $set: { boundDeviceId: null } });
  const users = await User.find({ role: 'student' });
  console.log('Successfully reset all device bindings to NULL:');
  users.forEach(u => console.log(` - ${u.collegeId} (${u.name}): boundDeviceId=${u.boundDeviceId}`));
  process.exit(0);
}

resetBindings().catch(err => {
  console.error(err);
  process.exit(1);
});
