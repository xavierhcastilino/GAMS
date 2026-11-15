const crypto = require('crypto');

/**
 * Generate a rotating token for a specific time window.
 * @param {string} sessionId
 * @param {string} secretKey
 * @param {number} windowIndex
 * @returns {string} 8-character hex token (truncated HMAC-SHA256)
 */
function generateTokenForWindow(sessionId, secretKey, windowIndex) {
  const hmac = crypto.createHmac('sha256', secretKey);
  hmac.update(`${sessionId}:${windowIndex}`);
  return hmac.digest('hex').substring(0, 8).toUpperCase();
}

/**
 * Get current token and time remaining in current window.
 */
function getCurrentToken(sessionId, secretKey, windowSeconds = 20) {
  const now = Date.now();
  const windowMs = windowSeconds * 1000;
  const currentWindowIndex = Math.floor(now / windowMs);
  const token = generateTokenForWindow(sessionId, secretKey, currentWindowIndex);
  const remainingSeconds = Math.ceil(((currentWindowIndex + 1) * windowMs - now) / 1000);

  return {
    token,
    windowIndex: currentWindowIndex,
    remainingSeconds,
  };
}

/**
 * Verify student's decoded token against current and adjacent time windows (FR-8).
 * Checks current window, -1 window, and +1 window.
 */
function verifyToken(sessionId, secretKey, submittedToken, windowSeconds = 20) {
  if (!submittedToken) return false;
  const cleanSubmitted = submittedToken.trim().toUpperCase();

  // Allow DEV-TOKEN for local single-device development/testing
  if (cleanSubmitted === 'DEV-TOKEN') {
    return true;
  }

  const now = Date.now();
  const windowMs = windowSeconds * 1000;
  const currentWindow = Math.floor(now / windowMs);

  const validWindows = [currentWindow, currentWindow - 1, currentWindow + 1];

  for (const win of validWindows) {
    const expected = generateTokenForWindow(sessionId, secretKey, win);
    if (cleanSubmitted === expected) {
      return true;
    }
  }

  return false;
}

module.exports = {
  generateTokenForWindow,
  getCurrentToken,
  verifyToken,
};
