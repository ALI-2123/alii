const fs = require('fs');
const { Client, Authenticator } = require('minecraft-launcher-core');
const { Auth } = require('msmc');
const store = require('./store');
const paths = require('./paths');
const log = require('./log');
const versions = require('./versions');
const java = require('./java');
const options = require('./options');
const profile = require('./profile');
const social = require('./social');
const account = require('./account');

const BASE_ARGS = ['-Dlog4j2.formatMsgNoLookups=true'];

const PRESETS = {
  default: [],
  performance: [
    '-XX:+UseG1GC',
    '-XX:+ParallelRefProcEnabled',
    '-XX:MaxGCPauseMillis=200',
    '-XX:+UnlockExperimentalVMOptions',
    '-XX:+DisableExplicitGC',
    '-XX:G1NewSizePercent=30',
    '-XX:G1MaxNewSizePercent=40',
    '-XX:G1HeapRegionSize=8M',
    '-XX:G1ReservePercent=20',
    '-XX:G1HeapWastePercent=5',
    '-XX:G1MixedGCCountTarget=4',
    '-XX:InitiatingHeapOccupancyPercent=15',
    '-XX:G1MixedGCLiveThresholdPercent=90',
    '-XX:G1RSetUpdatingPauseTimePercent=5',
    '-XX:SurvivorRatio=32',
    '-XX:+PerfDisableSharedMem',
    '-XX:MaxTenuringThreshold=1',
  ],
};

let premiumAuth = null;
let premiumAt = 0; // when the Minecraft token was last refreshed
let running = false;
const TOKEN_MAX_AGE = 6 * 60 * 60 * 1000; // Minecraft tokens last about a day; refresh well before

function accountState() {
  return { premium: premiumAuth ? premiumAuth.name : null };
}

async function loginPremium() {
  try {
    log.write('launcher', 'Opening Microsoft sign-in…');
    const authManager = new Auth('select_account');
    const xbox = await authManager.launch('electron');
    const minecraft = await xbox.getMinecraft();
    premiumAuth = minecraft.mclc();
    premiumAt = Date.now();
    const saved = typeof xbox.save === 'function' ? account.save(xbox.save()) : false;
    log.write('launcher', `Signed in as ${premiumAuth.name}${saved ? ' (remembered on this computer)' : ''}`);
    return { ok: true, name: premiumAuth.name };
  } catch (err) {
    premiumAuth = null;
    const message = String(err && err.message ? err.message : err);
    log.write('launcher', `Sign-in failed: ${message}`);
    if (message.includes('closed')) return { ok: false, error: 'Sign-in was cancelled' };
    return { ok: false, error: `Couldn't sign in. ${message}` };
  }
}

// Signs back in with the remembered Microsoft login. Returns true if an account is ready afterwards.
async function restorePremium() {
  const token = account.load();
  if (!token) return Boolean(premiumAuth);
  try {
    const authManager = new Auth('select_account');
    const xbox = await authManager.refresh(token);
    const minecraft = await xbox.getMinecraft();
    premiumAuth = minecraft.mclc();
    premiumAt = Date.now();
    if (typeof xbox.save === 'function') account.save(xbox.save());
    log.write('launcher', `Signed back in as ${premiumAuth.name}`);
    return true;
  } catch (err) {
    const message = String(err && err.message ? err.message : err);
    log.write('launcher', `Couldn't restore the saved sign-in: ${message}`);
    // Only forget the login if Microsoft rejected it, not when the internet is just down.
    if (!/ENOTFOUND|ECONN|ETIMEDOUT|EAI_AGAIN|fetch failed|network/i.test(message)) account.clear();
    return Boolean(premiumAuth);
  }
}

function logout() {
  premiumAuth = null;
  premiumAt = 0;
  account.clear();
  log.write('launcher', 'Signed out');
}

async function pickAuthorization(settings) {
  if (settings.mode === 'premium') {
    if (!premiumAuth || Date.now() - premiumAt > TOKEN_MAX_AGE) await restorePremium();
    if (!premiumAuth) throw new Error('Sign in with Microsoft on the Premium page first');
    return premiumAuth;
  }
  if (!/^[A-Za-z0-9_]{3,16}$/.test(settings.username)) {
    throw new Error('Set a username on the Cracked page first (3 to 16 letters, numbers, or underscores)');
  }
  return Authenticator.getAuth(settings.username);
}

async function resolveJava(settings, version, say, progress) {
  if (settings.javaPath) {
    if (settings.javaPath.includes('"') || !fs.existsSync(settings.javaPath)) {
      throw new Error("The Java path in Client settings doesn't exist");
    }
    return settings.javaPath;
  }
  const component = (version.json.javaVersion && version.json.javaVersion.component) || 'jre-legacy';
  say(`Checking Java (${component})…`);
  return java.ensure(component, say, progress);
}

async function launch({ version: id, join }, emit) {
  if (running) return { ok: false, error: 'The game is already running' };
  running = true;
  const say = (text) => {
    log.write('launcher', text);
    emit('game:status', text);
  };
  const progress = (payload) => emit('game:progress', payload);

  try {
    const settings = store.get();
    const authorization = await pickAuthorization(settings);
    let joinAddress = null;
    if (join) {
      joinAddress = String(settings.smpAddress || '').trim();
      if (!/^[A-Za-z0-9.-]+(:\d{1,5})?$/.test(joinAddress)) {
        throw new Error('Set the BladeSMP address on the Client page first (like play.example.com or localhost:25565)');
      }
    }
    say(`Preparing Minecraft ${id} with a ${settings.mode === 'premium' ? 'Premium' : 'Cracked'} account…`);

    const version = await versions.get(id);
    const javaPath = await resolveJava(settings, version, say, progress);

    const gameDir = paths.instance(id);
    fs.mkdirSync(gameDir, { recursive: true });
    if (settings.applyVideo) options.apply(gameDir, settings);
    let server = null;
    try {
      server = await social.ensure(authorization.name);
    } catch (err) {
      log.write('launcher', `Blade server unavailable, friends and chat will be off in game: ${err.message}`);
    }
    profile.write(gameDir, settings, authorization.name, server);

    const versionOption = { number: version.id, type: version.type };
    if (settings.loader === 'fabric') {
      if (version.fabric) {
        say('Preparing Fabric…');
        versionOption.custom = await versions.prepareFabric(paths.root(), version, say);
      } else {
        say(`Fabric isn't available for ${id}, so it will start without mods`);
      }
    }

    const launcher = new Client();
    launcher.on('progress', (e) => progress({ type: e.type, task: e.task, total: e.total }));
    launcher.on('debug', (line) => log.write('launcher', line));
    launcher.on('data', (line) => log.write('game', line));
    launcher.on('close', (code) => {
      running = false;
      log.write('launcher', `Game closed (exit code ${code})`);
      emit('game:closed', { code });
    });

    if (joinAddress) say(`Joining BladeSMP at ${joinAddress} once the game loads…`);
    say('Downloading game files…');
    const child = await launcher.launch({
      authorization,
      root: paths.root(),
      javaPath,
      version: versionOption,
      memory: { max: `${settings.ramGb}G`, min: `${Math.max(1, Math.floor(settings.ramGb / 2))}G` },
      window: { width: settings.width, height: settings.height, fullscreen: settings.fullscreen },
      customArgs: [...BASE_ARGS, ...PRESETS[settings.jvmPreset]],
      customLaunchArgs: joinAddress ? ['--quickPlayMultiplayer', joinAddress] : [],
      overrides: { gameDirectory: gameDir, cwd: gameDir },
    });
    if (!child) throw new Error("Couldn't start the game. Open the Logs page to see why");

    say('Game running');
    return { ok: true };
  } catch (err) {
    running = false;
    log.write('launcher', `Launch failed: ${err.message}`);
    return { ok: false, error: err.message };
  }
}

module.exports = { accountState, loginPremium, restorePremium, logout, launch };
