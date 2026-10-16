const fs = require('fs');
const path = require('path');
const paths = require('./paths');
const log = require('./log');
const { getJson, downloadFile } = require('./util');

const API = 'https://api.modrinth.com/v2';
// Every project is optional: a mod with no build for the chosen Minecraft version is skipped, not fatal.
const PACK = [
  'fabric-api',
  'sodium',
  'sodium-extra',
  'lithium',
  'ferrite-core',
  'entityculling',
  'moreculling',
  'immediatelyfast',
  'modernfix',
  'krypton',
  'c2me-fabric',
  'noisium',
  'scalablelux',
  'dynamic-fps',
  'memoryleakfix',
  'badoptimizations',
];
const JAR = /\.jar(\.disabled)?$/i;

function dirFor(version) {
  const dir = paths.mods(version);
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

function resolve(version, file) {
  const name = path.basename(String(file));
  if (name !== file || !JAR.test(name)) throw new Error('Invalid mod file');
  return path.join(dirFor(version), name);
}

function list(version) {
  const dir = dirFor(version);
  return fs
    .readdirSync(dir)
    .filter((file) => JAR.test(file))
    .sort((a, b) => a.localeCompare(b))
    .map((file) => ({
      file,
      name: file.replace(JAR, ''),
      enabled: !/\.disabled$/i.test(file),
      size: fs.statSync(path.join(dir, file)).size,
    }));
}

function toggle(version, file) {
  const src = resolve(version, file);
  fs.renameSync(src, /\.disabled$/i.test(src) ? src.replace(/\.disabled$/i, '') : `${src}.disabled`);
}

function remove(version, file) {
  fs.unlinkSync(resolve(version, file));
}

function add(version, files) {
  const dir = dirFor(version);
  let added = 0;
  for (const file of files) {
    if (!/\.jar$/i.test(file)) continue;
    fs.copyFileSync(file, path.join(dir, path.basename(file)));
    added += 1;
  }
  return added;
}

const indexFile = (dir) => path.join(dir, '.blade-mods.json');

function readIndex(dir) {
  try {
    return JSON.parse(fs.readFileSync(indexFile(dir), 'utf8'));
  } catch {
    return {};
  }
}

async function search(query, version) {
  const facets = JSON.stringify([['project_type:mod'], ['categories:fabric'], [`versions:${version}`]]);
  const url = `${API}/search?query=${encodeURIComponent(query)}&facets=${encodeURIComponent(facets)}&limit=20`;
  const data = await getJson(url);
  return data.hits.map((hit) => ({
    slug: hit.slug,
    title: hit.title,
    description: hit.description,
    downloads: hit.downloads,
  }));
}

async function installProject(idOrSlug, mcVersion, ctx) {
  if (ctx.seen.has(idOrSlug)) return;
  ctx.seen.add(idOrSlug);
  const loaders = encodeURIComponent('["fabric"]');
  const games = encodeURIComponent(JSON.stringify([mcVersion]));
  const found = await getJson(
    `${API}/project/${encodeURIComponent(idOrSlug)}/version?loaders=${loaders}&game_versions=${games}`
  );
  const pick = found.find((v) => v.version_type === 'release') || found[0];
  if (!pick) {
    ctx.skipped.push(idOrSlug);
    log.write('mods', `No Fabric build of "${idOrSlug}" for ${mcVersion}, skipped`);
    return;
  }
  const file = pick.files.find((f) => f.primary) || pick.files[0];
  const previous = ctx.index[pick.project_id];
  if (previous && previous !== file.filename) {
    for (const name of [previous, `${previous}.disabled`]) fs.rmSync(path.join(ctx.dir, name), { force: true });
  }
  await downloadFile(file.url, path.join(ctx.dir, file.filename), file.hashes && file.hashes.sha1);
  ctx.index[pick.project_id] = file.filename;
  if (!ctx.installed.includes(file.filename)) {
    ctx.installed.push(file.filename);
    log.write('mods', `Installed ${file.filename}`);
  }
  for (const dep of pick.dependencies || []) {
    if (dep.dependency_type === 'required' && dep.project_id) {
      await installProject(dep.project_id, mcVersion, ctx);
    }
  }
}

// With tolerant = true (the performance pack) one failing project is skipped instead of stopping the rest.
async function install(version, slugs, tolerant) {
  const dir = dirFor(version);
  const ctx = { dir, index: readIndex(dir), seen: new Set(), installed: [], skipped: [] };
  try {
    for (const slug of slugs) {
      try {
        await installProject(slug, version, ctx);
      } catch (err) {
        if (!tolerant) throw err;
        ctx.skipped.push(slug);
        log.write('mods', `Couldn't install "${slug}": ${err.message}`);
      }
    }
  } finally {
    fs.writeFileSync(indexFile(dir), JSON.stringify(ctx.index, null, 2));
  }
  return { installed: ctx.installed, skipped: ctx.skipped };
}

const BLADE_MC = ['1.21.1', '1.21.4', '1.21.11'];

// Copies the built Blade mod (put it in the launcher's bundled folder) into this version's mods folder.
// Jar names look like blade-client-0.2.0+1.21.4.jar, so the Minecraft version is picked from the name.
function installBlade(version) {
  if (!BLADE_MC.includes(version)) {
    throw new Error(`The Blade mod is built for Minecraft ${BLADE_MC.join(', ')}. Pick one of those versions first`);
  }
  const bundled = path.join(__dirname, '..', 'bundled');
  const jar = fs.existsSync(bundled)
    ? fs.readdirSync(bundled)
        .filter((f) => /^blade-client/i.test(f) && f.endsWith(`+${version}.jar`) && !/sources|dev/i.test(f))
        .sort((a, b) => a.localeCompare(b, undefined, { numeric: true }))
        .pop()
    : null;
  if (!jar) {
    throw new Error(`No Blade mod for ${version} in the bundled folder. Build it (see mod/README.md) and copy blade-client-<version>+${version}.jar there`);
  }
  const dir = dirFor(version);
  for (const old of fs.readdirSync(dir)) if (/^blade-client.*\.jar(\.disabled)?$/i.test(old)) fs.rmSync(path.join(dir, old));
  fs.copyFileSync(path.join(bundled, jar), path.join(dir, jar));
  log.write('mods', `Installed ${jar}`);
  return { jar };
}

module.exports = {
  installBlade,
  list,
  toggle,
  remove,
  add,
  search,
  install: (version, slug) => install(version, [slug], false),
  installPack: (version) => install(version, PACK, true),
};
