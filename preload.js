const { contextBridge, ipcRenderer } = require('electron');

const call = (channel) => (...args) => ipcRenderer.invoke(channel, ...args);

function listen(channel) {
  return (callback) => {
    const handler = (_event, payload) => callback(payload);
    ipcRenderer.on(channel, handler);
    return () => ipcRenderer.removeListener(channel, handler);
  };
}

contextBridge.exposeInMainWorld('blade', {
  getSettings: call('settings:get'),
  setSettings: call('settings:set'),
  listVersions: call('versions:list'),
  accountState: call('account:state'),
  loginPremium: call('account:premium'),
  logout: call('account:logout'),
  launch: call('game:launch'),
  openFolder: call('game:folder'),
  listMods: call('mods:list'),
  toggleMod: call('mods:toggle'),
  removeMod: call('mods:remove'),
  addMods: call('mods:add'),
  openModsFolder: call('mods:folder'),
  searchMods: call('mods:search'),
  installMod: call('mods:install'),
  installPack: call('mods:pack'),
  installBlade: call('mods:blade'),
  socialConnect: call('social:connect'),
  socialDisconnect: call('social:disconnect'),
  socialMe: call('social:me'),
  friendAction: call('social:friend'),
  sendChat: call('social:chat'),
  onSocial: listen('social:event'),
  getLog: call('log:get'),
  clearLog: call('log:clear'),
  onLog: listen('log:lines'),
  onProgress: listen('game:progress'),
  onStatus: listen('game:status'),
  onClosed: listen('game:closed'),
  minimize: () => ipcRenderer.send('win:minimize'),
  close: () => ipcRenderer.send('win:close'),
});
