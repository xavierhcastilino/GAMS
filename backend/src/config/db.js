const mongoose = require('mongoose');
const dns = require('dns');

// On Windows, local router DNS often fails SRV records for mongodb+srv
try {
  dns.setServers(['8.8.8.8', '1.1.1.1']);
} catch (e) {
  // Ignore if custom DNS cannot be set
}

const connectDB = async () => {
  try {
    const mongoUri = process.env.MONGO_URI;
    if (!mongoUri) {
      throw new Error('MONGO_URI environment variable is missing or empty.');
    }

    const maskedUri = mongoUri.replace(/\/\/([^:]+):([^@]+)@/, '//$1:****@');
    console.log(`[MongoDB] Connecting to ${maskedUri}...`);

    const conn = await mongoose.connect(mongoUri, {
      serverSelectionTimeoutMS: 5000,
    });
    console.log(`[MongoDB] Connected successfully: ${conn.connection.host}`);
  } catch (err) {
    console.error(`[MongoDB] Connection error: ${err.message}`);
    if (process.env.MONGO_URI && process.env.MONGO_URI.includes('mongodb.net')) {
      console.error('[MongoDB] Atlas Hint: Make sure your current IP address (or 0.0.0.0/0) is allowed under MongoDB Atlas -> Network Access, and your username/password are correct.');
    }
    process.exit(1);
  }
};

module.exports = connectDB;
