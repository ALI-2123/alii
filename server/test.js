const assert = require('assert');
const os = require('os');
const path = require('path');
process.env.DATA_FILE = path.join(os.tmpdir(), `blade-test-${Date.now()}.json`);
const server = require('./server');

async function call(base, method, route, who, body) {
  const res = await fetch(base + route, {
    method,
    headers: { 'Content-Type': 'application/json', ...(who ? { 'x-name': who.name, 'x-token': who.token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, body: await res.json() };
}

server.listen(0, async () => {
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const a = (await call(base, 'POST', '/register', null, { name: 'Alice' })).body;
    const b = (await call(base, 'POST', '/register', null, { name: 'Bob_1' })).body;
    assert.strictEqual((await call(base, 'POST', '/register', null, { name: 'alice' })).status, 409);
    assert.strictEqual((await call(base, 'POST', '/register', null, { name: 'Alice', token: a.token })).status, 200);
    assert.strictEqual((await call(base, 'GET', '/me', { name: 'Alice', token: 'bad' })).status, 401);

    // Bob listens for events
    const events = [];
    const stream = await fetch(`${base}/events?name=Bob_1&token=${b.token}`);
    const reader = stream.body.getReader();
    (async () => {
      const dec = new TextDecoder();
      for (;;) {
        const { value, done } = await reader.read();
        if (done) return;
        for (const line of dec.decode(value).split('\n')) if (line.startsWith('data: ')) events.push(JSON.parse(line.slice(6)));
      }
    })();
    await new Promise((r) => setTimeout(r, 100));

    assert.strictEqual((await call(base, 'POST', '/chat', a, { to: 'Bob_1', text: 'hi' })).status, 403);
    await call(base, 'POST', '/friends/request', a, { to: 'Bob_1' });
    assert.deepStrictEqual((await call(base, 'GET', '/me', b)).body.requests, ['Alice']);
    await call(base, 'POST', '/friends/accept', b, { from: 'Alice' });
    const me = (await call(base, 'GET', '/me', a)).body;
    assert.deepStrictEqual(me.friends, [{ name: 'Bob_1', online: true }]);

    await call(base, 'POST', '/chat', a, { text: 'hello world' });
    await call(base, 'POST', '/chat', a, { to: 'Bob_1', text: 'psst' });
    await new Promise((r) => setTimeout(r, 150));
    const chats = events.filter((e) => e.type === 'chat');
    assert.ok(chats.some((e) => e.text === 'hello world' && !e.to));
    assert.ok(chats.some((e) => e.text === 'psst' && e.to === 'Bob_1'));
    assert.ok(events.some((e) => e.type === 'request'));

    await call(base, 'POST', '/cosmetics', a, { aura: 'flame', wings: 'blade', blade: 'bogus', trail: 'spark' });
    await call(base, 'POST', '/cosmetics', b, { wings: 'shadow' });
    const cos = (await call(base, 'GET', '/cosmetics', b)).body;
    assert.deepStrictEqual(cos.bob_1, { aura: 'none', wings: 'shadow', blade: 'none', trail: 'none' });
    assert.strictEqual(cos.alice, undefined); // Alice isn't online
    await call(base, 'POST', '/friends/remove', b, { name: 'Alice' });
    assert.deepStrictEqual((await call(base, 'GET', '/me', a)).body.friends, []);
    console.log('All server tests passed');
  } catch (err) {
    console.error(err);
    process.exitCode = 1;
  }
  process.exit();
});
