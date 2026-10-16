const fs = require('fs');
const path = require('path');
const { getJson, downloadFile } = require('./util');

const MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const FABRIC_META = 'https://meta.fabricmc.net/v2/versions/loader';

let manifest = null;
const cache = new Map();

async function loadManifest() {
  if (!manifest) manifest = await getJson(MANIFEST_URL);
  return manifest;
}

function fabricSupported(id, type) {
  const match = /^1\.(\d+)/.exec(id);
  return type === 'release' && !!match && Number(match[1]) >= 14;
}

async function list({ snapshots = false, old = false } = {}) {
  const allowed = new Set(['release']);
  if (snapshots) allowed.add('snapshot');
  if (old) {
    allowed.add('old_beta');
    allowed.add('old_alpha');
  }
  return (await loadManifest()).versions
    .filter((v) => allowed.has(v.type))
    .map((v) => ({ id: v.id, type: v.type, fabric: fabricSupported(v.id, v.type) }));
}

async function get(id) {
  if (cache.has(id)) return cache.get(id);
  const entry = (await loadManifest()).versions.find((v) => v.id === id);
  if (!entry) throw new Error(`Unknown Minecraft version: ${id}`);
  const version = {
    id,
    type: entry.type,
    fabric: fabricSupported(id, entry.type),
    json: await getJson(entry.url),
  };
  cache.set(id, version);
  return version;
}

// group:artifact (plus classifier when present), ignoring the version, so Fabric's copy of a library wins.
function libKey(lib) {
  const [group, artifact, , classifier] = lib.name.split(':');
  return [group, artifact, classifier].filter(Boolean).join(':');
}

function mergeProfile(vanilla, fabric) {
  const fabricKeys = new Set(fabric.libraries.map(libKey));
  const merged = {
    ...vanilla,
    id: fabric.id,
    mainClass: fabric.mainClass,
    libraries: [...fabric.libraries, ...vanilla.libraries.filter((lib) => !fabricKeys.has(libKey(lib)))],
  };
  delete merged.inheritsFrom;
  const part = (source, key) => (source.arguments && source.arguments[key]) || [];
  merged.arguments = {
    game: [...part(vanilla, 'game'), ...part(fabric, 'game')],
    jvm: [...part(vanilla, 'jvm'), ...part(fabric, 'jvm')],
  };
  return merged;
}

async function prepareFabric(root, version, say) {
  const base = `${FABRIC_META}/${encodeURIComponent(version.id)}`;
  const loaders = await getJson(base);
  const pick = loaders.find((entry) => entry.loader && entry.loader.stable) || loaders[0];
  if (!pick) throw new Error(`Fabric doesn't support ${version.id} yet`);
  const profile = await getJson(`${base}/${encodeURIComponent(pick.loader.version)}/profile/json`);
  const dir = path.join(root, 'versions', profile.id);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, `${profile.id}.json`), JSON.stringify(mergeProfile(version.json, profile)));
  const client = version.json.downloads.client;
  await downloadFile(client.url, path.join(dir, `${profile.id}.jar`), client.sha1);
  say(`Fabric loader ${pick.loader.version} is ready`);
  return profile.id;
}

module.exports = { list, get, prepareFabric, mergeProfile, fabricSupported };
