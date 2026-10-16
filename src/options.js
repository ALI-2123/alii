const fs = require('fs');
const path = require('path');

const has = (object, key) => Object.prototype.hasOwnProperty.call(object, key);

// Writes Blade's video settings into the instance's options.txt and leaves every other line alone.
function apply(gameDir, settings) {
  const file = path.join(gameDir, 'options.txt');
  const wanted = {
    maxFps: settings.maxFps,
    renderDistance: settings.renderDistance,
    enableVsync: settings.vsync,
    particles: settings.particles,
    entityShadows: settings.entityShadows,
  };
  let lines = [];
  try {
    lines = fs.readFileSync(file, 'utf8').split(/\r?\n/).filter((line) => line !== '');
  } catch {
    /* new instance */
  }
  const seen = new Set();
  const out = lines.map((line) => {
    const key = line.includes(':') ? line.slice(0, line.indexOf(':')) : '';
    if (key && has(wanted, key)) {
      seen.add(key);
      return `${key}:${wanted[key]}`;
    }
    return line;
  });
  for (const [key, value] of Object.entries(wanted)) {
    if (!seen.has(key)) out.push(`${key}:${value}`);
  }
  fs.mkdirSync(gameDir, { recursive: true });
  fs.writeFileSync(file, `${out.join('\n')}\n`);
}

module.exports = { apply };
