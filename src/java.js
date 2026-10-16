const fs = require('fs');
const path = require('path');
const paths = require('./paths');
const { getJson, downloadFile, pool } = require('./util');

const INDEX_URL =
  'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';

function platformKey() {
  const { platform, arch } = process;
  if (platform === 'win32') {
    if (arch === 'arm64') return 'windows-arm64';
    return arch === 'ia32' ? 'windows-x86' : 'windows-x64';
  }
  if (platform === 'darwin') return arch === 'arm64' ? 'mac-os-arm64' : 'mac-os';
  return arch === 'ia32' ? 'linux-i386' : 'linux';
}

function binPath(dir) {
  if (process.platform === 'win32') return path.join(dir, 'bin', 'java.exe');
  if (process.platform === 'darwin') return path.join(dir, 'jre.bundle', 'Contents', 'Home', 'bin', 'java');
  return path.join(dir, 'bin', 'java');
}

async function ensure(component, say, progress) {
  const dir = path.join(paths.runtime(), component);
  const bin = binPath(dir);
  const marker = path.join(dir, '.complete');
  if (fs.existsSync(marker) && fs.existsSync(bin)) return bin;

  say(`Downloading Java runtime "${component}" (first launch only)…`);
  const index = await getJson(INDEX_URL);
  const platform = index[platformKey()];
  const entry = platform && platform[component] && platform[component][0];
  if (!entry) {
    throw new Error(`No Java runtime "${component}" is available for this system. Set a Java path in Client settings.`);
  }
  const manifest = await getJson(entry.manifest.url);
  const files = Object.entries(manifest.files);
  fs.mkdirSync(dir, { recursive: true });
  for (const [rel, file] of files) {
    if (file.type === 'directory') fs.mkdirSync(path.join(dir, rel), { recursive: true });
  }
  const jobs = files.filter(([, file]) => file.type === 'file');
  let done = 0;
  await pool(jobs, 8, async ([rel, file]) => {
    const dest = path.join(dir, rel);
    await downloadFile(file.downloads.raw.url, dest, file.downloads.raw.sha1);
    if (file.executable) fs.chmodSync(dest, 0o755);
    done += 1;
    progress({ type: 'java', task: done, total: jobs.length });
  });
  for (const [rel, file] of files) {
    if (file.type !== 'link') continue;
    const link = path.join(dir, rel);
    try {
      fs.rmSync(link, { force: true });
      fs.symlinkSync(file.target, link);
    } catch {
      /* symlinks aren't needed on Windows */
    }
  }
  fs.writeFileSync(marker, new Date().toISOString());
  say('Java runtime is ready');
  return bin;
}

module.exports = { ensure, platformKey };
