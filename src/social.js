const store = require('./store');
const log = require('./log');

let emit = () => {};
let session = null; // { name, token, base, closed, abort }

const setSink = (fn) => { emit = fn; };

function baseUrl() {
  const raw = String(store.get().serverUrl || '').trim().replace(/\/+$/, '');
  let url;
  try {
    url = new URL(raw);
  } catch {
    throw new Error('The server address is not a valid URL');
  }
  if (!/^https?:$/.test(url.protocol)) throw new Error('The server address must start with http:// or https://');
  return raw;
}

async function request(base, name, token, method, route, body) {
  const headers = { 'Content-Type': 'application/json' };
  if (name) {
    headers['x-name'] = name;
    headers['x-token'] = token;
  }
  let res;
  try {
    res = await fetch(base + route, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(4000),
    });
  } catch {
    throw new Error("Couldn't reach the Blade server. Check the address on the Friends page");
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `Server error (${res.status})`);
  return data;
}

function api(method, route, body) {
  if (!session) throw new Error('Not connected to the Blade server');
  return request(session.base, session.name, session.token, method, route, body);
}

async function stream(s) {
  while (!s.closed) {
    try {
      s.abort = new AbortController();
      const res = await fetch(`${s.base}/events`, {
        headers: { 'x-name': s.name, 'x-token': s.token },
        signal: s.abort.signal,
      });
      if (!res.ok) throw new Error(`status ${res.status}`);
      emit({ type: 'status', connected: true });
      const decoder = new TextDecoder();
      let buffer = '';
      for await (const chunk of res.body) {
        buffer += decoder.decode(chunk, { stream: true });
        let end;
        while ((end = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, end);
          buffer = buffer.slice(end + 2);
          for (const line of block.split('\n')) {
            if (!line.startsWith('data: ')) continue;
            try {
              emit(JSON.parse(line.slice(6)));
            } catch {
              /* ignore a broken event */
            }
          }
        }
      }
    } catch (err) {
      if (s.closed) return;
      log.write('social', `Connection lost: ${err.message}`);
    }
    if (s.closed) return;
    emit({ type: 'status', connected: false });
    await new Promise((resolve) => setTimeout(resolve, 3000));
  }
}

function disconnect() {
  if (!session) return;
  session.closed = true;
  if (session.abort) session.abort.abort();
  session = null;
  emit({ type: 'status', connected: false });
}

// Registers (or re-uses) this name on the server and returns what the game needs to sign in.
async function ensure(name) {
  const base = baseUrl();
  const saved = store.get();
  const known = saved.socialName === name && saved.socialBase === base ? saved.socialToken : undefined;
  const account = await request(base, null, null, 'POST', '/register', { name, token: known });
  store.set({ socialName: account.name, socialToken: account.token, socialBase: base });
  return { url: base, name: account.name, token: account.token };
}

async function connect(name) {
  disconnect();
  const { url: base, ...account } = await ensure(name);
  session = { name: account.name, token: account.token, base, closed: false, abort: null };
  stream(session);
  log.write('social', `Connected to ${base} as ${account.name}`);
  return request(base, account.name, account.token, 'GET', '/me');
}

const FRIEND_ROUTES = {
  request: ['/friends/request', 'to'],
  accept: ['/friends/accept', 'from'],
  decline: ['/friends/decline', 'from'],
  remove: ['/friends/remove', 'name'],
};

function friend(action, name) {
  const entry = FRIEND_ROUTES[action];
  if (!entry) throw new Error('Unknown friend action');
  return api('POST', entry[0], { [entry[1]]: String(name || '') });
}

const chat = (text, to) => api('POST', '/chat', { text: String(text || ''), to: to || undefined });
const me = () => api('GET', '/me');

module.exports = { setSink, ensure, connect, disconnect, friend, chat, me };
