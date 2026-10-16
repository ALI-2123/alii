const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const fs = require('fs');
const path = require('path');
const store = require('./src/store');
const game = require('./src/game');
const versions = require('./src/versions');
const mods = require('./src/mods');
const paths = require('./src/paths');
const log = require('./src/log');
const social = require('./src/social');

let win = null;

function createWindow() {
  win = new BrowserWindow({
    width: 1100,
    height: 650,
    resizable: false,
    frame: false,
    backgroundColor: '#000000',
    title: 'Blade client',
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', (event) => event.preventDefault());
  log.setSink((entries) => {
    if (win && !win.isDestroyed()) win.webContents.send('log:lines', entries);
  });
  social.setSink((event) => {
    if (win && !win.isDestroyed()) win.webContents.send('social:event', event);
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

// Every handler answers { ok: true, ... } or { ok: false, error } so the UI never sees a raw exception.
const wrap = (fn) => async (_event, ...args) => {
  try {
    return { ok: true, ...(await fn(...args)) };
  } catch (err) {
    return { ok: false, error: err.message || String(err) };
  }
};

ipcMain.handle('settings:get', () => store.get());
ipcMain.handle('settings:set', (_event, patch) => store.set(patch || {}));

ipcMain.handle('versions:list', wrap(async (opts) => ({ versions: await versions.list(opts || {}) })));

ipcMain.handle('account:state', () => game.accountState());
ipcMain.handle('account:premium', () => game.loginPremium());
ipcMain.handle('account:logout', wrap(async () => { game.logout(); return {}; }));

ipcMain.handle('game:launch', async (event, opts) => {
  const emit = (channel, payload) => {
    if (!event.sender.isDestroyed()) event.sender.send(channel, payload);
  };
  const result = await game.launch(opts || {}, emit);
  if (result.ok && store.get().minimizeOnLaunch && win) win.minimize();
  return result;
});

ipcMain.handle('game:folder', wrap(async (version) => {
  const dir = paths.instance(version);
  fs.mkdirSync(dir, { recursive: true });
  const problem = await shell.openPath(dir);
  if (problem) throw new Error(problem);
  return {};
}));

ipcMain.handle('mods:list', wrap((version) => ({ mods: mods.list(version) })));
ipcMain.handle('mods:toggle', wrap((version, file) => { mods.toggle(version, file); return {}; }));
ipcMain.handle('mods:remove', wrap((version, file) => { mods.remove(version, file); return {}; }));
ipcMain.handle('mods:add', wrap(async (version) => {
  const picked = await dialog.showOpenDialog(win, {
    title: 'Add mods',
    properties: ['openFile', 'multiSelections'],
    filters: [{ name: 'Mod files', extensions: ['jar'] }],
  });
  return { added: picked.canceled ? 0 : mods.add(version, picked.filePaths) };
}));
ipcMain.handle('mods:folder', wrap(async (version) => {
  const dir = paths.mods(version);
  fs.mkdirSync(dir, { recursive: true });
  const problem = await shell.openPath(dir);
  if (problem) throw new Error(problem);
  return {};
}));
ipcMain.handle('mods:search', wrap(async (query, version) => ({ results: await mods.search(query, version) })));
ipcMain.handle('mods:install', wrap((version, slug) => mods.install(version, slug)));
ipcMain.handle('mods:blade', wrap((version) => mods.installBlade(version)));
ipcMain.handle('mods:pack', wrap((version) => mods.installPack(version)));

function accountName() {
  const settings = store.get();
  return settings.mode === 'premium' ? game.accountState().premium : settings.username;
}
ipcMain.handle('social:connect', wrap(async () => {
  const name = accountName();
  if (!name) throw new Error('Sign in with Microsoft or set a username first');
  return { me: await social.connect(name) };
}));
ipcMain.handle('social:disconnect', wrap(async () => { social.disconnect(); return {}; }));
ipcMain.handle('social:me', wrap(async () => ({ me: await social.me() })));
ipcMain.handle('social:friend', wrap(async (action, name) => ({ me: await social.friend(action, name) })));
ipcMain.handle('social:chat', wrap(async (text, to) => { await social.chat(text, to); return {}; }));

ipcMain.handle('log:get', () => log.all());
ipcMain.handle('log:clear', () => { log.clear(); });

ipcMain.on('win:minimize', () => win && win.minimize());
ipcMain.on('win:close', () => win && win.close());

app.whenReady().then(async () => {
  // Bring back the remembered Microsoft sign-in before the window asks who is signed in.
  await Promise.race([game.restorePremium(), new Promise((resolve) => setTimeout(resolve, 10000))]);
  createWindow();
});
app.on('window-all-closed', () => {
  social.disconnect();
  app.quit();
});
