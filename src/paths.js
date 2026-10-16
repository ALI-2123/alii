const { app } = require('electron');
const path = require('path');

function safeVersion(version) {
  const value = String(version || '');
  if (!/^[\w.\- ]+$/.test(value) || value.includes('..')) throw new Error('Invalid version name');
  return value;
}

const root = () => path.join(app.getPath('userData'), 'minecraft');

module.exports = {
  root,
  runtime: () => path.join(app.getPath('userData'), 'runtime'),
  instance: (version) => path.join(root(), 'instances', safeVersion(version)),
  mods: (version) => path.join(root(), 'instances', safeVersion(version), 'mods'),
};
