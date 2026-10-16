const fs = require('fs');
const path = require('path');

// Writes the player's Blade look and server sign-in where the in-game Blade mod reads it.
function write(gameDir, settings, username, server) {
  const dir = path.join(gameDir, 'config', 'blade');
  fs.mkdirSync(dir, { recursive: true });
  const profile = {
    version: 1,
    username,
    theme: settings.theme,
    hudKey: 'RIGHT_SHIFT',
    cosmetics: { aura: settings.aura, wings: settings.wings, blade: settings.bladeItem, cape: settings.cape, trail: settings.trail },
    server: server || null,
    emotes: [settings.emote1, settings.emote2, settings.emote3, settings.emote4],
  };
  fs.writeFileSync(path.join(dir, 'profile.json'), `${JSON.stringify(profile, null, 2)}\n`);
}

module.exports = { write };
