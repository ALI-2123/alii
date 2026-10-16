# Blade in-game mod (Fabric, Minecraft 1.21.11, 1.21.4 and 1.21.1)

What it does in game:

- Blade title screen and a red and black tint on the pause menu.
- Right Shift opens the Blade menu: red to black fade background with the black-to-red blade in the middle.
  - HUD tab: turn PvP modules on and off (FPS, CPS, Ping, Coordinates, Keystrokes, Armor, Potions) and drag them anywhere on screen.
  - Cosmetics tab: wings, blade, aura, trail. Other Blade players see them.
  - Friends tab: add friends, accept or decline requests, see who is online, message a friend.
  - Chat tab: global chat and direct messages. Press Y to open it straight away.
- Friend requests and direct messages also show up in the normal Minecraft chat as `[Blade]` lines.

## Build it, easiest way (no installs): GitHub

1. Make a free GitHub repository and upload this whole project (the folder with `.github`, `mod`, `src`...).
2. Open the **Actions** tab, wait for **Build Blade mod** to turn green.
3. Open that run and download the `blade-client-1.21.11`, `blade-client-1.21.4` and `blade-client-1.21.1` files. Unzip them.
4. Copy the `.jar` files into the launcher's `bundled/` folder.

## Build it on your PC

You need Java 21 and Gradle 8.14.

    cd mod
    gradle build -Pmc=1.21.11
    gradle build -Pmc=1.21.4
    gradle build -Pmc=1.21.1

Jars are in `build/libs/` named like `blade-client-0.4.0+1.21.4.jar` (use the one without `-sources`).

## Install it

Copy the jar into the launcher's `bundled/` folder, pick the same Minecraft version in the launcher, turn on the Fabric loader, press **Install performance pack** (it adds Fabric API), then **Install Blade mod** on the Mods page.

## Notes

- Friends and chat need the Blade server (`server/`) running and its address set on the launcher's Friends page. The launcher signs the game in for you.
- Wings, blade, aura and trail are drawn with particles, so only Blade players see them.
- Cape and emotes aren't drawn yet.
- This code could not be compiled where it was written. If the build fails, send the first lines of the error and it will be fixed.

## Build for each version

    powershell -ExecutionPolicy Bypass -File ..\build-mod.ps1 -Mc 1.21.11

Or use `-Mc 1.21.4`, `-Mc 1.21.1` or `-Mc all`. With Gradle 8.14 yourself: `gradle build -Pmc=1.21.11` inside this folder.

## PvP tab

Toggle Sprint, Zoom (hold C) and zoom strength. The HUD tab also has Combo, Speed, Direction, Clock, Memory, Sprint, Target health and Item counts.
