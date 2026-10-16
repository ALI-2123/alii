# BladeSMP server

A Paper 1.21.11 server with the BladeSMP plugin (it also builds for older Paper versions, see below).

## How the Blade works

Everyone gets a **black Blade** the first time they join. It can't be dropped, stored in chests, or lost on death.

| Level | How | Look | Sharpness | Right click ability |
|-------|-----|------|-----------|---------------------|
| 0 | Start | Black | none | none |
| 1 | 5 kills | Black with a little blood | 3 | **Phantom Cloak**: invisible for 5 seconds, armor and held items hidden too |
| 2 | 10 kills | More blood | 4 | Cloak, then right click again to **throw mini blades** (up to 3, half a heart each) |
| 3 (max) | 15 kills | Black and red fade, glowing | 5 | Cloak, mini blades, and a **black hole**: the ground turns black in 10 blocks and everyone caught is stunned for 10 seconds |

Only player kills count. The same victim only counts once every 10 minutes so friends can't farm each other. Everything is in `plugins/BladeSMP/config.yml` (run `/blade reload` after editing).

Admin commands (ops only): `/blade give [player]`, `/blade level <0-3> [player]`, `/blade reload`.

## Set it up (Windows)

1. Build the plugin (from the main Blade folder):
   `powershell -ExecutionPolicy Bypass -File .\build-mod.ps1 -Target plugin`
2. In this folder:
   `powershell -ExecutionPolicy Bypass -File .\setup-server.ps1`
   It downloads Paper 1.21.11, accepts the EULA when you type YES, and writes `start.bat`. For another version add `-Version 1.21.4`.
3. Run `start.bat`. The first start creates the config files.
4. Textures: upload `resourcepack/BladeSMP-pack.zip` to your GitHub release, then put its link in `plugins/BladeSMP/config.yml` under `resource-pack: url:` (for example `https://github.com/YOURNAME/REPO/releases/latest/download/BladeSMP-pack.zip`) and run `/blade reload`. The sha1 is already filled in. If you change the pack, run `Get-FileHash -Algorithm SHA1 BladeSMP-pack.zip` and put the new value there. Without the pack the Blade is still a black netherite sword and everything works, you just don't see the blood and fade textures.
5. In the Blade launcher: Client page, set **BladeSMP address**, then press **Join BladeSMP** on the Play page.

## Letting friends join

- On your own network: friends need your public IP (or a hosting service) and you must forward TCP port 25565 on your router to this computer.
- Or rent a small Minecraft host, upload these files, and use its address.
- `online-mode=true` means only real Minecraft accounts can join. Turning it off lets cracked players in, but then anyone can pretend to be anyone, so add a login plugin like AuthMe first.

## Friends and chat inside Blade

The Blade launcher's friends and chat use a separate small server (`../server`, run with `node server/server.js`). Run it on the same machine and set its address on the launcher's Friends page.

## Versions

- The server and the players need the same Minecraft version, 1.21.11 by default. The Blade textures need Minecraft 1.21.4 or newer.
- To build the plugin for another Paper version: `powershell -ExecutionPolicy Bypass -File ..\build-mod.ps1 -Target plugin -Server 1.21.4`, then run `setup-server.ps1 -Version 1.21.4`.
