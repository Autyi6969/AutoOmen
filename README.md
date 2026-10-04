<p align="center"><img src="https://raw.githubusercontent.com/Autyism/AutoOmen/master/src/main/resources/assets/autoomen/icon.png" width="128" alt="icon"></p>
<h1 align="center">Auto Omen</h1>
<p align="center">Keeps your raid farm running while you are AFK: drinks the next Ominous Bottle for you, at exactly the right moment.</p>

**English** | [简体中文](https://github.com/Autyism/AutoOmen/blob/master/README.zh_CN.md)

![Minecraft 26.2 | 26.3](https://img.shields.io/badge/Minecraft-26.2%20%7C%2026.3-62B47A) ![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4) ![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue)

Auto Omen is a small client-side Fabric mod for Minecraft 26.2 and 26.3. You stand at your raid farm, it watches the raid boss bar and your effects, and as soon as the next raid can start it drinks an Ominous Bottle from your inventory and puts your sword back in your hand.

## Features

- **Drinks at the right time.** Only when you have neither Bad Omen nor Raid Omen, so no bottle is ever thrown away on an omen you already have.
- **Two timings.**
  - *After the raid* (default): drinks once the raid boss bar has disappeared.
  - *At victory*: drinks the moment the bar says "Raid - Victory". The new raid starts right after the old one is removed, about 30 seconds sooner per raid, and the bottle is still never merged into the old raid.
- **Finds the bottle anywhere.** Hotbar, main inventory (swapped onto a free hotbar slot with a normal inventory click) or offhand. Afterwards you hold the same slot as before.
- **Never clicks what you look at.** The bottle is used directly, so buttons, doors or beds in front of you are left alone.
- **Bottle priority.** Lowest level first, highest level first, or only one level (I to V).
- **Health limit.** No bottle while your health is below a value you choose.
- **Chat message** after every bottle, with how many are left (can be turned off).
- **Safety stop.** If two bottles in a row do not start a raid (raids turned off, you are not in the village, ...), the mod pauses and tells you, instead of drinking all your bottles.
- **Toggle key** (unbound by default) and a settings screen through Mod Menu.
- English and Simplified Chinese.

## When exactly is it safe to drink?

A Raid Omen that runs out while a raid is still going (or still celebrating its victory) is merged into that raid instead of starting a new one. The old raid is removed 30 seconds after its victory, and a Raid Omen lasts 30 seconds, so a bottle drunk once "Victory" is shown always starts its own new raid. The full explanation, checked against the 26.2 source (26.3 behaves the same in the self-test), is in [docs/MECHANICS.md](https://github.com/Autyism/AutoOmen/blob/master/docs/MECHANICS.md) (Chinese).

## Requirements

- Minecraft 26.2 or 26.3 (one file each: `autoomen-0.1.0.jar` for 26.2, `autoomen-0.1.0+26.3.jar` for 26.3), Fabric Loader 0.19.5 or newer, Fabric API
- Mod Menu (optional, for the settings button)
- Client only. Works in singleplayer; you have to stand within 96 blocks of the raid centre so the game shows you the raid boss bar.

## License

GPL-3.0-only, see [LICENSE](LICENSE).

## Building

Gradle has to run on Java 25 (Fabric Loom 1.18):

```
./gradlew build -Dorg.gradle.java.home=<path to JDK 25>
```

Made by Autyism.
