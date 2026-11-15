require('dotenv').config();
const express = require('express');
const cors = require('cors');
const connectDB = require('./config/db');

const authRoutes = require('./routes/auth');
const sessionRoutes = require('./routes/sessions');
const attendanceRoutes = require('./routes/attendance');
const adminRoutes = require('./routes/admin');

const app = express();

// Middleware
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Request logger
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
  next();
});

// Routes
app.use('/api/auth', authRoutes);
app.use('/api/sessions', sessionRoutes);
app.use('/api/attendance', attendanceRoutes);
app.use('/api/admin', adminRoutes);

const path = require('path');
const QRCode = require('qrcode');

const APK_PATH = path.resolve(__dirname, '../../android/app/build/outputs/apk/debug/app-debug.apk');

// Health check endpoint
app.get('/api/health', (req, res) => {
  res.json({
    status: 'healthy',
    system: 'GAMS Proximity Attendance Backend',
    timestamp: new Date().toISOString(),
  });
});

// Wireless APK Download Portal with QR Code
app.get('/download', async (req, res) => {
  const host = req.headers.host || '192.168.1.8:5000';
  const downloadUrl = `http://${host}/download/app-debug.apk`;
  
  let qrDataUrl = '';
  try {
    qrDataUrl = await QRCode.toDataURL(downloadUrl, {
      margin: 2,
      width: 320,
      color: { dark: '#000000', light: '#ffffff' }
    });
  } catch (e) {
    console.error('QR code generation failed:', e);
  }

  res.send(`
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Download GAMS Attendance App</title>
      <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
          background: #0B0F19;
          color: #F8FAFC;
          display: flex;
          align-items: center;
          justify-content: center;
          min-height: 100vh;
          padding: 24px;
        }
        .card {
          background: #151D2F;
          border: 1px solid #2A364F;
          border-radius: 24px;
          padding: 32px 24px;
          max-width: 420px;
          width: 100%;
          text-align: center;
          box-shadow: 0 20px 40px rgba(0,0,0,0.4);
        }
        .monogram {
          width: 64px;
          height: 64px;
          background: #3B82F6;
          border-radius: 18px;
          margin: 0 auto 16px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-weight: 800;
          font-size: 26px;
          color: #FFF;
          box-shadow: 0 8px 24px rgba(59,130,246,0.3);
        }
        h1 { font-size: 22px; font-weight: 700; margin-bottom: 6px; }
        p.subtitle { color: #94A3B8; font-size: 14px; margin-bottom: 24px; }
        .qr-box {
          background: #FFF;
          padding: 16px;
          border-radius: 16px;
          display: inline-block;
          margin-bottom: 20px;
          box-shadow: 0 4px 16px rgba(0,0,0,0.2);
        }
        .qr-box img { display: block; width: 220px; height: 220px; }
        .btn {
          display: block;
          width: 100%;
          background: #3B82F6;
          color: #FFF;
          text-decoration: none;
          padding: 14px;
          border-radius: 14px;
          font-weight: 600;
          font-size: 16px;
          margin-bottom: 16px;
          transition: background 0.2s;
        }
        .btn:hover { background: #2563EB; }
        .hint {
          font-size: 12px;
          color: #64748B;
          line-height: 1.5;
        }
      </style>
    </head>
    <body>
      <div class="card">
        <div class="monogram">G</div>
        <h1>GAMS Attendance</h1>
        <p class="subtitle">Wireless APK Installer for Testing Devices</p>
        <div class="qr-box">
          <img src="${qrDataUrl}" alt="Scan QR Code" />
        </div>
        <a href="/download/app-debug.apk" class="btn">⬇️ Download APK (v1.0-debug)</a>
        <p class="hint">1. Scan QR with your other phones or tap Download.<br>2. When prompted, allow installing apps from this source.<br>3. Connect to the same Wi-Fi (192.168.1.8).</p>
      </div>
    </body>
    </html>
  `);
});

// Direct APK download route
app.get('/download/app-debug.apk', (req, res) => {
  res.download(APK_PATH, 'gams-v1.0-debug.apk', (err) => {
    if (err) {
      console.error('Error sending APK file:', err);
      if (!res.headersSent) {
        res.status(404).send('APK file not found. Please build the project first.');
      }
    }
  });
});

// Error handling middleware
app.use((err, req, res, next) => {
  console.error('Unhandled Server Error:', err);
  res.status(500).json({
    success: false,
    message: err.message || 'Internal Server Error',
  });
});

const PORT = process.env.PORT || 5000;

// Connect Database and Start Server
connectDB().then(() => {
  app.listen(PORT, '0.0.0.0', () => {
    console.log(`===============================================`);
    console.log(`🚀 GAMS Node.js Backend listening on port ${PORT}`);
    console.log(`📡 Local URL: http://localhost:${PORT}/api/health`);
    console.log(`===============================================`);
  });
});
