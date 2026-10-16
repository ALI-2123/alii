const $ = (id) => document.getElementById(id);
const PAGES = ['play', 'premium', 'cracked', 'mods', 'game', 'cosmetics', 'friends', 'chat', 'client', 'logs'];
const LABELS = {
  java: 'Downloading Java',
  assets: 'Downloading assets',
  natives: 'Preparing natives',
  classes: 'Downloading libraries',
  'classes-custom': 'Downloading libraries',
  'classes-maven-custom': 'Downloading libraries',
};

let settings = {};
let premiumName = null;
let versionInfo = new Map();
let running = false;
const logLines = [];

function make(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function formatSize(bytes) {
  return bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/* ---------- navigation ---------- */
function show(page) {
  document.body.dataset.page = page;
  for (const id of PAGES) $(`page-${id}`).hidden = id !== page;
  for (const tab of document.querySelectorAll('.tabs button')) {
    tab.setAttribute('aria-selected', String(tab.dataset.page === page));
  }
  if (page === 'mods') refreshMods();
  if ((page === 'friends' || page === 'chat') && !me && !connecting) connectSocial();
  if (page === 'chat') {
    unread = 0;
    renderBadge();
    renderChat();
  }
  if (page === 'logs') $('log').scrollTop = $('log').scrollHeight;
}

for (const tab of document.querySelectorAll('.tabs button')) {
  tab.addEventListener('click', () => show(tab.dataset.page));
}
for (const link of document.querySelectorAll('[data-goto]')) {
  link.addEventListener('click', () => show(link.dataset.goto));
}
$('btn-min').addEventListener('click', () => blade.minimize());
$('btn-close').addEventListener('click', () => blade.close());

/* ---------- settings ---------- */
function controlValue(el) {
  if (el.type === 'checkbox') return el.checked;
  if (el.type === 'number' || el.type === 'range' || el.dataset.number !== undefined) return Number(el.value);
  return el.value.trim();
}

function syncControls() {
  for (const el of document.querySelectorAll('[data-setting]')) {
    const value = settings[el.dataset.setting];
    if (el.type === 'checkbox') el.checked = Boolean(value);
    else el.value = String(value);
  }
  for (const out of document.querySelectorAll('output[data-for]')) {
    out.textContent = `${settings[out.dataset.for]} ${out.dataset.unit || ''}`.trim();
  }
  document.body.dataset.theme = settings.theme;
  document.body.dataset.fx = settings.effects ? 'on' : 'off';
  renderAccount();
  renderLoader();
}

for (const el of document.querySelectorAll('[data-setting]')) {
  el.addEventListener('change', async () => {
    const key = el.dataset.setting;
    settings = await blade.setSettings({ [key]: controlValue(el) });
    syncControls();
    if (key === 'showSnapshots' || key === 'showOld') await loadVersions();
    if (key === 'loader' && !$('page-mods').hidden) refreshMods();
    if (key === 'serverUrl') {
      await blade.socialDisconnect();
      me = null;
      renderFriends();
    }
  });
}
for (const el of document.querySelectorAll('input[type="range"][data-setting]')) {
  el.addEventListener('input', () => {
    const out = document.querySelector(`output[data-for="${el.dataset.setting}"]`);
    if (out) out.textContent = `${el.value} ${out.dataset.unit || ''}`.trim();
  });
}

/* ---------- accounts ---------- */
function usernameValid(name) {
  return /^[A-Za-z0-9_]{3,16}$/.test(name);
}

function renderAccount() {
  const premium = settings.mode === 'premium';
  $('chip-name').textContent = premium ? premiumName || 'Not signed in' : settings.username || 'No username yet';
  $('chip-type').textContent = premium ? 'Premium' : 'Cracked';
  $('chip-change').dataset.goto = settings.mode;
  $('premium-status').textContent = premiumName ? `Signed in as ${premiumName}` : 'Not signed in';
  $('btn-login').textContent = premiumName ? 'Sign in again' : 'Sign in with Microsoft';
  $('btn-logout').hidden = !premiumName;
  $('use-premium').textContent = premium ? 'In use' : 'Use Premium';
  $('use-premium').classList.toggle('on', premium);
  $('use-cracked').textContent = premium ? 'Use Cracked' : 'In use';
  $('use-cracked').classList.toggle('on', !premium);
}

async function setMode(mode) {
  settings = await blade.setSettings({ mode });
  renderAccount();
}

$('use-premium').addEventListener('click', () => setMode('premium'));

$('use-cracked').addEventListener('click', () => {
  if (!usernameValid($('username').value.trim())) {
    $('username-hint').classList.add('error');
    return;
  }
  setMode('cracked');
});

$('username').addEventListener('input', () => {
  const value = $('username').value.trim();
  $('username-hint').classList.toggle('error', value !== '' && !usernameValid(value));
});

$('btn-logout').addEventListener('click', async () => {
  await blade.logout();
  premiumName = null;
  renderAccount();
});

$('btn-login').addEventListener('click', async () => {
  $('btn-login').disabled = true;
  $('premium-status').textContent = 'Waiting for Microsoft sign-in…';
  const res = await blade.loginPremium();
  $('btn-login').disabled = false;
  if (res.ok) {
    premiumName = res.name;
    await setMode('premium');
  } else {
    renderAccount();
    $('premium-status').textContent = res.error;
  }
});

/* ---------- versions and loader ---------- */
async function loadVersions() {
  const res = await blade.listVersions({ snapshots: settings.showSnapshots, old: settings.showOld });
  const select = $('version');
  select.replaceChildren();
  versionInfo = new Map();
  if (!res.ok) {
    setStatus(`Couldn't load versions. ${res.error}`, true);
    return;
  }
  for (const v of res.versions) {
    versionInfo.set(v.id, v);
    const label = v.type === 'release' ? v.id : `${v.id} (${v.type.replace('_', ' ')})`;
    const option = make('option', '', label);
    option.value = v.id;
    select.append(option);
  }
  if (versionInfo.has(settings.version)) select.value = settings.version;
  renderLoader();
}

function renderLoader() {
  const info = versionInfo.get($('version').value);
  const wantsFabric = settings.loader === 'fabric';
  let text = 'Vanilla';
  if (wantsFabric) text = info && info.fabric ? 'Fabric' : 'Vanilla (Fabric needs 1.14 or newer)';
  $('loader-text').textContent = text;
}

$('version').addEventListener('change', async () => {
  settings = await blade.setSettings({ version: $('version').value });
  renderLoader();
  if (!$('page-mods').hidden) refreshMods();
});

/* ---------- play ---------- */
function setStatus(text, isError = false) {
  $('status').textContent = text;
  $('status').classList.toggle('error', isError);
}

function setBusy(busy, label = 'Starting…') {
  running = busy;
  $('play').disabled = busy;
  $('smp').disabled = busy;
  $('play').textContent = busy ? label : 'Play';
  $('bar').hidden = !busy;
  if (!busy) $('bar').value = 0;
}

async function startGame(join) {
  setBusy(true);
  setStatus('Preparing…');
  const res = await blade.launch({ version: $('version').value, join });
  if (res.ok) {
    setBusy(true, 'Running');
    $('bar').hidden = true;
    setStatus('Game running');
  } else {
    setBusy(false);
    setStatus(res.error, true);
  }
}

$('play').addEventListener('click', () => startGame(false));
$('smp').addEventListener('click', () => startGame(true));

blade.onStatus((text) => {
  if (running) setStatus(text);
});

blade.onProgress((p) => {
  if (!running) return;
  $('bar').hidden = false;
  $('bar').max = p.total || 100;
  $('bar').value = p.task || 0;
  setStatus(LABELS[p.type] || `Downloading ${p.type}`);
});

blade.onClosed(({ code }) => {
  setBusy(false);
  if (code === 0 || code === null) setStatus('Game closed');
  else setStatus(`The game stopped with code ${code}. Check the Logs page.`, true);
});

/* ---------- mods ---------- */
function modsStatus(text, isError = false) {
  $('mods-status').textContent = text;
  $('mods-status').classList.toggle('error', isError);
}

async function refreshMods() {
  const version = $('version').value;
  const info = versionInfo.get(version);
  const canBrowse = Boolean(info && info.fabric);
  $('mods-version').textContent = version;
  $('mods-browse').hidden = !canBrowse;
  $('mods-pack').hidden = !canBrowse;
  $('mods-blade').hidden = !canBrowse;
  $('mods-note').textContent = canBrowse
    ? ''
    : 'Fabric needs Minecraft 1.14 or newer, so Modrinth search is off for this version. You can still add mod files yourself.';

  const res = await blade.listMods(version);
  const list = $('mods-list');
  list.replaceChildren();
  if (!res.ok) {
    modsStatus(res.error, true);
    return;
  }
  $('mods-count').textContent = `(${res.mods.length})`;
  if (!res.mods.length) list.append(make('p', 'small', 'No mods yet. Add some above.'));
  for (const mod of res.mods) {
    const row = make('div', mod.enabled ? 'mod' : 'mod off');
    const body = make('div', 'info');
    body.append(make('div', 'nm', mod.name), make('div', 'ds', formatSize(mod.size)));
    const toggle = make('input', 'switch');
    toggle.type = 'checkbox';
    toggle.checked = mod.enabled;
    toggle.setAttribute('aria-label', `Enable ${mod.name}`);
    toggle.addEventListener('change', async () => {
      await blade.toggleMod(version, mod.file);
      refreshMods();
    });
    const remove = make('button', 'btn', 'Remove');
    remove.type = 'button';
    remove.addEventListener('click', async () => {
      if (remove.dataset.confirm !== '1') {
        remove.dataset.confirm = '1';
        remove.textContent = 'Confirm';
        setTimeout(() => {
          remove.dataset.confirm = '';
          remove.textContent = 'Remove';
        }, 3000);
        return;
      }
      await blade.removeMod(version, mod.file);
      refreshMods();
    });
    row.append(body, toggle, remove);
    list.append(row);
  }
}

$('mods-add').addEventListener('click', async () => {
  const res = await blade.addMods($('version').value);
  modsStatus(res.ok ? (res.added ? `Added ${res.added} mod file${res.added === 1 ? '' : 's'}` : '') : res.error, !res.ok);
  refreshMods();
});

$('mods-blade').addEventListener('click', async () => {
  $('mods-blade').disabled = true;
  const res = await blade.installBlade($('version').value);
  $('mods-blade').disabled = false;
  modsStatus(res.ok ? `Installed ${res.jar}` : res.error, !res.ok);
  refreshMods();
});

$('mods-folder').addEventListener('click', async () => {
  const res = await blade.openModsFolder($('version').value);
  if (!res.ok) modsStatus(res.error, true);
});

$('mods-pack').addEventListener('click', async () => {
  $('mods-pack').disabled = true;
  modsStatus('Installing the performance pack…');
  const res = await blade.installPack($('version').value);
  $('mods-pack').disabled = false;
  if (!res.ok) modsStatus(res.error, true);
  else if (res.skipped.length) modsStatus(`Installed ${res.installed.length}. No build for this version: ${res.skipped.join(', ')}`);
  else modsStatus(`Installed ${res.installed.length} mod${res.installed.length === 1 ? '' : 's'}`);
  if (res.ok && settings.loader !== 'fabric') {
    settings = await blade.setSettings({ loader: 'fabric' });
    syncControls();
  }
  refreshMods();
});

async function searchMods() {
  const query = $('mods-query').value.trim();
  if (!query) return;
  const box = $('mods-results');
  box.replaceChildren(make('p', 'small', 'Searching…'));
  const version = $('version').value;
  const res = await blade.searchMods(query, version);
  box.replaceChildren();
  if (!res.ok) {
    box.append(make('p', 'small error', res.error));
    return;
  }
  if (!res.results.length) box.append(make('p', 'small', 'No Fabric mods found for this version.'));
  for (const hit of res.results) {
    const row = make('div', 'hit');
    const body = make('div', 'info');
    body.append(make('div', 'nm', hit.title), make('div', 'ds', hit.description));
    const button = make('button', 'btn', 'Install');
    button.type = 'button';
    button.addEventListener('click', async () => {
      button.disabled = true;
      button.textContent = 'Installing…';
      const done = await blade.installMod(version, hit.slug);
      button.textContent = done.ok && done.installed.length ? 'Installed' : 'Unavailable';
      if (!done.ok) modsStatus(done.error, true);
      if (done.ok && settings.loader !== 'fabric') {
        settings = await blade.setSettings({ loader: 'fabric' });
        syncControls();
      }
      refreshMods();
    });
    row.append(body, button);
    box.append(row);
  }
}

$('mods-search').addEventListener('click', searchMods);
$('mods-query').addEventListener('keydown', (event) => {
  if (event.key === 'Enter') searchMods();
});

/* ---------- client ---------- */
$('open-folder').addEventListener('click', async () => {
  const res = await blade.openFolder($('version').value);
  $('client-status').textContent = res.ok ? '' : res.error;
  $('client-status').classList.toggle('error', !res.ok);
});

/* ---------- friends and chat ---------- */
let me = null; // { name, friends: [{ name, online }], requests: [name] }
let connected = false;
let connecting = false;
let channel = 'global';
let unread = 0;
const globalChat = [];
const dmChats = new Map();

const friendsStatus = (text, isError = false) => {
  $('friends-status').textContent = text;
  $('friends-status').classList.toggle('error', isError);
};

function renderBadge() {
  const tab = document.querySelector('.tabs button[data-page="chat"]');
  tab.replaceChildren(document.createTextNode('Chat'));
  if (unread > 0) tab.append(make('span', 'badge', String(Math.min(unread, 99))));
}

function renderSocialState() {
  let text = 'Not connected';
  if (connecting) text = 'Connecting…';
  else if (me && connected) text = `Connected as ${me.name}`;
  else if (me) text = `Reconnecting as ${me.name}…`;
  $('social-state').textContent = text;
  $('social-connect').textContent = me ? 'Reconnect' : 'Connect';
}

function renderFriends() {
  renderSocialState();
  const requests = $('friend-requests');
  const list = $('friend-list');
  requests.replaceChildren();
  list.replaceChildren();
  $('request-count').textContent = me && me.requests.length ? `(${me.requests.length})` : '';
  $('friend-count').textContent = me ? `(${me.friends.length})` : '';
  if (!me) {
    list.append(make('p', 'small', 'Connect to see your friends.'));
    renderChannels();
    return;
  }
  if (!me.requests.length) requests.append(make('p', 'small', 'No requests right now.'));
  for (const name of me.requests) {
    const row = make('div', 'friend');
    const body = make('div', 'info');
    body.append(make('div', 'nm', name), make('div', 'ds', 'Wants to be your friend'));
    const accept = make('button', 'btn primary', 'Accept');
    const decline = make('button', 'btn', 'Decline');
    accept.type = decline.type = 'button';
    accept.addEventListener('click', () => friendAction('accept', name));
    decline.addEventListener('click', () => friendAction('decline', name));
    row.append(body, accept, decline);
    requests.append(row);
  }
  if (!me.friends.length) list.append(make('p', 'small', 'No friends yet. Send a request above.'));
  for (const friend of [...me.friends].sort((a, b) => Number(b.online) - Number(a.online) || a.name.localeCompare(b.name))) {
    const row = make('div', 'friend');
    const body = make('div', 'info');
    body.append(make('div', 'nm', friend.name), make('div', 'ds', friend.online ? 'Online' : 'Offline'));
    const message = make('button', 'btn', 'Message');
    const remove = make('button', 'btn', 'Remove');
    message.type = remove.type = 'button';
    message.addEventListener('click', () => {
      channel = friend.name;
      show('chat');
    });
    remove.addEventListener('click', () => {
      if (remove.dataset.confirm !== '1') {
        remove.dataset.confirm = '1';
        remove.textContent = 'Confirm';
        setTimeout(() => {
          remove.dataset.confirm = '';
          remove.textContent = 'Remove';
        }, 3000);
        return;
      }
      friendAction('remove', friend.name);
    });
    row.append(make('span', friend.online ? 'dot on' : 'dot'), body, message, remove);
    list.append(row);
  }
  renderChannels();
}

async function connectSocial() {
  connecting = true;
  renderSocialState();
  friendsStatus('');
  const res = await blade.socialConnect();
  connecting = false;
  if (res.ok) {
    me = res.me;
    friendsStatus('');
  } else {
    me = null;
    friendsStatus(res.error, true);
  }
  renderFriends();
}

async function refreshMe() {
  if (!me) return;
  const res = await blade.socialMe();
  if (res.ok) {
    me = res.me;
    renderFriends();
  }
}

async function friendAction(action, name) {
  const res = await blade.friendAction(action, name);
  if (res.ok) {
    me = res.me;
    friendsStatus(action === 'request' ? `Request sent to ${name}` : '');
    renderFriends();
  } else {
    friendsStatus(res.error, true);
  }
}

$('social-connect').addEventListener('click', connectSocial);
$('friend-add').addEventListener('click', async () => {
  const name = $('friend-name').value.trim();
  if (!name) return;
  if (!me) await connectSocial();
  if (!me) return;
  await friendAction('request', name);
  $('friend-name').value = '';
});
$('friend-name').addEventListener('keydown', (event) => {
  if (event.key === 'Enter') $('friend-add').click();
});

function renderChannels() {
  const select = $('chat-channel');
  select.replaceChildren();
  const names = me ? me.friends.map((f) => f.name) : [];
  if (channel !== 'global' && !names.includes(channel)) channel = 'global';
  const options = [['global', 'Global chat'], ...names.map((n) => [n, `Message ${n}`])];
  for (const [value, label] of options) {
    const option = make('option', '', label);
    option.value = value;
    select.append(option);
  }
  select.value = channel;
  renderChat();
}

function renderChat() {
  const box = $('chat-log');
  box.replaceChildren();
  const messages = channel === 'global' ? globalChat : dmChats.get(channel.toLowerCase()) || [];
  if (!me) box.append(make('div', 'l empty', 'Connect on the Friends page to chat.'));
  else if (!messages.length) box.append(make('div', 'l empty', channel === 'global' ? 'No messages yet. Say hi.' : `No messages with ${channel} yet.`));
  for (const m of messages) {
    const own = me && m.from.toLowerCase() === me.name.toLowerCase();
    const row = make('div', own ? 'l me' : 'l');
    const time = new Date(m.time);
    row.append(
      make('span', 't', `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`),
      make('span', 'who', own ? 'You' : m.from),
      make('span', 'm', m.text)
    );
    box.append(row);
  }
  box.scrollTop = box.scrollHeight;
}

$('chat-channel').addEventListener('change', () => {
  channel = $('chat-channel').value;
  renderChat();
});

async function sendChat() {
  const input = $('chat-input');
  const text = input.value.trim();
  if (!text) return;
  $('chat-status').textContent = '';
  if (!me) {
    $('chat-status').textContent = 'Connect on the Friends page first.';
    $('chat-status').classList.add('error');
    return;
  }
  input.value = '';
  const res = await blade.sendChat(text, channel === 'global' ? undefined : channel);
  $('chat-status').textContent = res.ok ? '' : res.error;
  $('chat-status').classList.toggle('error', !res.ok);
}
$('chat-send').addEventListener('click', sendChat);
$('chat-input').addEventListener('keydown', (event) => {
  if (event.key === 'Enter') sendChat();
});

blade.onSocial((event) => {
  if (event.type === 'status') {
    connected = event.connected;
    renderSocialState();
  } else if (event.type === 'history') {
    globalChat.splice(0, globalChat.length, ...event.messages);
    renderChat();
  } else if (event.type === 'friends') {
    refreshMe();
  } else if (event.type === 'request') {
    refreshMe();
    friendsStatus(`${event.from} sent you a friend request`);
  } else if (event.type === 'chat') {
    let key = 'global';
    if (event.to) {
      const mine = me ? me.name.toLowerCase() : '';
      key = (event.from.toLowerCase() === mine ? event.to : event.from).toLowerCase();
      if (!dmChats.has(key)) dmChats.set(key, []);
      dmChats.get(key).push(event);
    } else {
      globalChat.push(event);
      if (globalChat.length > 200) globalChat.shift();
    }
    const viewing = document.body.dataset.page === 'chat' && channel.toLowerCase() === key;
    const own = me && event.from.toLowerCase() === me.name.toLowerCase();
    if (viewing) renderChat();
    else if (!own) {
      unread += 1;
      renderBadge();
    }
  }
});

/* ---------- embers ---------- */
(function embers() {
  const canvas = $('embers');
  const ctx = canvas.getContext('2d');
  const sparks = [];
  let width = 0;
  let height = 0;
  let color = [255, 90, 60];

  function resize() {
    const ratio = window.devicePixelRatio || 1;
    width = canvas.clientWidth;
    height = canvas.clientHeight;
    canvas.width = width * ratio;
    canvas.height = height * ratio;
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
  }

  function readColor() {
    const hex = getComputedStyle(document.body).getPropertyValue('--hot').trim();
    const m = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(hex);
    if (m) color = [parseInt(m[1], 16), parseInt(m[2], 16), parseInt(m[3], 16)];
  }

  const spawn = (anywhere) => ({
    x: Math.random() * width,
    y: anywhere ? Math.random() * height : height + 10,
    r: 0.6 + Math.random() * 1.8,
    vy: 0.25 + Math.random() * 0.7,
    sway: Math.random() * Math.PI * 2,
    a: 0.2 + Math.random() * 0.6,
  });

  function frame() {
    requestAnimationFrame(frame);
    if (document.hidden || document.body.dataset.fx === 'off') return;
    ctx.clearRect(0, 0, width, height);
    while (sparks.length < 55) sparks.push(spawn(sparks.length < 55 && width && !sparks.length ? true : Math.random() < 0.5));
    for (const s of sparks) {
      s.y -= s.vy;
      s.sway += 0.02;
      s.x += Math.sin(s.sway) * 0.35;
      if (s.y < -10) Object.assign(s, spawn(false));
      const fade = Math.min(1, s.y / (height * 0.5));
      ctx.beginPath();
      ctx.fillStyle = `rgba(${color[0]}, ${color[1]}, ${color[2]}, ${s.a * fade})`;
      ctx.shadowColor = `rgb(${color[0]}, ${color[1]}, ${color[2]})`;
      ctx.shadowBlur = 8;
      ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  window.addEventListener('resize', resize);
  new MutationObserver(readColor).observe(document.body, { attributes: true, attributeFilter: ['data-theme'] });
  resize();
  readColor();
  frame();
})();

/* ---------- logs ---------- */
function addLogLines(entries) {
  const box = $('log');
  const fragment = document.createDocumentFragment();
  for (const entry of entries) {
    const row = make('div', `l lvl-${entry.level}`);
    row.append(make('span', 't', entry.time), make('span', 's', entry.source), make('span', 'm', entry.text));
    fragment.append(row);
    logLines.push(`${entry.time} [${entry.source}] ${entry.text}`);
  }
  box.append(fragment);
  while (box.childElementCount > 2000) box.firstElementChild.remove();
  if (logLines.length > 2000) logLines.splice(0, logLines.length - 2000);
  if ($('log-follow').checked) box.scrollTop = box.scrollHeight;
}

$('log-clear').addEventListener('click', async () => {
  await blade.clearLog();
  $('log').replaceChildren();
  logLines.length = 0;
});

$('log-copy').addEventListener('click', async () => {
  await navigator.clipboard.writeText(logLines.join('\n'));
  $('log-copy').textContent = 'Copied';
  setTimeout(() => { $('log-copy').textContent = 'Copy'; }, 1500);
});

blade.onLog(addLogLines);

/* ---------- start ---------- */
(async function init() {
  settings = await blade.getSettings();
  const account = await blade.accountState();
  premiumName = account.premium;
  syncControls();
  addLogLines(await blade.getLog());
  await loadVersions();
})();
