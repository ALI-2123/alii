// Blade social server: accounts, friends, global chat and direct messages. No dependencies.
// Run: node server.js   (PORT and DATA_FILE can be set in the environment)
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.PORT) || 3000;
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, 'data.json');
const NAME = /^[A-Za-z0-9_]{3,16}$/;
const MAX_TEXT = 256;
const COSMETIC_CHOICES = {
  aura: ['none', 'flame', 'ember', 'frost', 'void', 'blood', 'blade'],
  wings: ['none', 'blade', 'crimson', 'shadow'],
  blade: ['none', 'blade', 'crimson', 'shadow'],
  trail: ['none', 'spark', 'smoke', 'slash'],
};

let users = {}; // lowercase name -> { name, token, friends: [key], incoming: [key] }
try {
  users = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
} catch {
  /* first run */
}

let saveTimer = null;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => fs.writeFileSync(DATA_FILE, JSON.stringify(users, null, 2)), 200);
}

const streams = new Map(); // key -> Set of responses
const history = []; // recent global chat
const lastSent = new Map(); // key -> timestamps for rate limiting

const key = (name) => String(name || '').toLowerCase();
const isOnline = (k) => streams.has(k) && streams.get(k).size > 0;
const tokenOk = (user, token) => {
  const a = Buffer.from(String(user.token));
  const b = Buffer.from(String(token || ''));
  return a.length === b.length && crypto.timingSafeEqual(a, b);
};

function push(k, event) {
  for (const res of streams.get(k) || []) res.write(`data: ${JSON.stringify(event)}\n\n`);
}
function pushAll(event) {
  for (const k of streams.keys()) push(k, event);
}

function view(user) {
  return {
    name: user.name,
    friends: user.friends.map((f) => ({ name: users[f].name, online: isOnline(f) })),
    requests: user.incoming.map((f) => users[f].name),
  };
}
const refresh = (...keys) => keys.forEach((k) => users[k] && push(k, { type: 'friends' }));

function send(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  res.end(JSON.stringify(body));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
      if (raw.length > 4096) {
        reject(new Error('Request too large'));
        req.destroy();
      }
    });
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        reject(new Error('Bad JSON'));
      }
    });
  });
}

function auth(req, url) {
  const k = key(req.headers['x-name'] || url.searchParams.get('name'));
  const user = users[k];
  const token = req.headers['x-token'] || url.searchParams.get('token');
  if (!user || !tokenOk(user, token)) return null;
  return { k, user };
}

const pull = (list, k) => {
  const i = list.indexOf(k);
  if (i >= 0) list.splice(i, 1);
};

const routes = {
  'POST /register': async (req, res, url, body) => {
    if (!NAME.test(body.name || '')) return send(res, 400, { error: 'Name must be 3 to 16 letters, numbers, or underscores' });
    const k = key(body.name);
    if (users[k]) {
      if (!tokenOk(users[k], body.token)) return send(res, 409, { error: 'That name is already taken' });
      return send(res, 200, { name: users[k].name, token: users[k].token });
    }
    users[k] = { name: body.name, token: crypto.randomBytes(24).toString('hex'), friends: [], incoming: [] };
    save();
    send(res, 200, { name: users[k].name, token: users[k].token });
  },

  'GET /me': (req, res, url, body, me) => send(res, 200, view(me.user)),

  'POST /friends/request': (req, res, url, body, me) => {
    const to = key(body.to);
    if (!users[to]) return send(res, 404, { error: 'No player with that name' });
    if (to === me.k) return send(res, 400, { error: "You can't add yourself" });
    if (me.user.friends.includes(to)) return send(res, 400, { error: 'Already friends' });
    if (me.user.incoming.includes(to)) {
      // they already asked us: accept
      pull(me.user.incoming, to);
      me.user.friends.push(to);
      users[to].friends.push(me.k);
    } else if (!users[to].incoming.includes(me.k)) {
      users[to].incoming.push(me.k);
      push(to, { type: 'request', from: me.user.name });
    }
    save();
    refresh(me.k, to);
    send(res, 200, view(me.user));
  },

  'POST /friends/accept': (req, res, url, body, me) => {
    const from = key(body.from);
    if (!me.user.incoming.includes(from)) return send(res, 404, { error: 'No request from that player' });
    pull(me.user.incoming, from);
    me.user.friends.push(from);
    users[from].friends.push(me.k);
    save();
    refresh(me.k, from);
    send(res, 200, view(me.user));
  },

  'POST /friends/decline': (req, res, url, body, me) => {
    pull(me.user.incoming, key(body.from));
    save();
    refresh(me.k);
    send(res, 200, view(me.user));
  },

  'POST /friends/remove': (req, res, url, body, me) => {
    const other = key(body.name);
    pull(me.user.friends, other);
    if (users[other]) pull(users[other].friends, me.k);
    save();
    refresh(me.k, other);
    send(res, 200, view(me.user));
  },

  'POST /cosmetics': (req, res, url, body, me) => {
    const next = {};
    for (const [field, allowed] of Object.entries(COSMETIC_CHOICES)) {
      next[field] = allowed.includes(body[field]) ? body[field] : 'none';
    }
    me.user.cosmetics = next;
    save();
    send(res, 200, next);
  },

  // Cosmetics of everyone who is online, so the game can draw other players' wings and blades.
  'GET /cosmetics': (req, res) => {
    const out = {};
    for (const k of streams.keys()) if (users[k] && users[k].cosmetics) out[k] = users[k].cosmetics;
    send(res, 200, out);
  },

  'POST /chat': (req, res, url, body, me) => {
    const text = String(body.text || '').trim().slice(0, MAX_TEXT);
    if (!text) return send(res, 400, { error: 'Empty message' });
    const now = Date.now();
    const recent = (lastSent.get(me.k) || []).filter((t) => now - t < 4000);
    if (recent.length >= 5) return send(res, 429, { error: 'Slow down' });
    lastSent.set(me.k, [...recent, now]);

    const message = { type: 'chat', from: me.user.name, text, time: now };
    if (body.to) {
      const to = key(body.to);
      if (!users[to] || !me.user.friends.includes(to)) return send(res, 403, { error: 'You can only message friends' });
      message.to = users[to].name;
      push(to, message);
      if (to !== me.k) push(me.k, message);
    } else {
      history.push(message);
      if (history.length > 50) history.shift();
      pushAll(message);
    }
    send(res, 200, {});
  },

  'GET /events': (req, res, url, body, me) => {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
      'Access-Control-Allow-Origin': '*',
    });
    const set = streams.get(me.k) || new Set();
    const wasOnline = set.size > 0;
    set.add(res);
    streams.set(me.k, set);
    res.write(`data: ${JSON.stringify({ type: 'history', messages: history })}\n\n`);
    if (!wasOnline) for (const f of me.user.friends) push(f, { type: 'friends' });
    const ping = setInterval(() => res.write(': ping\n\n'), 25000);
    req.on('close', () => {
      clearInterval(ping);
      set.delete(res);
      if (!set.size) {
        streams.delete(me.k);
        for (const f of me.user.friends) push(f, { type: 'friends' });
      }
    });
  },
};

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  const route = routes[`${req.method} ${url.pathname}`];
  if (!route) return send(res, 404, { error: 'Not found' });
  try {
    const body = req.method === 'POST' ? await readBody(req) : {};
    let me = null;
    if (url.pathname !== '/register') {
      me = auth(req, url);
      if (!me) return send(res, 401, { error: 'Not signed in' });
    }
    await route(req, res, url, body, me);
  } catch (err) {
    if (!res.headersSent) send(res, 400, { error: err.message });
  }
});

if (require.main === module) {
  server.listen(PORT, () => console.log(`Blade social server on port ${PORT}`));
}
module.exports = server;
