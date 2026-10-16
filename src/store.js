const { app } = require('electron');
const fs = require('fs');
const path = require('path');

const defaults = {
  mode: 'premium',
  username: '',
  version: '1.8.9',
  loader: 'vanilla',
  ramGb: 4,
  javaPath: '',
  showSnapshots: false,
  showOld: false,
  minimizeOnLaunch: false,
  applyVideo: true,
  fullscreen: false,
  width: 1280,
  height: 720,
  maxFps: 260,
  renderDistance: 12,
  vsync: false,
  particles: 0,
  entityShadows: true,
  jvmPreset: 'performance',
  theme: 'blade',
  aura: 'none',
  wings: 'none',
  bladeItem: 'none',
  cape: 'none',
  trail: 'none',
  emote1: 'wave',
  emote2: 'gg',
  emote3: 'salute',
  emote4: 'taunt',
  effects: true,
  serverUrl: 'http://localhost:3000',
  smpAddress: 'localhost:25565',
  socialName: '',
  socialToken: '',
  socialBase: '',
};

const ranges = {
  ramGb: [1, 32],
  width: [320, 7680],
  height: [240, 4320],
  maxFps: [10, 260],
  renderDistance: [2, 32],
  particles: [0, 2],
};

const choices = {
  mode: ['premium', 'cracked'],
  loader: ['vanilla', 'fabric'],
  jvmPreset: ['default', 'performance'],
  theme: ['blade', 'crimson', 'abyss'],
  aura: ['none', 'flame', 'ember', 'frost', 'void', 'blood', 'blade'],
  wings: ['none', 'blade', 'crimson', 'shadow'],
  bladeItem: ['none', 'blade', 'crimson', 'shadow'],
  cape: ['none', 'blade', 'crimson', 'shadow'],
  trail: ['none', 'spark', 'smoke', 'slash'],
  emote1: ['wave', 'gg', 'salute', 'taunt', 'laugh', 'bow'],
  emote2: ['wave', 'gg', 'salute', 'taunt', 'laugh', 'bow'],
  emote3: ['wave', 'gg', 'salute', 'taunt', 'laugh', 'bow'],
  emote4: ['wave', 'gg', 'salute', 'taunt', 'laugh', 'bow'],
};

let cache = null;

const filePath = () => path.join(app.getPath('userData'), 'settings.json');

function sanitize(input) {
  const out = { ...defaults };
  for (const key of Object.keys(defaults)) {
    let value = input[key];
    if (typeof value !== typeof defaults[key]) continue;
    if (typeof value === 'number') {
      if (!Number.isFinite(value)) continue;
      value = Math.round(value);
      if (ranges[key]) value = Math.min(ranges[key][1], Math.max(ranges[key][0], value));
    }
    if (choices[key] && !choices[key].includes(value)) continue;
    out[key] = value;
  }
  return out;
}

function load() {
  if (cache) return cache;
  let saved = {};
  try {
    saved = JSON.parse(fs.readFileSync(filePath(), 'utf8'));
  } catch {
    /* first run */
  }
  if (saved.mode === 'offline') saved.mode = 'cracked';
  cache = sanitize(saved);
  return cache;
}

function get() {
  return { ...load() };
}

function set(patch) {
  cache = sanitize({ ...load(), ...patch });
  fs.mkdirSync(path.dirname(filePath()), { recursive: true });
  fs.writeFileSync(filePath(), JSON.stringify(cache, null, 2));
  return { ...cache };
}

module.exports = { get, set, defaults };
