const { app, safeStorage } = require('electron');
const fs = require('fs');
const path = require('path');

// The Microsoft refresh token is kept encrypted with the operating system's own protection
// (Windows DPAPI), so signing in once keeps you signed in after the launcher closes.
const file = () => path.join(app.getPath('userData'), 'account.bin');

function save(token) {
  if (!token) return false;
  if (!safeStorage.isEncryptionAvailable()) return false; // never write it as plain text
  fs.writeFileSync(file(), safeStorage.encryptString(token));
  return true;
}

function load() {
  try {
    if (!safeStorage.isEncryptionAvailable()) return null;
    return safeStorage.decryptString(fs.readFileSync(file()));
  } catch {
    return null;
  }
}

function clear() {
  fs.rmSync(file(), { force: true });
}

module.exports = { save, load, clear };
