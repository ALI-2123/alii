const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const UA = 'BladeClient/0.2 (Minecraft launcher)';

async function getJson(url) {
  const res = await fetch(url, { headers: { 'User-Agent': UA, Accept: 'application/json' } });
  if (!res.ok) throw new Error(`Request failed (${res.status}): ${url}`);
  return res.json();
}

function sha1Of(buffer) {
  return crypto.createHash('sha1').update(buffer).digest('hex');
}

async function downloadFile(url, dest, sha1) {
  try {
    if (sha1 && sha1Of(fs.readFileSync(dest)) === sha1) return;
  } catch {
    /* not downloaded yet */
  }
  const res = await fetch(url, { headers: { 'User-Agent': UA } });
  if (!res.ok) throw new Error(`Download failed (${res.status}): ${url}`);
  const buffer = Buffer.from(await res.arrayBuffer());
  if (sha1 && sha1Of(buffer) !== sha1) throw new Error(`Checksum mismatch: ${url}`);
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.writeFileSync(dest, buffer);
}

async function pool(items, size, worker) {
  let next = 0;
  const run = async () => {
    while (next < items.length) {
      const item = items[next];
      next += 1;
      await worker(item);
    }
  };
  await Promise.all(Array.from({ length: Math.min(size, items.length) }, run));
}

module.exports = { getJson, downloadFile, pool };
